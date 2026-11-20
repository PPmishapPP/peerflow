package ru.peerflow.demo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.peerflow.demo.data.model.CurrentUser
import ru.peerflow.demo.feature.proposal.NewProposalEvent
import ru.peerflow.demo.feature.proposal.NewProposalViewModel
import ru.peerflow.demo.ui.components.Pill

/**
 * Экран внесения предложения — первый шаг жизненного цикла
 * (`BR-OVER-2`, `BR-LIFE-1`, `BR-BUDGET-2`).
 *
 * Бюджет обязателен по правилу сообщества: без него предложение не внести.
 * Проверяем это и здесь, до запроса, чтобы человек не ждал сеть ради «бюджет
 * обязателен». Сервер проверяет то же самое — он остаётся последней линией.
 */
@Composable
fun NewProposalScreen(
    currentUser: CurrentUser,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: NewProposalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                NewProposalEvent.Created -> onCreated()
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            NewProposalTopBar(onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AuthorCard(currentUser)

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        FieldLabel("Тема")
                        OutlinedTextField(
                            value = state.title,
                            onValueChange = viewModel::onTitleChange,
                            singleLine = true,
                            isError = state.titleError != null,
                            placeholder = { Text("О чём предложение, коротко") },
                            supportingText = state.titleError?.let { error ->
                                { Text(error) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        FieldLabel("Описание")
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = viewModel::onDescriptionChange,
                            minLines = 3,
                            isError = state.descriptionError != null,
                            placeholder = { Text("Что и зачем делаем") },
                            supportingText = state.descriptionError?.let { error ->
                                { Text(error) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        FieldLabel("Бюджет, ₽")
                        OutlinedTextField(
                            value = state.budget,
                            onValueChange = viewModel::onBudgetChange,
                            singleLine = true,
                            isError = state.budgetError != null,
                            prefix = { Text("₽ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            supportingText = {
                                Text(
                                    state.budgetError
                                        ?: "Бюджет обязателен — без него предложение не внести.",
                                )
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

                state.formError?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Button(
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Внести предложение")
                }
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
private fun AuthorCard(currentUser: CurrentUser) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Вносит ${currentUser.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Сразу нужно указать бюджет — это правило сообщества.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
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
