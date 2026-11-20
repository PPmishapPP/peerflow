package ru.peerflow.demo.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

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
}
