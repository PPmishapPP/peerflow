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
 * Новое предложение (`BR-LIFE-1`, `BR-BUDGET-2`).
 *
 * Бюджет обязателен: без него сервер ответит `400`.
 */
@Serializable
data class CreateProposalDto(
    val title: String,
    val description: String,
    val budget: Long,
)

/**
 * Голос «делаем / не делаем» (`BR-VOTE-4`).
 */
@Serializable
data class DecisionVoteDto(
    val inFavor: Boolean,
)

/** Голос текущего пользователя в ответе сервера (`BR-VOTE-13`). */
@Serializable
data class MyVoteDto(
    val inFavor: Boolean,
    val castAt: String? = null,
)

/**
 * Предложение в ответе API (`BR-RANK-2`, `BR-VOTE-9`).
 *
 * Посчитанные сервером [participants] и [popularity] клиент не пересчитывает:
 * метрика популярности — правило сообщества, и её формула живёт в одном месте.
 */
@Serializable
data class ProposalDto(
    val id: String,
    val title: String,
    val description: String = "",
    val authorId: String,
    val authorName: String,
    val budget: Long,
    val taskBudget: Long? = null,
    val stage: String,
    val votesFor: Long = 0,
    val votesAgainst: Long = 0,
    val participants: Long = 0,
    val popularity: Long = 0,
    val createdAt: String,
    val decidedAt: String? = null,
    val votingDeadline: String? = null,
    val votingClosed: Boolean = false,
    val votingStatus: String = "",
    val myVote: MyVoteDto? = null,
)

/**
 * Список предложений двумя секциями (`BR-RANK-5`).
 *
 * Секции приходят готовыми: их состав — правило сообщества, а не оформление
 * экрана.
 */
@Serializable
data class ProposalListDto(
    val inWork: List<ProposalDto> = emptyList(),
    val discussing: List<ProposalDto> = emptyList(),
)

/**
 * Сообщение дискуссии в ответе API (`BR-DISC-1`–`BR-DISC-3`, `BR-SPAM-1`).
 *
 * Ответы приходят вложенными в [replies], а не плоским списком с
 * `parentId`: порядок и вложенность — правило сообщества (`BR-DISC-6`), и
 * клиент показывает дерево как есть, не восстанавливая его сам.
 *
 * Счётчики реакций считает сервер: формула «плюсы поднимают, минусы опускают»
 * живёт в одном месте (`BR-DISC-4`).
 */
@Serializable
data class MessageDto(
    val id: String,
    val authorId: String,
    val authorName: String,
    val body: String,
    val createdAt: String,
    val plus: Long = 0,
    val minus: Long = 0,
    val spam: Long = 0,
    val myReaction: String? = null,
    val replies: List<MessageDto> = emptyList(),
)

/**
 * Ветка обсуждения целиком (`BR-DISC-1`–`BR-DISC-6`, `BR-SPAM-1`).
 *
 * [hidden] — сколько сообщений скрыто метками «спам». Без этого числа человек
 * видел бы пустоту и не знал, что обсуждение было и его скрыли.
 */
@Serializable
data class ThreadDto(
    val messages: List<MessageDto> = emptyList(),
    val hidden: Long = 0,
)

/** Новое сообщение или ответ на сообщение (`BR-DISC-1`, `BR-DISC-3`). */
@Serializable
data class PostMessageDto(
    val body: String,
    val parentId: String? = null,
)

/** Реакция на сообщение (`BR-DISC-2`). */
@Serializable
data class ReactionDto(
    val kind: String,
)

/**
 * Кандидат в лидеры в ответе API (`BR-LEADER-1`–`BR-LEADER-4`).
 *
 * Список приходит в порядке силы: тай-брейкеры — правило сообщества, и клиент
 * его не пересчитывает.
 */
@Serializable
data class CandidateDto(
    val memberId: String,
    val displayName: String,
    val support: Long = 0,
    val reputation: Long = 0,
    val price: Long = 0,
    val me: Boolean = false,
)

/**
 * Лидер задачи в ответе API (`BR-LEGIT-4`, `BR-LEADER-5`, `BR-LEADER-6`).
 *
 * [requiredSupport] присылает сервер: порог удержания — правило, а не
 * оформление.
 */
