package ru.peerflow.demo.data.api

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Единая точка перевода исключений транспорта в понятные приложению.
 *
 * Retrofit на не-2xx бросает [HttpException], у которого тело ответа лежит в
 * `errorBody()`. Сервер отдаёт `application/problem+json`, поэтому вытаскиваем
 * оттуда `title` и `detail` — их и покажем человеку. Если тело не разобралось
 * (прокси, шлюз, пустой ответ), остаётся код состояния.
 *
 * Сетевые сбои (`IOException`) превращаются в [NetworkException]: «нет связи
 * с сервером» человек понимает и может исправить, «java.net.UnknownHostException»
 * — нет.
 */
suspend fun <T> apiCall(json: Json, block: suspend () -> T): T =
    try {
        block()
    } catch (e: HttpException) {
        throw e.toApiException(json)
    } catch (e: IOException) {
        throw NetworkException("нет связи с сервером", e)
    }

private fun HttpException.toApiException(json: Json): ApiException {
    val code = code()
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val problem = body
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.decodeFromString<ProblemDto>(it) }.getOrNull() }

    val message = problem?.detail
        ?: problem?.title
        ?: when (code) {
            401 -> "войдите в аккаунт повторно"
            403 -> "нужно членство в сообществе"
            404 -> "не найдено"
            in 500..599 -> "сервер недоступен, попробуйте позже"
            else -> "ошибка запроса ($code)"
        }

    return ApiException(status = code, title = problem?.title, message = message)
}
