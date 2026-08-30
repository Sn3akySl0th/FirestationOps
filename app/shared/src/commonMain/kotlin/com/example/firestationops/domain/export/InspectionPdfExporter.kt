package com.example.firestationops.domain.export

import com.example.firestationops.domain.model.InspectionStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object InspectionPdfExporter {
    fun export(report: InspectionReport): ByteArray {
        val lines = buildLines(report)
        return MinimalPdfWriter(title = "Inspection Report", lines = lines).build()
    }

    private fun buildLines(report: InspectionReport): List<String> {
        return buildList {
            add("FirestationOps Inspection Report")
            add("")
            add("Inspection ID: ${report.inspectionId}")
            add("Apparatus: ${report.apparatusRadioName} (${report.apparatusType})")
            add("Template: ${report.templateName} v${report.templateVersion}")
            add("Completed: ${formatTimestamp(report.completedAt)}")
            add("Inspector: ${report.inspectorName}")
            add("")
            add("Checklist Results")
            add("----------------")
            report.items.forEach { item ->
                val statusLabel = when (item.status) {
                    InspectionStatus.PASS -> "PASS"
                    InspectionStatus.FAIL -> "FAIL"
                    InspectionStatus.NOT_APPLICABLE -> "N/A"
                }
                val severity = item.severity?.name?.replace('_', ' ')?.let { " [$it]" }.orEmpty()
                val note = item.note?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
                add("${item.text}: $statusLabel$severity$note")
            }
            if (report.deficiencies.isNotEmpty()) {
                add("")
                add("Open Deficiencies Created")
                add("------------------------")
                report.deficiencies.forEach { deficiency ->
                    add("${deficiency.title} (${deficiency.severity.name.replace('_', ' ')})")
                    add("  ${deficiency.description}")
                }
            }
        }
    }

    private fun formatTimestamp(epochMillis: Long): String {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dateTime.monthNumber}/${dateTime.day}/${dateTime.year} " +
            "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
    }
}

/**
 * Minimal PDF 1.4 writer for plain-text reports. No external PDF library required.
 */
internal class MinimalPdfWriter(
    private val title: String,
    private val lines: List<String>
) {
    fun build(): ByteArray {
        val wrappedLines = lines.flatMap { wrapLine(it, MAX_CHARS_PER_LINE) }
        val stream = buildTextStream(wrappedLines)
        val streamBytes = stream.encodeToByteArray()

        val objects = mutableListOf<String>()
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        objects += "<< /Type /Pages /Kids [3 0 R] /Count 1 >>"
        objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>"
        objects += "<< /Length ${streamBytes.size} >>\nstream\n$stream\nendstream"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"

        val header = "%PDF-1.4\n"
        val body = StringBuilder()
        val offsets = IntArray(objects.size)
        var offset = header.length

        objects.forEachIndexed { index, obj ->
            offsets[index] = offset
            val entry = "${index + 1} 0 obj\n$obj\nendobj\n"
            body.append(entry)
            offset += entry.length
        }

        val xrefOffset = offset
        val xref = StringBuilder()
        xref.append("xref\n0 ${objects.size + 1}\n")
        xref.append("0000000000 65535 f \n")
        offsets.forEach { objOffset ->
            xref.append(objOffset.toString().padStart(10, '0'))
            xref.append(" 00000 n \n")
        }

        val trailer = "trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"
        return (header + body + xref + trailer).encodeToByteArray()
    }

    private fun buildTextStream(lines: List<String>): String {
        val builder = StringBuilder()
        builder.append("BT\n")
        builder.append("/F1 11 Tf\n")
        var y = 750
        builder.append("50 $y Td\n")
        lines.forEachIndexed { index, line ->
            if (index > 0) {
                builder.append("0 -$LINE_HEIGHT Td\n")
            }
            builder.append("(${escapePdfText(line)}) Tj\n")
            y -= LINE_HEIGHT
            if (y < 50) return@forEachIndexed
        }
        builder.append("ET")
        return builder.toString()
    }

    private fun wrapLine(line: String, maxChars: Int): List<String> {
        if (line.length <= maxChars) return listOf(line)
        return line.chunked(maxChars)
    }

    private fun escapePdfText(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")

    companion object {
        private const val LINE_HEIGHT = 14
        private const val MAX_CHARS_PER_LINE = 90
    }
}
