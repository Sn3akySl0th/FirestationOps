package com.example.firestationops.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

class JvmFileExporter : FileExporter {
    override suspend fun saveTextFile(suggestedFileName: String, content: String): ExportResult =
        saveBinaryFile(suggestedFileName, content.toByteArray(Charsets.UTF_8))

    override suspend fun saveBinaryFile(suggestedFileName: String, content: ByteArray): ExportResult =
        withContext(Dispatchers.IO) {
            runOnEdt {
                val extension = suggestedFileName.substringAfterLast('.', "")
                val chooser = JFileChooser().apply {
                    selectedFile = java.io.File(suggestedFileName)
                    if (extension.isNotEmpty()) {
                        fileFilter = FileNameExtensionFilter(extension.uppercase(), extension)
                    }
                }
                when (chooser.showSaveDialog(null)) {
                    JFileChooser.APPROVE_OPTION -> {
                        try {
                            val target = ensureExtension(chooser.selectedFile, extension)
                            target.writeBytes(content)
                            ExportResult.Success
                        } catch (e: Exception) {
                            ExportResult.Error(e.message ?: "Failed to save file")
                        }
                    }
                    else -> ExportResult.Cancelled
                }
            }
        }

    private fun ensureExtension(file: java.io.File, extension: String): java.io.File {
        if (extension.isEmpty() || file.name.endsWith(".$extension", ignoreCase = true)) {
            return file
        }
        return java.io.File(file.parentFile, "${file.name}.$extension")
    }

    private fun <T> runOnEdt(block: () -> T): T {
        if (EventQueue.isDispatchThread()) {
            return block()
        }
        val result = arrayOfNulls<Any>(1)
        val error = arrayOfNulls<Throwable>(1)
        EventQueue.invokeAndWait {
            try {
                result[0] = block()
            } catch (t: Throwable) {
                error[0] = t
            }
        }
        error[0]?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result[0] as T
    }
}

@Composable
actual fun rememberFileExporter(): FileExporter = remember { JvmFileExporter() }
