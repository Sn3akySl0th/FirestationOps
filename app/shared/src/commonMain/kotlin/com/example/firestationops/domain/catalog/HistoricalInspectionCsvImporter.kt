package com.example.firestationops.domain.catalog

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.InspectionEntrySource
import com.example.firestationops.domain.model.InspectionResponse
import com.example.firestationops.domain.model.InspectionStatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.InspectionTemplateItem
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.SyncStatus
import com.example.firestationops.domain.sync.SyncStatusTransitions
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Parses completed inspection history CSV (long format: one row per item response).
 *
 * Columns (flexible order, case-insensitive):
 * importGroupKey, apparatusRadioName, templateName, completedAt, completedByEmail,
 * completedByMemberNumber, category, itemText, status, actualQuantity, note
 */
object HistoricalInspectionCsvImporter {

    data class ItemMatch(
        val category: String?,
        val itemText: String,
        val status: InspectionStatus,
        val actualQuantity: Int?,
        val note: String?,
        val templateItem: InspectionTemplateItem?,
        val unmatched: Boolean
    )

    data class InspectionPreview(
        val importGroupKey: String,
        val apparatus: Apparatus?,
        val template: InspectionTemplate?,
        val completedAt: Long?,
        val completedBy: Member?,
        val items: List<ItemMatch>,
        val duplicate: Boolean,
        val errors: List<String>
    ) {
        val canImport: Boolean
            get() = errors.isEmpty() &&
                apparatus != null &&
                template != null &&
                completedAt != null &&
                completedBy != null &&
                items.any { !it.unmatched } &&
                !duplicate
    }

    data class Preview(
        val inspections: List<InspectionPreview>,
        val warnings: List<String> = emptyList()
    ) {
        val importableCount: Int get() = inspections.count { it.canImport }
        val blockedCount: Int get() = inspections.size - importableCount
        val summary: String
            get() = "${inspections.size} inspection(s): $importableCount ready, $blockedCount blocked" +
                if (warnings.isNotEmpty()) "; ${warnings.size} warning(s)" else ""
    }

    sealed class Result {
        data class Success(val preview: Preview) : Result()
        data class Failure(val message: String) : Result()
    }

