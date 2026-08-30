package com.example.firestationops.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyFirestoreIdNormalizerTest {
    @Test
    fun normalizeEntityId_stripsLegacyDepartmentPrefix() {
        assertEquals(
            "ap-engine-1",
            LegacyFirestoreIdNormalizer.normalizeEntityId("221", "221-ap-engine-1")
        )
        assertEquals(
            "tmpl-engine",
            LegacyFirestoreIdNormalizer.normalizeEntityId("221", "221-tmpl-engine")
        )
    }

    @Test
    fun normalizeEntityId_leavesCanonicalIdsUnchanged() {
        assertEquals(
            "ap-engine-1",
            LegacyFirestoreIdNormalizer.normalizeEntityId("221", "ap-engine-1")
        )
    }

    @Test
    fun normalizeEntityId_doesNotStripUnrelatedPrefix() {
        assertEquals(
            "mock-dept-id-ap-engine-1",
            LegacyFirestoreIdNormalizer.normalizeEntityId("221", "mock-dept-id-ap-engine-1")
        )
    }
}
