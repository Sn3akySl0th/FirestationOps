package com.example.firestationops.domain.repository.persistent

import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.membership.MemberProvisioningRules
import com.example.firestationops.domain.model.Department
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.repository.DepartmentRepository

class PersistentDepartmentRepository(private val database: FirestationOpsDatabase) : DepartmentRepository {
    override suspend fun getDepartment(id: String): Result<Department> =
        database.getDepartmentById(id)?.let { Result.success(it) }
            ?: Result.failure(Exception("Department not found"))

    override suspend fun getMember(id: String): Result<Member> =
        database.getMemberById(id)?.let { Result.success(it) }
            ?: Result.failure(Exception("Member not found"))

    override suspend fun getMembersByDepartment(departmentId: String): Result<List<Member>> =
        Result.success(
            MemberProvisioningRules.deduplicateMembersByEmail(
                database.getAllMembersByDepartment(departmentId)
            )
        )
}
