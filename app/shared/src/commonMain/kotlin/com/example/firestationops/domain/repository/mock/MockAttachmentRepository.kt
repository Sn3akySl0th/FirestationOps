package com.example.firestationops.domain.repository.mock

import com.example.firestationops.domain.model.Attachment
import com.example.firestationops.domain.model.SyncStatus
import com.example.firestationops.domain.repository.AttachmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class MockAttachmentRepository : AttachmentRepository {
    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())

    override fun getAttachmentsByDepartment(departmentId: String): Flow<List<Attachment>> = 
        _attachments.asStateFlow().map { list -> list.filter { it.departmentId == departmentId } }

    override suspend fun getAttachment(id: String): Result<Attachment> = 
        _attachments.value.find { it.id == id }?.let { Result.success(it) } ?: Result.failure(Exception("Not found"))

    override suspend fun saveAttachment(attachment: Attachment): Result<Unit> {
        _attachments.value += attachment
        return Result.success(Unit)
    }

    override suspend fun deleteAttachment(id: String): Result<Unit> {
        _attachments.value = _attachments.value.filter { it.id != id }
        return Result.success(Unit)
    }

    override suspend fun getPendingSyncAttachments(): Result<List<Attachment>> = 
        Result.success(_attachments.value.filter { it.syncStatus != SyncStatus.SYNCED })

    override suspend fun updateSyncStatus(id: String, syncStatus: SyncStatus): Result<Unit> {
        _attachments.value = _attachments.value.map { if (it.id == id) it.copy(syncStatus = syncStatus) else it }
        return Result.success(Unit)
    }

    override suspend fun updateRemoteUrl(id: String, remoteUrl: String): Result<Unit> {
        _attachments.value = _attachments.value.map { if (it.id == id) it.copy(remoteUrl = remoteUrl, syncStatus = SyncStatus.SYNCED) else it }
        return Result.success(Unit)
    }
}
