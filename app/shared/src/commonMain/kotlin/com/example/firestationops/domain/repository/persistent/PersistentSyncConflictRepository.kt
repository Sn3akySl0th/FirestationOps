package com.example.firestationops.domain.repository.persistent

import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.repository.SyncConflictRepository
import com.example.firestationops.domain.sync.SyncConflict
import com.example.firestationops.domain.sync.SyncConflictRecordType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class PersistentSyncConflictRepository(
    private val database: FirestationOpsDatabase
) : SyncConflictRepository {
    private val refreshSignal = MutableStateFlow(0)

    private fun refresh() {
        refreshSignal.value++
    }

    override fun getConflictsByDepartment(departmentId: String): Flow<List<SyncConflict>> =
        refreshSignal.map {
            database.getSyncConflictsByDepartment(departmentId)
        }

    override suspend fun getConflict(recordType: SyncConflictRecordType, recordId: String): SyncConflict? =
        database.getSyncConflictByRecord(recordType.name, recordId)

    override suspend fun saveConflict(conflict: SyncConflict): Result<Unit> {
        database.insertSyncConflict(conflict)
        refresh()
        return Result.success(Unit)
    }

    override suspend fun deleteConflict(conflictId: String): Result<Unit> {
        database.deleteSyncConflict(conflictId)
        refresh()
        return Result.success(Unit)
    }

    override suspend fun deleteConflictForRecord(
        recordType: SyncConflictRecordType,
        recordId: String
    ): Result<Unit> {
        database.deleteSyncConflictByRecord(recordType.name, recordId)
        refresh()
        return Result.success(Unit)
    }

    override suspend fun getBaselineSnapshot(recordType: SyncConflictRecordType, recordId: String): String? =
        database.getSyncBaselineSnapshot(recordType.name, recordId)

    override suspend fun saveBaseline(
        recordType: SyncConflictRecordType,
        recordId: String,
        snapshotJson: String
    ): Result<Unit> {
        database.upsertSyncBaseline(recordType.name, recordId, snapshotJson)
        return Result.success(Unit)
    }

    override suspend fun deleteBaseline(recordType: SyncConflictRecordType, recordId: String): Result<Unit> {
        database.deleteSyncBaseline(recordType.name, recordId)
        return Result.success(Unit)
    }
}
