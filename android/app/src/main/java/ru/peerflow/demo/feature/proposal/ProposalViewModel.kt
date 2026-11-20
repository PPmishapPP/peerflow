package ru.peerflow.demo.feature.proposal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.peerflow.demo.data.model.ProposalSections
import ru.peerflow.demo.data.model.ProposalSort
import ru.peerflow.demo.data.repo.ProposalRepository
import ru.peerflow.demo.ui.auth.toUserMessage

/**
 * Что человек видит на главном экране (`BR-RANK-5`).
 *
 * Состояния взаимоисключающие, поэтому `sealed interface`, а не набор флагов:
 * нельзя случайно оказаться «и загружаем, и с ошибкой» одновременно.
 */
sealed interface ProposalListState {

    /** Первая загрузка: ещё не знаем ни одного предложения. */
    data object Loading : ProposalListState

    /** Данные есть — показываем секции. */
    data class Loaded(val sections: ProposalSections) : ProposalListState

    /** Загрузить не удалось, и показать нечего. */
    data class Failure(val message: String) : ProposalListState
}

/**
 * Список предложений (`BR-RANK-1`–`BR-RANK-5`).
 *
 * Сортировку и метрику популярности считает сервер: это правила сообщества, а
 * не оформление экрана. ViewModel только запоминает выбранную вкладку и
 * перезапрашивает список (`BR-RANK-4`).
 *
 * Сбой сети не стирает уже показанный список: человек должен видеть те же
 * предложения и понятный текст, а не пустой экран.
 */
@HiltViewModel
class ProposalViewModel @Inject constructor(
    private val proposalRepository: ProposalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ProposalListState>(ProposalListState.Loading)
    val state: StateFlow<ProposalListState> = _state.asStateFlow()

    private val _sort = MutableStateFlow(ProposalSort.POPULAR)
    val sort: StateFlow<ProposalSort> = _sort.asStateFlow()

    /** Идёт загрузка списка; выбранная вкладка при этом остаётся активной. */
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Ошибка, которую показываем поверх списка (не стирая данные). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Загружает список.
     *
     * Повторный вызов во время загрузки игнорируется: иначе быстрое
     * переключение вкладок отправило бы несколько запросов, и в списке
     * оказался бы ответ на ту вкладку, которая пришла последней, а не на
     * выбранную.
     */
    fun refresh() {
        if (_loading.value) return
        _loading.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                val sections = proposalRepository.list(_sort.value)
                _state.value = ProposalListState.Loaded(sections)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (_state.value is ProposalListState.Loaded) {
                    // Данные уже есть — не выбрасываем их из-за сетевого сбоя.
                    _message.value = e.toUserMessage()
                } else {
                    _state.value = ProposalListState.Failure(e.toUserMessage())
                }
            } finally {
                _loading.value = false
            }
        }
    }

    /** Переключает сортировку и перезапрашивает список (`BR-RANK-4`). */
    fun selectSort(sort: ProposalSort) {
        if (_sort.value == sort) return
        _sort.value = sort
        // Сбрасываем загрузку, чтобы новый порядок запросился сразу: старая
        // загрузка относится к предыдущей вкладке и её результат уже не нужен.
        _loading.value = false
        refresh()
    }

    /** Убирает сообщение об ошибке после того, как его показали. */
    fun dismissMessage() {
        _message.value = null
    }
}

