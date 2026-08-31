package com.example.firestationops.domain.sync

import com.example.firestationops.domain.bootstrap.LegacyDemoCatalogMigrator

object AttachmentSyncSupport {
    fun storageDepartmentId(attachmentDepartmentId: String): String =
        LegacyDemoCatalogMigrator.resolveCatalogDepartmentId(attachmentDepartmentId)

    fun uploadFailureMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("does not have permission", ignoreCase = true) ||
                message.contains("Permission denied", ignoreCase = true) ||
                message.contains("unauthorized", ignoreCase = true) ||
                message.contains("403", ignoreCase = false) -> {
                "Photo upload blocked by Firebase Storage. Sign out, sign in with cloud login again, then retry."
            }
            message.contains("firebaseappcheck.googleapis.com", ignoreCase = true) -> {
                "Firebase App Check API is disabled for this project. Enable it in Google Cloud, " +
                    "register the debug secret from logcat, then retry the upload."
            }
            message.contains("User is not authenticated", ignoreCase = true) -> {
                "Cloud sign-in is required to upload photos. Use Login instead of Sign in offline."
            }
            message.isBlank() -> "Photo upload failed."
            else -> message
        }
    }
}
