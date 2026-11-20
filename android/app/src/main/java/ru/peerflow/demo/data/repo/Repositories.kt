package ru.peerflow.demo.data.repo

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.peerflow.demo.data.api.apiCall
import ru.peerflow.demo.data.api.CreateProposalDto
import ru.peerflow.demo.data.api.DecisionVoteDto
import ru.peerflow.demo.data.api.JoinVoteDto
import ru.peerflow.demo.data.api.LoginRequestDto
import ru.peerflow.demo.data.api.MyVoteDto
import ru.peerflow.demo.data.api.PeerflowApi
import ru.peerflow.demo.data.api.ProposalDto
import ru.peerflow.demo.data.api.ProposalListDto
import ru.peerflow.demo.data.api.RegisterRequestDto
import ru.peerflow.demo.data.api.SubmitJoinRequestDto
import ru.peerflow.demo.data.api.TokenStore
import ru.peerflow.demo.data.model.AvatarUpload
import ru.peerflow.demo.data.model.CurrentUser
import ru.peerflow.demo.data.model.JoinRequestSummary
import ru.peerflow.demo.data.model.MyVote
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

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
