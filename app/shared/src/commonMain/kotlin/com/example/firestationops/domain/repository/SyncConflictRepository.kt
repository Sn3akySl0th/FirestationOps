package com.example.firestationops.domain.repository

import com.example.firestationops.domain.sync.SyncConflict
import com.example.firestationops.domain.sync.SyncConflictRecordType
import kotlinx.coroutines.flow.Flow

interface SyncConflictRepository {
    fun getConflictsByDepartment(departmentId: String): Flow<List<SyncConflict>>
    suspend fun getConflict(recordType: SyncConflictRecordType, recordId: String): SyncConflict?
    suspend fun saveConflict(conflict: SyncConflict): Result<Unit>
    suspend fun deleteConflict(conflictId: String): Result<Unit>
    suspend fun deleteConflictForRecord(recordType: SyncConflictRecordType, recordId: String): Result<Unit>
    suspend fun getBaselineSnapshot(recordType: SyncConflictRecordType, recordId: String): String?
    suspend fun saveBaseline(recordType: SyncConflictRecordType, recordId: String, snapshotJson: String): Result<Unit>
    suspend fun deleteBaseline(recordType: SyncConflictRecordType, recordId: String): Result<Unit>
}
