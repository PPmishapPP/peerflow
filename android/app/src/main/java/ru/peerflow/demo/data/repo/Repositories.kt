package ru.peerflow.demo.data.repo

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.peerflow.demo.data.api.apiCall
import ru.peerflow.demo.data.api.AttachmentDto
import ru.peerflow.demo.data.api.BudgetExpenseDto
import ru.peerflow.demo.data.api.CandidateDto
import ru.peerflow.demo.data.api.ClosureStateDto
import ru.peerflow.demo.data.api.ClosureVoteDto
import ru.peerflow.demo.data.api.CreateProposalDto
import ru.peerflow.demo.data.api.DecisionVoteDto
import ru.peerflow.demo.data.api.DelegationDto
import ru.peerflow.demo.data.api.JoinVoteDto
import ru.peerflow.demo.data.api.LeaderDto
import ru.peerflow.demo.data.api.LegitimacyBoardDto
import ru.peerflow.demo.data.api.LoginRequestDto
import ru.peerflow.demo.data.api.MessageDto
import ru.peerflow.demo.data.api.MyLegitimacyDto
import ru.peerflow.demo.data.api.MyVoteDto
import ru.peerflow.demo.data.api.NominateDto
import ru.peerflow.demo.data.api.PeerflowApi
import ru.peerflow.demo.data.api.PostMessageDto
import ru.peerflow.demo.data.api.ProposalDto
import ru.peerflow.demo.data.api.ProposalListDto
import ru.peerflow.demo.data.api.ReactionDto
import ru.peerflow.demo.data.api.RegisterRequestDto
import ru.peerflow.demo.data.api.ReportDto
import ru.peerflow.demo.data.api.SpendRequestDto
import ru.peerflow.demo.data.api.SubmitJoinRequestDto
import ru.peerflow.demo.data.api.ThreadDto
import ru.peerflow.demo.data.api.TokenStore
import ru.peerflow.demo.data.model.Attachment
import ru.peerflow.demo.data.model.AvatarUpload
import ru.peerflow.demo.data.model.BudgetExpense
import ru.peerflow.demo.data.model.Candidate
import ru.peerflow.demo.data.model.ClosureState
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
import ru.peerflow.demo.data.model.ReactionKind
import ru.peerflow.demo.data.model.Report
import ru.peerflow.demo.data.model.ReportUpload
import ru.peerflow.demo.data.model.ProposalSections
import ru.peerflow.demo.data.model.ProposalSort
import ru.peerflow.demo.data.model.ProposalStage
import ru.peerflow.demo.data.model.ProposalSummary
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Аккаунт: вход, регистрация, кто вошёл.
 *
 * Интерфейс, а не класс: ViewModel-тесты подставляют свою реализацию и не
 * поднимают ни Retrofit, ни сеть.
 */
interface AccountRepository {

    /** `BR-JOIN-1`: вход. Токен сохраняется внутри. */
    suspend fun login(login: String, password: String): CurrentUser

    /** `BR-JOIN-1`, `BR-JOIN-4`: регистрация. Токен сохраняется внутри. */
    suspend fun register(
        login: String,
        password: String,
        displayName: String,
        about: String,
        avatar: AvatarUpload? = null,
    ): CurrentUser

    /** Профиль по сохранённому токену. */
    suspend fun me(): CurrentUser

    /** Выход: забыть токен. */
    fun logout()

    fun hasToken(): Boolean
}

/**
 * Заявки на вступление.
 *
 * `BR-JOIN-4` — подать, `BR-JOIN-5` — голосовать, `BR-VOTE-1` — видеть
 * очередь заявок может только член сообщества (сервер отвечает `403`).
 */
interface JoinRequestRepository {

    suspend fun submit(about: String, avatar: AvatarUpload? = null): JoinRequestSummary

    /** Своя заявка; `null`, если её нет (сервер отвечает `204`). */
    suspend fun mine(): JoinRequestSummary?

    suspend fun pending(): List<JoinRequestSummary>

    suspend fun vote(requestId: String, accept: Boolean): JoinRequestSummary
}

