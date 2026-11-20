package ru.peerflow.demo.feature.proposal

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.peerflow.demo.data.api.ApiException
import ru.peerflow.demo.data.api.NetworkException
import ru.peerflow.demo.data.model.MyVote
import ru.peerflow.demo.data.model.ProposalSections
import ru.peerflow.demo.data.model.ProposalStage
import ru.peerflow.demo.testing.FakeProposalRepository
import ru.peerflow.demo.testing.MainDispatcherRule
import ru.peerflow.demo.testing.sampleProposal

/**
 * Карточка предложения и голосование (`BR-VOTE-4`, `BR-VOTE-9`–`BR-VOTE-13`).
 *
 * Карточка всегда перезапрашивается при открытии: счётчики из списка могли
 * устареть, а решение по предложению принимается по свежим голосам.
 */
class ProposalDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeProposalRepository()

    private fun viewModel() = ProposalDetailViewModel(repository)

    @Test
    fun `load fetches the proposal by id`() = runTest {
        val proposal = sampleProposal(id = "p1")
        repository.sections = ProposalSections(discussing = listOf(proposal))
        val vm = viewModel()

        vm.load("p1")

        assertEquals(ProposalDetailState.Loaded(proposal), vm.state.value)
        assertEquals("p1", repository.lastGetId)
    }

    @Test
    fun `load failure is shown with the server reason`() = runTest {
        repository.getError = ApiException(404, "Not Found", "предложение не найдено")
        val vm = viewModel()

        vm.load("missing")

        assertEquals(ProposalDetailState.Failure("предложение не найдено"), vm.state.value)
    }

    @Test
    fun `network failure on load is explained`() = runTest {
        repository.getError = NetworkException("timeout")
        val vm = viewModel()

        vm.load("p1")

        assertEquals(
            ProposalDetailState.Failure("нет связи с сервером, проверьте интернет"),
            vm.state.value,
        )
    }

    @Test
    fun `vote sends the chosen side and takes the counters from the server`() = runTest {
        val proposal = sampleProposal(id = "p1", votesFor = 2, votesAgainst = 1)
        repository.sections = ProposalSections(discussing = listOf(proposal))
        val vm = viewModel()
        vm.load("p1")

        vm.vote(inFavor = true)

        assertEquals("p1", repository.lastVoteId)
        assertEquals(true, repository.lastVoteInFavor)
        val loaded = (vm.state.value as ProposalDetailState.Loaded).proposal
        assertEquals(3, loaded.votesFor)
        assertEquals(MyVote(inFavor = true), loaded.myVote)
        assertFalse(vm.voting.value)
    }

    @Test
    fun `vote can be changed while the poll is open`() = runTest {
        repository.sections = ProposalSections(
            discussing = listOf(sampleProposal(id = "p1", myVote = MyVote(inFavor = true))),
        )
        val vm = viewModel()
        vm.load("p1")

        vm.vote(inFavor = false)

        val loaded = (vm.state.value as ProposalDetailState.Loaded).proposal
        assertEquals(MyVote(inFavor = false), loaded.myVote)
    }

    @Test
    fun `vote that closes the poll shows the new stage`() = runTest {
        repository.sections = ProposalSections(discussing = listOf(sampleProposal(id = "p1")))
        val vm = viewModel()
        vm.load("p1")

        repository.voteResult = sampleProposal(
            id = "p1",
            stage = ProposalStage.IN_WORK,
            votingClosed = true,
        )
        vm.vote(inFavor = true)

        val loaded = (vm.state.value as ProposalDetailState.Loaded).proposal
        assertEquals(ProposalStage.IN_WORK, loaded.stage)
        assertTrue(loaded.votingClosed)
        assertFalse(loaded.canVote)
    }

    @Test
    fun `failed vote keeps the card and shows the reason`() = runTest {
        val proposal = sampleProposal(id = "p1")
        repository.sections = ProposalSections(discussing = listOf(proposal))
        val vm = viewModel()
        vm.load("p1")
        val loaded = vm.state.value

        repository.voteError = ApiException(409, "Conflict", "голосование уже закрыто")
        vm.vote(inFavor = true)

        assertEquals(loaded, vm.state.value)
        assertEquals("голосование уже закрыто", vm.message.value)
        assertFalse(vm.voting.value)
    }

    @Test
    fun `vote before load does nothing`() = runTest {
        val vm = viewModel()

        vm.vote(inFavor = true)

        assertEquals(0, repository.voteCalls)
    }

    @Test
    fun `dismissMessage clears the message`() = runTest {
        repository.sections = ProposalSections(discussing = listOf(sampleProposal(id = "p1")))
        val vm = viewModel()
        vm.load("p1")
        repository.voteError = NetworkException("timeout")
        vm.vote(inFavor = true)
        assertEquals("нет связи с сервером, проверьте интернет", vm.message.value)

        vm.dismissMessage()

        assertNull(vm.message.value)
    }
}
