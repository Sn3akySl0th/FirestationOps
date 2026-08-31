package com.example.firestationops.domain.sync

import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.SyncStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SyncRecordSnapshot {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeDeficiency(deficiency: Deficiency): String =
        json.encodeToString(deficiency.copy(syncStatus = SyncStatus.SYNCED))

    fun decodeDeficiency(snapshotJson: String): Deficiency? =
        runCatching { json.decodeFromString<Deficiency>(snapshotJson) }.getOrNull()

    fun encodeIncident(incident: Incident): String =
        json.encodeToString(incident.copy(syncStatus = SyncStatus.SYNCED))

    fun decodeIncident(snapshotJson: String): Incident? =
        runCatching { json.decodeFromString<Incident>(snapshotJson) }.getOrNull()

    fun encodeInspection(inspection: Inspection): String =
        json.encodeToString(inspection.copy(syncStatus = SyncStatus.SYNCED))

    fun decodeInspection(snapshotJson: String): Inspection? =
        runCatching { json.decodeFromString<Inspection>(snapshotJson) }.getOrNull()
}
