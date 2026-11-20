package ru.peerflow.demo.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * HTTP-контракт сервера PeerFlow.
 *
 * Пути и имена частей multipart-запроса повторяют серверные контроллеры
 * (`AuthController`, `JoinRequestController`) один в один — расхождение здесь
 * даёт `400` на клиенте и неверный экран у человека.
 *
 * Текстовые части (`request`) передаются заранее сериализованным
 * [RequestBody] с типом `application/json`: сервер читает их через
 * `@RequestPart`, а не `@RequestParam`, и различает части именно по
 * Content-Type.
 */
interface PeerflowApi {

    /** `BR-JOIN-1`: вход по логину и паролю. */
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequestDto): AuthDto

    /**
     * `BR-JOIN-1`, `BR-JOIN-4`: регистрация. Аватар — отдельная часть,
     * может отсутствовать.
     */
    @Multipart
    @POST("api/auth/register")
    suspend fun register(
        @Part("request") request: RequestBody,
        @Part avatar: MultipartBody.Part? = null,
    ): AuthDto

    /** Кто вошёл и принят ли он в сообщество (`BR-JOIN-4`, `BR-PRIV-1`). */
    @GET("api/auth/me")
    suspend fun me(): ProfileDto

    /** `BR-JOIN-4`: подать заявку на вступление. */
    @Multipart
    @POST("api/join-requests")
    suspend fun submitJoinRequest(
        @Part("request") request: RequestBody,
        @Part avatar: MultipartBody.Part? = null,
    ): JoinRequestDto

    /**
     * Заявки, ожидающие решения. Доступно только членам сообщества
     * (`BR-VOTE-1`), иначе сервер ответит `403`.
     */
    @GET("api/join-requests")
    suspend fun pendingJoinRequests(): List<JoinRequestDto>

    /**
     * Своя заявка. Сервер отвечает `204 No Content`, если заявки нет, —
     * поэтому здесь [Response], а не просто [JoinRequestDto].
     */
    @GET("api/join-requests/mine")
    suspend fun myJoinRequest(): Response<JoinRequestDto>

    /** `BR-JOIN-5`: голос члена сообщества по заявке. */
    @POST("api/join-requests/{id}/votes")
    suspend fun vote(
        @Path("id") requestId: String,
        @Body body: JoinVoteDto,
    ): JoinRequestDto

    /**
     * Список предложений двумя секциями (`BR-RANK-5`).
     *
     * Сортировку задаёт параметр `sort` (`BR-RANK-4`): порядок считает сервер,
     * потому что метрика популярности — правило сообщества, а не клиента.
     */
    @GET("api/proposals")
    suspend fun proposals(@Query("sort") sort: String): ProposalListDto

    /** Одно предложение с голосами (`BR-VOTE-4`). */
    @GET("api/proposals/{id}")
    suspend fun proposal(@Path("id") proposalId: String): ProposalDto

    /** `BR-OVER-2`, `BR-LIFE-1`: внести предложение с обязательным бюджетом. */
    @POST("api/proposals")
    suspend fun createProposal(@Body body: CreateProposalDto): ProposalDto

    /**
     * `BR-VOTE-4`, `BR-VOTE-13`: голос «делаем / не делаем» или смена голоса.
     */
    @POST("api/proposals/{id}/votes")
    suspend fun voteOnProposal(
        @Path("id") proposalId: String,
        @Body body: DecisionVoteDto,
    ): ProposalDto

    /**
     * Ветка обсуждения предложения (`BR-DISC-1`–`BR-DISC-5`).
     *
     * Порядок сообщений на каждом уровне ветки задаёт `sort`
     * (`BR-DISC-4`, `BR-DISC-5`): правило ранжирования — общее для
     * сообщества, поэтому считает его сервер.
     */
    @GET("api/proposals/{id}/messages")
    suspend fun messages(
        @Path("id") proposalId: String,
        @Query("sort") sort: String,
    ): ThreadDto

    /** `BR-DISC-1`, `BR-DISC-3`: написать сообщение или ответ на сообщение. */
    @POST("api/proposals/{id}/messages")
    suspend fun postMessage(
        @Path("id") proposalId: String,
        @Body body: PostMessageDto,
    ): ThreadDto

    /** `BR-DISC-2`: поставить или сменить свою реакцию на сообщение. */
    @POST("api/messages/{id}/reactions")
    suspend fun react(
        @Path("id") messageId: String,
        @Body body: ReactionDto,
    ): ThreadDto

    /**
     * Легитимность, кандидаты и лидер задачи (`BR-LEGIT-3`, `BR-LEADER-3`,
     * `BR-LEADER-5`).
     *
     * Одним запросом: экран показывает кандидатов, лидера и «кому я передал
     * доверие» одновременно.
     */
    @GET("api/proposals/{id}/legitimacy")
    suspend fun legitimacy(@Path("id") proposalId: String): LegitimacyBoardDto

    /** `BR-LEADER-1`, `BR-BUDGET-8`: выдвинуть себя кандидатом с ценой. */
    @POST("api/proposals/{id}/legitimacy/candidates")
    suspend fun nominate(
        @Path("id") proposalId: String,
        @Body body: NominateDto,
    ): LegitimacyBoardDto

    /**
     * `BR-LEGIT-2`: передать свою легитимность кандидату.
     *
     * Передаётся всегда своя легитимность — кто передаёт, определяется токеном,
     * поэтому в пути только получатель.
     */
    @POST("api/proposals/{id}/legitimacy/{candidateId}")
    suspend fun delegate(
        @Path("id") proposalId: String,
        @Path("candidateId") candidateId: String,
    ): LegitimacyBoardDto

    /**
     * `BR-LEGIT-5`: забрать свою легитимность себе.
     *
     * Без тела: отзывается ровно то, что моё. Если бы клиент называл получателя,
     * промах по устаревшим данным отзывал бы не то доверие.
     */
    @DELETE("api/proposals/{id}/legitimacy")
    suspend fun revoke(@Path("id") proposalId: String): LegitimacyBoardDto

    /** `BR-LEGIT-3`, `BR-REP-2`: передачи доверия и репутация в профиле. */
    @GET("api/me/legitimacy")
    suspend fun myLegitimacy(): MyLegitimacyDto

    /**
     * Отчёт задачи: файлы, траты и остаток бюджета (`BR-LIFE-7`,
     * `BR-BUDGET-4`, `BR-BUDGET-5`).
     */
    @GET("api/proposals/{id}/report")
    suspend fun report(@Path("id") proposalId: String): ReportDto

    /**
     * Публикует отчёт лидера (`BR-LIFE-7`).
     *
     * Текст и файл отправляются одной multipart-формой: «отчёт с текстом и
     * файлом» не должен распадаться на два запроса, где второй может не
     * дойти. Текст идёт обычным полем формы, файл — частью `file`: так же,
     * как его читает сервер.
     */
    @Multipart
    @POST("api/proposals/{id}/reports")
    suspend fun postReport(
        @Path("id") proposalId: String,
        @Part("text") text: RequestBody?,
        @Part file: MultipartBody.Part? = null,
    ): ReportDto

    /** `BR-BUDGET-4`: записать трату бюджета задачи. */
    @POST("api/proposals/{id}/expenses")
    suspend fun spend(
        @Path("id") proposalId: String,
        @Body body: SpendRequestDto,
    ): ReportDto

    /** Голосование «выполнено / не выполнено» (`BR-LIFE-9`). */
    @GET("api/proposals/{id}/closure")
    suspend fun closure(@Path("id") proposalId: String): ClosureStateDto

    /** `BR-LIFE-8`: лидер запускает процедуру закрытия. */
    @POST("api/proposals/{id}/closure")
    suspend fun openClosure(@Path("id") proposalId: String): ClosureStateDto

    /** `BR-VOTE-5`, `BR-VOTE-13`: ответить или поменять ответ. */
    @POST("api/proposals/{id}/closure/votes")
    suspend fun voteOnClosure(
        @Path("id") proposalId: String,
        @Body body: ClosureVoteDto,
    ): ClosureStateDto
}
