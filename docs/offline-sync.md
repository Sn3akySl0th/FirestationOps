# Offline sync

FirestationOps is offline-first. Operational writes are saved locally first, then uploaded when cloud sync is available.

## Sync states

| Status | Meaning |
|--------|---------|
| `LOCAL_ONLY` | Saved on device only; not queued for upload (draft inspections). |
| `PENDING_SYNC` | Queued for upload to Firebase. |
| `SYNCED` | Confirmed uploaded to Firestore/Storage. |
| `SYNC_FAILED` | Upload failed; retry on next sync. |
| `CONFLICT` | Reserved for future conflict resolution. |

## Write path

1. User action saves to SQLDelight immediately.
2. Finalized inspections, deficiencies, attachments, and incident records are marked `PENDING_SYNC`.
3. Draft inspections remain `LOCAL_ONLY` until submitted.
4. The dashboard shows a pending-sync count.

## Sync order

The Android sync worker downloads the department catalog first, then operational records, then uploads pending local changes:

**Download (cloud → device)**

1. Department catalog (department, stations, apparatus, templates, members)
2. Finalized inspections
3. Deficiencies
4. Incidents, command-log entries, unit assignments, and personnel assignments
5. Attachment metadata and photo files from Cloud Storage

**Upload (device → cloud)**

1. Attachments (files to Cloud Storage, metadata to Firestore)
2. Finalized inspections
3. Deficiencies
4. Incidents, command-log entries, unit assignments, and personnel assignments

Catalog download includes the department document, stations, apparatus, templates, and department member roster. Administrators can upload an initial catalog from **Department settings** when the cloud catalog is empty.

Records with local `PENDING_SYNC` status are not overwritten by cloud downloads. This prevents losing unsent field work when another device has older cloud data.

Record IDs are reused as Firestore document IDs for idempotent retries.

## Triggers

- **Manual:** Dashboard **Sync now** button
- **Foreground:** Initial sync after authentication when Firebase is configured
- **Background:** WorkManager periodic job every 15 minutes when network is available (Android only)
- **Desktop manual:** Dashboard **Sync now** runs sync in a background coroutine when desktop Firebase is configured

Desktop and web targets use `NoOpSyncCoordinator` unless Firebase is configured. The Windows desktop app supports Firebase when `firebase-desktop.json` (or `~/.firestationops/firebase.json`) is present.

## Platform notes

- Firebase SDK calls live in `app/shared/src/androidMain` (Android) and `app/shared/src/jvmMain` (desktop) behind `SyncCoordinator` and `AuthRepository` interfaces.
- Shared sync logic lives in `DepartmentSyncEngine` with platform-specific `CloudSyncClient` implementations.
- Without `google-services.json`, Android falls back to local simulated authentication.
- Without desktop Firebase config, Windows falls back to local simulated authentication.
