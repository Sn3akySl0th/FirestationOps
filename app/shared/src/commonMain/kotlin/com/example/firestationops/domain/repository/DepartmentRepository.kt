package com.example.firestationops.domain.repository

import com.example.firestationops.domain.model.Department
import com.example.firestationops.domain.model.Member

interface DepartmentRepository {
    suspend fun getDepartment(id: String): Result<Department>
    suspend fun getMember(id: String): Result<Member>
    suspend fun getMembersByDepartment(departmentId: String): Result<List<Member>>
}
