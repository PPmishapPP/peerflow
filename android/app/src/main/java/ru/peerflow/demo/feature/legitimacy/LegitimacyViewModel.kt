package ru.peerflow.demo.feature.legitimacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.peerflow.demo.data.model.LegitimacyBoard
import ru.peerflow.demo.data.model.MyLegitimacy
import ru.peerflow.demo.data.repo.LegitimacyRepository
import ru.peerflow.demo.ui.auth.toUserMessage

/**
 * Что человек видит в блоке легитимности (`BR-LEGIT-2`, `BR-LEGIT-3`).
 *
 * Состояния взаимоисключающие: нельзя одновременно «загружаем» и «ошибка».
 */
sealed interface LegitimacyState {

    /** Первая загрузка: кандидатов и лидера ещё не знаем. */
    data object Loading : LegitimacyState

    data class Loaded(val board: LegitimacyBoard) : LegitimacyState

    /** Загрузить не удалось, и показать нечего. */
    data class Failure(val message: String) : LegitimacyState
}

/**
 * Легитимность и лидер по задаче (`BR-LEGIT-2`–`BR-LEGIT-5`,
 * `BR-LEADER-1`, `BR-LEADER-5`).
 *
 * Кто лидер, какая у него поддержка и сколько можно потерять — считает сервер.
 * Здесь только действия человека и переходы состояний: повторение правил
 * выбора на клиенте дало бы «своего» лидера на каждом экране.
 *
 * Действия не «правят» уже показанную доску вручную: сервер отвечает новым
 * состоянием целиком, и поддержка с лидером всегда согласованы между собой.
 * После отзыва мандат мог быть утрачен — об этом знает только сервер.
 */
@HiltViewModel
class LegitimacyViewModel @Inject constructor(
    private val legitimacyRepository: LegitimacyRepository,
) : ViewModel() {

    private var proposalId: String = ""

    private val _state = MutableStateFlow<LegitimacyState>(LegitimacyState.Loading)
    val state: StateFlow<LegitimacyState> = _state.asStateFlow()

    /** Идёт действие — блокируем повторные нажатия. */
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** Ошибка поверх уже показанной доски (не стирая лидера и кандидатов). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Открывает блок легитимности задачи.
     *
     * @param proposalId идентификатор из навигации
     */
    fun load(proposalId: String) {
        this.proposalId = proposalId
        _state.value = LegitimacyState.Loading
        _message.value = null
        refresh()
    }

    /** Перезапрашивает доску: поддержку могли изменить другие члены. */
    fun refresh() {
        val id = proposalId
        if (id.isBlank()) return
        viewModelScope.launch {
            try {
                _state.value = LegitimacyState.Loaded(legitimacyRepository.board(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onFailure(e)
            }
        }
    }

    /**
     * Выдвигает себя кандидатом (`BR-LEADER-1`).
     *
     * Повторное выдвижение безопасно: сервер не создаёт второго кандидата, и
     * двойное нажатие на клиенте не превращается в две кандидатуры.
     */
    fun nominate() = perform { legitimacyRepository.nominate(it) }

    /**
     * Передаёт свою легитимность кандидату (`BR-LEGIT-2`).
     *
     * @param candidateId кому передаём
     */
    fun delegate(candidateId: String) = perform { legitimacyRepository.delegate(it, candidateId) }

    /**
     * Забирает свою легитимность себе (`BR-LEGIT-5`).
     *
     * Доступно в любой момент и без согласия кандидата: доверие — не договор
     * на срок.
     */
    fun revoke() = perform { legitimacyRepository.revoke(it) }

    /** Убирает сообщение об ошибке после того, как его показали. */
    fun dismissMessage() {
        _message.value = null
    }

    /** Общее для всех действий: блокировка, ответ сервера, ошибка, разблокировка. */
    private fun perform(action: suspend (String) -> LegitimacyBoard) {
        val id = proposalId
        if (id.isBlank() || _sending.value) return
        _sending.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                _state.value = LegitimacyState.Loaded(action(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.toUserMessage()
            } finally {
                _sending.value = false
            }
        }
    }

    private fun onFailure(e: Throwable) {
        if (_state.value is LegitimacyState.Loaded) {
            // Доска уже показана — не выбрасываем её из-за сетевого сбоя.
            _message.value = e.toUserMessage()
        } else {
            _state.value = LegitimacyState.Failure(e.toUserMessage())
        }
    }
}

/** Что человек видит в профиле (`BR-LEGIT-3`, `BR-REP-2`). */
sealed interface MyLegitimacyState {

    data object Loading : MyLegitimacyState

    data class Loaded(val mine: MyLegitimacy) : MyLegitimacyState

    data class Failure(val message: String) : MyLegitimacyState
}

/**
 * Кому человек передал легитимность и какой у него репутация
 * (`BR-LEGIT-3`, `BR-REP-2`).
 *
 * Отдельно от [LegitimacyViewModel]: профиль показывает все задачи сразу, а
 * карточка — одну. Объединять их значило бы грузить все передачи при открытии
 * каждой задачи.
 */
@HiltViewModel
class MyLegitimacyViewModel @Inject constructor(
    private val legitimacyRepository: LegitimacyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MyLegitimacyState>(MyLegitimacyState.Loading)
    val state: StateFlow<MyLegitimacyState> = _state.asStateFlow()

    /** Ошибка поверх уже показанного профиля. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Перезапрашивает профиль: передачи могли измениться на других экранах. */
    fun refresh() {
        viewModelScope.launch {
            try {
                _state.value = MyLegitimacyState.Loaded(legitimacyRepository.myLegitimacy())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (_state.value is MyLegitimacyState.Loaded) {
                    // Уже показанное не стираем: сетевой сбой — не повод
                    // показать пустой профиль.
                    _message.value = e.toUserMessage()
                } else {
                    _state.value = MyLegitimacyState.Failure(e.toUserMessage())
                }
            }
        }
    }

    /** Убирает сообщение об ошибке после того, как его показали. */
    fun dismissMessage() {
        _message.value = null
    }
}
