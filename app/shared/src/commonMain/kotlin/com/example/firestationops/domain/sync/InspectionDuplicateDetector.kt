package com.example.firestationops.domain.sync

import com.example.firestationops.domain.InspectionComplianceCalculator
import com.example.firestationops.domain.model.Inspection

object InspectionDuplicateDetector {
    fun findDuplicate(
        local: Inspection,
        remoteInspections: List<Inspection>,
        frequencyHours: Int = InspectionComplianceCalculator.DEFAULT_FREQUENCY_HOURS
    ): Inspection? {
        if (!local.isFinalized || local.completedAt == null || local.voidedAt != null) return null

        val windowMs = frequencyHours * 3_600_000L
        return remoteInspections.firstOrNull { remote ->
            remote.id != local.id &&
                remote.voidedAt == null &&
                remote.isFinalized &&
                remote.completedAt != null &&
                remote.apparatusId == local.apparatusId &&
                remote.templateId == local.templateId &&
                kotlin.math.abs(remote.completedAt!! - local.completedAt!!) < windowMs
        }
    }
}
