package com.example.firestationops.domain.export

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.InspectionStatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.InspectionTemplateItem
import com.example.firestationops.domain.model.InspectionResponse
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InspectionReportBuilderTest {
  private val apparatus = Apparatus(
      id = "ap-1",
      departmentId = "dept-1",
      stationId = "st-1",
      name = "Engine 1",
      type = "Engine",
      radioName = "E1"
  )

  private val template = InspectionTemplate(
      id = "tmpl-1",
      departmentId = "dept-1",
      name = "Daily Engine Inspection",
      apparatusType = "Engine",
      version = 2,
      items = listOf(
          InspectionTemplateItem(id = "item-1", text = "Engine Oil Level", category = "Engine"),
          InspectionTemplateItem(id = "item-2", text = "Lights and Siren", category = "Exterior")
      )
  )

  @Test
  fun build_mapsResponsesAndDeficiencies() {
      val report = InspectionReportBuilder.build(
          inspectionId = "insp-1",
          apparatus = apparatus,
          template = template,
          completedAt = 1_700_000_000_000L,
          inspectorName = "Alex Example",
          responses = mapOf(
              "item-1" to InspectionResponse("item-1", InspectionStatus.PASS),
              "item-2" to InspectionResponse(
                  itemId = "item-2",
                  status = InspectionStatus.FAIL,
                  note = "Left headlight out",
                  severity = DeficiencySeverity.REPAIR_NEEDED
              )
          )
      )

      assertEquals("E1", report.apparatusRadioName)
      assertEquals(2, report.items.size)
      assertEquals(1, report.deficiencies.size)
      assertEquals("Failed: Lights and Siren", report.deficiencies.first().title)
  }

  @Test
  fun suggestedFileBaseName_sanitizesRadioName() {
      val report = InspectionReportBuilder.build(
          inspectionId = "insp-1",
          apparatus = apparatus.copy(radioName = "Engine 1"),
          template = template,
          completedAt = 1L,
          inspectorName = "Alex Example",
          responses = emptyMap()
      )

      assertEquals("inspection_Engine_1_insp-1", InspectionReportBuilder.suggestedFileBaseName(report))
  }
}

class InspectionCsvExporterTest {
  @Test
  fun export_includesHeaderAndFailedItem() {
      val report = InspectionReport(
          inspectionId = "insp-1",
          apparatusRadioName = "E1",
          apparatusType = "Engine",
          templateName = "Daily Engine Inspection",
          templateVersion = 1,
          completedAt = 1_700_000_000_000L,
          inspectorName = "Alex Example",
          items = listOf(
              InspectionReportItem(
                  category = "Engine",
                  text = "Engine Oil Level",
                  status = InspectionStatus.PASS,
                  note = null,
                  severity = null
              ),
              InspectionReportItem(
                  category = "Exterior",
                  text = "Lights and Siren",
                  status = InspectionStatus.FAIL,
                  note = "Left headlight out",
                  severity = DeficiencySeverity.REPAIR_NEEDED
              )
          ),
          deficiencies = listOf(
              InspectionReportDeficiency(
                  title = "Failed: Lights and Siren",
                  description = "Left headlight out",
                  severity = DeficiencySeverity.REPAIR_NEEDED
              )
          )
      )

      val csv = InspectionCsvExporter.export(report)

      assertContains(csv, "Inspection ID,insp-1")
      assertContains(csv, "Expected Qty,Actual Qty")
      assertContains(csv, "Engine Oil Level,PASS")
      assertContains(csv, "Exterior,Lights and Siren,FAIL,,,REPAIR_NEEDED,Left headlight out")
      assertContains(csv, "Failed: Lights and Siren,REPAIR_NEEDED,Left headlight out")
  }

  @Test
  fun escapeCsv_quotesFieldsWithCommas() {
      assertEquals("\"note, with comma\"", InspectionCsvExporter.escapeCsv("note, with comma"))
  }
}

class InspectionPdfExporterTest {
  @Test
  fun export_producesPdfHeaderAndApparatusName() {
      val report = InspectionReport(
          inspectionId = "insp-1",
          apparatusRadioName = "E1",
          apparatusType = "Engine",
          templateName = "Daily Engine Inspection",
          templateVersion = 1,
          completedAt = 1_700_000_000_000L,
          inspectorName = "Alex Example",
          items = emptyList(),
          deficiencies = emptyList()
      )

      val pdf = InspectionPdfExporter.export(report)
      val content = pdf.decodeToString()

      assertTrue(content.startsWith("%PDF-1.4"))
      assertContains(content, "E1")
      assertContains(content, "Alex Example")
  }
}
