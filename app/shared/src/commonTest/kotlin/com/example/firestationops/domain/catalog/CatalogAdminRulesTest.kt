package com.example.firestationops.domain.catalog

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.InspectionTemplateItem
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.model.Station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CatalogAdminRulesTest {
    private val admin = Member(
        id = "admin-1",
        departmentId = "dept-1",
        email = "admin@example.com",
        firstName = "Admin",
        lastName = "User",
        roles = setOf(Role.ADMIN)
    )

    private val member = admin.copy(roles = setOf(Role.MEMBER))

    private val station = Station(
        id = "st-1",
        departmentId = "dept-1",
        name = "Station 1"
    )

    @Test
    fun requireAdmin_allowsAdministrator() {
        assertNull(CatalogAdminRules.requireAdmin(admin))
    }

    @Test
    fun requireAdmin_rejectsNonAdministrator() {
        assertNotNull(CatalogAdminRules.requireAdmin(member))
    }

    @Test
    fun validateStationInput_requiresName() {
        assertEquals(
            "Station name is required.",
            CatalogAdminRules.validateStationInput(StationCatalogInput(name = "  "))
        )
    }

    @Test
    fun validateApparatusInput_requiresValidStation() {
        assertEquals(
            "Select a valid station.",
            CatalogAdminRules.validateApparatusInput(
                ApparatusCatalogInput(
                    stationId = "missing",
                    name = "Engine 1",
                    type = "Engine",
                    radioName = "E1"
                ),
                stations = listOf(station)
            )
        )
    }

    @Test
    fun validateTemplateInput_requiresChecklistItems() {
        assertEquals(
            "Add at least one checklist item.",
            CatalogAdminRules.validateTemplateInput(
                TemplateCatalogInput(
                    name = "Daily Engine",
                    apparatusType = "Engine",
                    items = listOf(TemplateItemCatalogInput(text = "  "))
                )
            )
        )
    }

    @Test
    fun canDeactivateTemplate_requiresAnotherActiveTemplateForType() {
        val template = InspectionTemplate(
            id = "tmpl-1",
            departmentId = "dept-1",
            name = "Engine daily",
            apparatusType = "Engine",
            items = listOf(InspectionTemplateItem(id = "i-1", text = "Oil"))
        )
        val otherActive = template.copy(id = "tmpl-2", name = "Engine weekly")

        assertEquals(
            false,
            CatalogAdminRules.canDeactivateTemplate(template.copy(isActive = false), listOf(template))
        )
        assertEquals(
            true,
            CatalogAdminRules.canDeactivateTemplate(template.copy(isActive = false), listOf(template, otherActive))
        )
    }

    @Test
    fun apparatusCountForStation_countsAssignedApparatus() {
        val apparatus = listOf(
            Apparatus(
                id = "ap-1",
                departmentId = "dept-1",
                stationId = "st-1",
                name = "Engine 1",
                type = "Engine",
                radioName = "E1"
            ),
            Apparatus(
                id = "ap-2",
                departmentId = "dept-1",
                stationId = "st-1",
                name = "Rescue 1",
                type = "Rescue",
                radioName = "R1",
                status = ApparatusStatus.RESERVE
            )
        )

        assertEquals(2, CatalogAdminRules.apparatusCountForStation("st-1", apparatus))
    }
}
