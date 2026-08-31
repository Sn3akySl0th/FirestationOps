package com.example.firestationops.domain.sync

/**
 * Strips a legacy department prefix from entity IDs downloaded from Firestore.
 *
 * Older builds embedded [departmentId] in apparatus/template IDs (for example
 * `5-ap-engine-1`). Department scoping belongs on the record and collection path,
 * so canonical IDs are `ap-engine-5` under `departments/5/...`.
 */
object LegacyFirestoreIdNormalizer {
    fun normalizeEntityId(departmentId: String, rawId: String): String {
        if (departmentId.isBlank()) return rawId
        val prefix = "$departmentId-"
        return if (rawId.startsWith(prefix)) rawId.removePrefix(prefix) else rawId
    }
}
