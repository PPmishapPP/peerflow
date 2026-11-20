package ru.peerflow.demo.feature.proposal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.peerflow.demo.data.api.ApiException
import ru.peerflow.demo.data.api.NetworkException
import ru.peerflow.demo.testing.FakeProposalRepository
import ru.peerflow.demo.testing.MainDispatcherRule

/**
 * Внесение предложения (`BR-OVER-2`, `BR-LIFE-1`, `BR-BUDGET-2`).
 *
 * Ключевое правило: без потолка бюджета предложения нет. Проверяем, что клиент не
 * отправляет такой запрос вообще, а сервер остаётся последней линией защиты.
 */
class NewProposalViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeProposalRepository()

    private fun viewModel() = NewProposalViewModel(repository)

    @Test
    fun `empty form cannot be submitted`() = runTest {
        val vm = viewModel()

        assertFalse(vm.state.value.canSubmit)
    }

    @Test
    fun `form without budget cannot be submitted`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("Аренда зала")
        vm.onDescriptionChange("Нужен зал на субботу")

        assertFalse(vm.state.value.canSubmit)
    }

    @Test
    fun `submit without budget is rejected before the network`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("Аренда зала")
        vm.onDescriptionChange("Нужен зал на субботу")

        vm.submit()

        assertEquals("потолок бюджета обязателен", vm.state.value.budgetError)
        assertEquals(0, repository.createCalls)
    }

    @Test
    fun `zero budget is rejected before the network`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("Аренда зала")
        vm.onDescriptionChange("Нужен зал на субботу")
        vm.onBudgetChange("0")

        vm.submit()

        assertEquals("потолок должен быть больше нуля", vm.state.value.budgetError)
        assertEquals(0, repository.createCalls)
    }

    @Test
    fun `blank title and description are reported on their fields`() = runTest {
        val vm = viewModel()
        vm.onBudgetChange("1000")

        vm.submit()

        assertEquals("тема обязательна", vm.state.value.titleError)
        assertEquals("описание обязательно", vm.state.value.descriptionError)
        assertEquals(0, repository.createCalls)
    }

    @Test
    fun `budget accepts digits only`() = runTest {
        val vm = viewModel()

        vm.onBudgetChange("12 000 ₽")

        assertEquals("12000", vm.state.value.budget)
        assertEquals(12_000L, vm.state.value.budgetValue)
    }

    @Test
    fun `successful submit trims fields and reports creation`() = runTest {
        val vm = viewModel()
        val event = async { vm.events.first() }
        vm.onTitleChange("  Аренда зала  ")
        vm.onDescriptionChange("  Нужен зал на субботу  ")
        vm.onBudgetChange("12000")

        vm.submit()

        assertEquals("Аренда зала", repository.lastCreateTitle)
        assertEquals("Нужен зал на субботу", repository.lastCreateDescription)
        assertEquals(12_000L, repository.lastCreateBudget)
        assertEquals(NewProposalEvent.Created, event.await())
        assertFalse(vm.state.value.submitting)
        assertNull(vm.state.value.formError)
    }

    @Test
    fun `server error is shown above the button`() = runTest {
        repository.createError = ApiException(400, "Bad Request", "предложение без потолка бюджета внести нельзя")
        val vm = viewModel()
        vm.onTitleChange("Аренда зала")
        vm.onDescriptionChange("Нужен зал на субботу")
        vm.onBudgetChange("12000")

        vm.submit()

        assertEquals("предложение без потолка бюджета внести нельзя", vm.state.value.formError)
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun `network failure is explained without server text`() = runTest {
        repository.createError = NetworkException("timeout")
        val vm = viewModel()
        vm.onTitleChange("Аренда зала")
        vm.onDescriptionChange("Нужен зал на субботу")
        vm.onBudgetChange("12000")

        vm.submit()

        assertEquals(
            "нет связи с сервером, проверьте интернет",
            vm.state.value.formError,
        )
    }

    @Test
    fun `editing a field clears its error`() = runTest {
        val vm = viewModel()
        vm.submit()
        assertEquals("тема обязательна", vm.state.value.titleError)

        vm.onTitleChange("Аренда зала")

        assertNull(vm.state.value.titleError)
    }

    @Test
    fun `editing a field clears the form error`() = runTest {
        repository.createError = ApiException(400, "Bad Request", "ошибка сервера")
        val vm = viewModel()
        vm.onTitleChange("Аренда зала")
        vm.onDescriptionChange("Нужен зал на субботу")
        vm.onBudgetChange("12000")
        vm.submit()
        assertEquals("ошибка сервера", vm.state.value.formError)

        vm.onBudgetChange("13000")

        assertNull(vm.state.value.formError)
    }

    @Test
    fun `submitting twice while the request is in flight sends one proposal`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.createGate = gate
        val vm = viewModel()
        vm.onTitleChange("Аренда зала")
        vm.onDescriptionChange("Нужен зал на субботу")
        vm.onBudgetChange("12000")

        vm.submit()
        vm.submit()

        assertEquals(1, repository.createCalls)
        assertTrue(vm.state.value.submitting)

        gate.complete(Unit)
    }
}
