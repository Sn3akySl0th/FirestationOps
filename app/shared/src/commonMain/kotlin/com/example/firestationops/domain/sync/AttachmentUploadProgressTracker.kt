package com.example.firestationops.domain.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AttachmentUploadProgress(
    val attachmentId: String,
    val progressPercent: Int
)

object AttachmentUploadProgressTracker {
    private val _activeUploads = MutableStateFlow<Map<String, AttachmentUploadProgress>>(emptyMap())
    val activeUploads: StateFlow<Map<String, AttachmentUploadProgress>> = _activeUploads.asStateFlow()

    fun reportProgress(attachmentId: String, progressPercent: Int) {
        _activeUploads.update { current ->
            current + (attachmentId to AttachmentUploadProgress(attachmentId, progressPercent.coerceIn(0, 100)))
        }
    }

    fun clear(attachmentId: String) {
        _activeUploads.update { current -> current - attachmentId }
    }
}
