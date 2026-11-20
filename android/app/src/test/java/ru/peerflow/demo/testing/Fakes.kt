package ru.peerflow.demo.testing

import kotlinx.coroutines.CompletableDeferred
import ru.peerflow.demo.data.model.AvatarUpload
import ru.peerflow.demo.data.model.CurrentUser
import ru.peerflow.demo.data.model.JoinRequestSummary
import ru.peerflow.demo.data.repo.AccountRepository
import ru.peerflow.demo.data.repo.JoinRequestRepository

/**
 * Аккаунт для тестов ViewModel.
 *
 * Ошибку задаём полем, а не подклассом на каждый сценарий: тест читается как
 * «дано: сервер ответил 409», без лишних классов.
 */
class FakeAccountRepository : AccountRepository {

    var hasTokenValue: Boolean = false
    var currentUser: CurrentUser = sampleUser()

    /** Если задано — `login()` упадёт этим исключением. */
    var loginError: Throwable? = null
    var registerError: Throwable? = null
    var meError: Throwable? = null

    /**
     * Если задано — `login()` сначала ждёт сигнала.
     *
     * Нужно, чтобы поймать состояние «запрос ещё идёт»: с обычным фейком
     * корутина завершается мгновенно и повторное нажатие не проверить.
     */
    var loginGate: CompletableDeferred<Unit>? = null

    var loginCalls: Int = 0
    var registerCalls: Int = 0
    var meCalls: Int = 0
    var logoutCalls: Int = 0

    var lastLogin: String? = null
    var lastPassword: String? = null
    var lastDisplayName: String? = null
    var lastAbout: String? = null
    var lastAvatar: AvatarUpload? = null

    override suspend fun login(login: String, password: String): CurrentUser {
        loginCalls++
        lastLogin = login
        lastPassword = password
        loginGate?.await()
        loginError?.let { throw it }
        return currentUser
    }

    override suspend fun register(
        login: String,
        password: String,
        displayName: String,
        about: String,
        avatar: AvatarUpload?,
    ): CurrentUser {
        registerCalls++
        lastLogin = login
        lastPassword = password
        lastDisplayName = displayName
        lastAbout = about
        lastAvatar = avatar
        registerError?.let { throw it }
        return currentUser
    }

    override suspend fun me(): CurrentUser {
        meCalls++
        meError?.let { throw it }
        return currentUser
    }

    override fun logout() {
        logoutCalls++
        hasTokenValue = false
    }

    override fun hasToken(): Boolean = hasTokenValue
}

/** Заявки на вступление для тестов ViewModel. */
class FakeJoinRequestRepository : JoinRequestRepository {

    /** Что вернёт `mine()`; `null` — заявки нет. */
    var mineResult: JoinRequestSummary? = null
    var mineError: Throwable? = null

    var submitResult: JoinRequestSummary = sampleRequest(status = PENDING)
    var submitError: Throwable? = null

    var mineCalls: Int = 0
    var submitCalls: Int = 0
    var lastSubmitAbout: String? = null
    var lastSubmitAvatar: AvatarUpload? = null

    override suspend fun submit(about: String, avatar: AvatarUpload?): JoinRequestSummary {
        submitCalls++
        lastSubmitAbout = about
        lastSubmitAvatar = avatar
        submitError?.let { throw it }
        return submitResult
    }

    override suspend fun mine(): JoinRequestSummary? {
        mineCalls++
        mineError?.let { throw it }
        return mineResult
    }

    override suspend fun pending(): List<JoinRequestSummary> = emptyList()

    override suspend fun vote(requestId: String, accept: Boolean): JoinRequestSummary =
        sampleRequest(status = if (accept) ACCEPTED else REJECTED)
}

const val PENDING = "PENDING"
const val ACCEPTED = "ACCEPTED"
const val REJECTED = "REJECTED"

fun sampleUser(
    id: String = "11111111-1111-1111-1111-111111111111",
    login: String = "anna",
    displayName: String = "Анна",
    about: String = "",
    member: Boolean = false,
) = CurrentUser(
    id = id,
    login = login,
    displayName = displayName,
    about = about,
    avatarPath = null,
    member = member,
    communityId = null,
)

fun sampleRequest(
    id: String = "22222222-2222-2222-2222-222222222222",
    status: String = PENDING,
    about: String = "Хочу вступить в сообщество",
    accepts: Long = 1,
    totalVotes: Long = 2,
    required: Long = 4,
    votedByMe: Boolean = false,
) = JoinRequestSummary(
    id = id,
    applicantId = "11111111-1111-1111-1111-111111111111",
    applicantName = "Анна",
    about = about,
    avatarPath = null,
    status = status,
    createdAt = "2026-11-20T10:00:00Z",
    accepts = accepts,
    totalVotes = totalVotes,
    required = required,
    votedByMe = votedByMe,
)