@Singleton
class ApiAccountRepository @Inject constructor(
    private val api: PeerflowApi,
    private val tokenStore: TokenStore,
    private val json: Json,
) : AccountRepository {

    override suspend fun login(login: String, password: String): CurrentUser {
        val auth = apiCall(json) { api.login(LoginRequestDto(login = login, password = password)) }
        tokenStore.save(auth.token)
        return CurrentUser(
            id = auth.userId,
            login = auth.login,
            displayName = auth.displayName,
            member = auth.member,
            communityId = auth.communityId,
        )
    }

    override suspend fun register(
        login: String,
        password: String,
        displayName: String,
        about: String,
        avatar: AvatarUpload?,
    ): CurrentUser {
        val request = RegisterRequestDto(
            login = login,
            password = password,
            displayName = displayName,
            about = about,
        )
        val auth = apiCall(json) {
            api.register(
                request = request.asJsonPart(json),
                avatar = avatar?.asMultipart(),
            )
        }
        tokenStore.save(auth.token)
        return CurrentUser(
            id = auth.userId,
            login = auth.login,
            displayName = auth.displayName,
            about = about,
            member = auth.member,
            communityId = auth.communityId,
        )
    }

    override suspend fun me(): CurrentUser {
        val profile = apiCall(json) { api.me() }
        return CurrentUser(
            id = profile.id,
            login = profile.login,
            displayName = profile.displayName,
            about = profile.about,
            avatarPath = profile.avatarPath,
            member = profile.member,
            communityId = profile.communityId,
        )
    }

    override fun logout() = tokenStore.clear()

    override fun hasToken(): Boolean = tokenStore.hasToken()
}

@Singleton
class ApiJoinRequestRepository @Inject constructor(
    private val api: PeerflowApi,
    private val json: Json,
) : JoinRequestRepository {

    override suspend fun submit(about: String, avatar: AvatarUpload?): JoinRequestSummary {
        val request = SubmitJoinRequestDto(about = about)
        val created = apiCall(json) {
            api.submitJoinRequest(
                request = request.asJsonPart(json),
                avatar = avatar?.asMultipart(),
            )
        }
        return created.toSummary()
    }

    override suspend fun mine(): JoinRequestSummary? {
        val response = apiCall(json) { api.myJoinRequest() }
        if (response.code() == 204) {
            // `204` — заявки нет. Это норма, а не ошибка.
            response.errorBody()?.close()
            return null
        }
        if (response.isSuccessful) {
            return response.body()?.toSummary()
        }
        // Тело ошибки ещё не прочитано — прогоняем ответ через общий
        // переводчик, чтобы сообщение взялось из `ProblemDetail`.
        throw apiCall(json) { throw retrofit2.HttpException(response) }
    }

    override suspend fun pending(): List<JoinRequestSummary> =
        apiCall(json) { api.pendingJoinRequests() }.map { it.toSummary() }

    override suspend fun vote(requestId: String, accept: Boolean): JoinRequestSummary =
        apiCall(json) { api.vote(requestId, JoinVoteDto(accept = accept)) }.toSummary()
}

/**
 * Предложения сообщества: список, карточка, создание, голосование
 * (`BR-OVER-2`, `BR-LIFE-1`, `BR-RANK-5`, `BR-VOTE-4`).
 *
 * Интерфейс, а не класс: ViewModel-тесты подставляют фейк и не поднимают
 * Retrofit.
 */
interface ProposalRepository {

    /** Список двумя секциями, отсортированный на сервере (`BR-RANK-4`). */
    suspend fun list(sort: ProposalSort): ProposalSections

    /** Одно предложение со свежими голосами. */
    suspend fun get(proposalId: String): ProposalSummary

    /** `BR-OVER-2`, `BR-LIFE-1`: внести предложение с бюджетом. */
    suspend fun create(title: String, description: String, budget: Long): ProposalSummary

    /** `BR-VOTE-4`, `BR-VOTE-13`: проголосовать или поменять голос. */
    suspend fun vote(proposalId: String, inFavor: Boolean): ProposalSummary
}

@Singleton
class ApiProposalRepository @Inject constructor(
    private val api: PeerflowApi,
    private val json: Json,
) : ProposalRepository {

    override suspend fun list(sort: ProposalSort): ProposalSections =
        apiCall(json) { api.proposals(sort.query) }.toSections()

    override suspend fun get(proposalId: String): ProposalSummary =
        apiCall(json) { api.proposal(proposalId) }.toSummary()

    override suspend fun create(
        title: String,
        description: String,
        budget: Long,
    ): ProposalSummary = apiCall(json) {
        api.createProposal(
            CreateProposalDto(
                title = title,
                description = description,
                budget = budget,
            ),
        )
    }.toSummary()

    override suspend fun vote(proposalId: String, inFavor: Boolean): ProposalSummary =
        apiCall(json) {
            api.voteOnProposal(proposalId, DecisionVoteDto(inFavor = inFavor))
        }.toSummary()
}

