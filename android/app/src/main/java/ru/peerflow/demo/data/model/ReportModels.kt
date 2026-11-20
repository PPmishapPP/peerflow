package ru.peerflow.demo.data.model

/**
 * Файл отчёта лидера (`BR-LIFE-7`).
 *
 * [url] — адрес скачивания с сервера. Собирать его на клиенте из
 * идентификатора значило бы знать устройство маршрутов сервера.
 */
data class Attachment(
    val id: String,
    val fileName: String,
    val contentType: String = "",
    val sizeBytes: Long = 0,
    val authorId: String = "",
    val authorName: String = "",
    val createdAt: String = "",
    val url: String = "",
) {
    /** Расширение файла — по нему экран рисует значок. */
    val extension: String
        get() = fileName.substringAfterLast('.', "").lowercase()

    /** Размер для человека: байты, килобайты или мегабайты. */
    val readableSize: String
        get() = when {
            sizeBytes >= 1024 * 1024 ->
                String.format("%.1f МБ", sizeBytes / 1024.0 / 1024.0)

            sizeBytes >= 1024 -> "${sizeBytes / 1024} КБ"
            else -> "$sizeBytes Б"
        }
}

/**
 * Трата бюджета задачи (`BR-BUDGET-4`).
 *
 * Журнал, а не счётчик: человеку важно, на что ушли деньги, а не только
 * сколько осталось.
 */
data class BudgetExpense(
    val id: String,
    val memberId: String,
    val memberName: String,
    val amount: Long,
    val note: String? = null,
    val createdAt: String = "",
)

/**
 * Отчёт задачи: файлы, траты и остаток бюджета (`BR-LIFE-7`, `BR-BUDGET-5`).
 *
 * [remaining] приходит с сервера: остаток — правило («бюджет минус траты»), и
 * пересчитывать его на клиенте значило бы иметь два разных остатка.
 */
data class Report(
    val proposalId: String,
    val files: List<Attachment> = emptyList(),
    val expenses: List<BudgetExpense> = emptyList(),
    val budget: Long = 0,
    val spent: Long = 0,
    val remaining: Long = 0,
) {
    /** Ни файлов, ни трат — отчитываться ещё не начали. */
    val isEmpty: Boolean get() = files.isEmpty() && expenses.isEmpty()

    /** Какая доля бюджета потрачена, 0..1. */
    val spentShare: Float
        get() = if (budget <= 0) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f)

    /** Остаток кончился: тратить больше нечего (`BR-BUDGET-4`). */
    val isExhausted: Boolean get() = remaining <= 0
}

/**
 * Файл, готовый к отправке.
 *
 * Байты, а не `Uri`: ViewModel не должен знать про `ContentResolver`, иначе
 * его нельзя протестировать обычным unit-тестом. Экран читает файл и отдаёт
 * сюда готовые данные — так же, как с аватаром.
 */
class ReportUpload(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
)

/**
 * Голосование «выполнено / не выполнено» (`BR-LIFE-8`, `BR-LIFE-9`,
 * `BR-VOTE-5`, `BR-OVER-9`).
 *
 * @property open идёт ли голосование сейчас
 * @property votesFor сколько голосов «выполнено»
 * @property votesAgainst сколько голосов «не выполнено»
 * @property members сколько действующих членов — знаменатель шкалы
 * @property status что происходит, словами сервера
 * @property myVote свой ответ; `null` — не отвечал
 * @property reputationAwarded сколько репутации начислено лидеру за задачу
 */
data class ClosureState(
    val proposalId: String,
    val stage: ProposalStage,
    val open: Boolean = false,
    val votesFor: Long = 0,
    val votesAgainst: Long = 0,
    val members: Long = 0,
    val deadline: String? = null,
    val status: String = "",
    val myVote: Boolean? = null,
    val leaderId: String? = null,
    val leaderName: String? = null,
    val reputationAwarded: Long = 0,
) {
    /** Задача закрыта сообществом: работа признана выполненной. */
    val closed: Boolean get() = stage == ProposalStage.CLOSED

    /** Лидер ещё не запускал процедуру закрытия (`BR-LIFE-8`). */
    val notStarted: Boolean get() = stage == ProposalStage.IN_WORK && !open

    /** Можно голосовать: процедура идёт и задача ещё не закрыта. */
    val canVote: Boolean get() = open && !closed

    /** Доля голосов «выполнено» от проголосовавших, 0..1. */
    val completedShare: Float
        get() {
            val total = votesFor + votesAgainst
            return if (total <= 0) 0f else votesFor.toFloat() / total
        }

    /** Сколько членов ещё не ответили. */
    val silent: Long get() = (members - votesFor - votesAgainst).coerceAtLeast(0)
}
