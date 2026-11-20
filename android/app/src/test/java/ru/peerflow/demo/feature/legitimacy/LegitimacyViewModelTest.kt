package ru.peerflow.demo.feature.legitimacy

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
import ru.peerflow.demo.data.model.Delegation
import ru.peerflow.demo.data.model.LegitimacyBoard
import ru.peerflow.demo.data.model.MANDATE_LOST
import ru.peerflow.demo.data.model.MyLegitimacy
import ru.peerflow.demo.testing.FakeLegitimacyRepository
import ru.peerflow.demo.testing.MainDispatcherRule
import ru.peerflow.demo.testing.sampleCandidate
import ru.peerflow.demo.testing.sampleLeader

/**
 * Легитимность и лидер задачи (`BR-LEGIT-2`–`BR-LEGIT-5`, `BR-LEADER-1`,
 * `BR-LEADER-5`).
 *
 * Выбор лидера, тай-брейкеры и порог удержания проверяются на сервере: здесь
 * важно, что клиент не выдумывает своё состояние, отправляет действия человека
 * и не теряет уже показанную доску из-за сетевого сбоя.
 */
class LegitimacyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLegitimacyRepository()

    private fun viewModel() = LegitimacyViewModel(repository)

    @Test
    fun `load fetches the board of the proposal`() = runTest {
        val board = LegitimacyBoard(
            proposalId = "p1",
            candidates = listOf(sampleCandidate(support = 2)),
            members = 5,
        )
        repository.boardResult = board
        val vm = viewModel()

        vm.load("p1")

        assertEquals(LegitimacyState.Loaded(board), vm.state.value)
        assertEquals("p1", repository.lastProposalId)
    }

    @Test
    fun `load failure is shown with the server reason`() = runTest {
        repository.boardError = ApiException(404, "Not Found", "предложение не найдено")
        val vm = viewModel()

        vm.load("missing")

        assertEquals(
            LegitimacyState.Failure("предложение не найдено"),
            vm.state.value,
        )
    }

    @Test
    fun `network failure on load is explained`() = runTest {
        repository.boardError = NetworkException("timeout")
        val vm = viewModel()

        vm.load("p1")

        assertEquals(
            LegitimacyState.Failure("нет связи с сервером, проверьте интернет"),
            vm.state.value,
        )
    }

    @Test
    fun `nominate sends the price and keeps the server answer`() = runTest {
        repository.boardResult = LegitimacyBoard(proposalId = "p1", members = 3)
        val vm = viewModel()
        vm.load("p1")

        vm.nominate(9_000)

        assertEquals(1, repository.nominateCalls)
        assertEquals("цена уходит на сервер как есть", 9_000L, repository.lastNominatePrice)
        val loaded = vm.state.value as LegitimacyState.Loaded
        assertTrue("себя в списке кандидатов видно сразу", loaded.board.meIsCandidate)
        assertEquals(9_000L, loaded.board.candidates.single().price)
    }

    @Test
    fun `delegate passes the candidate id and marks my choice`() = runTest {
        val candidate = sampleCandidate(support = 0)
        repository.boardResult = LegitimacyBoard(
            proposalId = "p1",
            candidates = listOf(candidate),
            members = 4,
        )
        val vm = viewModel()
        vm.load("p1")

        vm.delegate(candidate.memberId)

        assertEquals(candidate.memberId, repository.lastDelegateCandidateId)
        val loaded = vm.state.value as LegitimacyState.Loaded
        assertEquals(candidate.memberId, loaded.board.myDelegation)
        assertEquals(1L, loaded.board.candidates.single().support)
    }

    @Test
    fun `delegating to another candidate replaces the recipient`() = runTest {
        val boris = sampleCandidate(memberId = "b", displayName = "Борис")
        val vera = sampleCandidate(memberId = "v", displayName = "Вера")
        repository.boardResult = LegitimacyBoard(
            proposalId = "p1",
            candidates = listOf(boris, vera),
            members = 4,
        )
        val vm = viewModel()
        vm.load("p1")

        vm.delegate(boris.memberId)
        vm.delegate(vera.memberId)

        val loaded = vm.state.value as LegitimacyState.Loaded
        assertEquals("v", loaded.board.myDelegation)
        assertEquals(
            "одна единица доверия: у прежнего кандидата поддержки не остаётся",
            0L,
            loaded.board.candidates.first { it.memberId == "b" }.support,
        )
        assertEquals(1L, loaded.board.total)
    }

    @Test
    fun `revoke returns legitimacy to me`() = runTest {
        val candidate = sampleCandidate()
        repository.boardResult = LegitimacyBoard(
            proposalId = "p1",
            candidates = listOf(candidate),
            myDelegation = candidate.memberId,
            total = 1,
            members = 4,
        )
        val vm = viewModel()
        vm.load("p1")

        vm.revoke()

        assertEquals(1, repository.revokeCalls)
        val loaded = vm.state.value as LegitimacyState.Loaded
        assertNull(loaded.board.myDelegation)
        assertEquals(0L, loaded.board.total)
    }

    @Test
    fun `revoke failure keeps the board and shows the reason`() = runTest {
        val board = LegitimacyBoard(
            proposalId = "p1",
            candidates = listOf(sampleCandidate()),
            myDelegation = "22222222-2222-2222-2222-222222222222",
            members = 4,
        )
        repository.boardResult = board
        val vm = viewModel()
        vm.load("p1")
        repository.revokeError = ApiException(409, "Conflict", "задача уже закрыта")

        vm.revoke()

        assertEquals("задача уже закрыта", vm.message.value)
        assertEquals(
            "показанная доска не должна исчезнуть из-за ошибки",
            LegitimacyState.Loaded(board),
            vm.state.value,
        )
        assertFalse(vm.sending.value)
    }

    @Test
    fun `actions block repeated taps`() = runTest {
        repository.boardResult = LegitimacyBoard(
            proposalId = "p1",
            candidates = listOf(sampleCandidate()),
            members = 4,
        )
        repository.delegateGate = CompletableDeferred()
        val vm = viewModel()
        vm.load("p1")

        vm.delegate("22222222-2222-2222-2222-222222222222")
        vm.revoke()

        assertEquals("второй запрос не должен уйти, пока идёт первый", 1, repository.delegateCalls)
        assertEquals(0, repository.revokeCalls)
        assertTrue(vm.sending.value)

        repository.delegateGate?.complete(Unit)
    }

    @Test
    fun `lost mandate is explained to the person`() = runTest {
        repository.boardResult = LegitimacyBoard(
            proposalId = "p1",
            leader = sampleLeader(active = false, endReason = MANDATE_LOST),
            members = 10,
        )
        val vm = viewModel()

        vm.load("p1")

        val loaded = vm.state.value as LegitimacyState.Loaded
        val leader = loaded.board.leader
        assertTrue(leader != null && leader.lostByRevoke)
        assertFalse(loaded.board.hasActiveLeader)
    }

    @Test
    fun `dismiss clears the error`() = runTest {
        repository.boardError = NetworkException("offline")
        val vm = viewModel()
        vm.load("p1")

        vm.dismissMessage()

        assertNull(vm.message.value)
    }
}

