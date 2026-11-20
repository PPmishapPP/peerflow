package ru.peerflow.demo.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.peerflow.demo.data.model.ClosureState
import ru.peerflow.demo.data.model.Report
import ru.peerflow.demo.data.model.ReportUpload
import ru.peerflow.demo.data.repo.ReportRepository
import ru.peerflow.demo.ui.auth.toUserMessage

/**
 * Что человек видит на экране отчёта и закрытия задачи (`BR-LIFE-7`–
 * `BR-LIFE-9`).
 *
 * Состояния взаимоисключающие: нельзя одновременно «загружаем» и «ошибка».
 */
sealed interface ReportState {

    /** Первая загрузка: ни файлов, ни голосов ещё не знаем. */
    data object Loading : ReportState

    /**
     * Загружено.
     *
     * Отчёт и голосование приходят одним состоянием, потому что экран
     * показывает их вместе: остаток бюджета и счётчики «выполнено» — части
     * одной картины задачи в работе.
     */
    data class Loaded(
        val report: Report,
        val closure: ClosureState,
    ) : ReportState

    /** Загрузить не удалось, и показать нечего. */
    data class Failure(val message: String) : ReportState
}

/**
 * Отчёт лидера, траты бюджета и голосование по выполнению
 * (`BR-LIFE-7`, `BR-LIFE-8`, `BR-LIFE-9`, `BR-BUDGET-4`, `BR-VOTE-5`).
 *
 * Кто лидер, сколько голосов уже подано и как переходит легитимность в
 * репутацию — считает сервер. Здесь только действия человека и переходы
 * состояний: повторение правил на клиенте дало бы «своё» голосование на
 * каждом экране.
 *
 * После каждого действия сервер отвечает новым состоянием целиком, и отчёт с
 * голосованием остаются согласованными: отправка отчёта могла изменить
 * остаток, а решающий голос — закрыть задачу и начислить репутацию.
 */
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private var proposalId: String = ""

    private val _state = MutableStateFlow<ReportState>(ReportState.Loading)
    val state: StateFlow<ReportState> = _state.asStateFlow()

    /** Идёт действие — блокируем повторные нажатия. */
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** Ошибка поверх уже показанного отчёта (не стирая файлы и голоса). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Открывает отчёт задачи.
     *
     * @param proposalId идентификатор из навигации
     */
    fun load(proposalId: String) {
        this.proposalId = proposalId
        _state.value = ReportState.Loading
        _message.value = null
        refresh()
    }

    /** Перезапрашивает отчёт и голосование: счётчики меняются, пока экран открыт. */
    fun refresh() {
        val id = proposalId
        if (id.isBlank()) return
        viewModelScope.launch {
            try {
                _state.value = ReportState.Loaded(
                    report = reportRepository.report(id),
                    closure = reportRepository.closure(id),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onLoadFailure(e)
            }
        }
    }

    /**
     * Публикует отчёт лидера (`BR-LIFE-7`).
     *
     * Пустой отчёт не отправляем: сервер его всё равно отклонит, но человек
     * получит ошибку сети вместо понятного «нечего отправлять».
     *
     * @param text текст отчёта; может быть пустым, если есть файл
     * @param file приложенный файл; может отсутствовать
     */
    fun postReport(text: String, file: ReportUpload?) {
        if (text.isBlank() && file == null) {
            // Сервер отклонил бы пустой отчёт, но человек ждал бы сетевой
            // ошибки и не понял, что именно не так.
            _message.value = "отчёт не может быть пустым: напишите текст или приложите файл"
            return
        }
        perform { id -> show(reportRepository.post(id, text, file), id) }
    }

    /**
     * Записывает трату бюджета (`BR-BUDGET-4`).
     *
     * @param amount сумма; должна быть положительной
     * @param note на что ушло; может отсутствовать
     */
    fun spend(amount: Long, note: String?) {
        if (amount <= 0) {
            _message.value = "трата должна быть положительной"
            return
        }
        perform { id -> show(reportRepository.spend(id, amount, note), id) }
    }

    /**
     * Лидер запускает процедуру закрытия (`BR-LIFE-8`).
     *
     * Отчёт перезапрашиваем: между экранами его могли дополнить, и после
     * запуска процедуры экран показывает уже другую картину задачи.
     */
    fun openClosure() = perform { id ->
        val closure = reportRepository.openClosure(id)
        _state.value = ReportState.Loaded(reportRepository.report(id), closure)
    }

    /**
     * Голос «выполнено / не выполнено» или смена ответа
     * (`BR-VOTE-5`, `BR-VOTE-13`).
     *
     * Ответ сервера — единственный источник правды о счётчиках и стадии:
     * голос мог стать решающим и закрыть задачу с начислением репутации
     * (`BR-REP-1`).
     */
    fun vote(completed: Boolean) = perform { id ->
        val closure = reportRepository.voteOnClosure(id, completed)
        _state.value = ReportState.Loaded(reportRepository.report(id), closure)
    }

    /**
     * Показывает обновлённый отчёт с актуальным голосованием.
     *
     * Голосование перезапрашиваем, потому что ответ на отчёт и трату его не
     * содержит: без этого счётчики на экране разошлись бы с отчётом.
     */
    private suspend fun show(report: Report, proposalId: String) {
        _state.value = ReportState.Loaded(report, reportRepository.closure(proposalId))
    }

    /** Убирает сообщение об ошибке после того, как его показали. */
    fun dismissMessage() {
        _message.value = null
    }

    /** Общее для всех действий: блокировка, ошибка, разблокировка. */
    private fun perform(action: suspend (String) -> Unit) {
        val id = proposalId
        if (id.isBlank() || _sending.value) return
        _sending.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                action(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.toUserMessage()
            } finally {
                _sending.value = false
            }
        }
    }

    private fun onLoadFailure(e: Throwable) {
        if (_state.value is ReportState.Loaded) {
            // Уже показанное не стираем: сетевой сбой — не повод показать
            // пустой отчёт и «лидер не отчитывался».
            _message.value = e.toUserMessage()
        } else {
            _state.value = ReportState.Failure(e.toUserMessage())
        }
    }
}
