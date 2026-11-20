package ru.peerflow.demo.data.model

/**
 * Варианты сортировки списка предложений (`BR-RANK-4`).
 *
 * Значение [query] уходит на сервер: порядок считает он, а не клиент. Иначе
 * правило популярности разъехалось бы между платформами
 * (`BR-RANK-2`).
 */
enum class ProposalSort(val query: String, val label: String) {
    POPULAR("popular", "Популярные"),
    BUDGET("budget", "По бюджету"),
    NEW("new", "Новые"),
}

/**
 * Стадия предложения (`BR-LIFE-1`…`BR-LIFE-10`).
 *
 * Сервер присылает строку; неизвестную стадию не скрываем и не угадываем —
 * показываем как есть, чтобы новый статус на сервере не ломал список.
 */
enum class ProposalStage(val label: String) {
    DISCUSSION("На обсуждении"),
    IN_WORK("В работе"),
    CLOSED("Закрыто"),
    REJECTED("Не делаем");

    companion object {

        /**
         * Разбирает стадию из ответа сервера.
         *
         * @param value строковое имя стадии
         * @return стадия; для незнакомого значения — [DISCUSSION], чтобы
         *         предложение не пропало из списка
         */
        fun fromServer(value: String): ProposalStage =
            entries.firstOrNull { it.name == value } ?: DISCUSSION
    }
}

/** Голос текущего пользователя по предложению (`BR-VOTE-13`). */
data class MyVote(val inFavor: Boolean)

/**
 * Предложение глазами клиента.
 *
 * Посчитанные сервером числа ([participants], [popularity]) не пересчитываются
 * здесь: метрика — правило сообщества (`BR-RANK-2`), и дублировать его на
 * клиенте значило бы однажды получить два разных ответа.
 */
data class ProposalSummary(
    val id: String,
    val title: String,
    val description: String,
    val authorId: String,
    val authorName: String,
    val budget: Long,
    val stage: ProposalStage,
    val votesFor: Long,
    val votesAgainst: Long,
    val participants: Long,
    val popularity: Long,
    val createdAt: String,
    val decidedAt: String? = null,
    val votingDeadline: String? = null,
    val votingClosed: Boolean,
    val votingStatus: String,
    val myVote: MyVote? = null,
) {
    /** Голосование ещё идёт — можно голосовать или менять голос. */
    val canVote: Boolean get() = stage == ProposalStage.DISCUSSION && !votingClosed

    /** Текущий человек уже проголосовал. */
    val votedByMe: Boolean get() = myVote != null
}

/**
 * Список предложений двумя секциями (`BR-RANK-5`).
 *
 * Секции приходят с сервера готовыми: их состав — правило сообщества, а не
 * оформление экрана.
 */
data class ProposalSections(
    val inWork: List<ProposalSummary> = emptyList(),
    val discussing: List<ProposalSummary> = emptyList(),
) {
    val isEmpty: Boolean get() = inWork.isEmpty() && discussing.isEmpty()

    /** Находит предложение в любой секции. */
    fun byId(id: String): ProposalSummary? =
        inWork.firstOrNull { it.id == id } ?: discussing.firstOrNull { it.id == id }
}
