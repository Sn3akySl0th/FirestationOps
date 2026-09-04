package com.example.firestationops.db

import app.cash.sqldelight.db.SqlDriver

internal object SchemaMigration {
    fun ensureSyncConflictTables(driver: SqlDriver) {
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE IF NOT EXISTS SyncBaselineEntity (
                    recordType TEXT NOT NULL,
                    recordId TEXT NOT NULL,
                    snapshotJson TEXT NOT NULL,
                    PRIMARY KEY(recordType, recordId)
                );
            """.trimIndent(),
            parameters = 0
        )
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE IF NOT EXISTS SyncConflictEntity (
                    id TEXT NOT NULL PRIMARY KEY,
                    departmentId TEXT NOT NULL,
                    recordType TEXT NOT NULL,
                    recordId TEXT NOT NULL,
                    localSnapshotJson TEXT NOT NULL,
                    remoteSnapshotJson TEXT NOT NULL,
                    detectedAt INTEGER NOT NULL
                );
            """.trimIndent(),
            parameters = 0
        )
        ensureInspectionVoidColumns(driver)
        ensureInspectionStockingColumns(driver)
        ensureAttachmentFailureColumns(driver)
        ensureApparatusIdentityColumns(driver)
        ensureApparatusAssignedTemplatesColumn(driver)
    }

    private fun ensureApparatusAssignedTemplatesColumn(driver: SqlDriver) {
        runCatching {
            driver.execute(
                identifier = null,
                sql = "ALTER TABLE ApparatusEntity ADD COLUMN assignedTemplateIdsJson TEXT NOT NULL DEFAULT '[]';",
                parameters = 0
            )
        }
        runCatching {
            driver.execute(
                identifier = null,
                sql = "ALTER TABLE ApparatusEntity ADD COLUMN assignedTemplateIdsJson TEXT;",
                parameters = 0
            )
        }
    }

    private fun ensureInspectionStockingColumns(driver: SqlDriver) {
        listOf(
            "entrySource TEXT",
            "odometerMiles INTEGER",
            "fluidOil TEXT",
            "fluidTransmission TEXT",
            "fluidFuel TEXT",
            "fluidAntifreeze TEXT",
            "fluidPowerSteering TEXT",
            "importedAt INTEGER",
            "importedByUserId TEXT"
        ).forEach { column ->
            runCatching {
                driver.execute(
                    identifier = null,
                    sql = "ALTER TABLE InspectionEntity ADD COLUMN $column;",
                    parameters = 0
                )
            }
        }
    }

    private fun ensureApparatusIdentityColumns(driver: SqlDriver) {
        listOf("vin", "licensePlate", "barcode").forEach { column ->
            runCatching {
                driver.execute(
                    identifier = null,
                    sql = "ALTER TABLE ApparatusEntity ADD COLUMN $column TEXT;",
                    parameters = 0
                )
            }
        }
    }

    private fun ensureAttachmentFailureColumns(driver: SqlDriver) {
        runCatching {
            driver.execute(
                identifier = null,
                sql = "ALTER TABLE AttachmentEntity ADD COLUMN lastError TEXT;",
                parameters = 0
            )
        }
        runCatching {
            driver.execute(
                identifier = null,
                sql = "ALTER TABLE AttachmentEntity ADD COLUMN failedAt INTEGER;",
                parameters = 0
            )
        }
    }

    private fun ensureInspectionVoidColumns(driver: SqlDriver) {
        runCatching {
            driver.execute(
                identifier = null,
                sql = "ALTER TABLE InspectionEntity ADD COLUMN voidedAt INTEGER;",
                parameters = 0
            )
        }
        runCatching {
            driver.execute(
                identifier = null,
                sql = "ALTER TABLE InspectionEntity ADD COLUMN voidedReason TEXT;",
                parameters = 0
            )
        }
    }
}
