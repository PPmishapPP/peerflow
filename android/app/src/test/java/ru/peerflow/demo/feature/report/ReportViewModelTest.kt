package ru.peerflow.demo.feature.report

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.peerflow.demo.data.api.ApiException
import ru.peerflow.demo.data.api.NetworkException
import ru.peerflow.demo.data.model.Attachment
import ru.peerflow.demo.data.model.BudgetExpense
import ru.peerflow.demo.data.model.ProposalStage
import ru.peerflow.demo.data.model.Report
import ru.peerflow.demo.data.model.ReportUpload
import ru.peerflow.demo.testing.FakeReportRepository
import ru.peerflow.demo.testing.MainDispatcherRule
import ru.peerflow.demo.testing.sampleClosure
import ru.peerflow.demo.testing.sampleReport

/**
 * Отчёт лидера, траты бюджета и закрытие задачи (`BR-LIFE-7`–`BR-LIFE-9`,
 * `BR-BUDGET-4`, `BR-VOTE-5`).
 *
 * Правила большинства и переход легитимности в репутацию проверяются на
 * сервере: здесь важно, что клиент отправляет действия человека, показывает
 * то, что посчитал сервер, и не стирает уже показанный отчёт из-за сбоя.
 */
class ReportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeReportRepository()

    private fun viewModel() = ReportViewModel(repository)

    @Test
    fun `load shows files, expenses and closure together`() = runTest {
        val report = sampleReport(
            files = listOf(Attachment(id = "a1", fileName = "смета.pdf")),
            expenses = listOf(BudgetExpense(id = "e1", memberId = "m", memberName = "Борис", amount = 500)),
            spent = 500,
        )
        repository.reportResult = report
        repository.closureResult = sampleClosure(open = true, votesFor = 1)
        val vm = viewModel()

        vm.load("p1")

        val loaded = vm.state.value as ReportState.Loaded
        assertEquals(report, loaded.report)
        assertEquals(1L, loaded.closure.votesFor)
    }

    @Test
    fun `load failure is shown with the server reason`() = runTest {
        repository.reportError = ApiException(404, "Not Found", "предложение не найдено")
        val vm = viewModel()

        vm.load("missing")

        assertEquals(
            ReportState.Failure("предложение не найдено"),
            vm.state.value,
        )
    }

    @Test
    fun `network failure on load is explained`() = runTest {
        repository.reportError = NetworkException("timeout")
        val vm = viewModel()

        vm.load("p1")

        assertEquals(
            ReportState.Failure("нет связи с сервером, проверьте интернет"),
            vm.state.value,
        )
    }

    @Test
    fun `posting a report sends text and keeps the server answer`() = runTest {
        repository.reportResult = sampleReport()
        val vm = viewModel()
        vm.load("p1")

        vm.postReport("Арендовали зал, чек приложен", null)

        assertEquals(1, repository.postCalls)
        assertEquals("Арендовали зал, чек приложен", repository.lastText)
        val loaded = vm.state.value as ReportState.Loaded
        assertEquals("p1", loaded.report.proposalId)
    }

    @Test
    fun `report with a file uploads the file and shows it`() = runTest {
        repository.reportResult = sampleReport()
        val vm = viewModel()
        vm.load("p1")
        val upload = ReportUpload(
            bytes = byteArrayOf(1, 2, 3),
            fileName = "акт.pdf",
            mimeType = "application/pdf",
        )

        vm.postReport("", upload)

        assertEquals(upload, repository.lastUpload)
        val loaded = vm.state.value as ReportState.Loaded
        assertEquals(
            "приложенный файл виден сразу после публикации",
            "акт.pdf",
            loaded.report.files.single().fileName,
        )
    }

    @Test
    fun `empty report is not sent to the server`() = runTest {
        val vm = viewModel()
        vm.load("p1")

        vm.postReport("   ", null)

        assertEquals("пустой отчёт незачем гонять по сети", 0, repository.postCalls)
        assertTrue(vm.message.value!!.contains("пустым"))
        assertFalse(vm.sending.value)
    }

    @Test
    fun `post failure keeps the shown report and explains why`() = runTest {
        val report = sampleReport(files = listOf(Attachment(id = "a1", fileName = "смета.pdf")))
        repository.reportResult = report
        val vm = viewModel()
        vm.load("p1")
        repository.postError = ApiException(403, "Forbidden", "действие доступно только лидеру задачи")

        vm.postReport("Отчёт", null)

        assertEquals("действие доступно только лидеру задачи", vm.message.value)
        assertEquals(
            "показанный отчёт не должен исчезнуть из-за ошибки",
            ReportState.Loaded(report, repository.closureResult),
            vm.state.value,
        )
        assertFalse(vm.sending.value)
    }

    @Test
    fun `spending reduces the remaining budget`() = runTest {
        repository.reportResult = sampleReport(budget = 12_000)
        val vm = viewModel()
        vm.load("p1")

        vm.spend(2_500, "аренда зала")

        assertEquals(2_500L, repository.lastAmount)
        assertEquals("аренда зала", repository.lastNote)
        val loaded = vm.state.value as ReportState.Loaded
        assertEquals(9_500L, loaded.report.remaining)
        assertEquals(2_500L, loaded.report.spent)
    }

    @Test
    fun `non-positive spend is not sent`() = runTest {
        val vm = viewModel()
        vm.load("p1")

        vm.spend(0, "ничего")

        assertEquals(0, repository.spendCalls)
        assertTrue(vm.message.value!!.contains("положительной"))
    }

    @Test
    fun `spend failure explains the budget limit`() = runTest {
        repository.reportResult = sampleReport(budget = 1_000)
        val vm = viewModel()
        vm.load("p1")
        repository.spendError = ApiException(
            400,
            "Bad Request",
            "трата больше остатка бюджета: осталось 1000",
        )

        vm.spend(5_000, "слишком много")

        assertEquals("трата больше остатка бюджета: осталось 1000", vm.message.value)
        assertFalse(vm.sending.value)
    }

    @Test
    fun `opening the closure shows the voting as started`() = runTest {
        repository.closureResult = sampleClosure()
        val vm = viewModel()
        vm.load("p1")

        vm.openClosure()

        assertEquals(1, repository.openCalls)
        val loaded = vm.state.value as ReportState.Loaded
        assertTrue("процедура запущена — голосовать можно", loaded.closure.canVote)
    }

    @Test
    fun `closure vote is sent and counted`() = runTest {
        repository.closureResult = sampleClosure(open = true)
        val vm = viewModel()
        vm.load("p1")

        vm.vote(true)

        assertEquals(true, repository.lastCompleted)
        val loaded = vm.state.value as ReportState.Loaded
        assertEquals(1L, loaded.closure.votesFor)
        assertEquals(true, loaded.closure.myVote)
    }

    @Test
    fun `voting again replaces the answer instead of adding a second`() = runTest {
        repository.closureResult = sampleClosure(open = true)
        val vm = viewModel()
        vm.load("p1")

        vm.vote(true)
        repository.closureResult = repository.closureResult.copy(votesFor = 1, myVote = true)
        vm.vote(false)
        repository.closureResult = repository.closureResult.copy(votesFor = 1, votesAgainst = 1, myVote = false)

        val loaded = vm.state.value as ReportState.Loaded
        assertEquals(false, loaded.closure.myVote)
        assertFalse(
            "смена ответа — не второй голос",
            loaded.closure.canVote.not() && loaded.closure.closed,
        )
    }

    @Test
    fun `vote failure keeps the shown state`() = runTest {
        val closure = sampleClosure(open = true, votesFor = 1)
        repository.closureResult = closure
        repository.reportResult = sampleReport()
        val vm = viewModel()
        vm.load("p1")
        repository.voteError = ApiException(409, "Conflict", "голосование по выполнению не идёт")

        vm.vote(true)

        assertEquals("голосование по выполнению не идёт", vm.message.value)
        assertFalse(vm.sending.value)
    }

    @Test
    fun `closed task shows the reputation awarded to the leader`() = runTest {
        repository.closureResult = sampleClosure(
            stage = ProposalStage.CLOSED,
            votesFor = 3,
            reputationAwarded = 7,
        )
        val vm = viewModel()

        vm.load("p1")

        val loaded = vm.state.value as ReportState.Loaded
        assertTrue(loaded.closure.closed)
        assertFalse(loaded.closure.canVote)
        assertEquals(7L, loaded.closure.reputationAwarded)
    }

    @Test
    fun `actions block repeated taps`() = runTest {
        repository.reportResult = sampleReport()
        repository.postGate = CompletableDeferred()
        val vm = viewModel()
        vm.load("p1")

        vm.postReport("Отчёт", null)
        vm.spend(100, "мелочь")

        assertEquals("второй запрос не должен уйти, пока идёт первый", 1, repository.postCalls)
        assertEquals(0, repository.spendCalls)
        assertTrue(vm.sending.value)

        repository.postGate?.complete(Unit)
    }

    @Test
    fun `dismiss clears the error`() = runTest {
        repository.reportError = NetworkException("offline")
        val vm = viewModel()
        vm.load("p1")

        vm.dismissMessage()

        assertNull(vm.message.value)
    }

    @Test
    fun `report progress and exhaustion are explained`() {
        val partial = sampleReport(budget = 1_000, spent = 250)
        assertEquals(0.25f, partial.spentShare, 0.001f)
        assertFalse(partial.isExhausted)

        val exhausted = Report(proposalId = "p1", budget = 1_000, spent = 1_000, remaining = 0)
        assertTrue(exhausted.isExhausted)
        assertTrue(exhausted.spentShare == 1f)
    }
}
