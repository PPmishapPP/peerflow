package ru.peerflow.demo.testing

import kotlinx.coroutines.CompletableDeferred
import ru.peerflow.demo.data.model.AvatarUpload
import ru.peerflow.demo.data.model.Candidate
import ru.peerflow.demo.data.model.CurrentUser
import ru.peerflow.demo.data.model.Delegation
import ru.peerflow.demo.data.model.DiscussionMessage
import ru.peerflow.demo.data.model.DiscussionThread
import ru.peerflow.demo.data.model.JoinRequestSummary
import ru.peerflow.demo.data.model.Leader
import ru.peerflow.demo.data.model.LegitimacyBoard
import ru.peerflow.demo.data.model.MessageSort
import ru.peerflow.demo.data.model.MyLegitimacy
import ru.peerflow.demo.data.model.MyVote
import ru.peerflow.demo.data.model.ProposalSections
import ru.peerflow.demo.data.model.ProposalSort
import ru.peerflow.demo.data.model.ProposalStage
import ru.peerflow.demo.data.model.ProposalSummary
import ru.peerflow.demo.data.model.ReactionKind
import ru.peerflow.demo.data.repo.AccountRepository
import ru.peerflow.demo.data.repo.DiscussionRepository
import ru.peerflow.demo.data.repo.JoinRequestRepository
import ru.peerflow.demo.data.repo.LegitimacyRepository
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

    /** Очередь для действующего члена (`pending()`). */
    var pendingResult: List<JoinRequestSummary> = emptyList()
    var pendingError: Throwable? = null

    /** Если задано — `vote()` вернёт именно это (например, уже принятую заявку). */
    var voteResult: JoinRequestSummary? = null
    var voteError: Throwable? = null

    /**
     * Если задано — `vote()` сначала ждёт сигнала.
     *
     * Нужно, чтобы поймать состояние «голос ещё отправляется»: с обычным фейком
     * корутина завершается мгновенно и блокировку кнопок не проверить.
     */
    var voteGate: CompletableDeferred<Unit>? = null

    var mineCalls: Int = 0
    var submitCalls: Int = 0
    var pendingCalls: Int = 0
    var voteCalls: Int = 0

    var lastSubmitAbout: String? = null
    var lastSubmitAvatar: AvatarUpload? = null
    var lastVoteId: String? = null
    var lastVoteAccept: Boolean? = null

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

    override suspend fun pending(): List<JoinRequestSummary> {
        pendingCalls++
        pendingError?.let { throw it }
        return pendingResult
    }

    override suspend fun vote(requestId: String, accept: Boolean): JoinRequestSummary {
        voteCalls++
        lastVoteId = requestId
        lastVoteAccept = accept
        voteGate?.await()
        voteError?.let { throw it }
        voteResult?.let { return it }
        // Заявка та же, что была в очереди: меняется только её судьба.
        val current = pendingResult.firstOrNull { it.id == requestId }
        return current?.copy(
            status = if (accept) ACCEPTED else REJECTED,
            votedByMe = true,
        ) ?: sampleRequest(id = requestId, status = if (accept) ACCEPTED else REJECTED)
    }
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

/**
 * Дискуссия для тестов ViewModel.
 *
 * Сценарий задаётся полями, а не подклассом на каждый случай. Ветка хранится в
 * памяти: видно, что сообщение и реакция действительно меняют данные экрана.
 */
class FakeDiscussionRepository : DiscussionRepository {

    var threadResult: DiscussionThread = DiscussionThread()

    var threadError: Throwable? = null
    var postError: Throwable? = null
    var reactError: Throwable? = null

    /** Если задано — `post()` сначала ждёт сигнала (проверка «запрос идёт»). */
    var postGate: CompletableDeferred<Unit>? = null

    var threadCalls: Int = 0
    var postCalls: Int = 0
    var reactCalls: Int = 0

    var lastSort: MessageSort? = null
    var lastThreadProposalId: String? = null
    var lastPostProposalId: String? = null
    var lastPostBody: String? = null
    var lastPostParentId: String? = null
    var lastReactId: String? = null
    var lastReactKind: ReactionKind? = null

    override suspend fun thread(proposalId: String, sort: MessageSort): DiscussionThread {
        threadCalls++
        lastThreadProposalId = proposalId
        lastSort = sort
        threadError?.let { throw it }
        return threadResult
    }

    override suspend fun post(
        proposalId: String,
        body: String,
        parentId: String?,
    ): DiscussionThread {
        postCalls++
        lastPostProposalId = proposalId
        lastPostBody = body
        lastPostParentId = parentId
        postGate?.await()
        postError?.let { throw it }
        val created = sampleMessage(id = "m-posted", body = body, replies = emptyList())
        threadResult = threadResult.copy(
            messages = threadResult.messages + created,
        )
        return threadResult
    }

    override suspend fun react(messageId: String, kind: ReactionKind): DiscussionThread {
        reactCalls++
        lastReactId = messageId
        lastReactKind = kind
        reactError?.let { throw it }
        threadResult = DiscussionThread(
            messages = threadResult.messages.map { it.withReaction(messageId, kind) },
            hidden = threadResult.hidden,
        )
        return threadResult
    }

    /** Проставляет реакцию сообщению и всем ответам: ветка плоская по смыслу. */
    private fun DiscussionMessage.withReaction(id: String, kind: ReactionKind): DiscussionMessage {
        val updated = if (this.id == id) {
            copy(
                plus = if (kind == ReactionKind.PLUS) plus + 1 else plus,
                minus = if (kind == ReactionKind.MINUS) minus + 1 else minus,
                spam = if (kind == ReactionKind.SPAM) spam + 1 else spam,
                myReaction = kind,
            )
        } else {
            this
        }
        return updated.copy(replies = replies.map { it.withReaction(id, kind) })
    }
}

