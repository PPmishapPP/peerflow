package ru.peerflow.demo.di

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.peerflow.demo.BuildConfig
import ru.peerflow.demo.data.api.AuthInterceptor
import ru.peerflow.demo.data.api.PeerflowApi
import ru.peerflow.demo.data.api.PreferencesTokenStore
import ru.peerflow.demo.data.api.TokenStore
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * Сборка сетевого стека: JSON, HTTP-клиент, Retrofit и контракт API.
 *
 * Базовый адрес берётся из `BuildConfig.BASE_URL` — он разный для debug
 * (`http://10.0.2.2:8080/`, localhost компьютера для эмулятора) и release
 * (`https://mayakspace.com/`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun tokenStore(impl: PreferencesTokenStore): TokenStore

    companion object {

        /**
         * Формат ответов сервера.
         *
         * `ignoreUnknownKeys` — сервер может добавить поле, и старая сборка
         * клиента не должна из-за этого падать. `explicitNulls = false` —
         * не отправлять `null` в JSON, чтобы серверные `@Size`/`@NotBlank`
         * не спотыкались о лишние ключи. `encodeDefaults = true` — значения
         * по умолчанию (например, пустой `about`) должны уйти на сервер
         * явно, иначе он увидит `null`.
         */
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        @Provides
        @Singleton
        fun json(): Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        @Provides
        @Singleton
        fun okHttpClient(tokenStore: TokenStore): OkHttpClient =
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(tokenStore))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

        @Provides
        @Singleton
        fun retrofit(json: Json, client: OkHttpClient): Retrofit =
            Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

        @Provides
        @Singleton
        fun peerflowApi(retrofit: Retrofit): PeerflowApi = retrofit.create(PeerflowApi::class.java)
    }
}
