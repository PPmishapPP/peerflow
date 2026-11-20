package ru.peerflow.demo.ui.auth

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
import ru.peerflow.demo.testing.FakeAccountRepository
import ru.peerflow.demo.testing.MainDispatcherRule
import ru.peerflow.demo.testing.sampleUser

/**
 * Регистрация (`BR-JOIN-1`, `BR-JOIN-4`).
 *
 * Отдельно проверяем, что «о себе» и фото уходят на сервер уже при создании
 * аккаунта: иначе человек заполнял бы их дважды — здесь и в заявке.
 */
class RegisterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAccountRepository()

    private fun viewModel() = RegisterViewModel(repository)

    private fun RegisterViewModel.fillValid() {
        onLoginChange("anna")
        onPasswordChange("password123")
        onDisplayNameChange("Анна")
    }

    @Test
    fun `state starts empty and cannot submit`() {
        val vm = viewModel()

        assertFalse(vm.state.value.canSubmit)
        assertNull(vm.state.value.formError)
    }

    @Test
    fun `about is optional and does not block submit`() {
        val vm = viewModel()
        vm.fillValid()

        assertTrue(vm.state.value.canSubmit)
        assertNull(CredentialRules.checkAbout(vm.state.value.about))
    }

    @Test
    fun `submit with blank display name shows field error`() {
        val vm = viewModel()
        vm.onLoginChange("anna")
        vm.onPasswordChange("password123")

        vm.submit()

        assertEquals("введите имя", vm.state.value.displayNameError)
        assertEquals(0, repository.registerCalls)
    }

    @Test
    fun `submit reports every invalid field at once`() {
        val vm = viewModel()
        vm.onLoginChange("a!")
        vm.onPasswordChange("123")
        vm.onDisplayNameChange(" ")

        vm.submit()

        assertEquals("логин короче 3 символов", vm.state.value.loginError)
        assertEquals("пароль короче 8 символов", vm.state.value.passwordError)
        assertEquals("введите имя", vm.state.value.displayNameError)
        assertEquals(0, repository.registerCalls)
    }

    @Test
    fun `too long about is rejected before the request`() {
        val vm = viewModel()
        vm.fillValid()
        vm.onAboutChange("я".repeat(CredentialRules.ABOUT_MAX + 1))

        vm.submit()

        assertEquals(
            "текст длиннее ${CredentialRules.ABOUT_MAX} символов",
            vm.state.value.aboutError,
        )
        assertEquals(0, repository.registerCalls)
    }

    @Test
    fun `successful registration trims values and passes avatar`() = runTest {
        val avatar = AvatarUpload(bytes = byteArrayOf(1, 2, 3), fileName = "a.png", mimeType = "image/png")
        val expected = sampleUser(login = "anna")
        repository.currentUser = expected
        val vm = viewModel()
        // Подписываемся до вызова: Channel без replay не отдаст уже отправленное.
        val event = async { vm.events.first() }

        vm.onLoginChange("  anna  ")
        vm.onPasswordChange("password123")
        vm.onDisplayNameChange("  Анна  ")
        vm.onAboutChange("  живу рядом  ")
        vm.onAvatarChange(avatar)
        vm.submit()

        assertEquals("anna", repository.lastLogin)
        assertEquals("Анна", repository.lastDisplayName)
        assertEquals("живу рядом", repository.lastAbout)
        assertEquals(avatar, repository.lastAvatar)
        assertEquals("", vm.state.value.password)
        assertEquals(RegisterEvent.Registered(expected), event.await())
    }

    @Test
    fun `avatar can be cleared before submit`() = runTest {
        val vm = viewModel()
        vm.onAvatarChange(AvatarUpload(byteArrayOf(9), "b.jpg", "image/jpeg"))
        vm.onAvatarChange(null)
        vm.fillValid()

        vm.submit()

        assertNull(repository.lastAvatar)
    }

    @Test
    fun `taken login shows server message`() = runTest {
        repository.registerError = ApiException(409, "Conflict", "логин уже занят")
        val vm = viewModel()
        vm.fillValid()

        vm.submit()

        assertEquals("логин уже занят", vm.state.value.formError)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `network failure is explained in plain words`() = runTest {
        repository.registerError = NetworkException("timeout")
        val vm = viewModel()
        vm.fillValid()

        vm.submit()

        assertEquals("нет связи с сервером, проверьте интернет", vm.state.value.formError)
    }

    @Test
    fun `editing a field clears the previous server error`() = runTest {
        repository.registerError = ApiException(409, "Conflict", "логин уже занят")
        val vm = viewModel()
        vm.fillValid()
        vm.submit()
        assertEquals("логин уже занят", vm.state.value.formError)

        vm.onLoginChange("anna2")

        assertNull(vm.state.value.formError)
    }

    @Test
    fun `credentials boundaries match the server rules`() {
        assertNull(CredentialRules.checkLogin("abc"))
        assertNull(CredentialRules.checkPassword("12345678"))
        assertEquals(
            "логин длиннее ${CredentialRules.LOGIN_MAX} символов",
            CredentialRules.checkLogin("a".repeat(CredentialRules.LOGIN_MAX + 1)),
        )
        assertEquals(
            "пароль длиннее ${CredentialRules.PASSWORD_MAX} символов",
            CredentialRules.checkPassword("a".repeat(CredentialRules.PASSWORD_MAX + 1)),
        )
    }
}
