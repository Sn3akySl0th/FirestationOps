# Milestone 7: PDF/CSV Inspection Export

Export finalized inspection summaries as CSV and PDF from the post-submit success screen.

## Scope

- `InspectionReport` snapshot model and builder from submitted inspection data
- Pure-Kotlin CSV and minimal PDF generation in `commonMain`
- Platform `FileExporter` — desktop save dialog (JVM), Android CreateDocument, stubs elsewhere
- Export buttons on inspection success screen
- Unit tests for report builder and CSV output

## Non-goals

- Bulk department exports, email, Firebase dependency, print preview composable
