package ru.peerflow.demo.data.api

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import ru.peerflow.demo.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Событие сообщества из потока `/api/events` (`BR-DISC-1`, `BR-VOTE-4`).
 *
 * [type] — вид изменения (`MESSAGE_POSTED`, `VOTE_CHANGED`, `REPORT_POSTED`,
 * `TASK_CLOSED`), [proposalId] — какой задачи. Данных задачи в событии нет
 * намеренно: клиент перезапрашивает экран и получает согласованное состояние,
 * посчитанное сервером, а не устаревшую копию.
 */
@Serializable
data class ServerEvent(
    val type: String = "",
    val proposalId: String? = null,
    val at: String? = null,
)

/**
 * Источник живых обновлений.
 *
 * Интерфейс, а не класс: ViewModel-тесты подставляют фейк и не открывают
 * соединение с сервером.
 */
interface RealtimeClient {

    /**
     * События сообщества.
     *
     * Поток общий для всех подписчиков: одно соединение на приложение, а не по
     * одному на каждый экран. Пока подписчиков нет, соединение закрыто.
     */
    fun events(): Flow<ServerEvent>
}

/**
 * Потребитель SSE-потока сервера.
 *
 * SSE, а не WebSocket: сервер только присылает «что-то изменилось», а
 * отправлять по этому каналу нечего — действия идут обычными запросами.
 *
 * Соединение одно на приложение и живёт, пока есть подписчики
 * (`SharingStarted.WhileSubscribed`): ушли с экрана — поток закрылся, и сервер
 * не держит сокет ради человека, который уже ничего не смотрит. При обрыве
 * связи поток переподключается сам: телефон теряет сеть в метро, и заставлять
 * человека перезаходить на экран ради этого незачем.
 */
@Singleton
class ApiRealtimeClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) : RealtimeClient {

    /**
     * Область жизни соединения.
     *
     * Не `viewModelScope`: поток общий для нескольких экранов, и его жизнь
     * определяется подписчиками, а не одной ViewModel.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val shared = stream()
        .retryWhen { _, _ ->
            // BR-DISC-1: без переподключения лента «замирает» после первого
            // обрыва, и человек видит старые сообщения, пока не перезайдёт.
            delay(RECONNECT_DELAY_MILLIS)
            true
        }
        .shareIn(
            scope = scope,
            // Небольшая задержка перед закрытием: поворот экрана размонтирует
            // и монтирует подписку, и соединение не должно рваться из-за этого.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            replay = 0,
        )

    override fun events(): Flow<ServerEvent> = shared

    private fun stream(): Flow<ServerEvent> = flow {
        val request = Request.Builder()
            .url(BuildConfig.BASE_URL + "api/events")
            .header("Accept", "text/event-stream")
            .build()
        // Читаем без ограничения по времени: поток молчит, пока ничего не
        // происходит, и таймаут чтения обрывал бы его без причины.
        val streaming = client.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val call = streaming.newCall(request)
        // Отмена корутины должна закрывать сокет: иначе блокирующее чтение
        // висело бы до следующего события или до серверного таймаута.
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }

        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw NetworkException("поток событий недоступен (${response.code})")
            }
            val body = response.body
            val parser = SseParser(json)
            val source: BufferedSource = body.source()
            while (currentCoroutineContext().isActive) {
                val line = source.readUtf8Line() ?: break
                parser.line(line)?.let { event -> emit(event) }
            }
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val RECONNECT_DELAY_MILLIS = 3_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Разбор SSE-потока по строкам.
 *
 * Вынесен из [ApiRealtimeClient] отдельным классом, чтобы проверять его без
 * сети: формат SSE простой, но пропущенная пустая строка или склеенные
 * `data:` из разных событий дают «событие пришло, а клиент его не заметил».
 *
 * Событие собирается из строк `event:` и `data:` и выдаётся на пустой строке.
 * Комментарии (`:` в начале) и неизвестные поля пропускаются — так сервер
 * может добавить `id:` или `retry:` и не сломать старую сборку клиента.
 */
internal class SseParser(private val json: Json) {

    private var type: String? = null
    private val data = StringBuilder()

    /**
     * Принимает одну строку потока.
     *
     * @param line строка без перевода строки
     * @return событие, если строка завершила его; иначе `null`
     */
    fun line(line: String): ServerEvent? {
        if (line.isEmpty()) {
            val name = type
            val payload = data.toString()
            type = null
            data.setLength(0)
            return decode(name, payload)
        }
        if (line.startsWith(':')) return null
        val separator = line.indexOf(':')
        val field = if (separator < 0) line else line.substring(0, separator)
        val value = if (separator < 0) "" else line.substring(separator + 1).removePrefix(" ")
        when (field) {
            "event" -> type = value
            "data" -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(value)
            }
            else -> Unit
        }
        return null
    }

    private fun decode(name: String?, payload: String): ServerEvent? {
        // `ready` — служебное событие: сервер подтверждает, что поток открыт.
        // Данных задачи в нём нет, и перезапрашивать по нему нечего.
        if (name == "ready") return null
        if (payload.isBlank()) return null
        val parsed = runCatching { json.decodeFromString<ServerEvent>(payload) }.getOrNull()
            ?: return null
        // Имя события в SSE может прийти только полем `event:`. Если сервера
        // его не прислали, вид берём из тела — контракт от этого не меняется.
        return if (parsed.type.isBlank() && name != null) parsed.copy(type = name) else parsed
    }
}
