package com.example.firestationops.data.firebase

import com.example.firestationops.domain.membership.MemberRosterInput
import com.example.firestationops.domain.model.Member
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

interface FirebaseMemberFunctionsGateway {
    suspend fun provisionMember(input: MemberRosterInput): Member
    suspend fun updateMember(memberId: String, input: MemberRosterInput): Member
    suspend fun deactivateMember(memberId: String): Member
}

class FirebaseMemberFunctionsClient(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1")
) : FirebaseMemberFunctionsGateway {
    override suspend fun provisionMember(input: MemberRosterInput): Member {
        val password = input.initialPassword
            ?: throw IllegalArgumentException("An initial password is required.")
        return callForMember(
            functionName = "provisionDepartmentMember",
            data = memberData(input) + ("password" to password)
        )
    }

    override suspend fun updateMember(memberId: String, input: MemberRosterInput): Member =
        callForMember(
            functionName = "updateDepartmentMember",
            data = memberData(input) + ("targetUserId" to memberId)
        )

    override suspend fun deactivateMember(memberId: String): Member =
        callForMember(
            functionName = "deactivateDepartmentMember",
            data = mapOf("targetUserId" to memberId)
        )

    private suspend fun callForMember(functionName: String, data: Map<String, Any?>): Member {
        val result = try {
            functions.getHttpsCallable(functionName).call(data).await()
        } catch (error: FirebaseFunctionsException) {
            throw mapFunctionError(error)
        }
        val resultMap = result.data as? Map<*, *>
            ?: error("Member service returned an invalid response.")
        val memberMap = resultMap["member"] as? Map<*, *>
            ?: error("Member service did not return a member profile.")
        val stringKeyedMap = memberMap.entries.associate { entry ->
            val key = entry.key as? String
                ?: error("Member service returned an invalid profile.")
            key to entry.value
        }
        val memberId = stringKeyedMap["id"] as? String
            ?: error("Member service did not return a member id.")
        return FirestoreMappers.memberFromMap(memberId, stringKeyedMap)
            ?: error("Member service returned an incomplete profile.")
    }

    private fun memberData(input: MemberRosterInput): Map<String, Any?> = mapOf(
        "email" to input.email.trim().lowercase(),
        "firstName" to input.firstName.trim(),
        "lastName" to input.lastName.trim(),
        "memberNumber" to input.memberNumber?.trim()?.takeIf(String::isNotEmpty),
        "roles" to input.roles.map { it.name },
        "isActive" to input.isActive
    )

    private fun mapFunctionError(error: FirebaseFunctionsException): Throwable {
        val safeMessage = error.message?.takeIf { it.isNotBlank() }
        return when (error.code) {
            FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                IllegalArgumentException(safeMessage ?: "Check the member details and try again.", error)
            FirebaseFunctionsException.Code.ALREADY_EXISTS ->
                IllegalStateException(safeMessage ?: "A sign-in account already exists for this email.", error)
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                IllegalStateException("Sign in again before managing the roster.", error)
            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                IllegalStateException("Only an active department administrator can manage this roster.", error)
            FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                IllegalStateException(
                    safeMessage ?: "This member change would leave the department without an active administrator.",
                    error
                )
            FirebaseFunctionsException.Code.NOT_FOUND ->
                IllegalStateException(safeMessage ?: "Member not found.", error)
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                IllegalStateException("The member service is unavailable. Check connectivity and try again.", error)
            else -> IllegalStateException("Unable to update the cloud member roster.", error)
        }
    }
}
