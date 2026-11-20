package ru.peerflow.demo.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.peerflow.demo.data.api.ApiException
import ru.peerflow.demo.data.model.CurrentUser
import ru.peerflow.demo.data.repo.AccountRepository
import ru.peerflow.demo.ui.auth.toUserMessage

/**
 * Состояние входа в приложение.
 *
 * Отдельная ViewModel нужна, потому что решение «показать вход» или «пустить
 * внутрь» принимается один раз и на всё приложение: иначе экраны начнут
 * выяснять это каждый сам и разойдутся.
 */
sealed interface SessionState {
    /** Пока не знаем, есть ли живая сессия: читаем токен и профиль. */
    data object Loading : SessionState

    /** Токена нет или он больше не действителен. */
    data object SignedOut : SessionState

    /** Есть действующий токен и профиль человека. */
    data class SignedIn(val user: CurrentUser) : SessionState

    /** Не смогли проверить сессию: например, сервер недоступен. */
    data class Failure(val message: String) : SessionState
}

/**
 * Проверяет сохранённый токен при запуске и хранит текущего пользователя.
 *
 * Токен лежит в [AccountRepository], поэтому ViewModel не знает ни про
 * `SharedPreferences`, ни про сеть — только про «вошёл / не вошёл».
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        restore()
    }

    /** Перечитать сессию с сервера. Вызывается при старте и по кнопке «Повторить». */
    fun restore() {
        if (!accountRepository.hasToken()) {
            _state.value = SessionState.SignedOut
            return
        }
        _state.value = SessionState.Loading
        viewModelScope.launch {
            try {
                _state.value = SessionState.SignedIn(accountRepository.me())
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.status == 401) {
                    // Токен истёк или отозван — человеку всё равно нужно войти заново.
                    accountRepository.logout()
                    _state.value = SessionState.SignedOut
                } else {
                    _state.value = SessionState.Failure(e.toUserMessage())
                }
            } catch (e: Throwable) {
                _state.value = SessionState.Failure(e.toUserMessage())
            }
        }
    }

    /** Вход или регистрация только что прошли успешно. */
    fun onAuthenticated(user: CurrentUser) {
        _state.value = SessionState.SignedIn(user)
    }

    /**
     * Заявку приняли, а профиль в памяти ещё «не член».
     *
     * Перечитываем профиль, чтобы членство пришло с сервера: так состояние
     * не разойдётся с тем, что видит сообщество.
     */
    fun refreshProfile() {
        if (_state.value !is SessionState.SignedIn) return
        viewModelScope.launch {
            try {
                _state.value = SessionState.SignedIn(accountRepository.me())
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.status == 401) {
                    accountRepository.logout()
                    _state.value = SessionState.SignedOut
                }
            } catch (e: Throwable) {
                // Профиль не перечитался — оставляем прежнее состояние:
                // это не повод выкидывать человека из приложения.
            }
        }
    }

    /** Выход по кнопке. Токен стирается, приложение возвращается ко входу. */
    fun logout() {
        accountRepository.logout()
        _state.value = SessionState.SignedOut
    }
}
