# Milestone 17: Weekly Vehicle Stocking Inspections

Digitize multi-page weekly gear/stocking checklists (tools, hoses, EMS supplies, etc.) so crews can complete them on a tablet instead of paper. Support large checklists (80–150+ items), section-based navigation, **quantity verification** (expected vs. on-hand count), and **historical import** of past completed sheets so compliance history is not lost when going digital.

## Status

**Planned** — blocked on a sample department checklist from the field to validate categories, item naming, and quantity rules.

## Current State (Milestones 1–15)

| Area | Status |
|------|--------|
| Inspection templates + items with `category` | Done |
| PASS / FAIL / N/A responses, notes, photos | Done |
| Weekly frequency (`frequencyHours = 168`) | Done |
| Offline draft autosave + submit + deficiencies | Done |
| Template admin UI (Department settings) | Done — manual one-line entry only |
| Inspection field UI | Done — flat list, no section grouping |
| Template selection | By apparatus **type** only (first active template) |
| Quantity fields | **Not implemented** |
| Historical inspection import | **Not implemented** |

**Gap:** Paper weekly stocking sheets (often 3+ pages per vehicle) cannot be entered or completed efficiently. Quantity-on-hand is not captured. Past completed paper records are not in the system, so compliance history resets when crews go digital.

---

## Goal

Replace paper weekly vehicle stocking checklists with an offline-capable digital workflow where:

1. Admins can load a full vehicle gear list without typing 100+ lines by hand.
2. Crews work through logical sections (hose bed, cab, tools, EMS, etc.) with clear progress.
3. Each line can record **how many should be on the truck** vs. **how many were found**.
4. Shortfalls create deficiencies with the same accountability rules as today.
5. Each apparatus can have its own stocking list (or pick among templates).
6. Officers can **import past completed sheets** (from spreadsheet exports of paper records) so overdue/compliance views reflect real history.

---

## Scope

### In scope

#### Domain model

- Add optional `expectedQuantity: Int?` to `InspectionTemplateItem` (null = presence-only check, no count).
- Add optional `actualQuantity: Int?` to `InspectionResponse`.
- Validation rules (domain, tested):
  - When `expectedQuantity` is set, `actualQuantity` is required on submit.
  - `actualQuantity < expectedQuantity` → treat as FAIL (or auto-set FAIL with configurable severity default `REPAIR_NEEDED`).
  - `actualQuantity >= expectedQuantity` → PASS (unless crew overrides to FAIL for damaged/wrong item).
  - `actualQuantity` must be `>= 0`.
- Preserve template version + item text + expected quantity on completed inspections (existing snapshot behavior via stored responses + template version).
- Add `InspectionEntrySource` (e.g. `FIELD`, `HISTORICAL_IMPORT`) on `Inspection` so imported records are auditable and distinguishable from live field entry.
- Add `importedAt` / `importedByUserId` audit fields when source is `HISTORICAL_IMPORT`.

#### Template administration

- **CSV bulk import** for template items:
  - Columns: `category`, `text`, `description` (optional), `expectedQuantity` (optional), `requiresNoteOnFail` (optional).
  - Preview + confirm before save.
  - Assign categories that map to paper “pages” or sections.
- **Per-apparatus template assignment** (or explicit template picker at inspection start):
  - Support multiple templates per apparatus type (e.g. daily ops vs. weekly stocking).
  - Prefer `apparatus.defaultTemplateIds` or a dedicated assignment table — decide during implementation.
- Template editor: show `category` and `expectedQuantity` per row (not just free-text line).

#### Field UI (large checklists)

- Group items by `category` with collapsible section headers.
- Per-section progress: `12 / 18 complete`.
- Overall progress bar across all items.
- Search / filter by item text.
- Quantity entry: stepper or numeric field when `expectedQuantity` is set; show `Found: _ / Expected: 4`.
- Optional **“Mark section all present”** (sets PASS with `actualQuantity = expectedQuantity` for qty items).
- Keep existing PASS / FAIL / N/A, note, and photo flows for failed or damaged items.

#### Compliance & dashboard

- Weekly stocking templates use `frequencyHours = 168` (or department-configured interval).
- Dashboard overdue logic applies to assigned weekly template per apparatus (not only type-level default).

#### Historical inspection import

Backfill **completed** weekly stocking inspections from spreadsheet exports of paper sheets, using the same column conventions as template CSV where possible.

