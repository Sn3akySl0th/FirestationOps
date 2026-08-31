# Milestone 16: Sync Conflict Detection and Resolution

Detect when deficiencies and incidents diverge across devices, preserve both versions, and let officers choose which copy to keep.

## Status

**Implemented** on branch `feature/milestone-16-sync-conflict-resolution`.

## Scope delivered

- Sync baselines stored locally after successful download/upload.
- Upload conflict detection for deficiencies and incidents using baseline/local/remote comparison.
- `SyncConflict` persistence and `CONFLICT` sync status on affected records.
- Officer **Review conflicts** screen with keep-local / keep-cloud actions.
- Dashboard banner when unresolved conflicts exist.
- Domain tests for detector, resolver, and dashboard integration.

## Non-goals (defer)

- Field-level merge for individual properties.
- Conflict resolution for finalized inspections (remain append-only).
- Command-log / assignment conflict UI (same engine pattern can extend later).

## Branch

`feature/milestone-16-sync-conflict-resolution`
