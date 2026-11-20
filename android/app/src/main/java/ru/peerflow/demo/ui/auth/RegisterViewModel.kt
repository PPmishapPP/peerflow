package ru.peerflow.demo.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.peerflow.demo.data.model.AvatarUpload
import ru.peerflow.demo.data.model.CurrentUser
import ru.peerflow.demo.data.repo.AccountRepository

/**
 * Состояние экрана регистрации (`BR-JOIN-1`).
 *
 * Поле «о себе» и фото нужны заявке на вступление, но по контракту сервера
 * они передаются уже при регистрации: аккаунт создаётся сразу с профилем,
 * а заявка потом только ссылается на него. Поэтому здесь те же поля, что и на
 * экране «вступить в сообщество», и человек не заполняет их дважды.
 */
data class RegisterUiState(
    val login: String = "",
    val password: String = "",
    val displayName: String = "",
    val about: String = "",
    val avatar: AvatarUpload? = null,
    val loginError: String? = null,
    val passwordError: String? = null,
    val displayNameError: String? = null,
    val aboutError: String? = null,
    val formError: String? = null,
    val loading: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !loading && login.isNotBlank() && password.isNotBlank() &&
            displayName.isNotBlank()
}

sealed interface RegisterEvent {
    data class Registered(val user: CurrentUser) : RegisterEvent
}

/**
 * Создание аккаунта.
 *
 * Регистрация сразу логинит: сервер возвращает токен, репозиторий его
 * сохраняет, дальше человек попадает на экран заявки, а не обратно на вход.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    private val _events = Channel<RegisterEvent>(Channel.BUFFERED)
    val events: Flow<RegisterEvent> = _events.receiveAsFlow()

    fun onLoginChange(value: String) {
        _state.value = _state.value.copy(login = value, loginError = null, formError = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, passwordError = null, formError = null)
    }

    fun onDisplayNameChange(value: String) {
        _state.value = _state.value.copy(
            displayName = value,
            displayNameError = null,
            formError = null,
        )
    }

    fun onAboutChange(value: String) {
        _state.value = _state.value.copy(about = value, aboutError = null, formError = null)
    }

    /** Выбранное фото. `null` — человек убрал его или не выбирал. */
    fun onAvatarChange(avatar: AvatarUpload?) {
        _state.value = _state.value.copy(avatar = avatar, formError = null)
    }

    fun submit() {
        val current = _state.value
        if (current.loading) return

        // Проверяем ровно то, что уйдёт на сервер: пробелы по краям человек
        // ставит случайно (автозамена, копирование) — это не ошибка.
        val loginError = CredentialRules.checkLogin(current.login.trim())
        val passwordError = CredentialRules.checkPassword(current.password)
        val displayNameError = CredentialRules.checkDisplayName(current.displayName.trim())
        val aboutError = CredentialRules.checkAbout(current.about.trim())
        if (loginError != null || passwordError != null ||
            displayNameError != null || aboutError != null
        ) {
            _state.value = current.copy(
                loginError = loginError,
                passwordError = passwordError,
                displayNameError = displayNameError,
                aboutError = aboutError,
            )
            return
        }

        _state.value = current.copy(loading = true, formError = null)
        viewModelScope.launch {
            try {
                val user = accountRepository.register(
                    login = current.login.trim(),
                    password = current.password,
                    displayName = current.displayName.trim(),
                    about = current.about.trim(),
                    avatar = current.avatar,
                )
                _state.value = _state.value.copy(loading = false, password = "")
                _events.send(RegisterEvent.Registered(user))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    formError = e.toUserMessage(),
                )
            }
        }
    }
}
