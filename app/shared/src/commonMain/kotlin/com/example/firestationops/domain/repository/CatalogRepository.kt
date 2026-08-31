package com.example.firestationops.domain.repository

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.Department
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Station

interface CatalogRepository {
    suspend fun applyDepartment(department: Department): Result<Unit>
    suspend fun applyStation(station: Station): Result<Unit>
    suspend fun applyApparatus(apparatus: Apparatus): Result<Unit>
    suspend fun applyTemplate(template: InspectionTemplate): Result<Unit>
    suspend fun applyMember(member: Member): Result<Unit>
    fun notifyCatalogUpdated()
}
