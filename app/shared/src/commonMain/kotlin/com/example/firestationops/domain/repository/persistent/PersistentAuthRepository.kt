package com.example.firestationops.domain.repository.persistent

import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.bootstrap.DemoDepartmentSeeder
import com.example.firestationops.domain.bootstrap.DepartmentCatalogProfiles
import com.example.firestationops.domain.model.*
import com.example.firestationops.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PersistentAuthRepository(private val database: FirestationOpsDatabase) : AuthRepository {
    private val _userState = MutableStateFlow<UserState>(UserState.Unauthenticated)
    override val userState: StateFlow<UserState> = _userState.asStateFlow()

    init {
        // Ensure default data exists
        seedIfMissing()
        recoverSession()
    }

    private fun seedIfMissing() {
        try {
            val departments = database.getAllDepartments()
            println("AuthRepository: Seeding check. Departments: ${departments.size}")
            
            val deptId = "mock-dept-id"
            if (departments.isEmpty()) {
                println("AuthRepository: Seeding mock department")
                val dept = Department(
                    id = deptId,
                    name = "Mock Department 1",
                    createdAt = 0,
                    updatedAt = 0
                )
                database.insertDepartment(dept)
            }

            val calhounDeptId = DepartmentCatalogProfiles.CALHOUN_DEPARTMENT_ID
            if (database.getDepartmentById(calhounDeptId) == null) {
                database.insertDepartment(
                    Department(
                        id = calhounDeptId,
                        name = DepartmentCatalogProfiles.profileFor(calhounDeptId)!!.departmentName,
                        createdAt = 0,
                        updatedAt = 0
                    )
                )
            }

            // Ensure Admin exists
            val adminEmail = "admin@example.com"
            if (database.getMemberByEmail(adminEmail) == null) {
                println("AuthRepository: Seeding admin user")
                database.insertMember(Member(
                    id = "admin-user-id",
                    departmentId = deptId,
                    email = adminEmail,
                    firstName = "Admin",
                    lastName = "User",
                    roles = setOf(Role.ADMIN),
                    isActive = true
                ))
            }

            // Ensure Your User exists
            val yourEmail = "clefebvre81@gmail.com"
            if (database.getMemberByEmail(yourEmail) == null) {
                println("AuthRepository: Seeding user $yourEmail")
                database.insertMember(Member(
                    id = "user-clefebvre-id",
                    departmentId = DepartmentCatalogProfiles.CALHOUN_DEPARTMENT_ID,
                    memberNumber = "221",
                    email = yourEmail,
                    firstName = "Chris",
                    lastName = "Lefebvre",
                    roles = setOf(Role.ADMIN),
                    isActive = true
                ))
            }
            
            val allMembers = database.getAllMembersByDepartment(deptId)
            println("AuthRepository: Seeding check complete. Members in DB: ${allMembers.map { it.email }}")
        } catch (e: Exception) {
            println("AuthRepository: Error during seeding: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun recoverSession() {
        try {
            val userId = database.getSessionUserId()
            println("AuthRepository: Recovering session. Found userId: $userId")
            if (userId != null) {
                val member = database.getMemberById(userId)
                if (member != null) {
                    println("AuthRepository: Session recovered for ${member.email}")
                    _userState.value = UserState.Authenticated(member)
                } else {
                    println("AuthRepository: Member $userId not found. Clearing session.")
                    database.setSessionUserId(null)
                }
            } else {
                println("AuthRepository: No session found in database.")
            }
        } catch (e: Exception) {
            println("AuthRepository: Error recovering session: ${e.message}")
            e.printStackTrace()
        }
    }

    override suspend fun login(email: String, password: String): Result<Unit> {
        _userState.value = UserState.Loading
        
        val normalizedEmail = email.trim().lowercase()
        println("AuthRepository: Attempting login for email: '$normalizedEmail' (orig: '$email') password: '$password'")
        
        val member = database.getMemberByEmail(normalizedEmail)
        return if (member != null) {
            println("AuthRepository: Login success for ${member.email} (id: ${member.id})")
            database.setSessionUserId(member.id)
            DemoDepartmentSeeder.ensureDemoData(database, member.departmentId)
            _userState.value = UserState.Authenticated(member)
            Result.success(Unit)
        } else {
            println("AuthRepository: Login failed - member not found for '$normalizedEmail'")
            val errorMsg = "Invalid email. Try admin@example.com or clefebvre81@gmail.com"
            _userState.value = UserState.Error(errorMsg)
            Result.failure(Exception(errorMsg))
        }
    }

    override suspend fun logout(): Result<Unit> {
        database.setSessionUserId(null)
        _userState.value = UserState.Unauthenticated
        return Result.success(Unit)
    }
}
