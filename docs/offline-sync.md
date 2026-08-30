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

## Upload order

The Android sync worker uploads in dependency order:

1. Attachments (files to Cloud Storage, metadata to Firestore)
2. Finalized inspections
3. Deficiencies
4. Incidents, command-log entries, unit assignments, and personnel assignments

Record IDs are reused as Firestore document IDs for idempotent retries.

## Triggers

- **Manual:** Dashboard **Sync now** button
- **Foreground:** Initial sync after authentication when Firebase is configured
- **Background:** WorkManager periodic job every 15 minutes when network is available (Android only)

Desktop and web targets currently use `NoOpSyncCoordinator` and remain local-only.

## Platform notes

- Firebase SDK calls live in `app/shared/src/androidMain` behind `SyncCoordinator` and `AuthRepository` interfaces.
- Without `google-services.json`, Android falls back to local simulated authentication.
