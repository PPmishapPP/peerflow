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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.peerflow.demo.data.DemoData
import ru.peerflow.demo.data.Reaction
import ru.peerflow.demo.ui.components.MemberAvatar
import ru.peerflow.demo.ui.components.Pill
import ru.peerflow.demo.ui.components.ReactionChip

@Composable
fun LeaderReportScreen(
    proposalId: String,
    onBack: () -> Unit,
    onCloseTask: () -> Unit,
) {
    val proposal = DemoData.byId(proposalId)
    val leader = proposal.leader ?: DemoData.vera
    val report = proposal.report ?: DemoData.rentProposal.report!!

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    Text("Отчёт лидера", style = MaterialTheme.typography.titleMedium)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MemberAvatar(leader)
                                Column(modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp)) {
                                    Text(leader.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Лидер задачи · исполняет с мандатом",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                                Pill(text = "★ ${leader.reputation}", color = Color(0xFFB45309))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                report.text,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            report.attachment?.let { attachment ->
                                Spacer(Modifier.height(12.dp))
                                Surface(
                                    color = Color(0xFFEFF6FF),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
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
                        }
                    }
                }
                item {
                    Text("Обсуждение отчёта", style = MaterialTheme.typography.titleMedium)
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MemberAvatar(report.commentAuthor, size = 30.dp)
                                Text(
                                    "  ${report.commentAuthor.name}",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                report.commentText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ReactionChip("+", report.commentPlus, Reaction.PLUS.color, active = true)
                                ReactionChip("−", 0, Reaction.MINUS.color, active = false)
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Написать в обсуждение отчёта") }
                }
                item {
                    Button(
                        onClick = onCloseTask,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Лидер запускает закрытие задачи →") }
                }
            }
        }
    }
}
