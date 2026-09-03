package com.example.firestationops.domain.apparatus

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApparatusTagMatcherTest {
    private val engine = Apparatus(
        id = "apparatus-engine-1",
        departmentId = "dept-1",
        stationId = "station-1",
        name = "Engine 1",
        type = "Engine",
        radioName = "E1",
        status = ApparatusStatus.IN_SERVICE,
        vin = "1FDXE4FS7KDA12345",
        licensePlate = "FD-101",
        barcode = "EQ-E1"
    )
    private val tanker = Apparatus(
        id = "apparatus-tanker-1",
        departmentId = "dept-1",
        stationId = "station-1",
        name = "Tanker 1",
        type = "Tanker",
        radioName = "T1",
        barcode = "EQ-T1"
    )

    @Test
    fun match_resolvesRadioNameVinPlateAndBarcode() {
        val list = listOf(engine, tanker)
        assertEquals(engine.id, ApparatusTagMatcher.match("E1", list)?.id)
        assertEquals(engine.id, ApparatusTagMatcher.match("eq-e1", list)?.id)
        assertEquals(engine.id, ApparatusTagMatcher.match("1FDXE4FS7KDA12345", list)?.id)
        assertEquals(engine.id, ApparatusTagMatcher.match("FD-101", list)?.id)
        assertEquals(tanker.id, ApparatusTagMatcher.match("T1", list)?.id)
    }

    @Test
    fun match_resolvesStructuredPayloads() {
        val list = listOf(engine, tanker)
        assertEquals(
            engine.id,
            ApparatusTagMatcher.match("fireops:apparatus:apparatus-engine-1", list)?.id
        )
        assertEquals(
            tanker.id,
            ApparatusTagMatcher.match("firestationops://apparatus/apparatus-tanker-1", list)?.id
        )
        assertEquals(
            engine.id,
            ApparatusTagMatcher.match("apparatus:apparatus-engine-1", list)?.id
        )
    }

    @Test
    fun match_returnsNullWhenUnknown() {
        assertNull(ApparatusTagMatcher.match("UNKNOWN", listOf(engine, tanker)))
        assertNull(ApparatusTagMatcher.match("   ", listOf(engine)))
    }

    @Test
    fun generateScanPayload_usesStablePrefix() {
        assertEquals(
            "fireops:apparatus:apparatus-engine-1",
            ApparatusTagMatcher.generateScanPayload("apparatus-engine-1")
        )
    }
}
