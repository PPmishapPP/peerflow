package ru.peerflow.demo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.peerflow.demo.data.DemoData
import ru.peerflow.demo.data.Proposal
import ru.peerflow.demo.data.ProposalStage
import ru.peerflow.demo.data.SortTab
import ru.peerflow.demo.ui.components.BudgetBadge
import ru.peerflow.demo.ui.components.LegitimacyBadge
import ru.peerflow.demo.ui.components.MemberAvatar
import ru.peerflow.demo.ui.components.Pill

@Composable
fun ProposalListScreen(
    onOpenProposal: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onNewProposal: () -> Unit,
) {
    var tab by remember { mutableStateOf(SortTab.POPULAR) }
    val sorted = DemoData.sorted(tab)
    val inWork = sorted.filter { it.stage == ProposalStage.IN_WORK }
    val discussing = sorted.filter { it.stage == ProposalStage.DISCUSSION }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                CommunityHeader(onOpenProfile)
                SortRow(selected = tab, onSelect = { tab = it })
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        SectionHeader(
                            title = "🔧 В работе",
                            subtitle = "Решение принято, есть лидер и бюджет",
                            count = inWork.size,
                            accent = Color(0xFF0F766E),
                        )
                    }
                    items(inWork, key = { it.id }) { proposal ->
                        ProposalCard(proposal, onClick = { onOpenProposal(proposal.id) })
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        SectionHeader(
                            title = "💬 На обсуждении",
                            subtitle = "Решаем, делать ли вообще",
                            count = discussing.size,
                            accent = Color(0xFF4F46E5),
                        )
                    }
                    items(discussing, key = { it.id }) { proposal ->
                        ProposalCard(proposal, onClick = { onOpenProposal(proposal.id) })
                    }
                }
            }
            NewProposalButton(
                onClick = onNewProposal,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp),
            )
        }
    }
}

/** Круглая «+» для внесения нового предложения. */
@Composable
private fun NewProposalButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
        shadowElevation = 8.dp,
        modifier = modifier.size(60.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(26.dp)) {
                val w = size.width
                val h = size.height
                val stroke = w * 0.13f
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.5f, h * 0.15f),
                    end = Offset(w * 0.5f, h * 0.85f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.15f, h * 0.5f),
                    end = Offset(w * 0.85f, h * 0.5f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun CommunityHeader(onOpenProfile: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Сообщество «Маяк»",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Решаем вместе. Исполняет — тот, кому доверили.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onOpenProfile() },
            ) {
                MemberAvatar(DemoData.currentUser)
                Text(
                    "Профиль",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Pill(
                text = "★ ${DemoData.currentUser.reputation}",
                color = Color(0xFFB45309),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun SortRow(selected: SortTab, onSelect: (SortTab) -> Unit) {
    val tabs = listOf(
        SortTab.POPULAR to "Популярные",
        SortTab.BUDGET to "По бюджету",
        SortTab.NEW to "Новые",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { (value, label) ->
            val active = value == selected
            Surface(
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                modifier = Modifier.clickable { onSelect(value) },
            ) {
                Text(
                    label,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    count: Int,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        Surface(color = accent.copy(alpha = 0.12f), shape = androidx.compose.foundation.shape.CircleShape) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun ProposalCard(proposal: Proposal, onClick: () -> Unit) {
    val inWork = proposal.stage == ProposalStage.IN_WORK
    val muted = proposal.id == "p2"
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (muted) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (muted) 0.dp else 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(text = proposal.heat, color = proposal.heatColor)
                Spacer(Modifier.weight(1f))
                Text(
                    "🔥 ${proposal.popularity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                proposal.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BudgetBadge(proposal.budget)
                Pill(
                    text = "💬 ${proposal.comments} · 👥 ${proposal.participants}",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (proposal.justCreated) {
                Text(
                    "Внесено только что — ждёт обсуждения",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4F46E5),
                )
            } else if (inWork && proposal.leader != null) {
                // В работе важнее всего: кто исполняет и насколько ему ещё доверяют.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemberAvatar(proposal.leader, size = 28.dp)
                    Column(modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)) {
                        Text(
                            proposal.leader.name,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            "Лидер задачи",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                    LegitimacyBadge(proposal.legitimacyPercent)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "За ${proposal.forVotes} · Против ${proposal.againstVotes}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.weight(1f))
                    if (proposal.candidates.isNotEmpty()) {
                        Pill(
                            text = "Кандидаты: ${proposal.candidates.size}",
                            color = Color(0xFF7C3AED),
                        )
                    }
                }
            }
            if (muted) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Много сообщений, но сообщество против — внизу списка",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}
