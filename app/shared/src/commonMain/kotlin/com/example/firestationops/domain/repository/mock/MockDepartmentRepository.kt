package com.example.firestationops.domain.repository.mock

import com.example.firestationops.domain.model.Department
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.repository.DepartmentRepository

class MockDepartmentRepository : DepartmentRepository {
    private val department = Department(
        id = "mock-dept-id",
        name = "Example Volunteer Fire Department",
        createdAt = 0,
        updatedAt = 0
    )

    private val members = listOf(
        Member(
            id = "admin-1",
            departmentId = "mock-dept-id",
            email = "admin@example.com",
            firstName = "Admin",
            lastName = "User",
            roles = setOf(Role.ADMIN)
        ),
        Member(
            id = "member-1",
            departmentId = "mock-dept-id",
            email = "officer@example.com",
            firstName = "Alex",
            lastName = "Rivera",
            roles = setOf(Role.OFFICER)
        ),
        Member(
            id = "member-2",
            departmentId = "mock-dept-id",
            email = "member@example.com",
            firstName = "Jamie",
            lastName = "Lee",
            roles = setOf(Role.MEMBER)
        )
    )

    override suspend fun getDepartment(id: String): Result<Department> =
        if (department.id == id) Result.success(department)
        else Result.failure(Exception("Department not found"))

    override suspend fun getMember(id: String): Result<Member> =
        members.find { it.id == id }?.let { Result.success(it) }
            ?: Result.failure(Exception("Member not found"))

    override suspend fun getMembersByDepartment(departmentId: String): Result<List<Member>> =
        Result.success(members.filter { it.departmentId == departmentId && it.isActive })
}
