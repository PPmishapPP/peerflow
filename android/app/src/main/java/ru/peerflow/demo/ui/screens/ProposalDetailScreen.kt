package ru.peerflow.demo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.peerflow.demo.data.Candidate
import ru.peerflow.demo.data.DemoData
import ru.peerflow.demo.data.DiscussionMessage
import ru.peerflow.demo.data.LeaderReport
import ru.peerflow.demo.data.MessageFilter
import ru.peerflow.demo.data.Proposal
import ru.peerflow.demo.data.ProposalStage
import ru.peerflow.demo.data.Reaction
import ru.peerflow.demo.ui.components.BudgetBadge
import ru.peerflow.demo.ui.components.LegitimacyBadge
import ru.peerflow.demo.ui.components.MemberAvatar
import ru.peerflow.demo.ui.components.Pill
import ru.peerflow.demo.ui.components.ReactionChip

@Composable
fun ProposalDetailScreen(
    proposalId: String,
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
) {
    val proposal = DemoData.byId(proposalId)
    var filter by remember { mutableStateOf(MessageFilter.POPULAR) }
    var myVote by remember { mutableStateOf<String?>(null) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailTopBar(proposal.title, onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ProposalHeaderCard(proposal) }

                if (proposal.stage == ProposalStage.IN_WORK) {
                    // В работе: сначала лидер и его мандат, потом отчёт, потом дискуссия.
                    item {
                        LeaderCard(
                            proposal = proposal,
                            onOpenReport = onOpenReport,
                        )
                    }
                    proposal.report?.let { report ->
                        item { ReportCard(report, onOpenReport) }
                    }
                } else {
                    item {
                        DecisionCard(proposal.forVotes, proposal.againstVotes, myVote) { myVote = it }
                    }
                    if (proposal.candidates.isNotEmpty()) {
                        item { CandidatesCard(proposal.candidates, proposal.leaderLegitimacy, proposal.leaderLegitimacyFrom) }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Дискуссия", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        MessageFilterRow(filter) { filter = it }
                    }
                }
                val ordered = when (filter) {
                    MessageFilter.POPULAR -> proposal.messages.sortedByDescending { it.plus }
                    MessageFilter.NEW -> proposal.messages.reversed()
                }
                items(ordered.size) { index ->
                    MessageCard(ordered[index])
                }
            }
        }
    }
}

@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("‹ Назад", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProposalHeaderCard(proposal: Proposal) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    text = proposal.stage.label,
                    color = if (proposal.stage == ProposalStage.IN_WORK) Color(0xFF0F766E) else Color(0xFF4F46E5),
                )
                Spacer(Modifier.weight(1f))
                BudgetBadge(proposal.budget)
            }
            Spacer(Modifier.height(10.dp))
            Text(proposal.summary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Карточка лидера — сердце экрана «в работе». Здесь же кнопка отзыва
 * легитимности: главное обещание PeerFlow в одном действии.
 */
@Composable
private fun LeaderCard(
    proposal: Proposal,
    onOpenReport: () -> Unit,
) {
    val leader = proposal.leader ?: return
    var delegated by remember { mutableStateOf(proposal.myLegitimacyDelegated) }
    var justRecalled by remember { mutableStateOf(false) }
    val percent = if (delegated) proposal.legitimacyPercent else proposal.legitimacyPercent - 5

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(leader, size = 44.dp)
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)) {
                    Text(leader.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Лидер задачи · исполняет по нашему доверию",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Pill(text = "★ ${leader.reputation}", color = Color(0xFFB45309))
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Осталось доверия",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.weight(1f))
                LegitimacyBadge(percent)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = if (percent >= 70) Color(0xFF7C3AED) else Color(0xFFEA580C),
                trackColor = Color(0xFFE5E7EB),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Порог удержания: ниже 70% мандат теряется, выбираем нового лидера.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )

            Spacer(Modifier.height(14.dp))
            if (justRecalled) {
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Вы забрали свою легитимность. Мандат держится, пока доверие выше 70%.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB91C1C),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else if (delegated) {
                OutlinedButton(
                    onClick = {
                        delegated = false
                        justRecalled = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Забрать свою легитимность")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ваша легитимность сейчас у лидера. Можно вернуть в любой момент.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            } else {
                Button(
                    onClick = {
                        delegated = true
                        justRecalled = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Передать свою легитимность")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Вы пока не поддержали этого лидера.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onOpenReport,
                    modifier = Modifier.weight(1f),
                ) { Text("Отчёт лидера") }
            }
        }
    }
}

@Composable
private fun ReportCard(report: LeaderReport, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Последний отчёт", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(report.text, style = MaterialTheme.typography.bodyMedium)
            report.attachment?.let { attachment ->
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "📎 $attachment",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF0369A1),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Обсудить отчёт →",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DecisionCard(
    forVotes: Int,
    againstVotes: Int,
    myVote: String?,
    onVote: (String) -> Unit,
) {
    val total = (forVotes + againstVotes).coerceAtLeast(1)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Решение: делаем или нет?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { forVotes.toFloat() / total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color(0xFFE5E7EB),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "За $forVotes · Против $againstVotes · явка открыта",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onVote("for") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (myVote == "for") Color(0xFF15803D) else MaterialTheme.colorScheme.primary,
                    ),
                ) { Text(if (myVote == "for") "✓ За" else "За") }
                Button(
                    onClick = { onVote("against") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (myVote == "against") Color(0xFFB91C1C) else Color(0xFF94A3B8),
                    ),
                ) { Text(if (myVote == "against") "✓ Против" else "Против") }
            }
        }
    }
}

