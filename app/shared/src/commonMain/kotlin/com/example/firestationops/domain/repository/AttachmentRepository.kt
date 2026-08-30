package com.example.firestationops.domain.repository

import com.example.firestationops.domain.model.Attachment
import kotlinx.coroutines.flow.Flow

interface AttachmentRepository {
    fun getAttachmentsByDepartment(departmentId: String): Flow<List<Attachment>>
    suspend fun getAttachment(id: String): Result<Attachment>
    suspend fun saveAttachment(attachment: Attachment): Result<Unit>
    suspend fun deleteAttachment(id: String): Result<Unit>
    suspend fun getPendingSyncAttachments(): Result<List<Attachment>>
    suspend fun updateSyncStatus(id: String, syncStatus: com.example.firestationops.domain.model.SyncStatus): Result<Unit>
    suspend fun updateRemoteUrl(id: String, remoteUrl: String): Result<Unit>
}
