package ru.peerflow.demo.feature.proposal

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
import ru.peerflow.demo.data.repo.ProposalRepository
import ru.peerflow.demo.ui.auth.toUserMessage

/**
 * Состояние формы нового предложения (`BR-LIFE-1`, `BR-BUDGET-2`).
 *
 * Ошибки полей — отдельно от общей ошибки формы: первую показываем у поля,
 * вторую (сервер, сеть) — над кнопкой. Так человек видит, что именно не так.
 */
data class NewProposalState(
    val title: String = "",
    val description: String = "",
    val budget: String = "",
    val titleError: String? = null,
    val descriptionError: String? = null,
    val budgetError: String? = null,
    val formError: String? = null,
    val submitting: Boolean = false,
) {
    /** Бюджет, разобранный из строки: только цифры. */
    val budgetValue: Long? get() = budget.toLongOrNull()

    /**
     * Можно ли отправлять.
     *
     * Проверяем то же, что требует сервер (`BR-BUDGET-2`), но не ждём сети,
     * чтобы сообщить очевидное: «бюджет обязателен».
     */
    val canSubmit: Boolean
        get() = !submitting &&
            title.isNotBlank() &&
            description.isNotBlank() &&
            (budgetValue ?: 0) > 0
}

/** Одноразовые события формы. */
sealed interface NewProposalEvent {

    /** Предложение создано — можно возвращаться в список. */
    data object Created : NewProposalEvent
}

/**
 * Внесение предложения (`BR-OVER-2`, `BR-LIFE-1`, `BR-BUDGET-2`).
 *
 * Бюджет обязателен и проверяется здесь до запроса: без него сервер ответит
 * `400`, но человеку не нужно ждать сеть, чтобы узнать правило сообщества.
 */
@HiltViewModel
class NewProposalViewModel @Inject constructor(
    private val proposalRepository: ProposalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NewProposalState())
    val state: StateFlow<NewProposalState> = _state.asStateFlow()

    private val _events = Channel<NewProposalEvent>(Channel.BUFFERED)
    val events: Flow<NewProposalEvent> = _events.receiveAsFlow()

    fun onTitleChange(value: String) {
        _state.value = _state.value.copy(title = value, titleError = null, formError = null)
    }

    fun onDescriptionChange(value: String) {
        _state.value = _state.value.copy(
            description = value,
            descriptionError = null,
            formError = null,
        )
    }

    /** Потолок бюджета принимаем только цифрами: «12 000 ₽» человек всё равно не наберёт. */
    fun onBudgetChange(value: String) {
        _state.value = _state.value.copy(
            budget = value.filter { it.isDigit() },
            budgetError = null,
            formError = null,
        )
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return

        val title = current.title.trim()
        val description = current.description.trim()
        val budget = current.budgetValue

        val titleError = if (title.isBlank()) "тема обязательна" else null
        val descriptionError = if (description.isBlank()) "описание обязательно" else null
        val budgetError = when {
            budget == null -> "потолок бюджета обязателен"
            budget <= 0L -> "потолок должен быть больше нуля"
            else -> null
        }
        if (titleError != null || descriptionError != null || budgetError != null) {
            _state.value = current.copy(
                titleError = titleError,
                descriptionError = descriptionError,
                budgetError = budgetError,
            )
            return
        }

        _state.value = current.copy(submitting = true, formError = null)
        viewModelScope.launch {
            try {
                proposalRepository.create(
                    title = title,
                    description = description,
                    budget = budget!!,
                )
                _events.send(NewProposalEvent.Created)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = _state.value.copy(formError = e.toUserMessage())
            } finally {
                _state.value = _state.value.copy(submitting = false)
            }
        }
    }
}
