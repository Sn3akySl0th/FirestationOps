package com.example.firestationops.domain.repository

import com.example.firestationops.domain.model.InspectionTemplate
import kotlinx.coroutines.flow.Flow

interface InspectionRepository {
    fun getActiveTemplates(departmentId: String): Flow<List<InspectionTemplate>>
    fun getTemplatesByApparatusType(departmentId: String, apparatusType: String): Flow<List<InspectionTemplate>>
    suspend fun getTemplate(id: String): Result<InspectionTemplate>
}
