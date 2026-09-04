# Engine 5 Inventory Checkoff — sample capture

Source: department paper **Inventory Checkoff** sheets for **ENGINE 5** (three pages, year line on form). Transcribed into the Milestone 17 template-import CSV for fixture and acceptance use.

**Fixture:** `app/shared/src/commonTest/resources/fixtures/inspections/engine-5-inventory-checkoff-template.csv`

## Template metadata (when importing)

| Field | Value |
|---|---|
| Template name | `Weekly Inventory Checkoff — Engine 5` |
| Apparatus | `ENGINE 5` (type: Engine) |
| Frequency | `168` hours (weekly) |
| Item count | 158 lines |

## Section summary

| Category | Lines | Notes |
|---|---|---|
| Cab | 25 | Mix of qty and functional (lights/siren) |
| D.S. Pump Panel | 7 | |
| D.S. Compartment 1 | 38 | Largest section |
| D.S. Compartment 2 | 5 | |
| D.S. Chute 1 / 2 | 1 each | |
| D.S. Compartment 3 | 17 | Includes saws/PPV; paper sometimes marks REMOVED |
| D.S. Top of Engine | 1 | Hard suction (paper also lists nearby on other page) |
| Rear compartments | 12 | D.S., Middle, O.S. combined |
| Hosebed | 5 | |
| O.S. Compartment 3 / 2 / 1 | 12 / 8 / 4 | |
| O.S. Chute 1 / 2 | 1 each | |
| Top of Engine | 6 | Scene lights, foam, strainers, monitor, deck gun |
| O.S. Running Board | 2 | |
| Functional Checks | 12 | Run / fuel / charge questions as flat items |

Most inventory lines have `expectedQuantity`. Presence-only (empty qty): cab light checks, Jump Pads, Bag of Stuffed Animals, and all Functional Checks.

## Inspection header fields (not in template CSV)

Capture on the inspection record (domain fields to add in Milestone 17 implementation), matching the paper header table:

| Field | Paper | Recommended type |
|---|---|---|
| Inspected by | ID / name | Signed-in member (already) |
| Date | MM-DD-YY | `completedAt` / field-entry time |
| Mileage | Odometer | `odometerMiles: Int?` |
| Oil | F / fraction | `fluidOil: String?` |
| Transmission | F / fraction | `fluidTransmission: String?` |
| Fuel | F / fraction | `fluidFuel: String?` |
| Antifreeze | F / fraction | `fluidAntifreeze: String?` |
| Power steering | F / fraction | `fluidPowerSteering: String?` |

Do **not** model fluids as inventory checklist rows in v1.

## Paper behaviors → app mapping

| Paper | App (v1) |
|---|---|
| Checkmark | PASS (+ `actualQuantity = expectedQuantity` when qty set) |
| X / missing | FAIL + note; deficiency |
| Handwritten count (e.g. `2` gloves) | `actualQuantity` |
| `1/2` gas | FAIL or note on Gallon of Gas (whole-qty MVP; no fractional inventory yet) |
| REMOVED across days | FAIL/N/A + deficiency; officer edits template when permanently gone |
| OUT OF SERVICE (e.g. Bullard TIC) | FAIL on charge check + deficiency |
| 7 day columns on one sheet | Separate weekly inspection records + history/compliance (no multi-day grid UI) |

## Transcription caveats

- Inch marks (`"`) omitted in CSV text to keep parsing simple; meaning preserved (`2.5`, `1.75`, etc.).
- Pump-panel nozzle lines vs compartment nozzle lines kept as separate locations (as on paper).
- Duplicate “Hard Suction” on paper collapsed to one **D.S. Top of Engine** line.
- “Athens City Hydrant Wrench” kept as printed (department equipment name).
- No real member IDs, dates, or mileage from filled sheets were copied into fixtures.

## Acceptance checklist (Engine 5 sample)

Use this list when verifying Milestone 17 against the real sheet:

- [ ] CSV imports all **158** Engine 5 lines without manual one-by-one entry
- [ ] Sections match paper locations (Cab through Functional Checks); collapsible with per-section progress
- [ ] Quantity lines show expected count and require `actualQuantity` on submit
- [ ] Short count (e.g. gloves expected 4, found 2) fails and can open a deficiency
- [ ] Functional check lines work with PASS/FAIL/N/A + note (no nested UI required)
- [ ] Inspection can record mileage + five fluid readings alongside the checklist
- [ ] Template assigns to **ENGINE 5** without replacing a separate daily ops template
- [ ] Completed inspection retains item text, expected qty, and actual qty
- [ ] Works offline with draft autosave through all sections
- [ ] Export CSV/PDF includes category and quantity columns
- [ ] (Later) Historical import can backfill past weekly sheets for Engine 5

## Suggested next implementation slice

1. Domain: `expectedQuantity` / `actualQuantity` + optional mileage/fluid fields on `Inspection`
2. Template CSV import wired to this fixture in tests
3. Category-grouped inspection UI
4. Assign template to Engine 5 apparatus
