package ru.peerflow.demo.ui.join

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
import ru.peerflow.demo.data.model.AvatarUpload
import ru.peerflow.demo.testing.ACCEPTED
import ru.peerflow.demo.testing.FakeJoinRequestRepository
import ru.peerflow.demo.testing.MainDispatcherRule
import ru.peerflow.demo.testing.PENDING
import ru.peerflow.demo.testing.REJECTED
import ru.peerflow.demo.testing.sampleRequest
import ru.peerflow.demo.ui.auth.CredentialRules

/**
 * Заявка на вступление (`BR-JOIN-2`, `BR-JOIN-3`).
 *
 * Главное, что проверяем: экран честно отражает решение сообщества — пока
 * голосуют, `Pending`; приняли — `Accepted` и событие перехода; отклонили —
 * `Rejected`, и человек не остаётся в неведении.
 */
class JoinRequestViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeJoinRequestRepository()

    private fun viewModel() = JoinRequestViewModel(repository)

    @Test
    fun `refresh without a request shows the form`() = runTest {
        repository.mineResult = null
        val vm = viewModel()

        vm.refresh()

        assertEquals(JoinUiState.Form, vm.state.value)
        assertEquals(1, repository.mineCalls)
    }

    @Test
    fun `pending request shows voting progress`() = runTest {
        val request = sampleRequest(status = PENDING, accepts = 2, totalVotes = 3, required = 5)
        repository.mineResult = request
        val vm = viewModel()

        vm.refresh()

        assertEquals(JoinUiState.Pending(request), vm.state.value)
    }

    @Test
    fun `accepted request switches state and tells the screen to move on`() = runTest {
        val request = sampleRequest(status = ACCEPTED)
        repository.mineResult = request
        val vm = viewModel()
        // Подписываемся до вызова: Channel без replay не отдаст уже отправленное.
        val event = async { vm.events.first() }

        vm.refresh()

        assertEquals(JoinUiState.Accepted(request), vm.state.value)
        assertEquals(JoinEvent.BecameMember, event.await())
    }

    @Test
    fun `rejected request is shown as rejected`() = runTest {
        val request = sampleRequest(status = REJECTED)
        repository.mineResult = request
        val vm = viewModel()

        vm.refresh()

        assertEquals(JoinUiState.Rejected(request), vm.state.value)
    }

    @Test
    fun `failing to read the status is explained`() = runTest {
        repository.mineError = NetworkException("timeout")
        val vm = viewModel()

        vm.refresh()

        assertEquals(
            JoinUiState.Failure("нет связи с сервером, проверьте интернет"),
            vm.state.value,
        )
    }

    @Test
    fun `server error on refresh keeps the message from the server`() = runTest {
        repository.mineError = ApiException(500, "Server Error", "сервер отдыхает")
        val vm = viewModel()

        vm.refresh()

        assertEquals(JoinUiState.Failure("сервер отдыхает"), vm.state.value)
    }

    @Test
    fun `too long about is rejected before the request`() = runTest {
        val vm = viewModel()
        vm.onAboutChange("я".repeat(CredentialRules.ABOUT_MAX + 1))

        vm.submit()

        assertEquals(
            "текст длиннее ${CredentialRules.ABOUT_MAX} символов",
            vm.aboutError.value,
        )
        assertEquals(0, repository.submitCalls)
    }

    @Test
    fun `spaces around the text do not count towards the length limit`() = runTest {
        // Пробелы по краям человек ставит случайно — проверяем то, что уйдёт
        // на сервер, а не «сырое» значение из поля ввода.
        val vm = viewModel()
        vm.onAboutChange("   " + "я".repeat(CredentialRules.ABOUT_MAX) + "   ")

        vm.submit()

        assertNull(vm.aboutError.value)
        assertEquals(1, repository.submitCalls)
        assertEquals("я".repeat(CredentialRules.ABOUT_MAX), repository.lastSubmitAbout)
    }

    @Test
    fun `successful submit trims the text and shows pending`() = runTest {
        val avatar = AvatarUpload(byteArrayOf(1), "a.png", "image/png")
        val pending = sampleRequest(status = PENDING)
        repository.submitResult = pending
        val vm = viewModel()
        vm.onAboutChange("  хочу к вам  ")

        vm.submit(avatar)

        assertEquals("хочу к вам", repository.lastSubmitAbout)
        assertEquals(avatar, repository.lastSubmitAvatar)
        assertEquals(JoinUiState.Pending(pending), vm.state.value)
        assertFalse(vm.submitting.value)
        assertNull(vm.formError.value)
    }

    @Test
    fun `empty about is allowed - it is not a required field on the server`() = runTest {
        val vm = viewModel()
        vm.onAboutChange("")

        vm.submit()

        assertEquals(1, repository.submitCalls)
        assertEquals("", repository.lastSubmitAbout)
    }

    @Test
    fun `failed submit keeps the form and shows the reason`() = runTest {
        repository.submitError = ApiException(409, "Conflict", "заявка уже подана")
        val vm = viewModel()

        vm.submit()

        assertEquals("заявка уже подана", vm.formError.value)
        assertFalse(vm.submitting.value)
    }

    @Test
    fun `editing the text clears the previous error`() = runTest {
        repository.submitError = ApiException(409, "Conflict", "заявка уже подана")
        val vm = viewModel()
        vm.submit()
        assertEquals("заявка уже подана", vm.formError.value)

        vm.onAboutChange("новый текст")

        assertNull(vm.formError.value)
    }

    @Test
    fun `submit that gets accepted immediately emits the event`() = runTest {
        repository.submitResult = sampleRequest(status = ACCEPTED)
        val vm = viewModel()
        // Подписываемся до вызова: Channel без replay не отдаст уже отправленное.
        val event = async { vm.events.first() }

        vm.submit()

        assertTrue(vm.state.value is JoinUiState.Accepted)
        assertEquals(JoinEvent.BecameMember, event.await())
    }
}
