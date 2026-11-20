package ru.peerflow.demo.data.model

/**
 * Реакция на сообщение (`BR-DISC-2`).
 *
 * Три состояния взаимоисключающие: у человека ровно одна реакция на сообщение,
 * и повторное нажатие меняет её вид, а не добавляет вторую. Поэтому «плюс» и
 * «спам» одновременно невозможны — и скрытие спама нельзя накрутить сменой
 * метки.
 */
enum class ReactionKind(val label: String, val server: String) {
    PLUS("+", "PLUS"),
    MINUS("−", "MINUS"),
    SPAM("спам", "SPAM");

    companion object {

        /**
         * Разбирает реакцию из ответа сервера.
         *
         * @param value строковое имя реакции; `null` — реакции нет
         * @return реакция или `null`
         */
        fun fromServer(value: String?): ReactionKind? =
            value?.let { name -> entries.firstOrNull { it.server == name } }
    }
}

/**
 * Варианты сортировки сообщений (`BR-DISC-4`, `BR-DISC-5`).
 *
 * Значение [query] уходит на сервер: правило «плюсы поднимают, минусы
 * опускают» одно на всех, и клиент его не пересчитывает.
 */
enum class MessageSort(val query: String, val label: String) {
    POPULAR("popular", "Популярные"),
    NEW("new", "Новые"),
}

/**
 * Сообщение дискуссии глазами экрана (`BR-DISC-1`–`BR-DISC-3`).
 *
 * [replies] приходят с сервера вложенными и уже в нужном порядке: клиент
 * дерево не сортирует и не перестраивает (`BR-DISC-6`).
 */
data class DiscussionMessage(
    val id: String,
    val authorId: String,
    val authorName: String,
    val body: String,
    val createdAt: String,
    val plus: Long = 0,
    val minus: Long = 0,
    val spam: Long = 0,
    val myReaction: ReactionKind? = null,
    val replies: List<DiscussionMessage> = emptyList(),
) {
    /** Есть ли ответы: для отступов ветки на экране. */
    val hasReplies: Boolean get() = replies.isNotEmpty()
}

/**
 * Ветка обсуждения целиком (`BR-DISC-1`–`BR-DISC-6`, `BR-SPAM-1`).
 *
 * [hidden] — число сообщений, скрытых метками «спам». Скрытие не удаляет
 * сообщение, и человек должен знать, что часть обсуждения он не видит.
 */
data class DiscussionThread(
    val messages: List<DiscussionMessage> = emptyList(),
    val hidden: Long = 0,
) {
    val isEmpty: Boolean get() = messages.isEmpty()

    /** Есть ли что показать: либо сообщения, либо сообщение о скрытом. */
    val hasContent: Boolean get() = messages.isNotEmpty() || hidden > 0
}
