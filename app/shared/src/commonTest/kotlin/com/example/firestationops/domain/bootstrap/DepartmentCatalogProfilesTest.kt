package com.example.firestationops.domain.bootstrap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DepartmentCatalogProfilesTest {
    @Test
    fun calhounProfile_usesDepartmentNameAndEngine5Apparatus() {
        val profile = DepartmentCatalogProfiles.profileFor(DepartmentCatalogProfiles.CALHOUN_DEPARTMENT_ID)!!

        assertEquals("Calhoun Fire Department - Department 5", profile.departmentName)
        assertTrue(profile.apparatus.any { it.name == "Engine 5" && it.radioName == "E5" })
        assertTrue(profile.apparatus.any { it.name == "Brush 5" && it.radioName == "B5" })
    }
}
