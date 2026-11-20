package ru.peerflow.demo.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Разбор SSE-потока (`BR-DISC-1`, `BR-VOTE-4`).
 *
 * Формат простой, но ошибка в нём незаметна: событие приходит, клиент его не
 * понимает, и лента «молча не обновляется». Поэтому разбор проверяется
 * отдельно от сети.
 */
class SseParserTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun parser() = SseParser(json)

    @Test
    fun `event and data are combined into one event`() {
        val parser = parser()

        assertNull(parser.line("event: MESSAGE_POSTED"))
        assertNull(parser.line("""data: {"type":"MESSAGE_POSTED","proposalId":"p1"}"""))
        val event = parser.line("")

        assertEquals("MESSAGE_POSTED", event?.type)
        assertEquals("p1", event?.proposalId)
    }

    @Test
    fun `several events in a row are parsed separately`() {
        val parser = parser()
        parser.line("event: MESSAGE_POSTED")
        parser.line("""data: {"type":"MESSAGE_POSTED","proposalId":"p1"}""")
        val first = parser.line("")
        parser.line("event: VOTE_CHANGED")
        parser.line("""data: {"type":"VOTE_CHANGED","proposalId":"p2"}""")
        val second = parser.line("")

        assertEquals("p1", first?.proposalId)
        assertEquals("p2", second?.proposalId)
        assertEquals("VOTE_CHANGED", second?.type)
    }

    @Test
    fun `ready event is service and is not delivered`() {
        val parser = parser()

        parser.line("event: ready")
        parser.line("""data: {"ok":true}""")

        assertNull(
            "по ready нечего перезапрашивать — это подтверждение открытия потока",
            parser.line(""),
        )
    }

    @Test
    fun `comment lines are skipped`() {
        val parser = parser()

        assertNull(parser.line(": keep-alive"))
        assertNull(parser.line(""))
    }

    @Test
    fun `unknown fields do not break the event`() {
        val parser = parser()
        parser.line("id: 42")
        parser.line("retry: 1000")
        parser.line("event: REPORT_POSTED")
        parser.line("""data: {"type":"REPORT_POSTED","proposalId":"p1"}""")

        val event = parser.line("")

        assertEquals("REPORT_POSTED", event?.type)
    }

    @Test
    fun `broken payload is ignored instead of crashing the stream`() {
        val parser = parser()
        parser.line("event: MESSAGE_POSTED")
        parser.line("data: {не json}")

        assertNull(parser.line(""))
        // После битого события поток продолжает читаться.
        parser.line("event: VOTE_CHANGED")
        parser.line("""data: {"type":"VOTE_CHANGED","proposalId":"p1"}""")
        assertEquals("VOTE_CHANGED", parser.line("")?.type)
    }

    @Test
    fun `event name is taken from the field when the body omits it`() {
        val parser = parser()
        parser.line("event: TASK_CLOSED")
        parser.line("""data: {"proposalId":"p1"}""")

        val event = parser.line("")

        assertEquals("TASK_CLOSED", event?.type)
    }

    @Test
    fun `multi-line data is joined into one payload`() {
        val parser = parser()
        parser.line("event: MESSAGE_POSTED")
        parser.line("""data: {"type":"MESSAGE_POSTED",""")
        parser.line("""data: "proposalId":"p1"}""")

        val event = parser.line("")

        assertEquals("p1", event?.proposalId)
    }
}
