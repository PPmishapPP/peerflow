package ru.peerflow.demo.ui.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.peerflow.demo.data.model.JoinRequestSummary
import ru.peerflow.demo.ui.components.Pill

/**
 * Заявки на вступление и голосование за приём (`BR-JOIN-5`, `BR-VOTE-8`).
 *
 * Экран действующего члена. Вступающий видит только «заявка на рассмотрении»,
 * поэтому принять человека можно лишь здесь — без этого экрана заявка висит
 * вечно, а порог `BR-LIMIT-3` некому набрать.
 *
 * Порог и прогресс приходят с сервера: он считает его от действующих членов
 * сообщества, и клиент не должен выводить это правило сам.
 */
@Composable
fun JoinRequestsScreen(
    onBack: () -> Unit,
    viewModel: JoinRequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val voting by viewModel.voting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Очередь перезапрашиваем при каждом открытии: пока человек был в другом
    // месте, другие могли проголосовать, и счётчики уже устарели.
    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                JoinRequestsTopBar(onBack = onBack)

                when (val current = state) {
                    JoinRequestsState.Loading -> CenteredColumn { CircularProgressIndicator() }

                    JoinRequestsState.Empty -> CenteredColumn {
                        Text(
                            "Заявок нет",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Когда кто-то попросится в сообщество, заявка появится здесь.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        OutlinedButton(onClick = viewModel::refresh) { Text("Обновить") }
                    }

                    is JoinRequestsState.Failure -> CenteredColumn {
                        Text(
                            "Не удалось загрузить заявки",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = viewModel::refresh) { Text("Повторить") }
                    }

                    is JoinRequestsState.Loaded -> RequestList(
                        requests = current.requests,
                        voting = voting,
                        onVote = viewModel::vote,
                    )
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )

            if (loading && state is JoinRequestsState.Loaded) {
                // Тонкий индикатор поверх: очередь уже есть, и блокировать её
                // на время перезагрузки незачем.
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun RequestList(
    requests: List<JoinRequestSummary>,
    voting: String?,
    onVote: (String, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                if (requests.size == 1) {
                    "1 заявка ждёт решения"
                } else {
                    "${requests.size} заявок ждут решения"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        items(requests, key = { it.id }) { request ->
            RequestCard(
                request = request,
                voting = voting == request.id,
                onVote = onVote,
            )
        }
    }
}

@Composable
private fun RequestCard(
    request: JoinRequestSummary,
    voting: Boolean,
    onVote: (String, Boolean) -> Unit,
) {
    val required = request.required.coerceAtLeast(1L)
    val progress = (request.accepts.toFloat() / required).coerceIn(0f, 1f)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(name = request.applicantName)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        request.applicantName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "просится в сообщество",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (request.votedByMe) {
                    Pill(text = "✓ вы голосовали", color = Color(0xFF15803D))
                }
            }

            if (request.about.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(request.about, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = Color(0xFFE5E7EB),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "«За»: ${request.accepts} из ${request.required} нужных · всего голосов ${request.totalVotes}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(12.dp))
            if (request.votedByMe) {
                // Голос по заявке не меняется (`BR-VOTE-8`): показываем это
                // прямо, чтобы человек не искал кнопку, которой нет.
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Ваш голос учтён. Изменить его нельзя — решение по заявке принимается один раз.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onVote(request.id, true) },
                        enabled = !voting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                    ) {
                        Text("Принять")
                    }
                    OutlinedButton(
                        onClick = { onVote(request.id, false) },
                        enabled = !voting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Отклонить")
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinRequestsTopBar(onBack: () -> Unit) {
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
                "Заявки на вступление",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
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

/** Аватар по имени: фото с сервера пока не грузим, показываем инициалы. */
@Composable
private fun InitialsAvatar(name: String) {
    val initials = name.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifBlank { "?" }
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        shape = CircleShape,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initials,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