- **Who:** `ADMIN` or `OFFICER` only (Department settings → **Import inspection history**).
- **Input:** CSV in *long format* — one row per item response, grouped into inspections by shared metadata columns (see format below).
- **Matching:**
  - Apparatus by `radioName` or `apparatusId` (must exist in catalog).
  - Template by `templateName` or `templateId` (must exist; import template first if needed).
  - Items by `itemText` + `category` within the template (fuzzy trim/case-insensitive match with preview of unmatched rows).
- **Required metadata per inspection group:**
  - `completedAt` (date/time from paper sheet — device local timezone)
  - `completedByEmail` or `completedByMemberNumber` (must resolve to an existing department member, or import preview flags row for manual fix)
  - `apparatusRadioName` (or `apparatusId`)
  - `templateName` (or `templateId`)
- **Per-item columns:** `status` (`PASS` / `FAIL` / `N/A`), `actualQuantity` (optional), `note` (optional).
- **Import preview** before commit:
  - Summary: N inspections, M item rows, unmatched apparatus/template/items, duplicate warnings.
  - Duplicate rule: same apparatus + template + `completedAt` (same calendar day) → warn and skip or require explicit override.
- **Persisted behavior:**
  - Create finalized `Inspection` records with `entrySource = HISTORICAL_IMPORT`.
  - Set `syncStatus = PENDING_SYNC` so officers can upload archive to cloud, or offer **“Keep local archive only”** → `LOCAL_ONLY` + no upload queue.
  - **Optional checkbox:** “Create deficiencies for failed items” (default off for history — past deficiencies may already be resolved on paper).
- **Compliance:** Imported finalized inspections count toward `InspectionComplianceCalculator` / dashboard overdue logic (same as field-submitted records).
- **UI:** List imported inspections with a “Historical import” badge in inspection history (when history view exists) or on export.

#### Export

- Extend CSV/PDF export to include `expectedQuantity`, `actualQuantity`, category, and `entrySource` columns.
- Provide a **history export template** download so admins can transcribe paper sheets into the expected CSV shape.

#### Tests

- Quantity validation rules.
- Auto-fail when actual < expected.
- Template CSV import parsing.
- Historical inspection CSV grouping, member/apparatus/template resolution, duplicate detection.
- Imported inspections included in compliance calculation.
- Section progress calculation.
- Template assignment resolution per apparatus.

### Non-goals (defer)

- Barcode/QR scanning for inventory items.
- OCR or photo scan of paper sheets (CSV/spreadsheet transcription only).
- Automatic reorder / supply ordering integration.
- Shared department-wide inventory pool (warehouse vs. on-truck).
- Quantity trend charts / analytics dashboards over time.
- iOS/web file export for checklist PDFs (follow existing platform stubs).
- Full merge UI for conflicting stocking inspections across devices (see planned Milestone 16).

---

## Sample Checklist Input (pending)

> [!IMPORTANT]
> A real department sample checklist is required before finalizing category names, item density, and quantity conventions.

When provided, capture:

- Vehicle type and radio name (e.g. Engine 1, Tanker 2).
- Section names (paper page headers).
- Approximate line count per section.
- Which lines use quantity vs. presence-only.
- Whether “serviceable condition” is separate from “present” (may stay as FAIL + note).
- Any items marked N/A on specific rigs.
- Whether past paper sheets record inspector signature, date only, or both (drives `completedAt` / `completedBy` mapping).
- Typical lookback period to import (e.g. last 90 days vs. full year).

Store redacted fixtures under `app/shared/src/commonTest/resources/` for template import, history import, and UI tests (fictional item names only).

---

## Proposed CSV formats

### Template import (checklist definition)

```csv
category,text,description,expectedQuantity,requiresNoteOnFail
Hose bed,1½ inch fog nozzle (preconnect),,1,true
Hose bed,2½ inch smooth bore,,1,true
Cab,SCBA spare cylinder,,2,true
Tools,Haligan bar,,1,true
Tools,Pick head axe,,1,true
EMS,Trauma dressings,4x4,,4,false
```

### Historical inspection import (completed sheets)

One row per item. Rows sharing the same `importGroupKey` (or same apparatus + template + completed date) form one inspection.

```csv
importGroupKey,apparatusRadioName,templateName,completedAt,completedByEmail,category,itemText,status,actualQuantity,note
2025-03-10-E1,E1,Weekly Stocking - Engine 1,2025-03-10T08:30:00,member@example.com,Hose bed,1½ inch fog nozzle (preconnect),PASS,1,
2025-03-10-E1,E1,Weekly Stocking - Engine 1,2025-03-10T08:30:00,member@example.com,Cab,SCBA spare cylinder,FAIL,1,Expired cylinder noted on paper
2025-03-17-E1,E1,Weekly Stocking - Engine 1,2025-03-17T09:00:00,member@example.com,Tools,Haligan bar,PASS,1,
```

