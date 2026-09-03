package com.example.firestationops.domain.apparatus

import com.example.firestationops.domain.model.Apparatus

/**
 * Resolves a scanned QR/barcode payload or typed tag to a department apparatus.
 * Matching is case-insensitive and department-scoped by the caller-supplied list.
 */
object ApparatusTagMatcher {
    private const val PREFIX_FIRESTATIONOPS_URI = "firestationops://apparatus/"
    private const val PREFIX_FIREOPS_COLON = "fireops:apparatus:"
    private const val PREFIX_APPARATUS_COLON = "apparatus:"

    fun generateScanPayload(apparatusId: String): String = "$PREFIX_FIREOPS_COLON$apparatusId"

    fun match(scannedCode: String, apparatusList: List<Apparatus>): Apparatus? {
        val trimmed = scannedCode.trim()
        if (trimmed.isBlank()) return null

        val extractedId = extractApparatusId(trimmed)
        if (extractedId != null) {
            apparatusList.firstOrNull { it.id.equals(extractedId, ignoreCase = true) }?.let {
                return it
            }
        }

        return apparatusList.firstOrNull { candidate ->
            identityValues(candidate).any { value -> value.equals(trimmed, ignoreCase = true) }
        } ?: run {
            val sanitized = trimmed.removePrefix("#").trim()
            if (sanitized == trimmed || sanitized.isBlank()) {
                null
            } else {
                apparatusList.firstOrNull { candidate ->
                    identityValues(candidate).any { value -> value.equals(sanitized, ignoreCase = true) }
                }
            }
        }
    }

    fun extractApparatusId(rawTag: String): String? {
        val clean = rawTag.trim()
        return when {
            clean.startsWith(PREFIX_FIRESTATIONOPS_URI, ignoreCase = true) ->
                clean.substring(PREFIX_FIRESTATIONOPS_URI.length).trim().takeIf { it.isNotEmpty() }
            clean.startsWith(PREFIX_FIREOPS_COLON, ignoreCase = true) ->
                clean.substring(PREFIX_FIREOPS_COLON.length).trim().takeIf { it.isNotEmpty() }
            clean.startsWith(PREFIX_APPARATUS_COLON, ignoreCase = true) ->
                clean.substring(PREFIX_APPARATUS_COLON.length).trim().takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    private fun identityValues(apparatus: Apparatus): List<String> = buildList {
        add(apparatus.id)
        add(apparatus.radioName.trim())
        add(apparatus.name.trim())
        apparatus.barcode?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        apparatus.vin?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        apparatus.licensePlate?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
    }
}
