package com.example.firestationops.domain.export

import com.example.firestationops.domain.model.InspectionStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object InspectionCsvExporter {
    fun export(report: InspectionReport): String {
        val lines = buildList {
            add(row("Report Type", "Inspection Summary"))
            add(row("Inspection ID", report.inspectionId))
            add(row("Apparatus", report.apparatusRadioName))
            add(row("Apparatus Type", report.apparatusType))
            add(row("Template", report.templateName))
            add(row("Template Version", report.templateVersion.toString()))
            add(row("Completed At", formatTimestamp(report.completedAt)))
            add(row("Inspector", report.inspectorName))
            add("")
            add(row("Category", "Item", "Status", "Expected Qty", "Actual Qty", "Severity", "Note"))
            report.items.forEach { item ->
                add(
                    row(
                        item.category.orEmpty(),
                        item.text,
                        item.status.name,
                        item.expectedQuantity?.toString().orEmpty(),
                        item.actualQuantity?.toString().orEmpty(),
                        item.severity?.name.orEmpty(),
                        item.note.orEmpty()
                    )
                )
            }
            if (report.deficiencies.isNotEmpty()) {
                add("")
                add(row("Deficiency Title", "Severity", "Description"))
                report.deficiencies.forEach { deficiency ->
                    add(
                        row(
                            deficiency.title,
                            deficiency.severity.name,
                            deficiency.description
                        )
                    )
                }
            }
        }
        return lines.joinToString("\n")
    }

    private fun row(vararg values: String): String =
        values.joinToString(",") { escapeCsv(it) }

    internal fun escapeCsv(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            return value
        }
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun formatTimestamp(epochMillis: Long): String {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dateTime.year}-${dateTime.monthNumber.toString().padStart(2, '0')}-${dateTime.day.toString().padStart(2, '0')} " +
            "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
    }
}
