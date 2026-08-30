package com.example.firestationops.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.firestationops.SyncDependencies

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val coordinator = SyncDependencies.coordinator ?: return Result.success()
        val departmentId = SyncDependencies.departmentIdProvider() ?: return Result.success()

        if (!coordinator.isAvailable()) {
            return Result.success()
        }

        val syncResult = coordinator.syncDepartment(departmentId)
        return if (syncResult.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
