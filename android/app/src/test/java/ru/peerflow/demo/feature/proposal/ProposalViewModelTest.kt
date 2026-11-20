package ru.peerflow.demo.feature.proposal

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import ru.peerflow.demo.data.api.ApiException
import ru.peerflow.demo.data.api.NetworkException
import ru.peerflow.demo.data.model.ProposalSections
import ru.peerflow.demo.data.model.ProposalSort
import ru.peerflow.demo.data.model.ProposalStage
import ru.peerflow.demo.testing.FakeProposalRepository
import ru.peerflow.demo.testing.MainDispatcherRule
import ru.peerflow.demo.testing.sampleProposal

/**
 * Список предложений (`BR-RANK-1`–`BR-RANK-5`).
 *
 * Главное, что проверяем: порядок и секции приходят с сервера и клиент их не
 * пересчитывает; сбой сети не стирает уже показанные данные; переключение
 * сортировки действительно перезапрашивает список, а не сортирует на клиенте.
 *
 * Голосование живёт в карточке предложения — см. [ProposalDetailViewModelTest].
 */
class ProposalViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeProposalRepository()

    private fun viewModel() = ProposalViewModel(repository)

    @Test
    fun `refresh loads sections from the server`() = runTest {
        val inWork = sampleProposal(id = "p1", stage = ProposalStage.IN_WORK)
        val discussing = sampleProposal(id = "p2")
        repository.sections = ProposalSections(inWork = listOf(inWork), discussing = listOf(discussing))
        val vm = viewModel()

        vm.refresh()

        assertEquals(
            ProposalListState.Loaded(ProposalSections(listOf(inWork), listOf(discussing))),
            vm.state.value,
        )
        assertEquals(ProposalSort.POPULAR, repository.lastSort)
        assertFalse(vm.loading.value)
    }

    @Test
    fun `first load failure is shown as a failure state`() = runTest {
        repository.listError = NetworkException("timeout")
        val vm = viewModel()

        vm.refresh()

        assertEquals(
            ProposalListState.Failure("нет связи с сервером, проверьте интернет"),
            vm.state.value,
        )
    }

    @Test
    fun `server error message is taken from the server`() = runTest {
        repository.listError = ApiException(403, "Forbidden", "нужно быть членом сообщества")
        val vm = viewModel()

        vm.refresh()

        assertEquals(
            ProposalListState.Failure("нужно быть членом сообщества"),
            vm.state.value,
        )
    }

    @Test
    fun `failure after success keeps the list and shows a message`() = runTest {
        repository.sections = ProposalSections(discussing = listOf(sampleProposal()))
        val vm = viewModel()
        vm.refresh()
        val loaded = vm.state.value

        repository.listError = NetworkException("timeout")
        vm.refresh()

        // Список не пропадает: человек должен видеть то же, что и раньше,
        // плюс понятную причину сбоя.
        assertEquals(loaded, vm.state.value)
        assertEquals("нет связи с сервером, проверьте интернет", vm.message.value)
    }

    @Test
    fun `switching sort asks the server for the new order`() = runTest {
        val vm = viewModel()
        vm.refresh()
        val callsBefore = repository.listCalls

        vm.selectSort(ProposalSort.BUDGET)

        assertEquals(ProposalSort.BUDGET, vm.sort.value)
        assertEquals(ProposalSort.BUDGET, repository.lastSort)
        assertEquals(callsBefore + 1, repository.listCalls)
    }

    @Test
    fun `selecting the same sort does not reload`() = runTest {
        val vm = viewModel()
        vm.refresh()
        val callsBefore = repository.listCalls

        vm.selectSort(ProposalSort.POPULAR)

        assertEquals(callsBefore, repository.listCalls)
    }

    @Test
    fun `dismissMessage clears the message`() = runTest {
        repository.sections = ProposalSections(discussing = listOf(sampleProposal()))
        val vm = viewModel()
        vm.refresh()
        repository.listError = NetworkException("timeout")
        vm.refresh()
        assertEquals("нет связи с сервером, проверьте интернет", vm.message.value)

        vm.dismissMessage()

        assertNull(vm.message.value)
    }
}
