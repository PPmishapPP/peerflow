package ru.peerflow.demo.ui.auth

/**
 * Правила ввода логина, пароля и имени — зеркало серверных `@Size`/`@Pattern`
 * из `RegisterRequest` (`BR-JOIN-1`).
 *
 * Зачем дублировать: сервер обязан проверять, иначе правило обходится любым
 * клиентом. Но гонять запрос ради «логин короче трёх символов» — плохой опыт:
 * человек ждёт сеть, чтобы узнать очевидное. Поэтому проверяем и здесь, а
 * сервер остаётся последней линией.
 *
 * Единственный источник цифр — этот объект: экран подписывает поля, тесты
 * проверяют границы, и никто не расходится с сервером молча.
 */
object CredentialRules {

    const val LOGIN_MIN = 3
    const val LOGIN_MAX = 64
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 72
    const val DISPLAY_NAME_MAX = 100
    const val ABOUT_MAX = 2000

    /** Разрешены латиница, цифры, точка, подчёркивание и дефис. */
    private val LOGIN_PATTERN = Regex("[A-Za-z0-9._-]+")

    /** `null` — значение годится. Иначе текст ошибки для человека. */
    fun checkLogin(value: String): String? = when {
        value.isBlank() -> "введите логин"
        value.length < LOGIN_MIN -> "логин короче $LOGIN_MIN символов"
        value.length > LOGIN_MAX -> "логин длиннее $LOGIN_MAX символов"
        !LOGIN_PATTERN.matches(value) ->
            "в логине допустимы латинские буквы, цифры, точка, дефис и подчёркивание"
        else -> null
    }

    fun checkPassword(value: String): String? = when {
        value.isEmpty() -> "введите пароль"
        value.length < PASSWORD_MIN -> "пароль короче $PASSWORD_MIN символов"
        value.length > PASSWORD_MAX -> "пароль длиннее $PASSWORD_MAX символов"
        else -> null
    }

    fun checkDisplayName(value: String): String? = when {
        value.isBlank() -> "введите имя"
        value.length > DISPLAY_NAME_MAX -> "имя длиннее $DISPLAY_NAME_MAX символов"
        else -> null
    }

    fun checkAbout(value: String): String? =
        if (value.length > ABOUT_MAX) "текст длиннее $ABOUT_MAX символов" else null
}
