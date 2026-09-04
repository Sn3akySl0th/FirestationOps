package com.example.firestationops.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

class JvmFileImporter : FileImporter {
    override suspend fun pickTextFile(): TextImportResult =
        withContext(Dispatchers.IO) {
            runOnEdt {
                val chooser = JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter("CSV / text", "csv", "txt")
                }
                when (chooser.showOpenDialog(null)) {
                    JFileChooser.APPROVE_OPTION -> {
                        try {
                            val file = chooser.selectedFile
                            TextImportResult.Success(
                                content = file.readText(Charsets.UTF_8),
                                fileName = file.name
                            )
                        } catch (e: Exception) {
                            TextImportResult.Error(e.message ?: "Failed to read file")
                        }
                    }
                    else -> TextImportResult.Cancelled
                }
            }
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
actual fun rememberFileImporter(): FileImporter = remember { JvmFileImporter() }
