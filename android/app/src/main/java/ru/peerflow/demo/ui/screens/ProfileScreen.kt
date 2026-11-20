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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.peerflow.demo.data.DemoData
import ru.peerflow.demo.data.model.CurrentUser
import ru.peerflow.demo.data.model.Delegation
import ru.peerflow.demo.feature.legitimacy.MyLegitimacyState
import ru.peerflow.demo.feature.legitimacy.MyLegitimacyViewModel
import ru.peerflow.demo.ui.components.MemberAvatar
import ru.peerflow.demo.ui.components.Pill

/**
 * Профиль прототипа.
 *
 * [currentUser] приходит из сессии, когда человек вошёл по-настоящему;
 * `null` означает, что показываем демонстрационные данные из `DemoData`.
 * Выход из аккаунта чистит токен и возвращает на экран входа.
 *
 * Блок «Моя легитимность» берётся с сервера (`BR-LEGIT-3`, `BR-REP-2`): кому и
 * по каким задачам передано доверие — это факт из журнала, а не оформление.
 * Пока данных нет, честно показываем загрузку, а не выдуманные строки.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    currentUser: CurrentUser? = null,
    onLogout: () -> Unit = {},
    legitimacyViewModel: MyLegitimacyViewModel = hiltViewModel(),
) {
    val me = DemoData.currentUser
    val legitimacy by legitimacyViewModel.state.collectAsStateWithLifecycle()
    val legitimacyMessage by legitimacyViewModel.message.collectAsStateWithLifecycle()

    // Перезапрашиваем при открытии: доверие могли изменить на карточке задачи.
    LaunchedEffect(currentUser?.id) {
        if (currentUser != null) {
            legitimacyViewModel.refresh()
        }
    }

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
                    Text("Профиль", style = MaterialTheme.typography.titleMedium)
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            MemberAvatar(me, size = 72.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(me.name, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill(text = "★ Репутация ${me.reputation}", color = Color(0xFFB45309))
                                Pill(text = "В сообществе с ${me.joined}", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Моя легитимность", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Право голоса у меня неизменно. Легитимность я передаю лидеру " +
                                    "по конкретной задаче и могу забрать в любой момент.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            Spacer(Modifier.height(12.dp))

                            if (currentUser == null) {
                                // Демонстрационный режим: реального журнала нет.
                                Text(
                                    "Войдите в аккаунт, чтобы видеть свои передачи доверия.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            } else {
                                when (val current = legitimacy) {
                                    MyLegitimacyState.Loading -> Text(
                                        "Загружаем…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )

                                    is MyLegitimacyState.Failure -> Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            current.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        TextButton(onClick = legitimacyViewModel::refresh) {
                                            Text("Повторить")
                                        }
                                    }

                                    is MyLegitimacyState.Loaded -> {
                                        legitimacyMessage?.let { text ->
                                            Text(
                                                text,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                            Spacer(Modifier.height(8.dp))
                                        }
                                        if (current.mine.delegations.isEmpty()) {
                                            Text(
                                                "Открытых задач нет — передавать доверие пока не по чему.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            )
                                        } else {
                                            current.mine.delegations.forEachIndexed { index, row ->
                                                if (index > 0) Spacer(Modifier.height(8.dp))
                                                DelegationRow(row)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("История", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            HistoryRow("Починить входную дверь", "+14 репутации", Color(0xFF15803D))
                            Spacer(Modifier.height(8.dp))
                            HistoryRow("Закупить воду и посуду", "+11 репутации", Color(0xFF15803D))
                            Spacer(Modifier.height(8.dp))
                            HistoryRow("Субботник во дворе", "+17 репутации", Color(0xFF15803D))
                        }
                    }
                }
                if (currentUser != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Аккаунт", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = currentUser.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "@${currentUser.login}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                                if (currentUser.about.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = currentUser.about,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = onLogout,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Выйти из аккаунта")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DelegationRow(row: Delegation) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.proposalTitle,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (row.isDelegated) {
                    "передана ${row.candidateName.orEmpty()}"
                } else {
                    "доверие у себя"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        if (row.isDelegated) {
            Pill(text = "⚖ передана", color = Color(0xFF7C3AED))
        } else {
            Pill(text = "⚖ у меня", color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun HistoryRow(title: String, points: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Pill(text = points, color = color)
    }
}
