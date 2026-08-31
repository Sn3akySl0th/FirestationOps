package com.example.firestationops.domain.repository.mock

import com.example.firestationops.domain.repository.SyncConflictRepository
import com.example.firestationops.domain.sync.SyncConflict
import com.example.firestationops.domain.sync.SyncConflictRecordType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MockSyncConflictRepository : SyncConflictRepository {
    private val conflicts = MutableStateFlow<List<SyncConflict>>(emptyList())
    private val baselines = mutableMapOf<Pair<SyncConflictRecordType, String>, String>()

    override fun getConflictsByDepartment(departmentId: String): Flow<List<SyncConflict>> =
        conflicts.map { list -> list.filter { it.departmentId == departmentId } }

    override suspend fun getConflict(recordType: SyncConflictRecordType, recordId: String): SyncConflict? =
        conflicts.value.find { it.recordType == recordType && it.recordId == recordId }

    override suspend fun saveConflict(conflict: SyncConflict): Result<Unit> {
        conflicts.value = conflicts.value
            .filterNot { it.recordType == conflict.recordType && it.recordId == conflict.recordId } + conflict
        return Result.success(Unit)
    }

    override suspend fun deleteConflict(conflictId: String): Result<Unit> {
        conflicts.value = conflicts.value.filterNot { it.id == conflictId }
        return Result.success(Unit)
    }

    override suspend fun deleteConflictForRecord(recordType: SyncConflictRecordType, recordId: String): Result<Unit> {
        conflicts.value = conflicts.value.filterNot { it.recordType == recordType && it.recordId == recordId }
        return Result.success(Unit)
    }

    override suspend fun getBaselineSnapshot(recordType: SyncConflictRecordType, recordId: String): String? =
        baselines[recordType to recordId]

    override suspend fun saveBaseline(
        recordType: SyncConflictRecordType,
        recordId: String,
        snapshotJson: String
    ): Result<Unit> {
        baselines[recordType to recordId] = snapshotJson
        return Result.success(Unit)
    }

    override suspend fun deleteBaseline(recordType: SyncConflictRecordType, recordId: String): Result<Unit> {
        baselines.remove(recordType to recordId)
        return Result.success(Unit)
    }
}