fun sampleMessage(
    id: String = "m1",
    authorName: String = "Борис",
    body: String = "Предлагаю аренду",
    plus: Long = 0,
    minus: Long = 0,
    spam: Long = 0,
    myReaction: ReactionKind? = null,
    replies: List<DiscussionMessage> = emptyList(),
) = DiscussionMessage(
    id = id,
    authorId = "22222222-2222-2222-2222-222222222222",
    authorName = authorName,
    body = body,
    createdAt = "2026-11-20T11:00:00Z",
    plus = plus,
    minus = minus,
    spam = spam,
    myReaction = myReaction,
    replies = replies,
)

/**
 * Легитимность для тестов ViewModel.
 *
 * Доска хранится в памяти, как и в остальных фейках: видно, что передача и
 * отзыв действительно меняют состояние экрана. Порог удержания и выбор лидера
 * здесь не воспроизводятся — это правило сервера, и фейк должен лишь
 * возвращать то, что он бы прислал.
 */
class FakeLegitimacyRepository : LegitimacyRepository {

    var boardResult: LegitimacyBoard = LegitimacyBoard(proposalId = "p1")

    var boardError: Throwable? = null
    var nominateError: Throwable? = null
    var delegateError: Throwable? = null
    var revokeError: Throwable? = null
    var myError: Throwable? = null

    /** Если задано — `delegate()` сначала ждёт сигнала (проверка «идёт запрос»). */
    var delegateGate: CompletableDeferred<Unit>? = null

    var myResult: MyLegitimacy = MyLegitimacy()

    var boardCalls: Int = 0
    var nominateCalls: Int = 0
    var delegateCalls: Int = 0
    var revokeCalls: Int = 0
    var myCalls: Int = 0

    var lastProposalId: String? = null
    var lastDelegateCandidateId: String? = null

    override suspend fun board(proposalId: String): LegitimacyBoard {
        boardCalls++
        lastProposalId = proposalId
        boardError?.let { throw it }
        return boardResult
    }

    override suspend fun nominate(proposalId: String): LegitimacyBoard {
        nominateCalls++
        lastProposalId = proposalId
        nominateError?.let { throw it }
        val me = Candidate(
            memberId = "11111111-1111-1111-1111-111111111111",
            displayName = "Анна",
            support = 0,
            reputation = 0,
            me = true,
        )
        boardResult = boardResult.copy(
            candidates = boardResult.candidates + me,
        )
        return boardResult
    }

    override suspend fun delegate(proposalId: String, candidateId: String): LegitimacyBoard {
        delegateCalls++
        lastProposalId = proposalId
        lastDelegateCandidateId = candidateId
        delegateGate?.await()
        delegateError?.let { throw it }
        // Передача заменяет получателя, а не добавляет второй голос:
        // одна единица доверия на человека (BR-LEGIT-1).
        val previous = boardResult.myDelegation
        val candidates = boardResult.candidates.map { candidate ->
            candidate.copy(
                support = when (candidate.memberId) {
                    candidateId -> candidate.support + 1
                    previous -> (candidate.support - 1).coerceAtLeast(0)
                    else -> candidate.support
                },
            )
        }
        boardResult = boardResult.copy(
            candidates = candidates,
            myDelegation = candidateId,
            total = candidates.sumOf { it.support },
        )
        return boardResult
    }

    override suspend fun revoke(proposalId: String): LegitimacyBoard {
        revokeCalls++
        lastProposalId = proposalId
        revokeError?.let { throw it }
        val previous = boardResult.myDelegation
        val candidates = boardResult.candidates.map { candidate ->
            if (candidate.memberId == previous) {
                candidate.copy(support = (candidate.support - 1).coerceAtLeast(0))
            } else {
                candidate
            }
        }
        boardResult = boardResult.copy(
            candidates = candidates,
            myDelegation = null,
            total = candidates.sumOf { it.support },
        )
        return boardResult
    }

    override suspend fun myLegitimacy(): MyLegitimacy {
        myCalls++
        myError?.let { throw it }
        return myResult
    }
}

fun sampleCandidate(
    memberId: String = "22222222-2222-2222-2222-222222222222",
    displayName: String = "Борис",
    support: Long = 0,
    reputation: Long = 0,
    me: Boolean = false,
) = Candidate(
    memberId = memberId,
    displayName = displayName,
    support = support,
    reputation = reputation,
    me = me,
)

fun sampleLeader(
    memberId: String = "22222222-2222-2222-2222-222222222222",
    displayName: String = "Борис",
    support: Long = 7,
    initialSupport: Long = 10,
    requiredSupport: Long = 7,
    active: Boolean = true,
    endReason: String? = null,
) = Leader(
    memberId = memberId,
    displayName = displayName,
    support = support,
    initialSupport = initialSupport,
    requiredSupport = requiredSupport,
    active = active,
    endReason = endReason,
)

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
    applicantName: String = "Анна",
    status: String = PENDING,
    about: String = "Хочу вступить в сообщество",
    accepts: Long = 1,
    totalVotes: Long = 2,
    required: Long = 4,
    votedByMe: Boolean = false,
) = JoinRequestSummary(
    id = id,
    applicantId = "11111111-1111-1111-1111-111111111111",
    applicantName = applicantName,
    about = about,
    avatarPath = null,
    status = status,
    createdAt = "2026-11-20T10:00:00Z",
    accepts = accepts,
    totalVotes = totalVotes,
    required = required,
    votedByMe = votedByMe,
)
