package ru.peerflow.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.peerflow.demo.data.model.Candidate
import ru.peerflow.demo.data.model.Leader
import ru.peerflow.demo.data.model.LegitimacyBoard
import ru.peerflow.demo.feature.legitimacy.LegitimacyState

/**
 * Блок легитимности и лидера под задачей (`BR-LEGIT-2`–`BR-LEGIT-5`,
 * `BR-LEADER-1`–`BR-LEADER-7`).
 *
 * Показываем три вещи, которые нельзя разделить без вранья: кто лидер сейчас,
 * с какой поддержкой он держится и кому человек передал своё доверие. Шкала
 * удержания берётся из серверного [Leader.requiredSupport], а не считается из
 * процентов здесь: порог — правило сообщества (`BR-LEADER-5`).
 */
@Composable
fun LegitimacySection(
    state: LegitimacyState,
    sending: Boolean,
    message: String?,
    onNominate: () -> Unit,
    onDelegate: (String) -> Unit,
    onRevoke: () -> Unit,
    onRetry: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Легитимность и лидер", style = MaterialTheme.typography.titleMedium)

            when (state) {
                LegitimacyState.Loading -> Text(
                    "Загружаем поддержку…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                is LegitimacyState.Failure -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text("Повторить") }
                }

                is LegitimacyState.Loaded -> {
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
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = onDismissMessage) { Text("Понятно") }
                            }
                        }
                    }
                    LoadedBoard(
                        board = state.board,
                        sending = sending,
                        onNominate = onNominate,
                        onDelegate = onDelegate,
                        onRevoke = onRevoke,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadedBoard(
    board: LegitimacyBoard,
    sending: Boolean,
    onNominate: () -> Unit,
    onDelegate: (String) -> Unit,
    onRevoke: () -> Unit,
) {
    board.leader?.let { leader ->
        LeaderBlock(leader = leader, board = board, sending = sending, onRevoke = onRevoke)
    }

    if (board.candidates.isEmpty()) {
        Text(
            if (board.leader == null) {
                "Кандидатов пока нет. Пока идёт обсуждение, любой член может выдвинуться."
            } else {
                "Кандидатов на эту задачу больше нет."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
    } else {
        Text(
            "Кандидаты",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        board.candidates.forEach { candidate ->
            CandidateRow(
                candidate = candidate,
                delegated = board.myDelegation == candidate.memberId,
                canDelegate = board.myDelegation != candidate.memberId && !candidate.me,
                enabled = !sending,
                onDelegate = { onDelegate(candidate.memberId) },
            )
        }
    }

    if (!board.meIsCandidate) {
        OutlinedButton(
            onClick = onNominate,
            enabled = !sending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Выдвинуть себя в лидеры")
        }
    }

    SupportSummary(board = board)
}

@Composable
private fun LeaderBlock(
    leader: Leader,
    board: LegitimacyBoard,
    sending: Boolean,
    onRevoke: () -> Unit,
) {
    // Действующий мандат — спокойный акцент; утраченный — предупреждение:
    // человеку важно отличать «работаем» от «лидер больше не лидер».
    val accent = when {
        leader.active -> Color(0xFF7C3AED)
        leader.lostByRevoke -> Color(0xFFB91C1C)
        else -> Color(0xFF57534E)
    }

    Surface(
        color = accent.copy(alpha = 0.07f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        leader.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            leader.active -> "Лидер задачи · мандат действует"
                            leader.lostByRevoke -> "Мандат утрачен: доверие отозвано"
                            else -> "Мандат завершён: задача закрыта"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                }
                Pill(
                    text = "⚖ ${leader.support} из ${board.members}",
                    color = accent,
                )
            }

            if (leader.active) {
                LinearProgressIndicator(
                    progress = {
                        if (leader.requiredSupport <= 0) {
                            1f
                        } else {
                            (leader.support.toFloat() / leader.requiredSupport).coerceIn(0f, 1f)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = accent,
                    trackColor = Color(0xFFE5E7EB),
                )
                Text(
                    if (leader.supportAboveRequired > 0) {
                        "Нужно не меньше ${leader.requiredSupport}. Можно потерять ещё " +
                            "${leader.supportAboveRequired} — и мандат уйдёт."
                    } else {
                        "Держится на нижней границе: ${leader.requiredSupport} из " +
                            "${leader.initialSupport}. Следующий отзыв заберёт мандат."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            } else if (leader.lostByRevoke) {
                Text(
                    "Сообщество заново решает, продолжать ли задачу.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            if (board.myDelegation != null) {
                TextButton(onClick = onRevoke, enabled = !sending) {
                    Text("Забрать свою легитимность")
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: Candidate,
    delegated: Boolean,
    canDelegate: Boolean,
    enabled: Boolean,
    onDelegate: () -> Unit,
) {
    val accent = if (delegated) Color(0xFF7C3AED) else Color(0xFF334155)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = accent.copy(alpha = if (delegated) 0.10f else 0.04f),
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled && canDelegate, onClick = onDelegate)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (candidate.me) "${candidate.displayName} (вы)" else candidate.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (delegated) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                "★ ${candidate.reputation} репутации",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        Pill(text = "⚖ ${candidate.support}", color = accent)
        if (delegated) {
            Spacer(Modifier.height(0.dp))
            Text(
                "  ✓ моя",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
        } else if (candidate.me) {
            Text(
                "  себе нельзя",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun SupportSummary(board: LegitimacyBoard) {
    Text(
        "Передано ${board.total} из ${board.members} — ${board.delegatedPercent}% сообщества. " +
            if (board.myDelegation == null) {
                "Ваше доверие сейчас у вас."
            } else {
                "Ваше доверие передано кандидату."
            },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
    )
}
