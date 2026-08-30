# Milestone 6: Officer Dashboard — Overdue Inspections & Open Deficiencies

High-level department view for officers: inspection compliance, overdue apparatus, and open deficiency workload.

## Current State (Milestones 1–5)

| Area | Status |
|------|--------|
| Auth, roles, department membership | Done |
| Stations, apparatus, inspection templates | Done |
| Offline inspection workflow + draft autosave | Done |
| Deficiency lifecycle (severity, OOS, resolution) | Done |
| Photo attachments + sync status tracking | Done |
| Basic Operations Dashboard | Partial — station/apparatus list + open deficiency count only |

**Gap:** No concept of inspection due/overdue, no department-wide compliance summary, no officer-focused layout, no role-based dashboard differentiation.

---

## Goal

Give officers and apparatus officers a single screen to answer:

1. Which apparatus have **overdue inspections**?
2. What **open deficiencies** need attention, and how severe are they?
3. Which units are **out of service** and why?
4. Is anything **pending sync** that hasn't reached the server?

## Scope

### In scope

- Domain logic to compute inspection compliance per apparatus (due / overdue / current / never inspected)
- Repository queries for department-level inspection history
- Enhanced dashboard UI with summary KPIs and actionable lists
- Per-apparatus inspection status on station cards
- Deficiency breakdown by severity (OOS, repair needed, informational)
- Navigation from dashboard items to inspection or deficiency detail
- Unit tests for compliance calculation and dashboard aggregation
- Desktop-friendly wide layout (two-column on wide screens)

### Non-goals (defer to later milestones)

- PDF/CSV export (Milestone 7)
- Firebase sync worker implementation (follow-up after sync status exists)
- Push notifications for overdue inspections
- Officer assignment workflows / workload routing
- Historical trend charts or analytics
- Configurable per-department inspection schedules in admin UI (use template-level defaults for now)

---

## User Review Required

> [!IMPORTANT]
> **Inspection overdue rules** — propose defaults below. Confirm or adjust before implementation.

| Template type (seed data) | Proposed interval | Overdue when |
|---------------------------|-------------------|--------------|
| Daily Engine Inspection | 24 hours | No finalized inspection in the last 24h (or since start of local calendar day — see open question) |
| Weekly Ladder Inspection | 7 days | No finalized inspection in the last 7 days |
| Other / unknown | 24 hours (fallback) | Same as daily |

> [!IMPORTANT]
> **Role visibility** — propose: all authenticated members see the enhanced dashboard; officers/admins see the full summary including overdue list and severity breakdown. Members still see station cards but overdue items are de-emphasized or collapsed. Alternative: gate the entire officer view behind `OFFICER | APPARATUS_OFFICER | ADMIN`.

### Open questions

1. **Daily due boundary:** Rolling 24h from last inspection, or "due by end of station day" (e.g., must complete before midnight local)?
2. **Draft in progress:** Should an in-progress draft count as "in progress" (not overdue) or still show overdue if the last *finalized* inspection is stale?
3. **Reserve apparatus:** Should `RESERVE` status apparatus be excluded from overdue calculations?

---

## Proposed Changes

### 1. Domain — Inspection schedule & compliance

#### [MODIFY] `InspectionTemplate.kt`
- Add `frequencyHours: Int` (default `24` for daily, `168` for weekly in seed data).
- Keeps schedule co-located with the template that defines what to inspect.

#### [NEW] `InspectionCompliance.kt` (domain model)
```kotlin
enum class InspectionComplianceStatus { CURRENT, DUE_SOON, OVERDUE, NEVER_INSPECTED, IN_PROGRESS }

data class ApparatusInspectionStatus(
    val apparatusId: String,
    val templateId: String?,
    val templateName: String?,
    val status: InspectionComplianceStatus,
    val lastCompletedAt: Long?,
    val dueAt: Long?,
    val daysOverdue: Int = 0,
    val draftInspectionId: String? = null
)
```

#### [NEW] `InspectionComplianceCalculator.kt` (pure domain, testable)
- Input: apparatus list, active templates (by type), finalized inspections, optional drafts, `now: Instant`, `dueSoonThresholdHours: Int = 4`.
- Output: `ApparatusInspectionStatus` per apparatus.
- Rules:
  - Match apparatus `type` → active template for department.
  - Find latest finalized inspection (`isFinalized == true`, `completedAt != null`).
  - If draft exists and no finalized today → `IN_PROGRESS`.
  - If never finalized → `NEVER_INSPECTED` (treat as overdue for dashboard).
  - If `now > lastCompleted + frequencyHours` → `OVERDUE` with `daysOverdue`.
  - If within `dueSoonThreshold` of due → `DUE_SOON`.
  - Else → `CURRENT`.

