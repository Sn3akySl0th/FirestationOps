package com.example.firestationops.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttachmentSyncSupportTest {
    @Test
    fun storageDepartmentId_mapsLegacyBadgeNumberToDepartmentFive() {
        assertEquals("5", AttachmentSyncSupport.storageDepartmentId("221"))
    }

    @Test
    fun uploadFailureMessage_mapsPermissionDeniedToActionableGuidance() {
        val message = AttachmentSyncSupport.uploadFailureMessage(
            IllegalStateException("User does not have permission to access this object.")
        )
        assertTrue(message.contains("Sign out"))
    }
}
