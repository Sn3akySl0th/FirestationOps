package com.example.firestationops.domain.sync

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.Attachment
import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.SyncStatus

enum class PendingSyncRecordType(val label: String) {
    INSPECTION("Inspection"),
    DEFICIENCY("Deficiency"),
    ATTACHMENT("Attachment"),
    INCIDENT("Incident")
}

data class PendingSyncQueueItem(
    val recordType: PendingSyncRecordType,
    val recordId: String,
    val title: String,
    val detail: String?,
    val syncStatus: SyncStatus,
    val lastError: String? = null,
    val canRetry: Boolean = false
)

data class SyncedRecordCounts(
    val inspections: Int = 0,
    val deficiencies: Int = 0,
    val attachments: Int = 0,
    val incidents: Int = 0
) {
    val total: Int = inspections + deficiencies + attachments + incidents

    fun summaryLabel(): String = buildList {
        if (inspections > 0) add("$inspections inspection${plural(inspections)}")
        if (deficiencies > 0) add("$deficiencies deficiency${plural(deficiencies)}")
        if (attachments > 0) add("$attachments attachment${plural(attachments)}")
        if (incidents > 0) add("$incidents incident${plural(incidents)}")
    }.joinToString(" · ")

    private fun plural(count: Int): String = if (count == 1) "" else "s"
}

data class SyncQueueState(
    val pendingItems: List<PendingSyncQueueItem>,
    val syncedCounts: SyncedRecordCounts,
    val failedAttachmentCount: Int = 0
)

object PendingSyncQueueBuilder {
    data class Input(
        val inspections: List<Inspection>,
        val deficiencies: List<Deficiency>,
        val attachments: List<Attachment>,
        val incidents: List<Incident>,
        val apparatusById: Map<String, Apparatus>,
        val templatesById: Map<String, InspectionTemplate>
    )

    fun build(input: Input): SyncQueueState {
        val pendingItems = buildList {
            input.inspections
                .filter { it.syncStatus != SyncStatus.SYNCED }
                .forEach { inspection ->
                    add(inspectionItem(inspection, input))
                }
            input.deficiencies
                .filter { it.syncStatus != SyncStatus.SYNCED }
                .forEach { deficiency ->
                    add(deficiencyItem(deficiency, input))
                }
            input.attachments
                .filter { it.syncStatus != SyncStatus.SYNCED }
                .forEach { attachment ->
                    add(attachmentItem(attachment, input))
                }
            input.incidents
                .filter { it.syncStatus != SyncStatus.SYNCED }
                .forEach { incident ->
                    add(incidentItem(incident))
                }
        }.sortedWith(
            compareBy<PendingSyncQueueItem> { statusPriority(it.syncStatus) }
                .thenBy { it.recordType.ordinal }
                .thenBy { it.title }
        )

        val syncedCounts = SyncedRecordCounts(
            inspections = input.inspections.count { it.syncStatus == SyncStatus.SYNCED },
            deficiencies = input.deficiencies.count { it.syncStatus == SyncStatus.SYNCED },
            attachments = input.attachments.count { it.syncStatus == SyncStatus.SYNCED },
            incidents = input.incidents.count { it.syncStatus == SyncStatus.SYNCED }
        )

        return SyncQueueState(
            pendingItems = pendingItems,
            syncedCounts = syncedCounts,
            failedAttachmentCount = input.attachments.count { it.syncStatus == SyncStatus.SYNC_FAILED }
        )
    }

    fun statusLabel(syncStatus: SyncStatus): String = when (syncStatus) {
        SyncStatus.LOCAL_ONLY -> "Local only"
        SyncStatus.PENDING_SYNC -> "Waiting to upload"
        SyncStatus.SYNC_FAILED -> "Upload failed"
        SyncStatus.CONFLICT -> "Conflict"
        SyncStatus.SYNCED -> "Synced"
    }

