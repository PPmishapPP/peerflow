package ru.peerflow.demo.ui.join

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.peerflow.demo.data.api.ApiException
import ru.peerflow.demo.data.api.NetworkException
import ru.peerflow.demo.testing.FakeJoinRequestRepository
import ru.peerflow.demo.testing.MainDispatcherRule
import ru.peerflow.demo.testing.PENDING
import ru.peerflow.demo.testing.sampleRequest

/**
 * Очередь заявок и голосование за приём (`BR-JOIN-5`, `BR-VOTE-8`).
 *
 * Главное, что проверяем: член сообщества действительно видит очередь и может
 * проголосовать — без этого экрана заявка висит вечно, потому что вступающий
 * видит только «заявка на рассмотрении», а кнопок «за» и «против» нет ни у
 * кого. Плюс: сбой сети не стирает уже показанную очередь, а решённая заявка
 * уходит из неё сразу, без перезагрузки.
 */
class JoinRequestsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeJoinRequestRepository()

    private fun viewModel() = JoinRequestsViewModel(repository)

    @Test
    fun `refresh shows the pending queue`() = runTest {
        val first = sampleRequest(id = "r1", applicantName = "Анна")
        val second = sampleRequest(id = "r2", applicantName = "Борис")
        repository.pendingResult = listOf(first, second)
        val vm = viewModel()

        vm.refresh()

        assertEquals(
            JoinRequestsState.Loaded(listOf(first, second)),
            vm.state.value,
        )
        assertEquals(1, repository.pendingCalls)
        assertFalse(vm.loading.value)
    }

    @Test
    fun `empty queue is a normal state, not an error`() = runTest {
        repository.pendingResult = emptyList()
        val vm = viewModel()

        vm.refresh()

        assertEquals(JoinRequestsState.Empty, vm.state.value)
        assertNull(vm.message.value)
    }

    @Test
    fun `queue load failure is shown with the server reason`() = runTest {
        repository.pendingError = ApiException(403, "Forbidden", "нужен статус члена сообщества")
        val vm = viewModel()

        vm.refresh()

        assertEquals(
            JoinRequestsState.Failure("нужен статус члена сообщества"),
            vm.state.value,
        )
    }

    @Test
    fun `network failure on queue is explained`() = runTest {
        repository.pendingError = NetworkException("timeout")
        val vm = viewModel()

        vm.refresh()

        assertEquals(
            JoinRequestsState.Failure("нет связи с сервером, проверьте интернет"),
            vm.state.value,
        )
    }

    @Test
    fun `accept sends the vote and removes the decided request`() = runTest {
        val request = sampleRequest(id = "r1")
        repository.pendingResult = listOf(request)
        val vm = viewModel()
        vm.refresh()

        vm.vote("r1", accept = true)

        assertEquals("r1", repository.lastVoteId)
        assertEquals(true, repository.lastVoteAccept)
        // Заявка принята — рассматривать больше нечего, она уходит из очереди.
        assertEquals(JoinRequestsState.Empty, vm.state.value)
        assertNull(vm.voting.value)
    }

    @Test
    fun `reject sends the vote and removes the decided request`() = runTest {
        repository.pendingResult = listOf(sampleRequest(id = "r1"))
        val vm = viewModel()
        vm.refresh()

        vm.vote("r1", accept = false)

        assertEquals(false, repository.lastVoteAccept)
        assertEquals(JoinRequestsState.Empty, vm.state.value)
    }

    @Test
    fun `vote that did not decide the request updates only its counters`() = runTest {
        val first = sampleRequest(id = "r1", accepts = 1, totalVotes = 1)
        val second = sampleRequest(id = "r2", accepts = 0, totalVotes = 0)
        repository.pendingResult = listOf(first, second)
        val vm = viewModel()
        vm.refresh()

        // Порог ещё не набран — сервер вернул ту же заявку с новым счётчиком.
        repository.voteResult = sampleRequest(
            id = "r1",
            status = PENDING,
            accepts = 2,
            totalVotes = 2,
            votedByMe = true,
        )
        vm.vote("r1", accept = true)

        val loaded = vm.state.value as JoinRequestsState.Loaded
        assertEquals(2, loaded.requests.size)
        assertEquals(2, loaded.requests.first().accepts)
        assertTrue(loaded.requests.first().votedByMe)
        // Соседняя заявка не тронута.
        assertEquals(second, loaded.requests[1])
    }

    @Test
    fun `failed vote keeps the queue and shows the reason`() = runTest {
        repository.pendingResult = listOf(sampleRequest(id = "r1"))
        val vm = viewModel()
        vm.refresh()
        val loaded = vm.state.value

        repository.voteError = ApiException(409, "Conflict", "заявка уже рассмотрена")
        vm.vote("r1", accept = true)

        assertEquals(loaded, vm.state.value)
        assertEquals("заявка уже рассмотрена", vm.message.value)
        assertNull(vm.voting.value)
    }

    @Test
    fun `repeat vote is blocked while the first one is in flight`() = runTest {
        repository.pendingResult = listOf(sampleRequest(id = "r1"))
        val gate = CompletableDeferred<Unit>()
        repository.voteGate = gate
        val vm = viewModel()
        vm.refresh()

        vm.vote("r1", accept = true)
        vm.vote("r1", accept = false)

        // Второе нажатие не должно уйти на сервер: иначе человек отправил бы
        // два противоположных голоса и получил 409 вместо результата.
        assertEquals(1, repository.voteCalls)
        assertEquals("r1", vm.voting.value)

        gate.complete(Unit)
    }

    @Test
    fun `queue load failure does not wipe an already shown queue`() = runTest {
        repository.pendingResult = listOf(sampleRequest(id = "r1"))
        val vm = viewModel()
        vm.refresh()
        val loaded = vm.state.value

        repository.pendingError = NetworkException("timeout")
        vm.refresh()

        assertEquals(loaded, vm.state.value)
        assertEquals("нет связи с сервером, проверьте интернет", vm.message.value)
        assertFalse(vm.loading.value)
    }

    @Test
    fun `dismissMessage clears the message`() = runTest {
        repository.pendingResult = listOf(sampleRequest(id = "r1"))
        val vm = viewModel()
        vm.refresh()
        repository.voteError = NetworkException("timeout")
        vm.vote("r1", accept = true)
        assertEquals("нет связи с сервером, проверьте интернет", vm.message.value)

        vm.dismissMessage()

        assertNull(vm.message.value)
    }
}

/**
 * Счётчик заявок для главного экрана (`BR-JOIN-5`).
 *
 * Цифра вспомогательная: её задача — не дать заявке остаться незамеченной.
 * Поэтому при сбое она не показывает ошибку и не мешает списку предложений.
 */
class PendingJoinRequestsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeJoinRequestRepository()

    private fun viewModel() = PendingJoinRequestsViewModel(repository)

    @Test
    fun `count reflects the queue size`() = runTest {
        repository.pendingResult = listOf(
            sampleRequest(id = "r1"),
            sampleRequest(id = "r2"),
            sampleRequest(id = "r3"),
        )
        val vm = viewModel()

        vm.refresh()

        assertEquals(3, vm.count.value)
    }

    @Test
    fun `empty queue gives zero, not an error`() = runTest {
        repository.pendingResult = emptyList()
        val vm = viewModel()

        vm.refresh()

        assertEquals(0, vm.count.value)
    }

    @Test
    fun `failure hides the badge instead of reporting an error`() = runTest {
        repository.pendingError = ApiException(403, "Forbidden", "нужен статус члена")
        val vm = viewModel()

        vm.refresh()

        assertNull(vm.count.value)
    }
}
