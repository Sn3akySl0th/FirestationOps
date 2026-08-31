package com.example.firestationops.domain.repository

import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

interface InspectionRepository {
    fun getActiveTemplates(departmentId: String): Flow<List<InspectionTemplate>>
    fun getTemplatesByDepartment(departmentId: String): Flow<List<InspectionTemplate>>
    fun getTemplatesByApparatusType(departmentId: String, apparatusType: String): Flow<List<InspectionTemplate>>
    suspend fun getTemplate(id: String): Result<InspectionTemplate>
    
    suspend fun saveInspection(inspection: Inspection): Result<Unit>
    fun getInspectionsForApparatus(apparatusId: String): Flow<List<Inspection>>
    suspend fun getLatestDraft(apparatusId: String): Result<Inspection?>
    fun getInspectionsByDepartment(departmentId: String): Flow<List<Inspection>>
    suspend fun getLatestFinalizedInspection(apparatusId: String): Result<Inspection?>
    suspend fun getPendingSyncInspections(): Result<List<Inspection>>
    suspend fun updateSyncStatus(id: String, syncStatus: SyncStatus): Result<Unit>
}
