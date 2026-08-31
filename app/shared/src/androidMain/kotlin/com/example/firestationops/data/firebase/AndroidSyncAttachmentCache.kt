package com.example.firestationops.data.firebase

import android.content.Context
import com.example.firestationops.data.sync.SyncAttachmentCache
import java.io.File

class AndroidSyncAttachmentCache(context: Context) : SyncAttachmentCache {
    private val cacheDir = File(context.cacheDir, "sync_attachments").apply { mkdirs() }

    override fun attachmentFilePath(attachmentId: String): String =
        File(cacheDir, "$attachmentId.jpg").absolutePath

    override fun fileExists(path: String): Boolean = File(path).exists()
}
