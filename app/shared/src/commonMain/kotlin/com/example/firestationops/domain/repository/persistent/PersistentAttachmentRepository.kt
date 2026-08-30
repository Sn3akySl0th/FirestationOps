package com.example.firestationops.domain.repository.persistent

import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.model.Attachment
import com.example.firestationops.domain.model.SyncStatus
import com.example.firestationops.domain.repository.AttachmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PersistentAttachmentRepository(private val database: FirestationOpsDatabase) : AttachmentRepository {
    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())

    init {
        refresh()
    }

    private fun refresh() {
        _attachments.value = database.getAllAttachments()
    }

    override fun getAttachmentsByDepartment(departmentId: String): Flow<List<Attachment>> = _attachments.asStateFlow()

    override suspend fun getAttachment(id: String): Result<Attachment> = 
        database.getAttachmentById(id)?.let { Result.success(it) } ?: Result.failure(Exception("Attachment not found"))

    override suspend fun saveAttachment(attachment: Attachment): Result<Unit> {
        database.insertAttachment(attachment)
        refresh()
        return Result.success(Unit)
    }

    override suspend fun deleteAttachment(id: String): Result<Unit> {
        database.deleteAttachment(id)
        refresh()
        return Result.success(Unit)
    }

    override suspend fun getPendingSyncAttachments(): Result<List<Attachment>> = 
        Result.success(database.getPendingSyncAttachments())

    override suspend fun updateSyncStatus(id: String, syncStatus: SyncStatus): Result<Unit> {
        database.updateAttachmentSyncStatus(id, syncStatus)
        refresh()
        return Result.success(Unit)
    }

    override suspend fun updateRemoteUrl(id: String, remoteUrl: String): Result<Unit> {
        database.updateAttachmentRemoteUrl(id, remoteUrl)
        refresh()
        return Result.success(Unit)
    }
}
