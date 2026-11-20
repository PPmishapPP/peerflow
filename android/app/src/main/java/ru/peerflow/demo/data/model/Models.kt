package ru.peerflow.demo.data.model

/**
 * Текущий пользователь в том виде, в каком он нужен экранам.
 *
 * Отличается от сетевого DTO: здесь нет токена и срока его жизни, зато есть
 * осмысленное имя поля [member] — «действующий член сообщества», по которому
 * приложение решает, показывать контент или экран ожидания (`BR-JOIN-4`,
 * `BR-PRIV-1`).
 */
data class CurrentUser(
    val id: String,
    val login: String,
    val displayName: String,
    val about: String = "",
    val avatarPath: String? = null,
    val member: Boolean,
    val communityId: String? = null,
)

/**
 * Файл аватара, готовый к отправке.
 *
 * Байты, а не `Uri`: ViewModel не должен знать про `ContentResolver`, иначе
 * его нельзя протестировать обычным unit-тестом. Экран читает файл и отдаёт
 * сюда готовые данные.
 */
class AvatarUpload(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
)

/**
 * Заявка на вступление глазами клиента.
 *
 * Числа [accepts], [totalVotes] и [required] — прогресс голосования: по ним
 * экран объясняет, сколько ещё нужно голосов (`BR-JOIN-5`, `BR-LIMIT-3`).
 */
data class JoinRequestSummary(
    val id: String,
    val applicantId: String,
    val applicantName: String,
    val about: String,
    val avatarPath: String?,
    val status: String,
    val createdAt: String,
    val accepts: Long,
    val totalVotes: Long,
    val required: Long,
    val votedByMe: Boolean,
) {
    /** Заявка ещё рассматривается. */
    val isPending: Boolean get() = status == "PENDING"

    /** Принята сообществом. */
    val isAccepted: Boolean get() = status == "ACCEPTED"

    /** Отклонена сообществом. */
    val isRejected: Boolean get() = status == "REJECTED"
}
