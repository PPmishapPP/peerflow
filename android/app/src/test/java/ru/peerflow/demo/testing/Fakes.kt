package ru.peerflow.demo.testing

import kotlinx.coroutines.CompletableDeferred
import ru.peerflow.demo.data.model.AvatarUpload
import ru.peerflow.demo.data.model.CurrentUser
import ru.peerflow.demo.data.model.JoinRequestSummary
import ru.peerflow.demo.data.model.MyVote
import ru.peerflow.demo.data.model.ProposalSections
import ru.peerflow.demo.data.model.ProposalSort
import ru.peerflow.demo.data.model.ProposalStage
import ru.peerflow.demo.data.model.ProposalSummary
import ru.peerflow.demo.data.repo.AccountRepository
import ru.peerflow.demo.data.repo.JoinRequestRepository
import ru.peerflow.demo.data.repo.ProposalRepository

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

/**
 * Предложения для тестов ViewModel.
 *
 * Как и остальные фейки: сценарий задаётся полями, а не подклассом на каждый
 * случай. Список хранится в памяти, чтобы проверить, что голос и создание
 * действительно меняют данные экрана.
 */
class FakeProposalRepository : ProposalRepository {

    var sections: ProposalSections = ProposalSections()

    /** Если задано — `list()` упадёт этим исключением. */
    var listError: Throwable? = null
    var getError: Throwable? = null
    var createError: Throwable? = null
    var voteError: Throwable? = null

    var createResult: ProposalSummary = sampleProposal()

    /** Если задано — `create()` сначала ждёт сигнала (проверка «запрос идёт»). */
    var createGate: CompletableDeferred<Unit>? = null

    /** Если задано — `vote()` вернёт именно это (например, уже принятое предложение). */
    var voteResult: ProposalSummary? = null

    var listCalls: Int = 0
    var getCalls: Int = 0
    var createCalls: Int = 0
    var voteCalls: Int = 0

    var lastSort: ProposalSort? = null
    var lastGetId: String? = null
    var lastCreateTitle: String? = null
    var lastCreateDescription: String? = null
    var lastCreateBudget: Long? = null
    var lastVoteId: String? = null
    var lastVoteInFavor: Boolean? = null

    override suspend fun list(sort: ProposalSort): ProposalSections {
        listCalls++
        lastSort = sort
        listError?.let { throw it }
        return sections
    }

    override suspend fun get(proposalId: String): ProposalSummary {
        getCalls++
        lastGetId = proposalId
        getError?.let { throw it }
        return sections.byId(proposalId) ?: sampleProposal(id = proposalId)
    }

    override suspend fun create(
        title: String,
        description: String,
        budget: Long,
    ): ProposalSummary {
        createCalls++
        lastCreateTitle = title
        lastCreateDescription = description
        lastCreateBudget = budget
        createGate?.await()
        createError?.let { throw it }
        return createResult
    }

    override suspend fun vote(proposalId: String, inFavor: Boolean): ProposalSummary {
        voteCalls++
        lastVoteId = proposalId
        lastVoteInFavor = inFavor
        voteError?.let { throw it }
        voteResult?.let { return it }
        val current = sections.byId(proposalId) ?: sampleProposal(id = proposalId)
        val updated = current.copy(
            votesFor = if (inFavor) current.votesFor + 1 else current.votesFor,
            votesAgainst = if (inFavor) current.votesAgainst else current.votesAgainst + 1,
            myVote = MyVote(inFavor = inFavor),
            popularity = current.popularity + 1,
        )
        sections = ProposalSections(
            inWork = sections.inWork.map { if (it.id == proposalId) updated else it },
            discussing = sections.discussing.map { if (it.id == proposalId) updated else it },
        )
        return updated
    }
}

fun sampleProposal(
    id: String = "33333333-3333-3333-3333-333333333333",
    title: String = "Аренда помещения для лекции",
    description: String = "Нужен зал на 30 человек на субботу",
    budget: Long = 12_000,
    stage: ProposalStage = ProposalStage.DISCUSSION,
    votesFor: Long = 2,
    votesAgainst: Long = 1,
    participants: Long = 4,
    popularity: Long = 5,
    votingClosed: Boolean = false,
    votingStatus: String = "Голосование идёт",
    myVote: MyVote? = null,
    authorName: String = "Вера",
) = ProposalSummary(
    id = id,
    title = title,
    description = description,
    authorId = "11111111-1111-1111-1111-111111111111",
    authorName = authorName,
    budget = budget,
    stage = stage,
    votesFor = votesFor,
    votesAgainst = votesAgainst,
    participants = participants,
    popularity = popularity,
    createdAt = "2026-11-20T10:00:00Z",
    decidedAt = null,
    votingDeadline = "2026-11-22T10:00:00Z",
    votingClosed = votingClosed,
    votingStatus = votingStatus,
    myVote = myVote,
)


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
