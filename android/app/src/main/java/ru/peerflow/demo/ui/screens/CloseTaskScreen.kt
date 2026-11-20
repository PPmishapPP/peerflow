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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import ru.peerflow.demo.data.DemoData
import ru.peerflow.demo.ui.components.MemberAvatar
import ru.peerflow.demo.ui.components.Pill

@Composable
fun CloseTaskScreen(
    proposalId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val proposal = DemoData.byId(proposalId)
    val leader = proposal.leader ?: DemoData.vera
    var voted by remember { mutableStateOf<String?>(null) }
    val done = voted == "done"

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
                    Text("Закрытие задачи", style = MaterialTheme.typography.titleMedium)
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
                                    Text("Лидер задачи", style = MaterialTheme.typography.labelSmall)
                                }
                                Pill(text = "⚖ ${proposal.legitimacyPercent}%", color = Color(0xFF7C3AED))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Лидер объявил задачу выполненной. Сообщество оценивает результат.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Выполнено?", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { if (done) 0.87f else 0.5f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color(0xFFE5E7EB),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (done) "Выполнено 20 · Не выполнено 3" else "Голосование идёт",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { voted = "done" },
                                    modifier = Modifier.weight(1f),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = if (done) Color(0xFF15803D) else MaterialTheme.colorScheme.primary,
                                    ),
                                ) { Text("✓ Выполнено") }
                                Button(
                                    onClick = { voted = "fail" },
                                    modifier = Modifier.weight(1f),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = if (voted == "fail") Color(0xFFB91C1C) else Color(0xFF94A3B8),
                                    ),
                                ) { Text("Не выполнено") }
                            }
                        }
                    }
                }
                if (done) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Легитимность → репутация",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF6D28D9),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Оставшаяся у лидера легитимность переходит в репутацию.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Pill(text = "⚖ ${proposal.leaderLegitimacy} легитимности", color = Color(0xFF7C3AED))
                                    Text("   →   ", style = MaterialTheme.typography.titleMedium)
                                    Pill(text = "★ +${proposal.leaderLegitimacy} репутации", color = Color(0xFFB45309))
                                }
                            }
                        }
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
