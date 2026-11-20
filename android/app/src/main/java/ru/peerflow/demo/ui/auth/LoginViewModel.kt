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
import ru.peerflow.demo.data.api.ApiException
import ru.peerflow.demo.data.api.NetworkException
import ru.peerflow.demo.data.model.CurrentUser
import ru.peerflow.demo.data.repo.AccountRepository

/**
 * Что экран входа показывает прямо сейчас.
 *
 * Поля ввода живут здесь, а не в `remember` композабла: так их видит ViewModel
 * (одна точка правды), они переживают поворот экрана и их можно проверить
 * обычным unit-тестом.
 */
data class LoginUiState(
    val login: String = "",
    val password: String = "",
    val loginError: String? = null,
    val passwordError: String? = null,
    val formError: String? = null,
    val loading: Boolean = false,
) {
    /** Кнопку «Войти» можно нажимать. */
    val canSubmit: Boolean get() = !loading && login.isNotBlank() && password.isNotBlank()
}

/** Одноразовые события: их показывают один раз и забывают. */
sealed interface LoginEvent {
    /** Вход выполнен — пора решать, куда идти дальше. */
    data class LoggedIn(val user: CurrentUser) : LoginEvent
}

/**
 * Вход в аккаунт (`BR-JOIN-1`).
 *
 * Зависит только от [AccountRepository], поэтому тест подставляет фейк и не
 * поднимает ни Hilt, ни сеть, ни Android.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    fun onLoginChange(value: String) {
        _state.value = _state.value.copy(
            login = value,
            loginError = null,
            formError = null,
        )
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(
            password = value,
            passwordError = null,
            formError = null,
        )
    }

    /**
     * Проверить поля и отправить запрос.
     *
     * Повторное нажатие во время запроса игнорируется: два параллельных входа
     * дали бы два токена, и какой из них победит — дело случая.
     */
    fun submit() {
        val current = _state.value
        if (current.loading) return

        // Проверяем ровно то, что уйдёт на сервер: пробелы по краям логина
        // человек ставит случайно (автозамена, копирование) — это не ошибка.
        val loginError = CredentialRules.checkLogin(current.login.trim())
        val passwordError = CredentialRules.checkPassword(current.password)
        if (loginError != null || passwordError != null) {
            _state.value = current.copy(
                loginError = loginError,
                passwordError = passwordError,
            )
            return
        }

        _state.value = current.copy(loading = true, formError = null)
        viewModelScope.launch {
            try {
                val user = accountRepository.login(
                    login = current.login.trim(),
                    password = current.password,
                )
                _state.value = _state.value.copy(loading = false, password = "")
                _events.send(LoginEvent.LoggedIn(user))
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

/**
 * Текст ошибки для человека.
 *
 * Сервер уже объясняет причину в `detail` (`ApiException.message`), поэтому
 * там ничего не придумываем. Всё неожиданное сводим к «попробуйте позже»:
 * показывать человеку `IllegalStateException` бессмысленно.
 */
internal fun Throwable.toUserMessage(): String = when (this) {
    is ApiException -> message
    is NetworkException -> "нет связи с сервером, проверьте интернет"
    else -> "что-то пошло не так, попробуйте позже"
}