---

### 2. Data layer — Repository extensions

#### [MODIFY] `InspectionRepository.kt`
- `fun getInspectionsByDepartment(departmentId: String): Flow<List<Inspection>>`
- `suspend fun getLatestFinalizedInspection(apparatusId: String): Result<Inspection?>`

#### [MODIFY] `FirestationOps.sq`
- `selectInspectionsByDepartment: SELECT * FROM InspectionEntity WHERE departmentId = ?`
- `selectLatestFinalizedByApparatus: SELECT * FROM InspectionEntity WHERE apparatusId = ? AND isFinalized = 1 ORDER BY completedAt DESC LIMIT 1`

#### [MODIFY] `PersistentInspectionRepository.kt` + `MockInspectionRepository.kt`
- Implement new queries.
- Update seed templates with `frequencyHours`.

#### [MODIFY] `DeficiencyRepository.kt` (optional convenience)
- `fun getOpenDeficiencySummary(departmentId: String): Flow<DeficiencySummary>` — or compute in ViewModel from existing `getOpenDeficiencies`.

```kotlin
data class DeficiencySummary(
    val total: Int,
    val outOfService: Int,
    val repairNeeded: Int,
    val informational: Int,
    val oldestOpenAt: Long?
)
```

---

### 3. Presentation — Dashboard upgrade

#### [MODIFY] `DashboardViewModel.kt`
- Inject `InspectionRepository` and `Clock` (or `() -> Long` for testability).
- Expose unified `DashboardUiState`:

```kotlin
sealed interface DashboardUiState {
    object Loading : DashboardUiState
    data class Success(
        val summary: DashboardSummary,
        val stations: List<StationDashboardSection>,
        val overdueInspections: List<OverdueInspectionItem>,
        val topDeficiencies: List<DeficiencyWithApparatus>, // top N by severity + age
        val pendingSyncCount: Int
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

data class DashboardSummary(
    val overdueCount: Int,
    val dueSoonCount: Int,
    val openDeficiencyCount: Int,
    val outOfServiceApparatusCount: Int,
    val pendingSyncCount: Int
)
```

- Combine flows: apparatus, stations, deficiencies, inspections, drafts.
- Run `InspectionComplianceCalculator` on each emission.

#### [MODIFY] `DashboardScreen.kt`
- Rename header to **Officer Dashboard** (or show role-appropriate title).
- **Summary row** (horizontal scroll on mobile, grid on desktop):
  - Overdue inspections (red)
  - Open deficiencies (orange)
  - Out of service (red badge)
  - Pending sync (neutral, if > 0)
- **Overdue inspections section** — list apparatus missing required inspection, sorted by days overdue; tap → start/continue inspection.
- **Open deficiencies section** — top 5 by severity; "View all" → existing `DeficiencyList` screen.
- **Station cards** — add per-apparatus inspection badge alongside status badge:
  - `CURRENT` → green "Inspected"
  - `DUE_SOON` → amber "Due soon"
  - `OVERDUE` / `NEVER_INSPECTED` → red "Overdue"
  - `IN_PROGRESS` → blue "In progress"
- Use text labels per AGENTS.md accessibility rules (not color alone).
- `BoxWithConstraints` or window-size check for desktop two-column layout (summary + overdue left, deficiencies right).

#### [MODIFY] `App.kt`
- Pass `inspectionRepository` into `DashboardViewModel`.
- Optionally pass `member.roles` for conditional sections.

---

### 4. Seed data for manual testing

#### [MODIFY] `PersistentInspectionRepository.seed()` or a `seedDashboardDemoData()` helper (dev only)
- Insert a finalized inspection for E1 completed 2 days ago → shows overdue.
- Insert a finalized inspection for R1 completed today → shows current.
- Leave L1 with no inspections → `NEVER_INSPECTED`.
- Ensure at least one open OOS deficiency exists for summary cards.

---

### 5. Tests

