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
import ru.peerflow.demo.data.repo.JoinRequestRepository

/**
 * Сколько заявок ждёт решения — для отметки на главном экране (`BR-JOIN-5`).
 *
 * Без этой цифры новая заявка остаётся незамеченной: член сообщества видит
 * только список предложений и не знает, что кого-то нужно принять. Поэтому
 * счётчик запрашивается вместе с главным экраном.
 *
 * Ошибка здесь не показывается: это вспомогательная цифра, и заслонять ею
 * список предложений нельзя. При сбое просто не рисуем отметку.
 */
@HiltViewModel
class PendingJoinRequestsViewModel @Inject constructor(
    private val joinRequestRepository: JoinRequestRepository,
) : ViewModel() {

    /** Число ожидающих заявок; `null` — ещё не знаем или не смогли узнать. */
    private val _count = MutableStateFlow<Int?>(null)
    val count: StateFlow<Int?> = _count.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                _count.value = joinRequestRepository.pending().size
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Показывать ошибку нечего: цифра необязательна, а список
                // предложений важнее. Просто не рисуем отметку.
                _count.value = null
            }
        }
    }
}