    fun parse(
        csvText: String,
        apparatus: List<Apparatus>,
        templates: List<InspectionTemplate>,
        members: List<Member>,
        existingInspections: List<Inspection>,
        departmentId: String
    ): Result {
        val normalized = csvText.removePrefix("\uFEFF").trim()
        if (normalized.isEmpty()) return Result.Failure("CSV is empty.")

        val rows = TemplateCsvImporter.parseCsvRows(normalized)
        if (rows.isEmpty()) return Result.Failure("CSV is empty.")

        val header = rows.first().map { it.trim().lowercase() }
        fun idx(vararg names: String) = header.indexOfFirst { it in names }

        val groupIdx = idx("importgroupkey", "groupkey", "group")
        val radioIdx = idx("apparatusradioname", "radioname", "apparatus")
        val templateIdx = idx("templatename", "template")
        val completedAtIdx = idx("completedat", "completed", "date")
        val emailIdx = idx("completedbyemail", "email", "inspectorEmail")
        val memberNumberIdx = idx("completedbymembernumber", "membernumber", "inspectorId")
        val categoryIdx = idx("category")
        val itemTextIdx = idx("itemtext", "text", "item")
        val statusIdx = idx("status")
        val qtyIdx = idx("actualquantity", "quantity", "qty")
        val noteIdx = idx("note", "notes")

        if (radioIdx < 0 || templateIdx < 0 || completedAtIdx < 0 || itemTextIdx < 0 || statusIdx < 0) {
            return Result.Failure(
                "CSV must include apparatusRadioName, templateName, completedAt, itemText, and status columns."
            )
        }
        if (emailIdx < 0 && memberNumberIdx < 0) {
            return Result.Failure("CSV must include completedByEmail or completedByMemberNumber.")
        }

        data class RawRow(
            val groupKey: String,
            val radioName: String,
            val templateName: String,
            val completedAtRaw: String,
            val email: String?,
            val memberNumber: String?,
            val category: String?,
            val itemText: String,
            val statusRaw: String,
            val qtyRaw: String?,
            val note: String?,
            val rowNumber: Int
        )

        fun cell(cells: List<String>, index: Int): String? =
            if (index >= 0 && index < cells.size) cells[index].trim().takeIf { it.isNotEmpty() } else null

        val rawRows = mutableListOf<RawRow>()
        val warnings = mutableListOf<String>()
        rows.drop(1).forEachIndexed { offset, cells ->
            val rowNumber = offset + 2
            if (cells.all { it.isBlank() }) return@forEachIndexed
            val itemText = cell(cells, itemTextIdx)
            if (itemText.isNullOrBlank()) {
                warnings.add("Row $rowNumber: skipped (missing itemText).")
                return@forEachIndexed
            }
            val radio = cell(cells, radioIdx)
            val templateName = cell(cells, templateIdx)
            val completedAtRaw = cell(cells, completedAtIdx)
            if (radio.isNullOrBlank() || templateName.isNullOrBlank() || completedAtRaw.isNullOrBlank()) {
                warnings.add("Row $rowNumber: skipped (missing apparatus/template/date).")
                return@forEachIndexed
            }
            val groupKey = cell(cells, groupIdx)
                ?: "$completedAtRaw|$radio|$templateName"
            rawRows += RawRow(
                groupKey = groupKey,
                radioName = radio,
                templateName = templateName,
                completedAtRaw = completedAtRaw,
                email = cell(cells, emailIdx),
                memberNumber = cell(cells, memberNumberIdx),
                category = cell(cells, categoryIdx),
                itemText = itemText,
                statusRaw = cell(cells, statusIdx) ?: "PASS",
                qtyRaw = cell(cells, qtyIdx),
                note = cell(cells, noteIdx),
                rowNumber = rowNumber
            )
        }

        if (rawRows.isEmpty()) {
            return Result.Failure("No inspection item rows found in CSV.")
        }

        val apparatusByRadio = apparatus
            .filter { it.departmentId == departmentId }
            .associateBy { it.radioName.trim().lowercase() }
        val templatesByName = templates
            .filter { it.departmentId == departmentId }
            .associateBy { it.name.trim().lowercase() }

        val grouped = linkedMapOf<String, MutableList<RawRow>>()
        rawRows.forEach { row ->
            grouped.getOrPut(row.groupKey) { mutableListOf() }.add(row)
        }

        val previews = grouped.map { (groupKey, groupRows) ->
            val first = groupRows.first()
            val matchedApparatus = apparatusByRadio[first.radioName.lowercase()]
            val matchedTemplate = templatesByName[first.templateName.lowercase()]
            val completedAt = parseCompletedAt(first.completedAtRaw)
            val completedBy = resolveMember(members, departmentId, first.email, first.memberNumber)

            val errors = mutableListOf<String>()
            if (matchedApparatus == null) errors.add("Apparatus \"${first.radioName}\" not found.")
            if (matchedTemplate == null) errors.add("Template \"${first.templateName}\" not found.")
            if (completedAt == null) errors.add("Invalid completedAt \"${first.completedAtRaw}\".")
            if (completedBy == null) {
                errors.add(
                    "Inspector not found (${first.email ?: first.memberNumber ?: "missing"})."
                )
            }

            val items = groupRows.map { row ->
                val status = parseStatus(row.statusRaw)
                if (status == null) {
                    errors.add("Row ${row.rowNumber}: invalid status \"${row.statusRaw}\".")
                }
                val qty = row.qtyRaw?.toIntOrNull()
                if (!row.qtyRaw.isNullOrBlank() && qty == null) {
                    warnings.add("Row ${row.rowNumber}: invalid actualQuantity \"${row.qtyRaw}\" (ignored).")
                }
                val templateItem = matchedTemplate?.let { template ->
                    matchTemplateItem(template, row.category, row.itemText)
                }
                ItemMatch(
                    category = row.category,
                    itemText = row.itemText,
                    status = status ?: InspectionStatus.PASS,
                    actualQuantity = qty,
                    note = row.note,
                    templateItem = templateItem,
                    unmatched = matchedTemplate != null && templateItem == null
                )
            }

            if (items.any { it.unmatched }) {
                val unmatchedCount = items.count { it.unmatched }
                errors.add("$unmatchedCount item(s) did not match template checklist lines.")
            }

            val duplicate = matchedApparatus != null &&
                matchedTemplate != null &&
                completedAt != null &&
                isDuplicate(
                    existingInspections = existingInspections,
                    apparatusId = matchedApparatus.id,
                    templateId = matchedTemplate.id,
                    completedAt = completedAt
                )

            if (duplicate) {
                errors.add("Duplicate of an existing finalized inspection on the same calendar day.")
            }

            InspectionPreview(
                importGroupKey = groupKey,
                apparatus = matchedApparatus,
                template = matchedTemplate,
                completedAt = completedAt,
                completedBy = completedBy,
                items = items,
                duplicate = duplicate,
                errors = errors.distinct()
            )
        }

        return Result.Success(Preview(inspections = previews, warnings = warnings))
    }