#### [NEW] `InspectionComplianceCalculatorTest.kt`
- `neverInspected_isOverdue`
- `completedWithinInterval_isCurrent`
- `completedOutsideInterval_isOverdue_withCorrectDays`
- `draftWithoutRecentFinalized_isInProgress`
- `dueSoon_withinThreshold`
- `weeklyTemplate_usesSevenDayInterval`
- `reserveApparatus_excluded` (if decided)

#### [NEW] `DashboardViewModelTest.kt`
- Aggregates overdue count correctly.
- Deficiency severity breakdown matches open deficiencies.
- OOS apparatus count matches apparatus with `OUT_OF_SERVICE` status or open OOS deficiencies.
- Error state on repository failure.

---

## Acceptance Criteria

- [ ] Officer sees summary counts: overdue inspections, open deficiencies, OOS apparatus.
- [ ] Overdue apparatus list is accurate based on template `frequencyHours` and last finalized inspection.
- [ ] Tapping an overdue apparatus navigates to the inspection screen.
- [ ] Open deficiency summary card navigates to deficiency list.
- [ ] Station cards show both apparatus status and inspection compliance badge.
- [ ] Dashboard handles empty states (no apparatus, no deficiencies, all current).
- [ ] Loading and error states are explicit.
- [ ] Compliance logic is unit-tested with a fixed clock.
- [ ] `./gradlew :app:shared:testAndroidHostTest` passes.
- [ ] Manual verification on Android and desktop.

---

## Offline / Sync Considerations

- All dashboard data comes from local SQLDelight — works fully offline.
- Show `pendingSyncCount` badge when inspections/deficiencies/attachments have `syncStatus != SYNCED`.
- Do not block dashboard on network; sync status is informational only in this milestone.

---

## Security / Permission Considerations

- Dashboard queries scoped by `departmentId` from authenticated session (already enforced).
- Role-based section visibility: officers see full overdue list; consider hiding from plain `MEMBER` if department policy requires it.
- No cross-department data leakage in repository queries.

---

## Files Likely Affected

| File | Change |
|------|--------|
| `domain/model/InspectionTemplate.kt` | Add `frequencyHours` |
| `domain/model/InspectionCompliance.kt` | New |
| `domain/InspectionComplianceCalculator.kt` | New |
| `domain/repository/InspectionRepository.kt` | New methods |
| `domain/repository/persistent/PersistentInspectionRepository.kt` | Implement + seed |
| `domain/repository/mock/MockInspectionRepository.kt` | Implement |
| `sqldelight/.../FirestationOps.sq` | New queries |
| `db/FirestationOpsDatabase.kt` | Wrapper methods |
| `ui/dashboard/DashboardViewModel.kt` | Major enhancement |
| `ui/dashboard/DashboardScreen.kt` | Major UI enhancement |
| `App.kt` | Wire inspection repo + roles |
| `commonTest/.../InspectionComplianceCalculatorTest.kt` | New |
| `commonTest/.../DashboardViewModelTest.kt` | New |

---

## Implementation Order

1. **Domain first** — `frequencyHours`, compliance model, calculator + tests.
2. **Data layer** — SQL queries, repository methods, seed updates.
3. **ViewModel** — combine flows, map to `DashboardUiState` + tests.
4. **UI** — summary cards, overdue list, enhanced station cards, desktop layout.
5. **Integration** — wire in `App.kt`, manual QA with seeded overdue data.

Estimated effort: **1 focused PR** (matches prior milestone sizing).

---

## Manual Verification Plan

1. Log in as seeded officer (`admin@example.com` or configured test user).
2. Confirm summary shows overdue count ≥ 1 (from seed data).
3. Tap overdue apparatus → inspection screen opens.
4. Complete and finalize inspection → return to dashboard → apparatus shows "Inspected".
5. Confirm open deficiency card shows correct count; tap → deficiency list.
6. Resolve an OOS deficiency → dashboard OOS count updates.
7. Run desktop app → verify two-column layout at wide width.
8. Toggle airplane mode → dashboard still loads from local DB.

---

## Risks & Follow-ups

| Risk | Mitigation |
|------|------------|
| No `frequencyHours` on legacy templates | Default to 24h in calculator |
| Timezone edge cases for "daily" | Use device local timezone consistently; document in code |
| Performance with large fleets | Compliance calc is in-memory over small lists; add DB aggregation later if needed |
| Dashboard vs field UX | Keep station cards tappable for members; officer sections can collapse on phone |

**Follow-up (post-M6):** Firebase sync worker, push reminders for overdue inspections, admin UI to edit template schedules, Milestone 7 exports.
