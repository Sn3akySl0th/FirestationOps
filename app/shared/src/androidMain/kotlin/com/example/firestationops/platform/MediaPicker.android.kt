package com.example.firestationops.platform

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.UUID

class AndroidMediaPicker(private val onResult: (String?) -> Unit) : MediaPicker {
    private var cameraLauncher: androidx.activity.result.ActivityResultLauncher<Uri>? = null
    private var galleryLauncher: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var cameraUri: Uri? = null
    private var cameraFile: File? = null
    private var appContext: Context? = null

    @Composable
    override fun registerPicker(onResult: (String?) -> Unit) {
        val context = LocalContext.current
        appContext = context.applicationContext

        cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                onResult(cameraFile?.absolutePath)
            } else {
                onResult(null)
            }
        }

        galleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri == null) {
                onResult(null)
                return@rememberLauncherForActivityResult
            }
            val copiedPath = copyPickedImageToTempFile(context, uri)
            onResult(copiedPath)
        }
    }

    override fun launchCamera() {
        val context = appContext ?: return
        val tempFile = File(context.cacheDir, "temp_image_${UUID.randomUUID()}.jpg").apply {
            parentFile?.mkdirs()
            createNewFile()
        }
        cameraFile = tempFile
        cameraUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        cameraUri?.let { cameraLauncher?.launch(it) }
    }

    override fun launchGallery() {
        galleryLauncher?.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun copyPickedImageToTempFile(context: Context, uri: Uri): String? {
        return runCatching {
            val tempFile = File(context.cacheDir, "picked_image_${UUID.randomUUID()}.jpg").apply {
                parentFile?.mkdirs()
                createNewFile()
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            tempFile.absolutePath
        }.getOrNull()
    }
}

@Composable
actual fun rememberMediaPicker(onResult: (String?) -> Unit): MediaPicker {
    val picker = remember { AndroidMediaPicker(onResult) }
    picker.registerPicker(onResult)
    return picker
}
