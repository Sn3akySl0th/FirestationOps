# Implementation Plan - Robust Deficiency Handling and Resolution

Enhance the inspection workflow to support detailed deficiency reporting (severity, mandatory notes) and ensure apparatus status is correctly updated when deficiencies are reported or resolved.

## User Review Required

> [!IMPORTANT]
> This change introduces mandatory fields in the inspection workflow. If an item fails, a note might be required based on the template or the selected severity (OUT_OF_SERVICE always requires a note).

## Proposed Changes

### Core Logic & Repository Layer

#### [MODIFY] [FirestationOps.sq](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/sqldelight/com/example/firestationops/db/FirestationOps.sq)
- Add `updateApparatusStatus` query.

#### [MODIFY] [FirestationOpsDatabase.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/db/FirestationOpsDatabase.kt)
- Add `updateApparatusStatus` method to the database wrapper.

#### [MODIFY] [ApparatusRepository.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/domain/repository/ApparatusRepository.kt)
- Add `updateApparatusStatus(id: String, status: ApparatusStatus): Result<Unit>` to the interface.

#### [MODIFY] [PersistentApparatusRepository.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/domain/repository/persistent/PersistentApparatusRepository.kt)
- Implement `updateApparatusStatus`.

---

### Inspection Workflow Enhancements

#### [MODIFY] [InspectionViewModel.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/ui/inspection/InspectionViewModel.kt)
- Update `InspectionUiState` to track severity per failed item.
- Update `updateResponse` to accept severity.
- Enhance `submit` logic:
    - Enforce mandatory notes (if `requiresNoteOnFail` or `OUT_OF_SERVICE`).
    - Update `ApparatusStatus` to `OUT_OF_SERVICE` if any reported deficiency is `OUT_OF_SERVICE`.
    - Map selected severities to created `Deficiency` objects.

#### [MODIFY] [InspectionScreen.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/ui/inspection/InspectionScreen.kt)
- Update `InspectionItemCard` to show severity selection when an item fails.
- Highlight mandatory note requirement.

---

### Deficiency Resolution Enhancements

#### [MODIFY] [DeficiencyDetailViewModel.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestationOps/app/shared/src/commonMain/kotlin/com/example/firestationops/ui/deficiency/DeficiencyDetailViewModel.kt)
- When resolving a deficiency, check if it was the last `OUT_OF_SERVICE` deficiency for that apparatus.
- If it was, update the apparatus status back to `IN_SERVICE`.

## Verification Plan

### Automated Tests
- Unit test for `InspectionViewModel` submission logic:
    - Verify `OUT_OF_SERVICE` deficiency triggers apparatus status update.
    - Verify mandatory note validation.
- Unit test for `DeficiencyDetailViewModel` resolution logic:
    - Verify resolving the last OOS deficiency restores apparatus status.

### Manual Verification
1. Start an inspection.
2. Mark an item as `FAIL` and select `OUT_OF_SERVICE`.
3. Verify that the "Submit" button is disabled until a note is entered.
4. Submit and verify the apparatus status in the dashboard changes to `OUT_OF_SERVICE`.
5. Go to the deficiency list, resolve the OOS deficiency.
6. Verify the apparatus status returns to `IN_SERVICE`.