@Serializable
data class LeaderDto(
    val memberId: String,
    val displayName: String,
    val support: Long = 0,
    val initialSupport: Long = 0,
    val requiredSupport: Long = 0,
    val taskBudget: Long = 0,
    val startedAt: String? = null,
    val active: Boolean = true,
    val endReason: String? = null,
)

/** Выдвижение кандидата с ценой (`BR-LEADER-1`, `BR-BUDGET-8`). */
@Serializable
data class NominateDto(
    val price: Long,
)

/** Легитимность и лидер задачи (`BR-LEGIT-2`, `BR-LEGIT-3`, `BR-LEADER-3`). */
@Serializable
data class LegitimacyBoardDto(
    val proposalId: String,
    val candidates: List<CandidateDto> = emptyList(),
    val leader: LeaderDto? = null,
    val myDelegation: String? = null,
    val total: Long = 0,
    val members: Long = 0,
)

/** Передача доверия по одной задаче (`BR-LEGIT-3`). */
@Serializable
data class DelegationDto(
    val proposalId: String,
    val proposalTitle: String,
    val candidateId: String? = null,
    val candidateName: String? = null,
)

/** Репутация и передачи доверия текущего человека (`BR-REP-2`). */
@Serializable
data class MyLegitimacyDto(
    val reputation: Long = 0,
    val delegations: List<DelegationDto> = emptyList(),
)

/**
 * Файл отчёта в ответе API (`BR-LIFE-7`).
 *
 * Путь на диске сервер не отдаёт: клиенту нужно имя файла и адрес скачивания,
 * а не устройство хранилища.
 */
@Serializable
data class AttachmentDto(
    val id: String,
    val fileName: String,
    val contentType: String = "",
    val sizeBytes: Long = 0,
    val authorId: String,
    val authorName: String,
    val createdAt: String,
    val url: String = "",
)

/** Трата бюджета задачи (`BR-BUDGET-4`). */
@Serializable
data class BudgetExpenseDto(
    val id: String,
    val memberId: String,
    val memberName: String,
    val amount: Long,
    val note: String? = null,
    val createdAt: String,
)

/**
 * Отчёт задачи (`BR-LIFE-7`, `BR-BUDGET-4`, `BR-BUDGET-5`).
 *
 * Остаток [remaining] считает сервер: «бюджет минус траты» — правило
 * сообщества, и повторять его на клиенте значило бы однажды получить два
 * разных остатка на двух экранах.
 */
@Serializable
data class ReportDto(
    val proposalId: String,
    val files: List<AttachmentDto> = emptyList(),
    val expenses: List<BudgetExpenseDto> = emptyList(),
    val budget: Long = 0,
    val spent: Long = 0,
    val remaining: Long = 0,
)

/** Запись траты бюджета (`BR-BUDGET-4`). */
@Serializable
data class SpendRequestDto(
    val amount: Long,
    val note: String? = null,
)

/** Свой ответ «выполнено / не выполнено» (`BR-VOTE-5`). */
@Serializable
data class MyClosureVoteDto(
    val completed: Boolean,
    val castAt: String? = null,
)

/** Ответ на вопрос «выполнена ли задача» (`BR-VOTE-5`, `BR-OVER-9`). */
@Serializable
data class ClosureVoteDto(
    val completed: Boolean,
)

/**
 * Голосование «выполнено / не выполнено» (`BR-LIFE-8`, `BR-LIFE-9`).
 *
 * [reputationAwarded] заполняется после закрытия: человек должен видеть, что
 * легитимность лидера не пропала, а стала репутацией (`BR-REP-1`).
 */
@Serializable
data class ClosureStateDto(
    val proposalId: String,
    val stage: String,
    val open: Boolean = false,
    val votesFor: Long = 0,
    val votesAgainst: Long = 0,
    val members: Long = 0,
    val deadline: String? = null,
    val status: String = "",
    val myVote: MyClosureVoteDto? = null,
    val leaderId: String? = null,
    val leaderName: String? = null,
    val reputationAwarded: Long = 0,
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
