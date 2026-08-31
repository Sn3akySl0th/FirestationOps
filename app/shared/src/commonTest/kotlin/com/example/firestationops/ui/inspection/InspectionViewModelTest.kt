package com.example.firestationops.ui.inspection

import com.example.firestationops.domain.model.*
import com.example.firestationops.domain.repository.mock.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InspectionViewModelTest {

    private val member = Member(
        id = "member-1",
        departmentId = "mock-dept-id",
        email = "test@example.com",
        firstName = "John",
        lastName = "Doe",
        roles = setOf(Role.ADMIN)
    )

    private val inspectionRepository = MockInspectionRepository()
    private val deficiencyRepository = MockDeficiencyRepository()
    private val apparatusRepository = MockApparatusRepository()
    private val attachmentRepository = MockAttachmentRepository()

    @Test
    fun `loadData should load template for apparatus`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.first { !it.isLoading }

        val state = viewModel.uiState.value
        assertEquals("ap-1", state.apparatus?.id)
        assertEquals("Daily Engine Inspection", state.template?.name)
        assertEquals(5, state.responses.size)
        awaitViewModelWork()
        finishViewModelTest()
    }

    @Test
    fun `submit should save inspection and create deficiencies`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.first { !it.isLoading }

        viewModel.updateResponse("item-1", InspectionStatus.FAIL, note = "Oil is low")
        viewModel.submit()

        viewModel.uiState.first { it.isSuccess }

        assertTrue(viewModel.uiState.value.isSuccess)
        awaitViewModelWork()
        finishViewModelTest()
    }

    @Test
    fun `updateResponse should save draft`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.first { !it.isLoading }

        viewModel.updateResponse("item-1", InspectionStatus.FAIL, note = "Draft test")
        awaitViewModelWork()

        val draft = inspectionRepository.getLatestDraft("ap-1").getOrNull()
        assertTrue(draft != null)
        assertEquals(false, draft.isFinalized)
        assertEquals(InspectionStatus.FAIL, draft.responses.find { it.itemId == "item-1" }?.status)
        finishViewModelTest()
    }

    @Test
    fun `loadData should resume from draft`() = runTest {
        val draft = Inspection(
            id = "draft-1",
            templateId = "tmpl-engine",
            apparatusId = "ap-1",
            departmentId = "mock-dept-id",
            startedAt = 1000L,
            startedByUserId = member.id,
            responses = listOf(InspectionResponse("item-1", InspectionStatus.FAIL, "Existing draft")),
            isFinalized = false
        )
        inspectionRepository.saveInspection(draft)

        val viewModel = createViewModel()

        viewModel.uiState.first { !it.isLoading }

        val state = viewModel.uiState.value
        assertEquals("draft-1", state.inspectionId)
        assertEquals(1000L, state.startedAt)
        assertEquals(InspectionStatus.FAIL, state.responses["item-1"]?.status)
        assertEquals("Existing draft", state.responses["item-1"]?.note)
        awaitViewModelWork()
        finishViewModelTest()
    }

    @Test
    fun `submit with OOS severity should update apparatus status`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.first { !it.isLoading }

        viewModel.updateResponse("item-1", InspectionStatus.FAIL, DeficiencySeverity.OUT_OF_SERVICE, "Critical failure")
        viewModel.submit()

        viewModel.uiState.first { it.isSuccess }

        val apparatus = apparatusRepository.getApparatus("ap-1").getOrNull()
        assertEquals(ApparatusStatus.OUT_OF_SERVICE, apparatus?.status)
        finishViewModelTest()
    }

    @Test
    fun `submit should fail if note is missing for OOS`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.first { !it.isLoading }

        viewModel.updateResponse("item-1", InspectionStatus.FAIL, DeficiencySeverity.OUT_OF_SERVICE, "")
        viewModel.submit()
        awaitViewModelWork()

        val state = viewModel.uiState.value
        assertEquals("Notes are required for failed items.", state.error)
        assertFalse(state.isSuccess)
        finishViewModelTest()
    }

    @Test
    fun `isValid should reflect note requirement`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.first { !it.isLoading }
        assertTrue(viewModel.uiState.value.isValid)

        viewModel.updateResponse("item-1", InspectionStatus.FAIL, DeficiencySeverity.OUT_OF_SERVICE, "")
        assertFalse(viewModel.uiState.value.isValid)

        viewModel.updateResponse("item-1", InspectionStatus.FAIL, DeficiencySeverity.OUT_OF_SERVICE, "Gasket blown")
        assertTrue(viewModel.uiState.value.isValid)
        finishViewModelTest()
    }

    @Test
    fun `addAttachment should save attachment and update response`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.first { !it.isLoading }

        viewModel.addAttachment("item-1", "/path/to/photo.jpg")
        awaitViewModelWork()

        val response = checkNotNull(viewModel.uiState.value.responses["item-1"])
        assertEquals(1, response.attachmentIds.size)
        assertTrue(response.attachmentIds.first().startsWith("att-"))

        val attachment = attachmentRepository.getAttachment(response.attachmentIds.first()).getOrNull()
        assertEquals("/path/to/photo.jpg", attachment?.localUri)
        finishViewModelTest()
    }

    private fun TestScope.createViewModel(): InspectionViewModel =
        InspectionViewModel(
            apparatusId = "ap-1",
            member = member,
            inspectionRepository = inspectionRepository,
            deficiencyRepository = deficiencyRepository,
            apparatusRepository = apparatusRepository,
            attachmentRepository = attachmentRepository,
            scope = this,
        )

    private suspend fun TestScope.awaitViewModelWork() {
        advanceUntilIdle()
    }

    private fun TestScope.finishViewModelTest() {
        coroutineContext[Job]?.cancelChildren()
    }
}
