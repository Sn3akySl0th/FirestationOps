package com.example.firestationops.domain

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusInspectionStatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.InspectionComplianceStatus
import com.example.firestationops.domain.model.InspectionTemplate

object InspectionComplianceCalculator {
    private const val MILLIS_PER_HOUR = 3_600_000L
    private const val MILLIS_PER_DAY = 86_400_000L
    const val DEFAULT_FREQUENCY_HOURS = 24
    const val DEFAULT_DUE_SOON_THRESHOLD_HOURS = 4

    fun calculateForDepartment(
        apparatusList: List<Apparatus>,
        templates: List<InspectionTemplate>,
        inspections: List<Inspection>,
        nowMillis: Long,
        dueSoonThresholdHours: Int = DEFAULT_DUE_SOON_THRESHOLD_HOURS
    ): List<ApparatusInspectionStatus> {
        val templatesByType = templates
            .filter { it.isActive }
            .groupBy { it.apparatusType }
            .mapValues { (_, typeTemplates) -> typeTemplates.maxByOrNull { it.version } }

        val finalizedByApparatus = inspections
            .filter { it.isFinalized && it.completedAt != null }
            .groupBy { it.apparatusId }
            .mapValues { (_, apparatusInspections) ->
                apparatusInspections.maxByOrNull { it.completedAt!! }
            }

        val draftsByApparatus = inspections
            .filter { !it.isFinalized }
            .groupBy { it.apparatusId }
            .mapValues { (_, apparatusInspections) ->
                apparatusInspections.maxByOrNull { it.startedAt }
            }

        return apparatusList
            .filter { it.status != ApparatusStatus.RESERVE }
            .mapNotNull { apparatus ->
                calculate(
                    apparatus = apparatus,
                    template = templatesByType[apparatus.type],
                    latestFinalized = finalizedByApparatus[apparatus.id],
                    draft = draftsByApparatus[apparatus.id],
                    nowMillis = nowMillis,
                    dueSoonThresholdHours = dueSoonThresholdHours
                )
            }
    }

    fun calculate(
        apparatus: Apparatus,
        template: InspectionTemplate?,
        latestFinalized: Inspection?,
        draft: Inspection?,
        nowMillis: Long,
        dueSoonThresholdHours: Int = DEFAULT_DUE_SOON_THRESHOLD_HOURS
    ): ApparatusInspectionStatus? {
        if (apparatus.status == ApparatusStatus.RESERVE) return null

        val frequencyHours = template?.frequencyHours ?: DEFAULT_FREQUENCY_HOURS
        val templateId = template?.id
        val templateName = template?.name

        if (draft != null) {
            return ApparatusInspectionStatus(
                apparatusId = apparatus.id,
                templateId = templateId,
                templateName = templateName,
                status = InspectionComplianceStatus.IN_PROGRESS,
                lastCompletedAt = latestFinalized?.completedAt,
                dueAt = latestFinalized?.completedAt?.let { it + frequencyHours * MILLIS_PER_HOUR },
                daysOverdue = 0,
                draftInspectionId = draft.id
            )
        }

        if (latestFinalized?.completedAt == null) {
            return ApparatusInspectionStatus(
                apparatusId = apparatus.id,
                templateId = templateId,
                templateName = templateName,
                status = InspectionComplianceStatus.NEVER_INSPECTED,
                lastCompletedAt = null,
                dueAt = null,
                daysOverdue = 0
            )
        }

        val lastCompletedAt = latestFinalized.completedAt
        val dueAt = lastCompletedAt + frequencyHours * MILLIS_PER_HOUR
        val dueSoonAt = dueAt - dueSoonThresholdHours * MILLIS_PER_HOUR

        val status = when {
            nowMillis > dueAt -> InspectionComplianceStatus.OVERDUE
            nowMillis >= dueSoonAt -> InspectionComplianceStatus.DUE_SOON
            else -> InspectionComplianceStatus.CURRENT
        }

        val daysOverdue = if (status == InspectionComplianceStatus.OVERDUE) {
            ((nowMillis - dueAt) / MILLIS_PER_DAY).toInt().coerceAtLeast(1)
        } else {
            0
        }

        return ApparatusInspectionStatus(
            apparatusId = apparatus.id,
            templateId = templateId,
            templateName = templateName,
            status = status,
            lastCompletedAt = lastCompletedAt,
            dueAt = dueAt,
            daysOverdue = daysOverdue
        )
    }
}
