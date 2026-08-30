package com.example.firestationops.domain.export

import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.InspectionStatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.InspectionTemplateItem
import com.example.firestationops.domain.model.InspectionResponse
import com.example.firestationops.domain.model.Apparatus

data class InspectionReport(
    val inspectionId: String,
    val apparatusRadioName: String,
    val apparatusType: String,
    val templateName: String,
    val templateVersion: Int,
    val completedAt: Long,
    val inspectorName: String,
    val items: List<InspectionReportItem>,
    val deficiencies: List<InspectionReportDeficiency>
)

data class InspectionReportItem(
    val category: String?,
    val text: String,
    val status: InspectionStatus,
    val note: String?,
    val severity: DeficiencySeverity?
)

data class InspectionReportDeficiency(
    val title: String,
    val description: String,
    val severity: DeficiencySeverity
)

object InspectionReportBuilder {
    fun build(
        inspectionId: String,
        apparatus: Apparatus,
        template: InspectionTemplate,
        completedAt: Long,
        inspectorName: String,
        responses: Map<String, InspectionResponse>
    ): InspectionReport {
        val items = template.items.map { item ->
            val response = responses[item.id]
            InspectionReportItem(
                category = item.category,
                text = item.text,
                status = response?.status ?: InspectionStatus.NOT_APPLICABLE,
                note = response?.note,
                severity = response?.severity
            )
        }

        val deficiencies = items
            .filter { it.status == InspectionStatus.FAIL }
            .map { item ->
                InspectionReportDeficiency(
                    title = "Failed: ${item.text}",
                    description = item.note ?: "No note provided",
                    severity = item.severity ?: DeficiencySeverity.REPAIR_NEEDED
                )
            }

        return InspectionReport(
            inspectionId = inspectionId,
            apparatusRadioName = apparatus.radioName,
            apparatusType = apparatus.type,
            templateName = template.name,
            templateVersion = template.version,
            completedAt = completedAt,
            inspectorName = inspectorName,
            items = items,
            deficiencies = deficiencies
        )
    }

    fun suggestedFileBaseName(report: InspectionReport): String {
        val safeRadio = report.apparatusRadioName.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "inspection_${safeRadio}_${report.inspectionId}"
    }
}
