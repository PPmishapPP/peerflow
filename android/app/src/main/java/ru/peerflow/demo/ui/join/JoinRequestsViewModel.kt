package ru.peerflow.demo.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.peerflow.demo.data.model.JoinRequestSummary
import ru.peerflow.demo.data.repo.JoinRequestRepository
import ru.peerflow.demo.ui.auth.toUserMessage

/**
 * Что член сообщества видит на экране заявок (`BR-JOIN-5`, `BR-VOTE-8`).
 *
 * Состояния взаимоисключающие: нельзя одновременно «загружаем» и «заявок нет»,
 * иначе экран показал бы две правды сразу.
 */
sealed interface JoinRequestsState {

    /** Первая загрузка: очередь ещё не известна. */
    data object Loading : JoinRequestsState

    /** Заявок на рассмотрении нет — это норма, а не ошибка. */
    data object Empty : JoinRequestsState

    data class Loaded(val requests: List<JoinRequestSummary>) : JoinRequestsState

    /** Загрузить не удалось, и показать нечего. */
    data class Failure(val message: String) : JoinRequestsState
}

/**
 * Очередь заявок на вступление и голосование за приём (`BR-JOIN-5`).
 *
 * Экран нужен действующему члену: без него заявку физически некому принять —
 * вступающий видит только «ваша заявка рассматривается», а кнопок «за» и
 * «против» нет ни у кого.
 *
 * Очередь перезапрашивается при каждом открытии экрана: пока человек читал
 * список предложений, другие могли проголосовать, и счётчики уже устарели.
 *
 * Голос нельзя отменить или поменять — так устроено правило приёма
 * (`BR-VOTE-8`): решение принимается одним голосом, повторный сервер отклонит.
 * Поэтому после голоса заявка либо обновляет счётчик, либо уходит из очереди,
 * если её судьба решилась.
 */
@HiltViewModel
class JoinRequestsViewModel @Inject constructor(
    private val joinRequestRepository: JoinRequestRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<JoinRequestsState>(JoinRequestsState.Loading)
    val state: StateFlow<JoinRequestsState> = _state.asStateFlow()

    /** Идёт перезагрузка при уже показанной очереди — не блокируем экран. */
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Идёт голосование по конкретной заявке — блокируем повторные нажатия. */
    private val _voting = MutableStateFlow<String?>(null)
    val voting: StateFlow<String?> = _voting.asStateFlow()

    /** Ошибка поверх очереди (не стирая её). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Загружает очередь.
     *
     * Повторный вызов во время загрузки игнорируется: иначе возврат на экран
     * после поворота экрана отправил бы два одинаковых запроса.
     */
    fun refresh() {
        if (_loading.value) return
        _loading.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                publish(joinRequestRepository.pending())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (_state.value is JoinRequestsState.Loaded) {
                    // Очередь уже есть — сетевой сбой не должен её стирать.
                    _message.value = e.toUserMessage()
                } else {
                    _state.value = JoinRequestsState.Failure(e.toUserMessage())
                }
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Голосует за приём или против (`BR-VOTE-8`).
     *
     * @param requestId заявка
     * @param accept    {@code true} — «за», {@code false} — «против»
     */
    fun vote(requestId: String, accept: Boolean) {
        if (_voting.value != null) return
        _voting.value = requestId
        _message.value = null
        viewModelScope.launch {
            try {
                val updated = joinRequestRepository.vote(requestId, accept)
                replace(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.toUserMessage()
            } finally {
                _voting.value = null
            }
        }
    }

    /** Убирает сообщение об ошибке после того, как его показали. */
    fun dismissMessage() {
        _message.value = null
    }

    private fun publish(requests: List<JoinRequestSummary>) {
        _state.value = if (requests.isEmpty()) {
            JoinRequestsState.Empty
        } else {
            JoinRequestsState.Loaded(requests)
        }
    }

    /**
     * Обновляет одну заявку после голоса.
     *
     * Если голос решил её судьбу, сервер вернёт новое состояние — заявка
     * уходит из очереди без ожидания следующей загрузки. Полный перезапрос
     * сдвинул бы список под пальцем.
     */
    private fun replace(updated: JoinRequestSummary) {
        val current = _state.value
        if (current !is JoinRequestsState.Loaded) return

        val next = if (updated.isPending) {
            current.requests.map { if (it.id == updated.id) updated else it }
        } else {
            // Принята или отклонена — рассматривать больше нечего.
            current.requests.filterNot { it.id == updated.id }
        }
        publish(next)
    }
}
