# Implementation Plan - Media Attachments and Sync Tracking

Enable photo attachments for deficiencies and implement a robust sync state tracking system to prepare for Firebase synchronization.

## User Review Required

> [!IMPORTANT]
> - **Sync State**: Every operational record (`Inspection`, `Deficiency`, `Attachment`) will now have a `syncStatus` field (`LOCAL_ONLY`, `PENDING_SYNC`, `SYNCED`, `SYNC_FAILED`).
> - **Local Storage**: Photos will be stored in the app's internal storage directory and referenced by path in the database until they are synced to Cloud Storage.

## Proposed Changes

### Domain & Data Layer

#### [MODIFY] [Deficiency.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/domain/model/Deficiency.kt)
- Add `syncStatus: SyncStatus`.
- Add `attachmentIds: List<String>`.

#### [MODIFY] [Inspection.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/domain/model/Inspection.kt)
- Add `syncStatus: SyncStatus`.

#### [NEW] [Attachment.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/domain/model/Attachment.kt)
- Define `Attachment` model: `id`, `departmentId`, `localUri`, `remoteUrl`, `syncStatus`, `createdAt`, `createdByUserId`.

#### [NEW] [SyncStatus.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/domain/model/SyncStatus.kt)
- Define `SyncStatus` enum: `LOCAL_ONLY`, `PENDING_SYNC`, `SYNCED`, `SYNC_FAILED`, `CONFLICT`.

---

### Persistence Layer

#### [MODIFY] [FirestationOps.sq](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/sqldelight/com/example/firestationops/db/FirestationOps.sq)
- Add `syncStatus` column to `InspectionEntity` and `DeficiencyEntity`.
- Create `AttachmentEntity` table.
- Add queries for fetching pending sync items.

#### [NEW] [AttachmentRepository.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/domain/repository/AttachmentRepository.kt)
- Interface for saving, fetching, and deleting attachments.

#### [NEW] [PersistentAttachmentRepository.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/domain/repository/persistent/PersistentAttachmentRepository.kt)
- Implementation using SQLDelight and platform-specific file storage.

---

### Platform Integration (Camera/File Picking)

#### [NEW] [MediaPicker.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/platform/MediaPicker.kt)
- `expect` class/interface for taking photos or picking images.

#### [NEW] [MediaPicker.android.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/androidMain/kotlin/com/example/firestationops/platform/MediaPicker.android.kt)
- `actual` implementation using Android `ActivityResultContracts.TakePicture`.

---

### UI Enhancements

#### [MODIFY] [InspectionScreen.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/ui/inspection/InspectionScreen.kt)
- Add "Add Photo" button to `InspectionItemCard` when status is `FAIL`.
- Show thumbnails of attached photos.

#### [MODIFY] [DeficiencyDetailScreen.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/ui/deficiency/DeficiencyDetailScreen.kt)
- Display attachments associated with the deficiency.

## Verification Plan

### Automated Tests
- Unit tests for `SyncStatus` transitions.
- Repository tests for attachment persistence.

### Manual Verification
1. Start an inspection, fail an item.
2. Attach a photo (using camera or mock picker).
3. Verify photo is saved locally and referenced in the inspection summary.
4. Verify the record is marked as `PENDING_SYNC` (once the sync worker is implemented in a follow-up task).
