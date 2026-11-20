package ru.peerflow.demo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.peerflow.demo.data.model.ProposalStage
import ru.peerflow.demo.data.model.ProposalSummary
import ru.peerflow.demo.feature.proposal.ProposalDetailState
import ru.peerflow.demo.feature.proposal.ProposalDetailViewModel
import ru.peerflow.demo.ui.components.BudgetBadge
import ru.peerflow.demo.ui.components.Pill

/**
 * Карточка предложения и голосование по нему (`BR-VOTE-4`, `BR-VOTE-9`–
 * `BR-VOTE-13`).
 *
 * Голоса, стадия и срок голосования приходят с сервера: итог считает он, а не
 * клиент, иначе правило «простое большинство» разъехалось бы между
 * платформами. Экран показывает, сколько голосов уже подано, сколько осталось
 * до конца голосования и почему решение ещё не принято.
 */
@Composable
fun ProposalDetailScreen(
    proposalId: String,
    onBack: () -> Unit,
    viewModel: ProposalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val voting by viewModel.voting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(proposalId) { viewModel.load(proposalId) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailTopBar(
                title = (state as? ProposalDetailState.Loaded)?.proposal?.title.orEmpty(),
                onBack = onBack,
            )

            when (val current = state) {
                ProposalDetailState.Loading -> CenteredBox { CircularProgressIndicator() }

                is ProposalDetailState.Failure -> CenteredBox {
                    Text(
                        "Не удалось открыть предложение",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = viewModel::refresh) { Text("Повторить") }
                }

                is ProposalDetailState.Loaded -> LoadedProposal(
                    proposal = current.proposal,
                    voting = voting,
                    message = message,
                    onVote = viewModel::vote,
                    onDismissMessage = viewModel::dismissMessage,
                )
            }
        }
    }
}

@Composable
private fun LoadedProposal(
    proposal: ProposalSummary,
    voting: Boolean,
    message: String?,
    onVote: (Boolean) -> Unit,
    onDismissMessage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderCard(proposal)
        DecisionCard(
            proposal = proposal,
            voting = voting,
            onVote = onVote,
        )
        message?.let { text ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissMessage) { Text("Понятно") }
                }
            }
        }
        Text(
            "Обсуждение и отчёты появятся в следующих версиях: сейчас решение " +
                "принимается голосованием «делаем / не делаем».",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun HeaderCard(proposal: ProposalSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(text = proposal.stage.label, color = stageColor(proposal.stage))
                Spacer(Modifier.weight(1f))
                BudgetBadge(proposal.budget)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                proposal.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(proposal.description, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Внёс ${proposal.authorName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                Spacer(Modifier.weight(1f))
                Pill(
                    text = "👥 ${proposal.participants} · 🔥 ${proposal.popularity}",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DecisionCard(
    proposal: ProposalSummary,
    voting: Boolean,
    onVote: (Boolean) -> Unit,
) {
    val total = (proposal.votesFor + proposal.votesAgainst).coerceAtLeast(1)
    val canVote = proposal.canVote

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Решение: делаем или нет?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { proposal.votesFor.toFloat() / total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color(0xFFE5E7EB),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "За ${proposal.votesFor} · Против ${proposal.votesAgainst}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                proposal.votingStatus,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )

            if (canVote) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onVote(true) },
                        enabled = !voting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (proposal.myVote?.inFavor == true) {
                                Color(0xFF15803D)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        ),
                    ) {
                        Text(if (proposal.myVote?.inFavor == true) "✓ За" else "За")
                    }
                    Button(
                        onClick = { onVote(false) },
                        enabled = !voting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (proposal.myVote?.inFavor == false) {
                                Color(0xFFB91C1C)
                            } else {
                                Color(0xFF94A3B8)
                            },
                        ),
                    ) {
                        Text(if (proposal.myVote?.inFavor == false) "✓ Против" else "Против")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (proposal.myVote != null) {
                        "Свой голос можно поменять, пока голосование не закрыто."
                    } else {
                        "Голос один на человека. Пока голосование открыто, его можно поменять."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            } else {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when (proposal.stage) {
                            ProposalStage.IN_WORK -> "Решение принято: делаем."
                            ProposalStage.REJECTED -> "Решение принято: не делаем."
                            ProposalStage.CLOSED -> "Задача закрыта."
                            ProposalStage.DISCUSSION -> "Голосование закрыто."
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(12.dp),
                    )
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
private fun CenteredBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

private fun stageColor(stage: ProposalStage): Color = when (stage) {
    ProposalStage.IN_WORK -> Color(0xFF0F766E)
    ProposalStage.DISCUSSION -> Color(0xFF4F46E5)
    ProposalStage.CLOSED -> Color(0xFF57534E)
    ProposalStage.REJECTED -> Color(0xFFB91C1C)
}
