package ru.peerflow.demo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import ru.peerflow.demo.data.model.ClosureState
import ru.peerflow.demo.feature.report.ReportState
import ru.peerflow.demo.feature.report.ReportViewModel
import ru.peerflow.demo.ui.components.CenteredBox
import ru.peerflow.demo.ui.components.DetailTopBar
import ru.peerflow.demo.ui.components.MessageBanner
import ru.peerflow.demo.ui.components.Pill

/**
 * Закрытие задачи: лидер запускает процедуру, сообщество решает, выполнена ли
 * работа (`BR-LIFE-8`, `BR-LIFE-9`, `BR-VOTE-5`, `BR-OVER-9`).
 *
 * Лидер не может объявить свою работу выполненной сам — он лишь открывает
 * голосование. Счётчики, срок и итог считает сервер: правило большинства и
 * срок голосования — общие для сообщества, а не оформление экрана.
 *
 * При закрытии оставшаяся легитимность лидера становится репутацией
 * (`BR-REP-1`) — экран показывает это явно, иначе доверие выглядело бы
 * пропавшим.
 */
@Composable
fun CloseTaskScreen(
    proposalId: String,
    currentUserId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(proposalId) { viewModel.load(proposalId) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailTopBar(title = "Закрытие задачи", onBack = onBack)

            when (val current = state) {
                ReportState.Loading -> CenteredBox { CircularProgressIndicator() }

                is ReportState.Failure -> CenteredBox {
                    Text(
                        "Не удалось открыть голосование",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = viewModel::refresh) { Text("Повторить") }
                }

                is ReportState.Loaded -> {
                    val closure = current.closure
                    val iAmLeader = closure.leaderId == currentUserId

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { LeaderCard(closure) }

                        message?.let { text ->
                            item { MessageBanner(text = text, onDismiss = viewModel::dismissMessage) }
                        }

                        if (closure.closed) {
                            item { ClosedCard(closure) }
                        } else {
                            item {
                                VoteCard(
                                    closure = closure,
                                    sending = sending,
                                    iAmLeader = iAmLeader,
                                    onOpen = viewModel::openClosure,
                                    onVote = viewModel::vote,
                                )
                            }
                        }

                        item {
                            Button(
                                onClick = onDone,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("К списку предложений") }
                        }
                    }
                }
            }
        }
    }
}

/** Кто лидер и что это за процедура. */
@Composable
private fun LeaderCard(closure: ClosureState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        closure.leaderName ?: "Лидер не выбран",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("Лидер задачи", style = MaterialTheme.typography.labelSmall)
                }
                Pill(
                    text = closure.stage.label,
                    color = when (closure.stage) {
                        ru.peerflow.demo.data.model.ProposalStage.CLOSED -> Color(0xFF57534E)
                        ru.peerflow.demo.data.model.ProposalStage.IN_WORK -> Color(0xFF0F766E)
                        else -> Color(0xFF4F46E5)
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Лидер объявляет задачу выполненной. Решает сообщество: лидер " +
                    "только запускает процедуру закрытия.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Голосование «выполнено / не выполнено». */
@Composable
private fun VoteCard(
    closure: ClosureState,
    sending: Boolean,
    iAmLeader: Boolean,
    onOpen: () -> Unit,
    onVote: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (closure.open) "Выполнено?" else "Закрытие ещё не запущено",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(10.dp))

            if (closure.notStarted) {
                Text(
                    "Пока лидер не запустил процедуру, голосовать не о чем.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                if (iAmLeader) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onOpen,
                        enabled = !sending,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Запустить закрытие задачи") }
                }
            } else {
                LinearProgressIndicator(
                    progress = { closure.completedShare },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0xFFE5E7EB),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Выполнено ${closure.votesFor} · Не выполнено ${closure.votesAgainst}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    closure.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )

                if (closure.canVote) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onVote(true) },
                            enabled = !sending,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (closure.myVote == true) {
                                    Color(0xFF15803D)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            ),
                        ) {
                            Text(if (closure.myVote == true) "✓ Выполнено" else "Выполнено")
                        }
                        Button(
                            onClick = { onVote(false) },
                            enabled = !sending,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (closure.myVote == false) {
                                    Color(0xFFB91C1C)
                                } else {
                                    Color(0xFF94A3B8)
                                },
                            ),
                        ) {
                            Text(if (closure.myVote == false) "✓ Не выполнено" else "Не выполнено")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (closure.myVote != null) {
                            "Свой ответ можно поменять, пока голосование не закрыто."
                        } else {
                            "Ответ один на человека. Пока голосование открыто, его можно поменять."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

/**
 * Итог закрытия: легитимность стала репутацией (`BR-REP-1`).
 *
 * Показываем переход явно: без этого человек видел бы, что доверие лидеру
 * «исчезло» при закрытии, и не понял бы, что оно стало достижением.
 */
@Composable
private fun ClosedCard(closure: ClosureState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Задача закрыта",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF6D28D9),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Сообщество признало работу выполненной.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    text = "★ +${closure.reputationAwarded} репутации",
                    color = Color(0xFFB45309),
                )
                Spacer(Modifier.height(0.dp))
                Text(
                    "  ${closure.leaderName ?: "лидеру"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Оставшаяся у лидера легитимность перешла в репутацию.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