    private fun statusPriority(syncStatus: SyncStatus): Int = when (syncStatus) {
        SyncStatus.SYNC_FAILED -> 0
        SyncStatus.CONFLICT -> 1
        SyncStatus.PENDING_SYNC -> 2
        SyncStatus.LOCAL_ONLY -> 3
        SyncStatus.SYNCED -> 4
    }

    private fun inspectionItem(inspection: Inspection, input: Input): PendingSyncQueueItem {
        val apparatus = input.apparatusById[inspection.apparatusId]
        val template = input.templatesById[inspection.templateId]
        val apparatusLabel = apparatus?.radioName ?: inspection.apparatusId
        val templateLabel = template?.name ?: "Inspection"
        val stateLabel = when {
            inspection.isFinalized -> "Submitted"
            inspection.completedAt != null -> "Completed"
            else -> "Draft"
        }

        return PendingSyncQueueItem(
            recordType = PendingSyncRecordType.INSPECTION,
            recordId = inspection.id,
            title = "$apparatusLabel · $templateLabel",
            detail = stateLabel,
            syncStatus = inspection.syncStatus
        )
    }

    private fun deficiencyItem(deficiency: Deficiency, input: Input): PendingSyncQueueItem {
        val apparatusLabel = input.apparatusById[deficiency.apparatusId]?.radioName ?: deficiency.apparatusId
        return PendingSyncQueueItem(
            recordType = PendingSyncRecordType.DEFICIENCY,
            recordId = deficiency.id,
            title = deficiency.title,
            detail = apparatusLabel,
            syncStatus = deficiency.syncStatus
        )
    }

    private fun attachmentItem(attachment: Attachment, input: Input): PendingSyncQueueItem {
        val parent = findAttachmentParent(attachment.id, input)
        val title = parent?.first ?: "Photo attachment"
        val detail = buildString {
            parent?.second?.let { append(it) }
            attachment.localUri?.substringAfterLast('/')?.let { fileName ->
                if (isNotEmpty()) append(" · ")
                append(fileName)
            }
            if (attachment.syncStatus == SyncStatus.SYNC_FAILED && !attachment.lastError.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append(attachment.lastError)
            }
        }.ifBlank { null }

        return PendingSyncQueueItem(
            recordType = PendingSyncRecordType.ATTACHMENT,
            recordId = attachment.id,
            title = title,
            detail = detail,
            syncStatus = attachment.syncStatus,
            lastError = attachment.lastError,
            canRetry = attachment.syncStatus == SyncStatus.SYNC_FAILED
        )
    }

    private fun findAttachmentParent(attachmentId: String, input: Input): Pair<String, String>? {
        input.inspections.forEach { inspection ->
            val response = inspection.responses.firstOrNull { attachmentId in it.attachmentIds } ?: return@forEach
            val apparatusLabel = input.apparatusById[inspection.apparatusId]?.radioName ?: inspection.apparatusId
            val templateLabel = input.templatesById[inspection.templateId]?.name ?: "Inspection"
            val itemLabel = input.templatesById[inspection.templateId]
                ?.items
                ?.firstOrNull { it.id == response.itemId }
                ?.text
                ?: "Inspection item"
            return "$apparatusLabel · $templateLabel" to itemLabel
        }

        input.deficiencies.forEach { deficiency ->
            if (attachmentId in deficiency.attachmentIds) {
                val apparatusLabel = input.apparatusById[deficiency.apparatusId]?.radioName ?: deficiency.apparatusId
                return deficiency.title to apparatusLabel
            }
        }

        return null
    }

    private fun incidentItem(incident: Incident): PendingSyncQueueItem =
        PendingSyncQueueItem(
            recordType = PendingSyncRecordType.INCIDENT,
            recordId = incident.id,
            title = incident.title.ifBlank { "Incident report" },
            detail = incident.status.name.replace('_', ' ').lowercase()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            syncStatus = incident.syncStatus
        )
}
