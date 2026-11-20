package ru.peerflow.demo.ui.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.peerflow.demo.data.model.AvatarUpload
import ru.peerflow.demo.data.model.JoinRequestSummary
import ru.peerflow.demo.ui.auth.CredentialRules
import ru.peerflow.demo.ui.components.AvatarPicker

/**
 * Экран вступления в сообщество (`BR-JOIN-1`…`BR-JOIN-4`).
 *
 * Один экран на все состояния — форма, ожидание, отказ, ошибка: человек
 * остаётся в одном месте и видит, что с его заявкой, без блуждания по меню.
 */
@Composable
fun JoinCommunityScreen(
    onBecameMember: () -> Unit,
    viewModel: JoinRequestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val about by viewModel.about.collectAsStateWithLifecycle()
    val aboutError by viewModel.aboutError.collectAsStateWithLifecycle()
    val formError by viewModel.formError.collectAsStateWithLifecycle()
    val submitting by viewModel.submitting.collectAsStateWithLifecycle()

    // Фото живёт только на экране: ViewModel получает готовые байты в момент отправки.
    var avatar by remember { mutableStateOf<AvatarUpload?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                JoinEvent.BecameMember -> onBecameMember()
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val current = state) {
                JoinUiState.Loading -> {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                JoinUiState.Form -> {
                    Text(
                        text = "Вступление в сообщество",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Расскажите о себе. Заявку рассмотрят действующие члены " +
                            "голосованием: нужно не меньше 30 % голосов «за».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = about,
                        onValueChange = viewModel::onAboutChange,
                        label = { Text("О себе") },
                        enabled = !submitting,
                        isError = aboutError != null,
                        supportingText = {
                            Text(
                                aboutError
                                    ?: "${about.length} из ${CredentialRules.ABOUT_MAX}",
                            )
                        },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    AvatarPicker(
                        avatar = avatar,
                        enabled = !submitting,
                        onPicked = { avatar = it },
                    )

                    formError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Button(
                        onClick = { viewModel.submit(avatar) },
                        enabled = !submitting && about.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Подать заявку")
                        }
                    }
                }

                is JoinUiState.Pending -> PendingContent(
                    request = current.request,
                    onRefresh = viewModel::refresh,
                )

                is JoinUiState.Accepted -> AcceptedContent()

                is JoinUiState.Rejected -> RejectedContent(request = current.request)

                is JoinUiState.Failure -> FailureContent(
                    message = current.message,
                    onRetry = viewModel::refresh,
                )
            }
        }
    }
}

/** Заявка подана и ждёт голосования: показываем, сколько голосов уже есть. */
@Composable
private fun PendingContent(
    request: JoinRequestSummary,
    onRefresh: () -> Unit,
) {
    Text(
        text = "Заявка на рассмотрении",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = "Члены сообщества голосуют. Как только голосов «за» станет достаточно, " +
            "вы попадёте внутрь — перезаходить не нужно.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(8.dp))

    val required = request.required.coerceAtLeast(1L)
    val progress = (request.accepts.toFloat() / required).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "«За»: ${request.accepts} из ${required} нужных",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = "Всего голосов: ${request.totalVotes}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(8.dp))

    OutlinedButton(
        onClick = onRefresh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Проверить статус")
    }
}

/** Заявку приняли: экран коротко подтверждает и уводит дальше. */
@Composable
private fun ColumnScope.AcceptedContent() {
    Text(
        text = "Вас приняли",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = "Добро пожаловать в сообщество. Открываем приложение…",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
}

/** Заявку отклонили: объясняем, что произошло, без обвинений и оценок. */
@Composable
private fun RejectedContent(request: JoinRequestSummary) {
    Text(
        text = "Заявку отклонили",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.error,
    )
    Text(
        text = "Сообщество не набрало нужного числа голосов «за». Это не запрет навсегда: " +
            "можно пообщаться с членами и подать заявку снова позже.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (request.about.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ваш текст заявки:",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = request.about,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Не удалось узнать статус: сообщаем причину и даём повторить. */
@Composable
private fun FailureContent(
    message: String,
    onRetry: () -> Unit,
) {
    Text(
        text = "Не удалось загрузить статус",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Start,
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text("Повторить")
    }
}