`importGroupKey` can be any stable string (e.g. `YYYY-MM-DD-radioName`) — helps when transcribing one paper sheet at a time.

---

## User flows

### Admin — load a new vehicle checklist

1. Department settings → Templates → **Import from CSV**.
2. Name template (e.g. `Weekly Stocking — Engine 1`), set frequency to 168 hours.
3. Assign template to Engine 1 apparatus.
4. Sync catalog to cloud.

### Member — weekly stocking in the bay

1. Dashboard → Engine 1 → **Weekly stocking check**.
2. Expand **Hose bed** section; enter counts or tap PASS per line.
3. Continue through Cab, Tools, EMS sections (draft autosaves).
4. Submit → deficiencies created for shortfalls; dashboard shows compliance updated.

### Officer — backfill paper history

1. Transcribe past completed paper sheets into the history CSV (or export from an existing spreadsheet).
2. Department settings → **Import inspection history** → select CSV.
3. Review preview: matched apparatus, template, items, duplicates, unresolved members.
4. Confirm import → finalized historical inspections stored locally.
5. Optional: sync to cloud; optional: create deficiencies for failed rows.

---

## Acceptance criteria

- [ ] Admin can import 100+ items from CSV without manual one-by-one entry.
- [ ] Inspection screen groups items by category with section and overall progress.
- [ ] Items with `expectedQuantity` require `actualQuantity` on submit.
- [ ] `actualQuantity < expectedQuantity` fails the item and can open a deficiency.
- [ ] Each apparatus can run a weekly stocking template distinct from its daily check.
- [ ] Completed inspection retains item text, expected qty, and actual qty at time of completion.
- [ ] CSV/PDF export includes quantity columns.
- [ ] Works fully offline; syncs when connectivity returns.
- [ ] Officer can import past completed sheets from CSV with preview and duplicate warnings.
- [ ] Imported inspections are marked `HISTORICAL_IMPORT` and count toward compliance/overdue calculation.
- [ ] Unmatched apparatus, template, items, or members are surfaced in preview — nothing silently dropped.

---

## Offline / sync considerations

- Template catalog changes sync via existing catalog download path.
- Completed stocking inspections follow existing `PENDING_SYNC` → upload flow.
- Quantity fields are part of inspection response payload in Firestore mappers.
- Historical imports use the same Firestore inspection document shape; include `entrySource` in mapper payload.
- Do not overwrite local `PENDING_SYNC` inspections on download (existing rule).
- Idempotent history import: re-importing the same `importGroupKey` should warn/skip, not duplicate records.

---

## Security / permissions

- CSV template import, history import, and template edit: `ADMIN` or `OFFICER` (match existing catalog admin rules).
- Historical import must not allow cross-department apparatus or member references.
- Any member can complete assigned stocking inspections for apparatus they can access.
- Department isolation unchanged (`departmentId` on all records).

---

## Suggested implementation order

1. Domain: `expectedQuantity` / `actualQuantity` + `InspectionEntrySource` + validation tests.
2. Template CSV import in catalog admin.
3. Category-grouped inspection UI + progress.
4. Per-apparatus template assignment / picker.
5. Historical inspection CSV import + preview + compliance integration tests.
6. Export updates (include qty + entry source; downloadable history template).
7. Redacted sample-checklist fixture tests once sample is provided.

---

## Risks & open questions

| Risk | Mitigation |
|------|------------|
| 150+ items slow on low-end tablets | Section collapse, lazy list, search |
| Daily + weekly both due on same truck | Template picker; separate compliance per template |
| Quantity vs. condition (4 present but damaged) | FAIL override + note always available |
| Template drift after gear change | Version templates; completed records keep old version |
| Paper history does not match current template items | Preview unmatched rows; allow partial import or fix CSV |
| Re-importing same sheet creates duplicates | `importGroupKey` + apparatus + date duplicate detection |
| Inspector on paper is not in member roster | Preview error; officer adds member first or maps email |

**Open until sample received:** exact section names, avg item count, % of lines with qty, whether partial quantities (e.g. 3 of 4) need a distinct severity, how paper sheets record date/signature, and how far back history should be transcribed.

---

## Branch

`feature/milestone-17-weekly-stocking-inspections`
