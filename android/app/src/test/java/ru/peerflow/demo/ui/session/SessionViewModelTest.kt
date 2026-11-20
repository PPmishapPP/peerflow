package ru.peerflow.demo.ui.session

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.peerflow.demo.data.api.ApiException
import ru.peerflow.demo.data.api.NetworkException
import ru.peerflow.demo.testing.FakeAccountRepository
import ru.peerflow.demo.testing.MainDispatcherRule
import ru.peerflow.demo.testing.sampleUser

/**
 * Сессия приложения (`BR-JOIN-4`, `BR-PRIV-1`).
 *
 * Это единственное место, где решается «пускать внутрь или на вход». Если оно
 * ошибается, человек либо видит контент без членства, либо выкидывается с
 * действующим токеном.
 */
class SessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAccountRepository()

    private fun viewModel() = SessionViewModel(repository)

    @Test
    fun `no token means signed out without a request`() {
        repository.hasTokenValue = false

        val vm = viewModel()

        assertEquals(SessionState.SignedOut, vm.state.value)
        assertEquals(0, repository.meCalls)
    }

    @Test
    fun `valid token loads the profile`() {
        repository.hasTokenValue = true
        val user = sampleUser(member = true)
        repository.currentUser = user

        val vm = viewModel()

        assertEquals(SessionState.SignedIn(user), vm.state.value)
    }

    @Test
    fun `expired token is dropped and the person goes to sign in`() {
        repository.hasTokenValue = true
        repository.meError = ApiException(401, "Unauthorized", "токен истёк")

        val vm = viewModel()

        assertEquals(SessionState.SignedOut, vm.state.value)
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun `server error while checking the session is shown as a failure`() {
        repository.hasTokenValue = true
        repository.meError = ApiException(500, "Server Error", "сервер отдыхает")

        val vm = viewModel()

        assertEquals(SessionState.Failure("сервер отдыхает"), vm.state.value)
        assertEquals(0, repository.logoutCalls)
    }

    @Test
    fun `network error keeps the session for a retry`() {
        repository.hasTokenValue = true
        repository.meError = NetworkException("timeout")

        val vm = viewModel()

        assertEquals(
            SessionState.Failure("нет связи с сервером, проверьте интернет"),
            vm.state.value,
        )
        assertEquals(0, repository.logoutCalls)
    }

    @Test
    fun `retry after a failure signs the person in`() {
        repository.hasTokenValue = true
        repository.meError = NetworkException("timeout")
        val vm = viewModel()
        assertTrue(vm.state.value is SessionState.Failure)

        repository.meError = null
        vm.restore()

        assertTrue(vm.state.value is SessionState.SignedIn)
    }

    @Test
    fun `successful login signs the person in without a request`() {
        val vm = viewModel()
        val user = sampleUser()

        vm.onAuthenticated(user)

        assertEquals(SessionState.SignedIn(user), vm.state.value)
        assertEquals(0, repository.meCalls)
    }

    @Test
    fun `refreshing the profile picks up the new membership`() {
        repository.hasTokenValue = true
        repository.currentUser = sampleUser(member = false)
        val vm = viewModel()

        repository.currentUser = sampleUser(member = true)
        vm.refreshProfile()

        val state = vm.state.value
        assertTrue(state is SessionState.SignedIn)
        assertTrue((state as SessionState.SignedIn).user.member)
    }

    @Test
    fun `refreshing the profile does nothing while signed out`() {
        repository.hasTokenValue = false
        val vm = viewModel()

        vm.refreshProfile()

        assertEquals(SessionState.SignedOut, vm.state.value)
        assertEquals(0, repository.meCalls)
    }

    @Test
    fun `profile refresh with an expired token signs the person out`() {
        repository.hasTokenValue = true
        val vm = viewModel()

        repository.meError = ApiException(401, "Unauthorized", "токен истёк")
        vm.refreshProfile()

        assertEquals(SessionState.SignedOut, vm.state.value)
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun `profile refresh keeps the old state when the server is unavailable`() {
        repository.hasTokenValue = true
        val user = sampleUser()
        repository.currentUser = user
        val vm = viewModel()

        repository.meError = NetworkException("timeout")
        vm.refreshProfile()

        assertEquals(SessionState.SignedIn(user), vm.state.value)
    }

    @Test
    fun `logout clears the token and returns to sign in`() {
        repository.hasTokenValue = true
        val vm = viewModel()

        vm.logout()

        assertEquals(SessionState.SignedOut, vm.state.value)
        assertEquals(1, repository.logoutCalls)
    }
}
