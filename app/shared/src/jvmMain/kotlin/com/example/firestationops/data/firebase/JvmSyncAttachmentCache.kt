package com.example.firestationops.data.firebase

import com.example.firestationops.data.sync.SyncAttachmentCache
import java.io.File

class JvmSyncAttachmentCache : SyncAttachmentCache {
    private val cacheDir = File(
        System.getProperty("user.home"),
        ".firestationops/sync_attachments"
    ).apply { mkdirs() }

    override fun attachmentFilePath(attachmentId: String): String =
        File(cacheDir, "$attachmentId.jpg").absolutePath

    override fun fileExists(path: String): Boolean = File(path).exists()
}