@Composable
private fun CandidatesCard(candidates: List<Candidate>, legit: Int, legitFrom: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Кандидаты в лидеры", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Им можно передать свою легитимность до решения о задаче.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))
            candidates.forEachIndexed { index, candidate ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    MemberAvatar(candidate.member, size = 34.dp)
                    Column(modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)) {
                        Text(candidate.member.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Репутация ★ ${candidate.member.reputation} · в сообществе с ${candidate.member.joined}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                    Pill(text = "⚖ ${candidate.legitimacy}", color = Color(0xFF7C3AED))
                }
            }
            if (legitFrom > 0) {
                Spacer(Modifier.height(10.dp))
                LegitimacyBadge(legit * 100 / legitFrom)
            }
        }
    }
}

@Composable
private fun MessageFilterRow(filter: MessageFilter, onSelect: (MessageFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(MessageFilter.POPULAR to "Популярные", MessageFilter.NEW to "Новые").forEach { (value, label) ->
            val active = value == filter
            Surface(
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(50),
                modifier = Modifier.clickable { onSelect(value) },
            ) {
                Text(
                    label,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageCard(message: DiscussionMessage) {
    if (message.isHidden) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "Сообщение скрыто",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Помечено как спам ${message.spam} участниками. Нажмите, чтобы показать.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                )
            }
        }
        return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(message.author, size = 32.dp)
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)) {
                    Text(message.author.name, style = MaterialTheme.typography.labelLarge)
                    Text(
                        message.agoLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                if (message.plus >= 10) {
                    Pill(text = "Топ аргумент", color = Color(0xFF15803D))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(message.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReactionChip("+", message.plus, Reaction.PLUS.color, active = message.plus > 0)
                ReactionChip("−", message.minus, Reaction.MINUS.color, active = false)
                ReactionChip("⚑", message.spam, Reaction.SPAM.color, active = false)
            }
            if (message.replies.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                message.replies.forEach { reply ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, bottom = 8.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MemberAvatar(reply.author, size = 24.dp)
                                Text(
                                    "  ${reply.author.name} · ${reply.agoLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(reply.text, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ReactionChip("+", reply.plus, Reaction.PLUS.color, active = reply.plus > 0)
                                ReactionChip("−", reply.minus, Reaction.MINUS.color, active = false)
                            }
                        }
                    }
                }
            }
        }
    }
}