/**
 * Профиль: кому передано доверие (`BR-LEGIT-3`, `BR-REP-2`).
 *
 * Строка «доверие у себя» — не отсутствие данных, а состояние: поэтому она
 * приходит и проверяется отдельно от переданной.
 */
class MyLegitimacyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLegitimacyRepository()

    private fun viewModel() = MyLegitimacyViewModel(repository)

    @Test
    fun `refresh shows delegations and reputation`() = runTest {
        val mine = MyLegitimacy(
            reputation = 26,
            delegations = listOf(
                Delegation(
                    proposalId = "p1",
                    proposalTitle = "Аренда помещения",
                    candidateId = "b",
                    candidateName = "Борис",
                ),
            ),
        )
        repository.myResult = mine
        val vm = viewModel()

        vm.refresh()

        assertEquals(MyLegitimacyState.Loaded(mine), vm.state.value)
        assertEquals(1, repository.myCalls)
    }

    @Test
    fun `proposal with own legitimacy is kept in the list`() = runTest {
        repository.myResult = MyLegitimacy(
            delegations = listOf(
                Delegation(proposalId = "p1", proposalTitle = "Аренда"),
            ),
        )
        val vm = viewModel()

        vm.refresh()

        val loaded = vm.state.value as MyLegitimacyState.Loaded
        assertEquals(1, loaded.mine.delegations.size)
        assertFalse(loaded.mine.delegations.single().isDelegated)
        assertEquals(1, loaded.mine.kept.size)
        assertEquals(0, loaded.mine.delegated.size)
    }

    @Test
    fun `failure on first load is shown`() = runTest {
        repository.myError = NetworkException("offline")
        val vm = viewModel()

        vm.refresh()

        assertEquals(
            MyLegitimacyState.Failure("нет связи с сервером, проверьте интернет"),
            vm.state.value,
        )
    }

    @Test
    fun `failure after load keeps the shown profile`() = runTest {
        val mine = MyLegitimacy(reputation = 12)
        repository.myResult = mine
        val vm = viewModel()
        vm.refresh()

        repository.myError = NetworkException("offline")
        vm.refresh()

        assertEquals(
            "сетевой сбой не должен стирать уже показанный профиль",
            MyLegitimacyState.Loaded(mine),
            vm.state.value,
        )
        assertEquals("нет связи с сервером, проверьте интернет", vm.message.value)
    }
}
