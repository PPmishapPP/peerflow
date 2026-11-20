package ru.peerflow.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.Color
import ru.peerflow.demo.data.DemoData
import ru.peerflow.demo.data.Proposal
import ru.peerflow.demo.data.ProposalStage
import ru.peerflow.demo.data.SortTab

class DemoDataTest {

    @Test
    fun popularSortPutsRealDiscussionFirstAndNoiseLast() {
        val sorted = DemoData.sorted(SortTab.POPULAR)
        assertEquals("p1", sorted.first().id)
        assertEquals("p2", sorted.last().id)
    }

    @Test
    fun popularityDoesNotRewardSpamVolume() {
        assertTrue(DemoData.rentProposal.popularity > DemoData.fiveGProposal.popularity)
    }

    @Test
    fun budgetSortOrdersByBudgetDescending() {
        val sorted = DemoData.sorted(SortTab.BUDGET)
        assertEquals(12000, sorted.first().budget)
        assertEquals(2000, sorted.last().budget)
    }

    @Test
    fun threeSpamMarksHideMessage() {
        val hidden = DemoData.rentProposal.messages.first { it.id == "r4" }
        assertTrue(hidden.isHidden)
    }

    @Test
    fun inWorkProposalsHaveLeaderAndReport() {
        val inWork = DemoData.allProposals.filter { it.stage == ProposalStage.IN_WORK }
        assertTrue(inWork.size >= 2)
        assertTrue(inWork.all { it.leader != null })
        assertTrue(inWork.all { it.report != null })
    }

    @Test
    fun proposalInWorkKeepsLegitimacyAboveThreshold() {
        // 21 из 30 — лидер держится, так как порог удержания 70%.
        assertEquals(70, DemoData.rentProposal.legitimacyPercent)
        assertTrue(DemoData.rentProposal.legitimacyPercent >= 70)
    }

    @Test
    fun newProposalIsMarkedAndOpensByItsId() {
        // Внесённое предложение живёт в стадии обсуждения и открывается по id.
        val created = Proposal(
            id = "new-1",
            title = "Закупить проектор для мероприятий",
            summary = "Свой проектор вместо аренды.",
            author = DemoData.currentUser,
            budget = 5000,
            comments = 0,
            participants = 1,
            forVotes = 0,
            againstVotes = 0,
            stage = ProposalStage.DISCUSSION,
            heat = "Только внесено",
            heatColor = Color(0xFF4F46E5),
            justCreated = true,
        )

        assertEquals(ProposalStage.DISCUSSION, created.stage)
        assertTrue(created.justCreated)
        // Демо-предложения по умолчанию не помечены как только что внесённые.
        assertTrue(DemoData.allProposals.none { it.justCreated })
    }
}
