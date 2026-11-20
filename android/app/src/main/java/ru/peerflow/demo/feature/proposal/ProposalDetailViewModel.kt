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
import ru.peerflow.demo.data.model.ProposalSummary
import ru.peerflow.demo.data.repo.ProposalRepository
import ru.peerflow.demo.ui.auth.toUserMessage

/**
 * Состояние карточки предложения (`BR-VOTE-4`).
 */
sealed interface ProposalDetailState {

    /** Ещё не загружали: идентификатор известен, данных нет. */
    data object Loading : ProposalDetailState

    data class Loaded(val proposal: ProposalSummary) : ProposalDetailState

    data class Failure(val message: String) : ProposalDetailState
}

/**
 * Карточка предложения и голосование по нему (`BR-VOTE-4`, `BR-VOTE-13`).
 *
 * Карточку загружаем отдельным запросом, а не берём из списка: пока человек
 * читал список, голоса могли измениться, и счётчики в списке уже устарели.
 *
 * Идентификатор приходит из навигации через [load], а не из
 * `SavedStateHandle`: навигация в приложении пока на состоянии, и
 * `SavedStateHandle` в ней пуст. Так ViewModel ещё и тестируется без
 * `SavedStateHandle`.
 */
@HiltViewModel
class ProposalDetailViewModel @Inject constructor(
    private val proposalRepository: ProposalRepository,
) : ViewModel() {

    private var proposalId: String = ""

    private val _state = MutableStateFlow<ProposalDetailState>(ProposalDetailState.Loading)
    val state: StateFlow<ProposalDetailState> = _state.asStateFlow()

    private val _voting = MutableStateFlow(false)
    val voting: StateFlow<Boolean> = _voting.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Открывает карточку.
     *
     * @param proposalId идентификатор из навигации
     */
    fun load(proposalId: String) {
        this.proposalId = proposalId
        _state.value = ProposalDetailState.Loading
        _message.value = null
        refresh()
    }

    /** Перезапрашивает карточку: счётчики голосов меняются, пока экран открыт. */
    fun refresh() {
        val id = proposalId
        if (id.isBlank()) return
        viewModelScope.launch {
            try {
                _state.value = ProposalDetailState.Loaded(proposalRepository.get(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (_state.value is ProposalDetailState.Loaded) {
                    _message.value = e.toUserMessage()
                } else {
                    _state.value = ProposalDetailState.Failure(e.toUserMessage())
                }
            }
        }
    }

    /**
     * Голос «делаем / не делаем» или смена голоса (`BR-VOTE-13`).
     *
     * Ответ сервера — единственный источник правды о счётчиках и стадии:
     * голос мог стать решающим и закрыть голосование (`BR-VOTE-10`).
     */
    fun vote(inFavor: Boolean) {
        if (_voting.value || proposalId.isBlank()) return
        _voting.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                _state.value = ProposalDetailState.Loaded(
                    proposalRepository.vote(proposalId, inFavor),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.toUserMessage()
            } finally {
                _voting.value = false
            }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
