package ru.peerflow.demo.feature.discussion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.peerflow.demo.data.model.DiscussionThread
import ru.peerflow.demo.data.model.MessageSort
import ru.peerflow.demo.data.model.ReactionKind
import ru.peerflow.demo.data.repo.DiscussionRepository
import ru.peerflow.demo.ui.auth.toUserMessage

/**
 * Что человек видит в блоке обсуждения (`BR-DISC-1`–`BR-DISC-6`).
 *
 * Состояния взаимоисключающие: нельзя одновременно «загружаем» и «ошибка».
 */
sealed interface DiscussionState {

    /** Первая загрузка: сообщений ещё не знаем. */
    data object Loading : DiscussionState

    data class Loaded(val thread: DiscussionThread) : DiscussionState

    /** Загрузить не удалось, и показать нечего. */
    data class Failure(val message: String) : DiscussionState
}

/**
 * Дискуссия под предложением (`BR-DISC-1`–`BR-DISC-6`, `BR-SPAM-1`).
 *
 * Порядок сообщений, вложенность и скрытие спама считает сервер: это правила
 * сообщества, и клиент их не повторяет. ViewModel только запоминает выбранную
 * сортировку и отправляет действия человека.
 *
 * После каждого действия (сообщение, реакция) сервер возвращает всю ветку
 * заново — так счётчики и скрытие не расходятся с тем, что видят другие.
 */
@HiltViewModel
class DiscussionViewModel @Inject constructor(
    private val discussionRepository: DiscussionRepository,
) : ViewModel() {

    private var proposalId: String = ""

    private val _state = MutableStateFlow<DiscussionState>(DiscussionState.Loading)
    val state: StateFlow<DiscussionState> = _state.asStateFlow()

    private val _sort = MutableStateFlow(MessageSort.POPULAR)
    val sort: StateFlow<MessageSort> = _sort.asStateFlow()

    /** Идёт отправка сообщения или реакции — блокируем повторные нажатия. */
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** Ошибка поверх уже показанной ветки (не стирая сообщения). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Открывает обсуждение предложения.
     *
     * @param proposalId идентификатор из навигации
     */
    fun load(proposalId: String) {
        this.proposalId = proposalId
        _state.value = DiscussionState.Loading
        _message.value = null
        refresh()
    }

    /** Перезапрашивает ветку в текущей сортировке. */
    fun refresh() {
        val id = proposalId
        if (id.isBlank()) return
        viewModelScope.launch {
            try {
                _state.value = DiscussionState.Loaded(
                    discussionRepository.thread(id, _sort.value),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onFailure(e)
            }
        }
    }

    /** Переключает сортировку и перезапрашивает ветку (`BR-DISC-4`, `BR-DISC-5`). */
    fun selectSort(sort: MessageSort) {
        if (_sort.value == sort) return
        _sort.value = sort
        refresh()
    }

    /**
     * Пишет сообщение или ответ (`BR-DISC-1`, `BR-DISC-3`).
     *
     * Пустое сообщение не отправляем: это не аргумент, и сервер всё равно
     * ответит `400`. Проверка здесь — чтобы не гонять запрос зря.
     *
     * @param body     текст
     * @param parentId сообщение, на которое отвечают; `null` — корневое
     */
    fun post(body: String, parentId: String? = null) {
        val id = proposalId
        if (id.isBlank() || _sending.value) return
        if (body.isBlank()) return
        _sending.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                _state.value = DiscussionState.Loaded(
                    discussionRepository.post(id, body.trim(), parentId),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.toUserMessage()
            } finally {
                _sending.value = false
            }
        }
    }

    /**
     * Ставит или меняет свою реакцию (`BR-DISC-2`).
     *
     * Повторное нажатие той же реакции не отправляем: реакция уже стоит, и
     * лишний запрос ничего не изменит.
     */
    fun react(messageId: String, kind: ReactionKind) {
        if (_sending.value) return
        _sending.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                _state.value = DiscussionState.Loaded(
                    discussionRepository.react(messageId, kind),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.toUserMessage()
            } finally {
                _sending.value = false
            }
        }
    }

    /** Убирает сообщение об ошибке после того, как его показали. */
    fun dismissMessage() {
        _message.value = null
    }

    private fun onFailure(e: Throwable) {
        if (_state.value is DiscussionState.Loaded) {
            // Ветка уже показана — не выбрасываем её из-за сетевого сбоя.
            _message.value = e.toUserMessage()
        } else {
            _state.value = DiscussionState.Failure(e.toUserMessage())
        }
    }
}
