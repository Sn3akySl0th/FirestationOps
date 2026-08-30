package com.example.firestationops.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.UUID

class AndroidMediaPicker(private val onResult: (String?) -> Unit) : MediaPicker {
    private var launcher: androidx.activity.result.ActivityResultLauncher<Uri>? = null
    private var tempUri: Uri? = null

    @Composable
    override fun registerPicker(onResult: (String?) -> Unit) {
        val context = LocalContext.current
        val tempFile = remember { 
            File(context.cacheDir, "temp_image_${UUID.randomUUID()}.jpg").apply {
                createNewFile()
            }
        }
        tempUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                onResult(tempFile.absolutePath)
            } else {
                onResult(null)
            }
        }
    }

    override fun launch() {
        tempUri?.let { launcher?.launch(it) }
    }
}

@Composable
actual fun rememberMediaPicker(onResult: (String?) -> Unit): MediaPicker {
    val picker = remember { AndroidMediaPicker(onResult) }
    picker.registerPicker(onResult)
    return picker
}