/**
 * Дискуссия под предложением (`BR-DISC-1`–`BR-DISC-6`, `BR-SPAM-1`).
 *
 * Интерфейс, а не класс: ViewModel-тесты подставляют фейк и не поднимают
 * Retrofit.
 */
interface DiscussionRepository {

    /** Ветка обсуждения в выбранном порядке (`BR-DISC-4`, `BR-DISC-5`). */
    suspend fun thread(proposalId: String, sort: MessageSort): DiscussionThread

    /**
     * `BR-DISC-1`, `BR-DISC-3`: написать сообщение или ответ.
     *
     * @param parentId сообщение, на которое отвечают; `null` — корневое
     */
    suspend fun post(
        proposalId: String,
        body: String,
        parentId: String? = null,
    ): DiscussionThread

    /** `BR-DISC-2`: поставить или сменить свою реакцию. */
    suspend fun react(messageId: String, kind: ReactionKind): DiscussionThread
}

@Singleton
class ApiDiscussionRepository @Inject constructor(
    private val api: PeerflowApi,
    private val json: Json,
) : DiscussionRepository {

    override suspend fun thread(proposalId: String, sort: MessageSort): DiscussionThread =
        apiCall(json) { api.messages(proposalId, sort.query) }.toThread()

    override suspend fun post(
        proposalId: String,
        body: String,
        parentId: String?,
    ): DiscussionThread = apiCall(json) {
        api.postMessage(proposalId, PostMessageDto(body = body, parentId = parentId))
    }.toThread()

    override suspend fun react(messageId: String, kind: ReactionKind): DiscussionThread =
        apiCall(json) {
            api.react(messageId, ReactionDto(kind = kind.server))
        }.toThread()
}

/**
 * Легитимность и лидер задачи (`BR-LEGIT-2`, `BR-LEGIT-3`, `BR-LEGIT-5`,
 * `BR-LEADER-1`, `BR-LEADER-5`).
 *
 * Интерфейс, а не класс: ViewModel-тесты подставляют фейк и не поднимают
 * Retrofit.
 *
 * После каждого действия сервер возвращает доску целиком — так поддержка,
 * лидер и «кому я передал доверие» не расходятся между собой.
 */
interface LegitimacyRepository {

    /** Кандидаты, лидер и текущая передача доверия (`BR-LEGIT-3`). */
    suspend fun board(proposalId: String): LegitimacyBoard

    /** `BR-LEADER-1`, `BR-BUDGET-8`: выдвинуть себя кандидатом, назвав цену. */
    suspend fun nominate(proposalId: String, price: Long): LegitimacyBoard

    /** `BR-LEGIT-2`: передать свою легитимность кандидату. */
    suspend fun delegate(proposalId: String, candidateId: String): LegitimacyBoard

    /** `BR-LEGIT-5`: забрать свою легитимность себе. */
    suspend fun revoke(proposalId: String): LegitimacyBoard

    /** `BR-LEGIT-3`, `BR-REP-2`: передачи доверия и репутация в профиле. */
    suspend fun myLegitimacy(): MyLegitimacy
}

@Singleton
class ApiLegitimacyRepository @Inject constructor(
    private val api: PeerflowApi,
    private val json: Json,
) : LegitimacyRepository {

    override suspend fun board(proposalId: String): LegitimacyBoard =
        apiCall(json) { api.legitimacy(proposalId) }.toBoard()

    override suspend fun nominate(proposalId: String, price: Long): LegitimacyBoard =
        apiCall(json) { api.nominate(proposalId, NominateDto(price)) }.toBoard()

    override suspend fun delegate(proposalId: String, candidateId: String): LegitimacyBoard =
        apiCall(json) { api.delegate(proposalId, candidateId) }.toBoard()

    override suspend fun revoke(proposalId: String): LegitimacyBoard =
        apiCall(json) { api.revoke(proposalId) }.toBoard()

    override suspend fun myLegitimacy(): MyLegitimacy =
        apiCall(json) { api.myLegitimacy() }.toModel()
}

