package ru.peerflow.demo.ui.auth

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
import ru.peerflow.demo.testing.FakeAccountRepository
import ru.peerflow.demo.testing.MainDispatcherRule
import ru.peerflow.demo.testing.sampleUser

/**
 * Вход (`BR-JOIN-1`).
 *
 * Проверяем то, что человек увидит: тексты ошибок, блокировку кнопки на время
 * запроса и переход дальше только при успехе. Сеть и Hilt не участвуют.
 */
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAccountRepository()

    private fun viewModel() = LoginViewModel(repository)

    @Test
    fun `state starts empty and cannot submit`() {
        val vm = viewModel()

        assertEquals("", vm.state.value.login)
        assertEquals("", vm.state.value.password)
        assertFalse(vm.state.value.canSubmit)
    }

    @Test
    fun `canSubmit becomes true after both fields are filled`() {
        val vm = viewModel()

        vm.onLoginChange("anna")
        vm.onPasswordChange("password123")

        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `submit with short login shows field error and does not call server`() {
        val vm = viewModel()
        vm.onLoginChange("ab")
        vm.onPasswordChange("password123")

        vm.submit()

        assertEquals("логин короче 3 символов", vm.state.value.loginError)
        assertEquals(0, repository.loginCalls)
    }

    @Test
    fun `submit with short password shows field error and does not call server`() {
        val vm = viewModel()
        vm.onLoginChange("anna")
        vm.onPasswordChange("short")

        vm.submit()

        assertEquals("пароль короче 8 символов", vm.state.value.passwordError)
        assertEquals(0, repository.loginCalls)
    }

    @Test
    fun `editing a field clears its error`() {
        val vm = viewModel()
        vm.onLoginChange("ab")
        vm.onPasswordChange("password123")
        vm.submit()
        assertEquals("логин короче 3 символов", vm.state.value.loginError)

        vm.onLoginChange("anna")

        assertNull(vm.state.value.loginError)
    }

    @Test
    fun `successful login clears password and emits logged in event`() = runTest {
        val expected = sampleUser(login = "anna", member = true)
        repository.currentUser = expected
        val vm = viewModel()
        // Подписываемся до вызова: Channel без replay не отдаст уже отправленное.
        val event = async { vm.events.first() }

        vm.onLoginChange("anna")
        vm.onPasswordChange("password123")
        vm.submit()

        assertEquals("", vm.state.value.password)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.formError)
        assertEquals(LoginEvent.LoggedIn(expected), event.await())
    }

    @Test
    fun `second submit while request is in flight is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.loginGate = gate
        val vm = viewModel()
        vm.onLoginChange("anna")
        vm.onPasswordChange("password123")

        vm.submit()
        assertTrue(vm.state.value.loading)
        vm.submit()

        assertEquals(1, repository.loginCalls)

        gate.complete(Unit)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `server message is shown as is`() = runTest {
        repository.loginError = ApiException(401, "Unauthorized", "неверный логин или пароль")
        val vm = viewModel()
        vm.onLoginChange("anna")
        vm.onPasswordChange("wrongpassword")

        vm.submit()

        assertEquals("неверный логин или пароль", vm.state.value.formError)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `network failure tells the person to check the connection`() = runTest {
        repository.loginError = NetworkException("timeout")
        val vm = viewModel()
        vm.onLoginChange("anna")
        vm.onPasswordChange("password123")

        vm.submit()

        assertEquals("нет связи с сервером, проверьте интернет", vm.state.value.formError)
    }

    @Test
    fun `unexpected failure does not leak the exception text`() = runTest {
        repository.loginError = IllegalStateException("boom at 12:00")
        val vm = viewModel()
        vm.onLoginChange("anna")
        vm.onPasswordChange("password123")

        vm.submit()

        assertEquals("что-то пошло не так, попробуйте позже", vm.state.value.formError)
    }

    @Test
    fun `empty fields keep the person on the form`() {
        val vm = viewModel()

        vm.submit()

        assertEquals("введите логин", vm.state.value.loginError)
        assertEquals("введите пароль", vm.state.value.passwordError)
        assertEquals(0, repository.loginCalls)
    }
}
