package ru.peerflow.demo.data.api

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Где приложение держит токен доступа.
 *
 * Интерфейс, а не класс, нужен тестам: ViewModel-тесты подставляют
 * реализацию в памяти и не трогают `SharedPreferences`.
 */
interface TokenStore {

    /** Токен для `Authorization: Bearer …`; `null`, если человек не вошёл. */
    fun token(): String?

    fun save(token: String)

    fun clear()

    fun hasToken(): Boolean = token() != null
}

/**
 * Токен в приватных `SharedPreferences`.
 *
 * Это не «секретное хранилище» уровня `EncryptedSharedPreferences`:
 * MVP работает с одним сообществом и без денег, а токен живёт 12 часов
 * (`peerflow.jwt.ttl`), так что цена его утечки ограничена. Когда появятся
 * платежи — переедем на Keystore, не меняя [TokenStore]-интерфейс.
 */
@Singleton
class PreferencesTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : TokenStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun token(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    override fun save(token: String) {
        prefs.edit { putString(KEY_TOKEN, token) }
    }

    override fun clear() {
        prefs.edit { remove(KEY_TOKEN) }
    }

    private companion object {
        const val PREFS_NAME = "peerflow.auth"
        const val KEY_TOKEN = "token"
    }
}
