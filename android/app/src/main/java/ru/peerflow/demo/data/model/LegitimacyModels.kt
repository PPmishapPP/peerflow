package ru.peerflow.demo.data.model

/**
 * Кандидат в лидеры задачи (`BR-LEADER-1`–`BR-LEADER-4`).
 *
 * Показатели приходят посчитанными и список — уже в порядке силы: порядок
 * задают тай-брейкеры (`BR-LEADER-4`), и клиент его не пересчитывает, иначе
 * «кто лидер» решался бы по-разному на разных экранах.
 *
 * @property price за сколько человек выполнит задачу (`BR-BUDGET-8`,
 *                `BR-BUDGET-9`): видно до голосования, чтобы выбирать лидера
 *                можно было в том числе по стоимости
 * @property me кандидат — это текущий пользователь
 */
data class Candidate(
    val memberId: String,
    val displayName: String,
    val support: Long,
    val reputation: Long,
    val price: Long,
    val me: Boolean,
) {
    /** Начальная буква для аватара-заглушки: имена короткие, картинок нет. */
    val initial: String get() = displayName.trim().take(1).uppercase()
}

/**
 * Действующий или последний лидер задачи (`BR-LEGIT-4`, `BR-LEADER-5`,
 * `BR-LEADER-6`).
 *
 * [requiredSupport] приходит с сервера, а не считается из процентов: порог
 * удержания — правило сообщества, и повторять его на клиенте значило бы
 * получить две разные шкалы.
 *
 * @property taskBudget бюджет задачи — цена, за которую лидер работает
 *                     (`BR-LIFE-6`); `0`, если мандат уже завершён
 * @property active действует ли мандат
 * @property endReason почему завершён (`MANDATE_LOST`, `TASK_CLOSED`);
 *                     `null`, пока мандат действует
 */
data class Leader(
    val memberId: String,
    val displayName: String,
    val support: Long,
    val initialSupport: Long,
    val requiredSupport: Long,
    val taskBudget: Long = 0,
    val active: Boolean,
    val endReason: String? = null,
) {
    /** Насколько изначальная поддержка сохранилась, 0..100. */
    val retainedPercent: Int
        get() = if (initialSupport <= 0) {
            100
        } else {
            ((support.toDouble() / initialSupport) * 100).toInt().coerceIn(0, 100)
        }

    /** Сколько ещё можно потерять, прежде чем мандат уйдёт. */
    val supportAboveRequired: Long get() = (support - requiredSupport).coerceAtLeast(0)

    /** Мандат утрачен по отзыву доверия, а не потому что задачу закрыли. */
    val lostByRevoke: Boolean get() = !active && endReason == MANDATE_LOST
}

/** Причина завершения мандата: серверные имена (`BR-LEADER-5`, `BR-LIFE-9`). */
const val MANDATE_LOST = "MANDATE_LOST"

/** Задачу закрыли решением сообщества (`BR-LIFE-9`). */
const val TASK_CLOSED = "TASK_CLOSED"

/**
 * Легитимность, кандидаты и лидер по одной задаче (`BR-LEGIT-2`,
 * `BR-LEGIT-3`, `BR-LEADER-3`).
 *
 * Всё одним объектом, потому что экран показывает это одновременно: отдельные
 * запросы дали бы разные моменты времени и лидера, которого уже нет.
 *
 * @property myDelegation кому текущий человек передал доверие;
 *                        `null` — доверие у себя (`BR-LEGIT-5`)
 * @property total сколько единиц доверия передано кандидатам
 * @property members сколько действующих членов в сообществе
 */
data class LegitimacyBoard(
    val proposalId: String,
    val candidates: List<Candidate> = emptyList(),
    val leader: Leader? = null,
    val myDelegation: String? = null,
    val total: Long = 0,
    val members: Long = 0,
) {
    /** Текущий человек уже выдвигался. */
    val meIsCandidate: Boolean get() = candidates.any { it.me }

    /** Лидер действует: можно выполнять задачу. */
    val hasActiveLeader: Boolean get() = leader?.active == true

    /** Никто не собрал поддержки — выбирать не из кого (`BR-LEGIT-4`). */
    val isEmpty: Boolean get() = candidates.isEmpty() && leader == null

    /**
     * Доля доверия сообщества, переданная кандидатам, 0..100.
     *
     * Это не «прогресс голосования»: передача добровольна, и 100% означало бы,
     * что каждый член кого-то поддержал.
     */
    val delegatedPercent: Int
        get() = if (members <= 0) 0 else ((total.toDouble() / members) * 100).toInt()
}

/**
 * Кому человек передал доверие по одной задаче (`BR-LEGIT-3`,
 * `BR-LEGIT-5`).
 *
 * Строка есть всегда, даже когда доверие осталось у себя: «не передана» — это
 * состояние, и пропуск строки выглядел бы как отсутствие задачи.
 */
data class Delegation(
    val proposalId: String,
    val proposalTitle: String,
    val candidateId: String? = null,
    val candidateName: String? = null,
) {
    /** Доверие передано кандидату, а не осталось у себя. */
    val isDelegated: Boolean get() = candidateId != null
}

/**
 * Легитимность и репутация текущего человека (`BR-REP-2`).
 *
 * Репутация приходит рядом с передачами: профиль показывает их на одном
 * экране, и два запроса дали бы два разных момента времени.
 */
data class MyLegitimacy(
    val reputation: Long = 0,
    val delegations: List<Delegation> = emptyList(),
) {
    /** Задачи, по которым доверие действительно передано. */
    val delegated: List<Delegation> get() = delegations.filter { it.isDelegated }

    /** Задачи, где доверие осталось у себя. */
    val kept: List<Delegation> get() = delegations.filterNot { it.isDelegated }
}
