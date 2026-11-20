package ru.peerflow.demo.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Тело запроса на вход (BR-JOIN-1).
 */
@Serializable
data class LoginRequestDto(
    val login: String,
    val password: String,
)

/**
 * Тело запроса на регистрацию (BR-JOIN-1).
 *
 * Отправляется как часть `multipart/form-data` под именем `request`,
 * аватар — отдельной частью `avatar` (см. [PeerflowApi.register]).
 */
@Serializable
data class RegisterRequestDto(
    val login: String,
    val password: String,
    val displayName: String,
    val about: String = "",
)

/**
 * Ответ на успешный вход и регистрацию.
 */
@Serializable
data class AuthDto(
    val token: String,
    val expiresAt: String,
    val userId: String,
    val login: String,
    val displayName: String,
    val member: Boolean,
    val communityId: String? = null,
)

/**
 * Профиль текущего пользователя (`GET /api/auth/me`).
 */
@Serializable
data class ProfileDto(
    val id: String,
    val login: String,
    val displayName: String,
    val about: String = "",
    val avatarPath: String? = null,
    val member: Boolean,
    val communityId: String? = null,
)

/**
 * Тело заявки на вступление (BR-JOIN-1, BR-JOIN-4).
 */
@Serializable
data class SubmitJoinRequestDto(
    val about: String,
)

/**
 * Заявка на вступление в сообщество в ответах API.
 */
@Serializable
data class JoinRequestDto(
    val id: String,
    val applicantId: String,
    val applicantName: String,
    val about: String = "",
    val avatarPath: String? = null,
    val status: String,
    val createdAt: String,
    val decidedAt: String? = null,
    val accepts: Long = 0,
    val votes: Long = 0,
    val required: Long = 0,
    val votedByMe: Boolean = false,
)

/**
 * Голос по заявке на вступление (BR-JOIN-4).
 */
@Serializable
data class JoinVoteDto(
    val accept: Boolean,
)

/**
 * Ошибка в формате RFC 9457 (`application/problem+json`).
 *
 * Сервер заполняет `title` и `detail` по-русски (см. `ApiExceptionHandler`),
 * поэтому клиенту достаточно показать `detail`.
 */
@Serializable
data class ProblemDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
)
