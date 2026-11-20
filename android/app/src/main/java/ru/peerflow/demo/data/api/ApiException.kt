package ru.peerflow.demo.data.api

/**
 * Ошибка, которую нужно показать человеку.
 *
 * Наследуемся от [Exception], чтобы её можно было бросать из `suspend`-функции
 * и ловить в ViewModel одним `catch`. Поле [status] нужно для разбора
 * конкретных случаев: `401` — разлогинить, `409` — «логин занят», `403` —
 * «нужно членство».
 */
class ApiException(
    val status: Int,
    val title: String?,
    override val message: String,
) : Exception(message)

/**
 * Сетевая ошибка, которую человек может исправить сам: нет интернета,
 * сервер не отвечает, запрос истёк.
 *
 * Отдельный тип нужен, потому что текст для человека другой: «нет связи»,
 * а не «ошибка 500».
 */
class NetworkException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)
