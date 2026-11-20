package ru.peerflow.demo.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Подставляет токен доступа в каждый запрос.
 *
 * Токен не передаётся в конструктор: он меняется после входа и выхода, а
 * `OkHttpClient` живёт всё время работы приложения. Читаем его из
 * [TokenStore] на каждом запросе — это дешёвая операция чтения из памяти
 * `SharedPreferences`.
 *
 * Если заголовок уже задан явно (например, будущий запрос с другим токеном),
 * не перезаписываем его.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenStore.token()

        if (token == null || request.header(HEADER) != null) {
            return chain.proceed(request)
        }

        return chain.proceed(
            request.newBuilder()
                .header(HEADER, "Bearer $token")
                .build(),
        )
    }

    private companion object {
        const val HEADER = "Authorization"
    }
}
