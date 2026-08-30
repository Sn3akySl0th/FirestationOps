package com.example.firestationops.domain.sync

import com.example.firestationops.domain.model.Attachment
import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.SyncStatus

object SyncStatusTransitions {
    fun inspectionForDraft(inspection: Inspection): Inspection =
        inspection.copy(syncStatus = SyncStatus.LOCAL_ONLY)

    fun inspectionForSubmit(inspection: Inspection): Inspection =
        inspection.copy(syncStatus = SyncStatus.PENDING_SYNC)

    fun deficiencyForSave(deficiency: Deficiency): Deficiency =
        deficiency.copy(syncStatus = SyncStatus.PENDING_SYNC)

    fun attachmentForSave(attachment: Attachment): Attachment =
        if (attachment.syncStatus == SyncStatus.SYNCED) {
            attachment
        } else {
            attachment.copy(syncStatus = SyncStatus.PENDING_SYNC)
        }
}
