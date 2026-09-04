package com.example.firestationops.domain.catalog

/**
 * Parses checklist definition CSV into template item inputs.
 *
 * Expected header columns (order flexible; names case-insensitive):
 * `category`, `text`, `description`, `expectedQuantity`, `requiresNoteOnFail`
 *
 * `text` is required. Other columns are optional.
 */
object TemplateCsvImporter {

    data class Preview(
        val items: List<TemplateItemCatalogInput>,
        val totalDataRows: Int,
        val skippedBlankRows: Int,
        val categoryCount: Int,
        val quantityItemCount: Int,
        val warnings: List<String> = emptyList()
    ) {
        val summary: String
            get() = buildString {
                append("${items.size} checklist items")
                if (categoryCount > 0) append(" across $categoryCount sections")
                if (quantityItemCount > 0) append("; $quantityItemCount with expected quantity")
                if (skippedBlankRows > 0) append("; skipped $skippedBlankRows blank row(s)")
                if (warnings.isNotEmpty()) append("; ${warnings.size} warning(s)")
            }
    }

    sealed class Result {
        data class Success(val preview: Preview) : Result()
        data class Failure(val message: String) : Result()
    }

    fun parse(csvText: String): Result {
        val normalized = csvText.removePrefix("\uFEFF").trim()
        if (normalized.isEmpty()) {
            return Result.Failure("CSV is empty.")
        }

        val rows = parseCsvRows(normalized)
        if (rows.isEmpty()) {
            return Result.Failure("CSV is empty.")
        }

        val header = rows.first().map { it.trim().lowercase() }
        val textIndex = header.indexOfFirst { it == "text" || it == "item" || it == "itemtext" }
        if (textIndex < 0) {
            return Result.Failure(
                "CSV must include a text column (category,text,description,expectedQuantity,requiresNoteOnFail)."
            )
        }

        val categoryIndex = header.indexOfFirst { it == "category" }
        val descriptionIndex = header.indexOfFirst { it == "description" }
        val expectedQuantityIndex = header.indexOfFirst {
            it == "expectedquantity" || it == "expected_qty" || it == "expectedqty"
        }
        val requiresNoteIndex = header.indexOfFirst {
            it == "requiresnoteonfail" || it == "requires_note_on_fail"
        }

        val items = mutableListOf<TemplateItemCatalogInput>()
        val warnings = mutableListOf<String>()
        var skippedBlankRows = 0

        rows.drop(1).forEachIndexed { offset, cells ->
            val rowNumber = offset + 2
            if (cells.all { it.isBlank() }) {
                skippedBlankRows++
                return@forEachIndexed
            }

            val text = cellAt(cells, textIndex)?.trim().orEmpty()
            if (text.isEmpty()) {
                skippedBlankRows++
                warnings.add("Row $rowNumber: skipped (missing text).")
                return@forEachIndexed
            }

            val expectedRaw = cellAt(cells, expectedQuantityIndex)?.trim().orEmpty()
            val expectedQuantity: Int? = if (expectedRaw.isEmpty()) {
                null
            } else {
                val parsed = expectedRaw.toIntOrNull()
                when {
                    parsed == null -> {
                        warnings.add("Row $rowNumber: invalid expectedQuantity \"$expectedRaw\" (ignored).")
                        null
                    }
                    parsed < 0 -> {
                        warnings.add("Row $rowNumber: negative expectedQuantity $parsed (ignored).")
                        null
                    }
                    else -> parsed
                }
            }

            val requiresNoteRaw = cellAt(cells, requiresNoteIndex)?.trim().orEmpty()
            val requiresNoteOnFail = if (requiresNoteRaw.isEmpty()) {
                true
            } else {
                val parsed = parseBoolean(requiresNoteRaw)
                if (parsed == null) {
                    warnings.add(
                        "Row $rowNumber: invalid requiresNoteOnFail \"$requiresNoteRaw\" (defaulted to true)."
                    )
                    true
                } else {
                    parsed
                }
            }

            items += TemplateItemCatalogInput(
                text = text,
                category = cellAt(cells, categoryIndex)?.trim()?.takeIf { it.isNotEmpty() },
                description = cellAt(cells, descriptionIndex)?.trim()?.takeIf { it.isNotEmpty() },
                expectedQuantity = expectedQuantity,
                requiresNoteOnFail = requiresNoteOnFail
            )
        }

        if (items.isEmpty()) {
            return Result.Failure("No checklist items found in CSV.")
        }

        val categories = items.mapNotNull { it.category }.toSet()
        return Result.Success(
            Preview(
                items = items,
                totalDataRows = rows.size - 1,
                skippedBlankRows = skippedBlankRows,
                categoryCount = categories.size,
                quantityItemCount = items.count { it.expectedQuantity != null },
                warnings = warnings
            )
        )
    }

    private fun cellAt(cells: List<String>, index: Int): String? =
        if (index >= 0 && index < cells.size) cells[index] else null

    private fun parseBoolean(raw: String): Boolean? =
        when (raw.trim().lowercase()) {
            "true", "t", "yes", "y", "1" -> true
            "false", "f", "no", "n", "0" -> false
            else -> null
        }

    /**
     * RFC-style CSV splitter supporting quoted fields and commas inside quotes.
     */
    fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when (c) {
                    '"' -> {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    }
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    currentRow += field.toString()
                    field.clear()
                }
                c == '\n' -> {
                    currentRow += field.toString()
                    field.clear()
                    rows += currentRow.toList()
                    currentRow.clear()
                }
                c == '\r' -> Unit
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow += field.toString()
            rows += currentRow.toList()
        }
        return rows.filter { row -> row.any { cell -> cell.isNotBlank() } }
    }
}