/**
 * Отчёты лидера, файлы, траты бюджета и закрытие задачи (`BR-LIFE-7`–
 * `BR-LIFE-9`, `BR-BUDGET-4`, `BR-VOTE-5`).
 *
 * Интерфейс, а не класс: ViewModel-тесты подставляют фейк и не поднимают
 * Retrofit.
 *
 * Отчёт и закрытие живут в одном репозитории, потому что это одна поверхность
 * — экран задачи в работе: лидер отчитывается, тратит бюджет и запускает
 * закрытие, сообщество голосует. Разделение дало бы два запроса на один
 * экран и два момента времени в одном заголовке.
 */
interface ReportRepository {

    /** Файлы, траты и остаток бюджета (`BR-LIFE-7`, `BR-BUDGET-5`). */
    suspend fun report(proposalId: String): Report

    /**
     * `BR-LIFE-7`: опубликовать отчёт — текст, файл или оба.
     *
     * @param text текст отчёта; пустой, если есть файл
     * @param file приложенный файл; `null`, если только текст
     */
    suspend fun post(proposalId: String, text: String, file: ReportUpload?): Report

    /** `BR-BUDGET-4`: записать трату бюджета. */
    suspend fun spend(proposalId: String, amount: Long, note: String?): Report

    /** Состояние голосования по выполнению (`BR-LIFE-9`). */
    suspend fun closure(proposalId: String): ClosureState

    /** `BR-LIFE-8`: лидер запускает процедуру закрытия. */
    suspend fun openClosure(proposalId: String): ClosureState

    /** `BR-VOTE-5`, `BR-VOTE-13`: ответить или поменять ответ. */
    suspend fun voteOnClosure(proposalId: String, completed: Boolean): ClosureState
}

@Singleton
class ApiReportRepository @Inject constructor(
    private val api: PeerflowApi,
    private val json: Json,
) : ReportRepository {

    override suspend fun report(proposalId: String): Report =
        apiCall(json) { api.report(proposalId) }.toModel()

    override suspend fun post(
        proposalId: String,
        text: String,
        file: ReportUpload?,
    ): Report = apiCall(json) {
        api.postReport(
            proposalId = proposalId,
            text = text.takeIf { it.isNotBlank() }?.toRequestBody(PLAIN_TEXT_MEDIA_TYPE),
            file = file?.asMultipart(),
        )
    }.toModel()

    override suspend fun spend(proposalId: String, amount: Long, note: String?): Report =
        apiCall(json) {
            api.spend(
                proposalId,
                SpendRequestDto(amount = amount, note = note?.takeIf { it.isNotBlank() }),
            )
        }.toModel()

    override suspend fun closure(proposalId: String): ClosureState =
        apiCall(json) { api.closure(proposalId) }.toModel()

    override suspend fun openClosure(proposalId: String): ClosureState =
        apiCall(json) { api.openClosure(proposalId) }.toModel()

    override suspend fun voteOnClosure(proposalId: String, completed: Boolean): ClosureState =
        apiCall(json) { api.voteOnClosure(proposalId, ClosureVoteDto(completed)) }.toModel()
}

/** DTO отчёта → модель экрана. */
private fun ReportDto.toModel() = Report(
    proposalId = proposalId,
    files = files.map { it.toModel() },
    expenses = expenses.map { it.toModel() },
    budget = budget,
    spent = spent,
    remaining = remaining,
)

private fun AttachmentDto.toModel() = Attachment(
    id = id,
    fileName = fileName,
    contentType = contentType,
    sizeBytes = sizeBytes,
    authorId = authorId,
    authorName = authorName,
    createdAt = createdAt,
    url = url,
)

private fun BudgetExpenseDto.toModel() = BudgetExpense(
    id = id,
    memberId = memberId,
    memberName = memberName,
    amount = amount,
    note = note,
    createdAt = createdAt,
)

private fun ClosureStateDto.toModel() = ClosureState(
    proposalId = proposalId,
    stage = ProposalStage.fromServer(stage),
    open = open,
    votesFor = votesFor,
    votesAgainst = votesAgainst,
    members = members,
    deadline = deadline,
    status = status,
    myVote = myVote?.completed,
    leaderId = leaderId,
    leaderName = leaderName,
    reputationAwarded = reputationAwarded,
)

/** DTO доски → модель экрана. */
private fun LegitimacyBoardDto.toBoard() = LegitimacyBoard(
    proposalId = proposalId,
    candidates = candidates.map { it.toModel() },
    leader = leader?.toModel(),
    myDelegation = myDelegation,
    total = total,
    members = members,
)