    fun buildInspections(
        preview: Preview,
        importedAt: Long,
        importedByUserId: String,
        keepLocalOnly: Boolean
    ): List<Inspection> {
        return preview.inspections.filter { it.canImport }.map { group ->
            val apparatus = checkNotNull(group.apparatus)
            val template = checkNotNull(group.template)
            val completedAt = checkNotNull(group.completedAt)
            val member = checkNotNull(group.completedBy)
            val responses = group.items.mapNotNull { item ->
                val templateItem = item.templateItem ?: return@mapNotNull null
                InspectionResponse(
                    itemId = templateItem.id,
                    status = item.status,
                    note = item.note,
                    actualQuantity = item.actualQuantity,
                    expectedQuantity = templateItem.expectedQuantity
                )
            }
            val base = Inspection(
                id = "insp-import-${group.importGroupKey.hashCode().toUInt()}-$completedAt",
                templateId = template.id,
                apparatusId = apparatus.id,
                departmentId = apparatus.departmentId,
                startedAt = completedAt,
                completedAt = completedAt,
                startedByUserId = member.id,
                responses = responses,
                isFinalized = true,
                entrySource = InspectionEntrySource.HISTORICAL_IMPORT,
                importedAt = importedAt,
                importedByUserId = importedByUserId
            )
            if (keepLocalOnly) {
                base.copy(syncStatus = SyncStatus.LOCAL_ONLY)
            } else {
                SyncStatusTransitions.inspectionForSubmit(base)
            }
        }
    }

    private fun matchTemplateItem(
        template: InspectionTemplate,
        category: String?,
        itemText: String
    ): InspectionTemplateItem? {
        val textKey = itemText.trim().lowercase()
        val categoryKey = category?.trim()?.lowercase()
        val candidates = template.items.filter { it.text.trim().lowercase() == textKey }
        if (candidates.isEmpty()) return null
        if (categoryKey.isNullOrBlank()) return candidates.first()
        return candidates.firstOrNull {
            it.category?.trim()?.lowercase() == categoryKey
        } ?: candidates.firstOrNull()
    }

    private fun resolveMember(
        members: List<Member>,
        departmentId: String,
        email: String?,
        memberNumber: String?
    ): Member? {
        val deptMembers = members.filter { it.departmentId == departmentId && it.isActive }
        if (!email.isNullOrBlank()) {
            deptMembers.firstOrNull { it.email.equals(email, ignoreCase = true) }?.let { return it }
        }
        if (!memberNumber.isNullOrBlank()) {
            deptMembers.firstOrNull {
                it.memberNumber?.equals(memberNumber, ignoreCase = true) == true
            }?.let { return it }
        }
        return null
    }

    private fun parseStatus(raw: String): InspectionStatus? =
        when (raw.trim().uppercase()) {
            "PASS", "P", "OK", "✓", "CHECK" -> InspectionStatus.PASS
            "FAIL", "F", "X", "FAILED" -> InspectionStatus.FAIL
            "N/A", "NA", "NOT_APPLICABLE", "NOT APPLICABLE" -> InspectionStatus.NOT_APPLICABLE
            else -> runCatching { InspectionStatus.valueOf(raw.trim().uppercase()) }.getOrNull()
        }

    private fun parseCompletedAt(raw: String): Long? {
        val trimmed = raw.trim()
        trimmed.toLongOrNull()?.let { return it }
        runCatching { Instant.parse(trimmed).toEpochMilliseconds() }.getOrNull()?.let { return it }
        runCatching {
            LocalDateTime.parse(trimmed).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        }.getOrNull()?.let { return it }
        runCatching {
            LocalDate.parse(trimmed).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        }.getOrNull()?.let { return it }
        // MM-DD-YY or M-D-YY paper style (assume 20xx)
        val paper = Regex("""^(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})$""").matchEntire(trimmed)
        if (paper != null) {
            val month = paper.groupValues[1].toInt()
            val day = paper.groupValues[2].toInt()
            val yearRaw = paper.groupValues[3].toInt()
            val year = if (yearRaw < 100) 2000 + yearRaw else yearRaw
            return runCatching {
                LocalDate(year, month, day).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            }.getOrNull()
        }
        return null
    }

    private fun isDuplicate(
        existingInspections: List<Inspection>,
        apparatusId: String,
        templateId: String,
        completedAt: Long
    ): Boolean {
        val day = calendarDayKey(completedAt)
        return existingInspections.any { inspection ->
            inspection.apparatusId == apparatusId &&
                inspection.templateId == templateId &&
                inspection.isFinalized &&
                inspection.voidedAt == null &&
                inspection.completedAt != null &&
                calendarDayKey(inspection.completedAt) == day
        }
    }

    private fun calendarDayKey(epochMillis: Long): String {
        val date = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return date.toString()
    }
}
