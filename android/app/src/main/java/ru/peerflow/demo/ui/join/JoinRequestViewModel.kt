package ru.peerflow.demo.ui.join

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
import ru.peerflow.demo.data.model.JoinRequestSummary
import ru.peerflow.demo.data.repo.JoinRequestRepository
import ru.peerflow.demo.ui.auth.CredentialRules
import ru.peerflow.demo.ui.auth.toUserMessage

/**
 * Что человек видит на экране вступления (`BR-JOIN-2`, `BR-JOIN-3`).
 *
 * Состояния взаимоисключающие, поэтому `sealed interface`, а не набор флагов:
 * нельзя случайно оказаться «и в ожидании, и отклонён» одновременно.
 */
sealed interface JoinUiState {

    /** Первая загрузка: ещё не знаем, есть ли заявка. */
    data object Loading : JoinUiState

    /** Заявки нет — показываем форму. */
    data object Form : JoinUiState

    /** Заявка подана, ждёт голосования. */
    data class Pending(val request: JoinRequestSummary) : JoinUiState

    /** Заявку приняли — человек стал членом (`BR-JOIN-3`). */
    data class Accepted(val request: JoinRequestSummary) : JoinUiState

    /** Заявку отклонили. */
    data class Rejected(val request: JoinRequestSummary) : JoinUiState

    /** Не смогли узнать статус — сеть или сервер. */
    data class Failure(val message: String) : JoinUiState
}

/** Одноразовые события экрана. */
sealed interface JoinEvent {
    /** Заявку приняли: пора открывать основное приложение. */
    data object BecameMember : JoinEvent
}

/**
 * Подача заявки и отслеживание её судьбы.
 *
 * Статус запрашивается у сервера, а не хранится в клиенте: решение принимают
 * другие люди голосованием (`BR-JOIN-3`), и клиент об этом узнаёт только
 * запросом. Поэтому `refresh()` вызывается при каждом возврате на экран.
 */
@HiltViewModel
class JoinRequestViewModel @Inject constructor(
    private val joinRequestRepository: JoinRequestRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<JoinUiState>(JoinUiState.Loading)
    val state: StateFlow<JoinUiState> = _state.asStateFlow()

    private val _about = MutableStateFlow("")
    val about: StateFlow<String> = _about.asStateFlow()

    private val _aboutError = MutableStateFlow<String?>(null)
    val aboutError: StateFlow<String?> = _aboutError.asStateFlow()

    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _events = Channel<JoinEvent>(Channel.BUFFERED)
    val events: Flow<JoinEvent> = _events.receiveAsFlow()

    fun onAboutChange(value: String) {
        _about.value = value
        _aboutError.value = null
        _formError.value = null
    }

    /**
     * Узнать текущий статус заявки.
     *
     * `null` от репозитория означает «заявки нет» — это норма, а не ошибка:
     * сервер отвечает 204.
     */
    fun refresh() {
        _state.value = JoinUiState.Loading
        viewModelScope.launch {
            try {
                publish(joinRequestRepository.mine())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = JoinUiState.Failure(e.toUserMessage())
            }
        }
    }

    fun submit(avatar: AvatarUpload? = null) {
        if (_submitting.value) return

        // Проверяем ровно то, что уйдёт на сервер: пробелы по краям человек
        // ставит случайно (автозамена, копирование) — это не ошибка.
        val aboutError = CredentialRules.checkAbout(_about.value.trim())
        if (aboutError != null) {
            _aboutError.value = aboutError
            return
        }

        _submitting.value = true
        _formError.value = null
        viewModelScope.launch {
            try {
                publish(
                    joinRequestRepository.submit(
                        about = _about.value.trim(),
                        avatar = avatar,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _formError.value = e.toUserMessage()
            } finally {
                _submitting.value = false
            }
        }
    }

    /**
     * Выставить состояние и, если заявку только что приняли, сообщить об этом
     * событием: экран должен один раз увести человека в основное приложение,
     * а не показывать «принято» до перезапуска.
     */
    private suspend fun publish(request: JoinRequestSummary?) {
        val next = request.toState()
        _state.value = next
        if (next is JoinUiState.Accepted) {
            _events.send(JoinEvent.BecameMember)
        }
    }

    private fun JoinRequestSummary?.toState(): JoinUiState = when {
        this == null -> JoinUiState.Form
        isAccepted -> JoinUiState.Accepted(this)
        isRejected -> JoinUiState.Rejected(this)
        else -> JoinUiState.Pending(this)
    }
}