private fun CandidateDto.toModel() = Candidate(
    memberId = memberId,
    displayName = displayName,
    support = support,
    reputation = reputation,
    price = price,
    me = me,
)

private fun LeaderDto.toModel() = Leader(
    memberId = memberId,
    displayName = displayName,
    support = support,
    initialSupport = initialSupport,
    requiredSupport = requiredSupport,
    taskBudget = taskBudget,
    active = active,
    endReason = endReason,
)

private fun MyLegitimacyDto.toModel() = MyLegitimacy(
    reputation = reputation,
    delegations = delegations.map { it.toModel() },
)

private fun DelegationDto.toModel() = Delegation(
    proposalId = proposalId,
    proposalTitle = proposalTitle,
    candidateId = candidateId,
    candidateName = candidateName,
)

/** DTO ветки → модель экрана. */
private fun ThreadDto.toThread() = DiscussionThread(
    messages = messages.map { it.toMessage() },
    hidden = hidden,
)

private fun MessageDto.toMessage(): DiscussionMessage = DiscussionMessage(
    id = id,
    authorId = authorId,
    authorName = authorName,
    body = body,
    createdAt = createdAt,
    plus = plus,
    minus = minus,
    spam = spam,
    myReaction = ReactionKind.fromServer(myReaction),
    replies = replies.map { it.toMessage() },
)

/** DTO списка → секции экрана. */
private fun ProposalListDto.toSections() = ProposalSections(
    inWork = inWork.map { it.toSummary() },
    discussing = discussing.map { it.toSummary() },
)

/** DTO предложения → модель экрана. */
private fun ProposalDto.toSummary() = ProposalSummary(
    id = id,
    title = title,
    description = description,
    authorId = authorId,
    authorName = authorName,
    budget = budget,
    taskBudget = taskBudget,
    stage = ProposalStage.fromServer(stage),
    votesFor = votesFor,
    votesAgainst = votesAgainst,
    participants = participants,
    popularity = popularity,
    createdAt = createdAt,
    decidedAt = decidedAt,
    votingDeadline = votingDeadline,
    votingClosed = votingClosed,
    votingStatus = votingStatus,
    myVote = myVote?.toModel(),
)

private fun MyVoteDto.toModel() = MyVote(inFavor = inFavor)

/** DTO заявки → модель экрана. */
private fun ru.peerflow.demo.data.api.JoinRequestDto.toSummary() = JoinRequestSummary(
    id = id,
    applicantId = applicantId,
    applicantName = applicantName,
    about = about,
    avatarPath = avatarPath,
    status = status,
    createdAt = createdAt,
    accepts = accepts,
    totalVotes = votes,
    required = required,
    votedByMe = votedByMe,
)

/**
 * JSON-часть multipart-запроса.
 *
 * Имя части (`request`) и тип `application/json` заданы аннотацией
 * `@Part("request")` в [PeerflowApi]; здесь важно только правильно
 * закодировать тело.
 */
private inline fun <reified T> T.asJsonPart(json: Json): RequestBody =
    json.encodeToString(this)
        .toRequestBody(JSON_MEDIA_TYPE)

/** Файл аватара как часть multipart с именем `avatar`. */
private fun AvatarUpload.asMultipart(): MultipartBody.Part =
    MultipartBody.Part.createFormData(
        name = "avatar",
        filename = fileName,
        body = bytes.toRequestBody(mimeType.toMediaType()),
    )

/**
 * Файл отчёта как часть multipart с именем `file`.
 *
 * Имя части повторяет серверное (`@RequestPart("file")`): другое имя — и
 * сервер увидит отчёт без файла, а человек — успешно опубликованный пустой
 * отчёт.
 */
private fun ReportUpload.asMultipart(): MultipartBody.Part =
    MultipartBody.Part.createFormData(
        name = "file",
        filename = fileName,
        body = bytes.toRequestBody(mimeType.toMediaType()),
    )

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Тип текстовой части отчёта.
 *
 * Сервер читает текст как обычное поле формы (`@RequestParam`), поэтому тип
 * здесь ничего не решает, но должен быть явным: без него OkHttp отправит
 * `application/octet-stream`, и промежуточный прокси может решить, что это
 * файл.
 */
private val PLAIN_TEXT_MEDIA_TYPE = "text/plain; charset=utf-8".toMediaType()
