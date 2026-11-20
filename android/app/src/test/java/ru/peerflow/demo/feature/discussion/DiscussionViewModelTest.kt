package ru.peerflow.demo.feature.discussion

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.peerflow.demo.data.api.ApiException
import ru.peerflow.demo.data.api.NetworkException
import ru.peerflow.demo.data.model.DiscussionThread
import ru.peerflow.demo.data.model.MessageSort
import ru.peerflow.demo.data.model.ReactionKind
import ru.peerflow.demo.testing.FakeDiscussionRepository
import ru.peerflow.demo.testing.MainDispatcherRule
import ru.peerflow.demo.testing.sampleMessage

/**
 * Дискуссия под предложением (`BR-DISC-1`–`BR-DISC-6`, `BR-SPAM-1`).
 *
 * Порядок и вложенность проверяются на сервере: здесь важно, что ViewModel не
 * пересортировывает пришедшее, отправляет действия человека и не теряет уже
 * показанную ветку из-за сетевого сбоя.
 */
class DiscussionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeDiscussionRepository()

    private fun viewModel() = DiscussionViewModel(repository)

    @Test
    fun `load fetches the thread in popular order by default`() = runTest {
        val thread = DiscussionThread(messages = listOf(sampleMessage()))
        repository.threadResult = thread
        val vm = viewModel()

        vm.load("p1")

        assertEquals(DiscussionState.Loaded(thread), vm.state.value)
        assertEquals("p1", repository.lastThreadProposalId)
        assertEquals(MessageSort.POPULAR, repository.lastSort)
    }

    @Test
    fun `load failure is shown with the server reason`() = runTest {
        repository.threadError = ApiException(404, "Not Found", "предложение не найдено")
        val vm = viewModel()

        vm.load("missing")

        assertEquals(
            DiscussionState.Failure("предложение не найдено"),
            vm.state.value,
        )
    }

    @Test
    fun `network failure on load is explained`() = runTest {
        repository.threadError = NetworkException("timeout")
        val vm = viewModel()

        vm.load("p1")

        assertEquals(
            DiscussionState.Failure("нет связи с сервером, проверьте интернет"),
            vm.state.value,
        )
    }

    @Test
    fun `selecting a sort refetches from the server`() = runTest {
        val vm = viewModel()
        vm.load("p1")

        vm.selectSort(MessageSort.NEW)

        assertEquals(MessageSort.NEW, vm.sort.value)
        assertEquals(MessageSort.NEW, repository.lastSort)
        assertEquals(2, repository.threadCalls)
    }

    @Test
    fun `selecting the same sort does not refetch`() = runTest {
        val vm = viewModel()
        vm.load("p1")

        vm.selectSort(MessageSort.POPULAR)

        assertEquals(1, repository.threadCalls)
    }

    @Test
    fun `post sends the message text`() = runTest {
        val vm = viewModel()
        vm.load("p1")

        vm.post("  Предлагаю аренду  ")

        assertEquals(1, repository.postCalls)
        assertEquals("p1", repository.lastPostProposalId)
        assertEquals("Предлагаю аренду", repository.lastPostBody)
        assertNull(repository.lastPostParentId)
        assertTrue(vm.state.value is DiscussionState.Loaded)
    }

    @Test
    fun `post with parent id becomes a reply`() = runTest {
        val vm = viewModel()
        vm.load("p1")

        vm.post("Согласен", parentId = "m1")

        assertEquals("m1", repository.lastPostParentId)
    }

    @Test
    fun `blank message is not sent`() = runTest {
        val vm = viewModel()
        vm.load("p1")

        vm.post("   ")

        assertEquals(0, repository.postCalls)
        assertFalse(vm.sending.value)
    }

    @Test
    fun `sending blocks repeated taps`() = runTest {
        repository.postGate = kotlinx.coroutines.CompletableDeferred()
        val vm = viewModel()
        vm.load("p1")

        vm.post("Первое")
        vm.post("Второе")

        assertEquals("второй запрос не должен уйти, пока идёт первый", 1, repository.postCalls)
        assertTrue(vm.sending.value)

        repository.postGate?.complete(Unit)
    }

    @Test
    fun `post failure is shown without losing the thread`() = runTest {
        val thread = DiscussionThread(messages = listOf(sampleMessage(body = "Старое")))
        repository.threadResult = thread
        val vm = viewModel()
        vm.load("p1")
        repository.postError = ApiException(400, "Bad Request", "сообщение не длиннее 5000 символов")

        vm.post("Слишком длинное")

        assertEquals("сообщение не длиннее 5000 символов", vm.message.value)
        assertEquals(
            "показанная ветка не должна исчезнуть из-за ошибки отправки",
            DiscussionState.Loaded(thread),
            vm.state.value,
        )
        assertFalse(vm.sending.value)
    }

    @Test
    fun `react sends the chosen reaction`() = runTest {
        repository.threadResult = DiscussionThread(messages = listOf(sampleMessage(id = "m1")))
        val vm = viewModel()
        vm.load("p1")

        vm.react("m1", ReactionKind.PLUS)

        assertEquals(1, repository.reactCalls)
        assertEquals("m1", repository.lastReactId)
        assertEquals(ReactionKind.PLUS, repository.lastReactKind)
    }

    @Test
    fun `reaction failure is shown to the person`() = runTest {
        repository.threadResult = DiscussionThread(messages = listOf(sampleMessage()))
        val vm = viewModel()
        vm.load("p1")
        repository.reactError = NetworkException("offline")

        vm.react("m1", ReactionKind.SPAM)

        assertEquals("нет связи с сервером, проверьте интернет", vm.message.value)
        assertFalse(vm.sending.value)
    }

    @Test
    fun `hidden messages are kept as a number, not as silence`() = runTest {
        repository.threadResult = DiscussionThread(messages = emptyList(), hidden = 3)
        val vm = viewModel()

        vm.load("p1")

        val loaded = vm.state.value as DiscussionState.Loaded
        assertEquals(3, loaded.thread.hidden)
        assertTrue("скрытое — повод показать пояснение", loaded.thread.hasContent)
    }

    @Test
    fun `dismiss clears the error`() = runTest {
        repository.threadError = NetworkException("offline")
        val vm = viewModel()
        vm.load("p1")

        vm.dismissMessage()

        assertNull(vm.message.value)
    }
}
