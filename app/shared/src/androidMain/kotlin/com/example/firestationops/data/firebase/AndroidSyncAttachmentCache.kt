package com.example.firestationops.data.firebase

import android.content.Context
import com.example.firestationops.data.sync.SyncAttachmentCache
import java.io.File

class AndroidSyncAttachmentCache(context: Context) : SyncAttachmentCache {
    private val cacheDir = File(context.cacheDir, "sync_attachments").apply { mkdirs() }

    override fun attachmentFilePath(attachmentId: String): String =
        File(cacheDir, "$attachmentId.jpg").absolutePath

    override fun fileExists(path: String): Boolean = File(path).exists()

    override fun copyToAttachmentPath(attachmentId: String, sourcePath: String): String {
        val destination = File(attachmentFilePath(attachmentId))
        val source = File(sourcePath)
        if (source.absolutePath == destination.absolutePath) {
            return destination.absolutePath
        }
        destination.parentFile?.mkdirs()
        source.inputStream().use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destination.absolutePath
    }
}
