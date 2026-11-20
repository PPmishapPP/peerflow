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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.peerflow.demo.data.DemoData
import ru.peerflow.demo.data.Proposal
import ru.peerflow.demo.data.ProposalStage
import ru.peerflow.demo.ui.components.MemberAvatar
import ru.peerflow.demo.ui.components.Pill

/**
 * Экран внесения предложения — первый шаг жизненного цикла.
 *
 * Поля преднаполнены примером, чтобы на демонстрации показать сам механизм
 * (бюджет обязателен), не набирая текст вживую. После отправки предложение
 * появляется в списке в секции «На обсуждении».
 */
@Composable
fun NewProposalScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    var title by remember { mutableStateOf("Закупить проектор для мероприятий") }
    var summary by remember {
        mutableStateOf(
            "Свой проектор избавит от аренды и возни с перевозкой. " +
                "Возьмём компактный Full HD, чтобы помещался в шкаф.",
        )
    }
    var budget by remember { mutableStateOf("35 000") }

    val budgetValue = budget.filter { it.isDigit() }.toIntOrNull() ?: 0
    val canSubmit = title.isNotBlank() && summary.isNotBlank() && budgetValue > 0

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            NewProposalTopBar(onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AuthorCard()

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        FieldLabel("Тема")
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            singleLine = true,
                            placeholder = { Text("О чём предложение, коротко") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        FieldLabel("Описание")
                        OutlinedTextField(
                            value = summary,
                            onValueChange = { summary = it },
                            minLines = 3,
                            placeholder = { Text("Что и зачем делаем") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        FieldLabel("Бюджет, ₽")
                        OutlinedTextField(
                            value = budget,
                            onValueChange = { budget = it.filter { ch -> ch.isDigit() } },
                            singleLine = true,
                            prefix = { Text("₽ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            supportingText = {
                                Text("Бюджет обязателен — без него предложение не внести.")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Pill(text = "На обсуждении", color = Color(0xFF4F46E5))
                        Spacer(Modifier.weight(1f))
                        Text(
                            "после внесения",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
            }

            Button(
                onClick = {
                    DemoData.addProposal(
                        Proposal(
                            id = "new-" + DemoData.nextCreatedId(),
                            title = title.trim(),
                            summary = summary.trim(),
                            author = DemoData.currentUser,
                            budget = budgetValue,
                            comments = 0,
                            participants = 1,
                            forVotes = 0,
                            againstVotes = 0,
                            stage = ProposalStage.DISCUSSION,
                            heat = "Только внесено",
                            heatColor = Color(0xFF4F46E5),
                            justCreated = true,
                        ),
                    )
                    onCreated()
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Внести предложение")
            }
        }
    }
}

@Composable
private fun NewProposalTopBar(onBack: () -> Unit) {
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
                "Новое предложение",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun AuthorCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MemberAvatar(DemoData.currentUser, size = 40.dp)
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)) {
                Text(
                    "Вносит ${DemoData.currentUser.name}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Сразу нужно указать бюджет — это правило сообщества.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Pill(text = "★ ${DemoData.currentUser.reputation}", color = Color(0xFFB45309))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}
