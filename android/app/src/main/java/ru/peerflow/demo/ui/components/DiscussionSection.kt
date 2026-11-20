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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import ru.peerflow.demo.data.model.DiscussionMessage
import ru.peerflow.demo.data.model.DiscussionThread
import ru.peerflow.demo.data.model.MessageSort
import ru.peerflow.demo.data.model.ReactionKind
import ru.peerflow.demo.feature.discussion.DiscussionState

/**
 * Блок обсуждения под предложением (`BR-DISC-1`–`BR-DISC-6`, `BR-SPAM-1`).
 *
 * Ветку и порядок сообщений целиком присылает сервер: клиент её не
 * пересортировывает и не восстанавливает дерево из плоского списка. Иначе
 * правило «плюсы поднимают, минусы опускают» пришлось бы повторять на каждой
 * платформе, и однажды оно разъехалось бы.
 *
 * Обсуждение живёт внутри прокручиваемой карточки предложения, поэтому здесь
 * обычные [Column], а не `LazyColumn`: вложенная ленивая прокрутка внутри
 * `verticalScroll` ломает измерение высоты.
 */
@Composable
fun DiscussionSection(
    state: DiscussionState,
    sort: MessageSort,
    sending: Boolean,
    onSelectSort: (MessageSort) -> Unit,
    onPost: (String, String?) -> Unit,
    onReact: (String, ReactionKind) -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Обсуждение", style = MaterialTheme.typography.titleMedium)
            SortRow(sort = sort, onSelectSort = onSelectSort)

            when (state) {
                DiscussionState.Loading -> Text(
                    "Загружаем обсуждение…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                is DiscussionState.Failure -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text("Повторить") }
                }

                is DiscussionState.Loaded -> LoadedThread(
                    thread = state.thread,
                    sending = sending,
                    onPost = onPost,
                    onReact = onReact,
                )
            }
        }
    }
}

@Composable
private fun SortRow(sort: MessageSort, onSelectSort: (MessageSort) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MessageSort.entries.forEach { option ->
            val selected = option == sort
            TextButton(
                onClick = { onSelectSort(option) },
                modifier = Modifier
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(50),
                    ),
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun LoadedThread(
    thread: DiscussionThread,
    sending: Boolean,
    onPost: (String, String?) -> Unit,
    onReact: (String, ReactionKind) -> Unit,
) {
    if (thread.isEmpty && thread.hidden == 0L) {
        Text(
            "Пока никто не высказался. Первый аргумент — ваш.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }

    thread.messages.forEach { message ->
        MessageItem(
            message = message,
            depth = 0,
            sending = sending,
            onPost = onPost,
            onReact = onReact,
        )
    }

    if (thread.hidden > 0) {
        // Скрытие — не удаление (BR-SPAM-1): человек должен знать, что часть
        // обсуждения он не видит, иначе пустота выглядит как «никто не писал».
        Text(
            if (thread.hidden == 1L) {
                "1 сообщение скрыто метками «спам»."
            } else {
                "${thread.hidden} сообщений скрыто метками «спам»."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }

    MessageComposer(parentId = null, sending = sending, onPost = onPost)
}

@Composable
private fun MessageItem(
    message: DiscussionMessage,
    depth: Int,
    sending: Boolean,
    onPost: (String, String?) -> Unit,
    onReact: (String, ReactionKind) -> Unit,
) {
    var replying by remember(message.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Отступ — единственное, чем вложенность отличается на экране:
            // порядок и состав веток уже решил сервер (BR-DISC-6).
            .padding(start = (depth * 12).dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                message.authorName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { replying = !replying }) {
                Text("Ответить", style = MaterialTheme.typography.labelSmall)
            }
        }

        Text(message.body, style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReactionChoice(
                kind = ReactionKind.PLUS,
                count = message.plus,
                active = message.myReaction == ReactionKind.PLUS,
                color = Color(0xFF15803D),
                enabled = !sending,
                onClick = { onReact(message.id, ReactionKind.PLUS) },
            )
            ReactionChoice(
                kind = ReactionKind.MINUS,
                count = message.minus,
                active = message.myReaction == ReactionKind.MINUS,
                color = Color(0xFFB91C1C),
                enabled = !sending,
                onClick = { onReact(message.id, ReactionKind.MINUS) },
            )
            ReactionChoice(
                kind = ReactionKind.SPAM,
                count = message.spam,
                active = message.myReaction == ReactionKind.SPAM,
                color = Color(0xFF57534E),
                enabled = !sending,
                onClick = { onReact(message.id, ReactionKind.SPAM) },
            )
        }

        if (replying) {
            MessageComposer(
                parentId = message.id,
                sending = sending,
                onPost = { body, parent -> 
                    onPost(body, parent)
                    replying = false
                },
            )
        }

        message.replies.forEach { reply ->
            MessageItem(
                message = reply,
                depth = depth + 1,
                sending = sending,
                onPost = onPost,
                onReact = onReact,
            )
        }
    }
}

@Composable
private fun ReactionChoice(
    kind: ReactionKind,
    count: Long,
    active: Boolean,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                color = color.copy(alpha = if (active) 0.22f else 0.08f),
                shape = RoundedCornerShape(50),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            kind.label,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            count.toString(),
            color = color,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MessageComposer(
    parentId: String?,
    sending: Boolean,
    onPost: (String, String?) -> Unit,
) {
    var text by remember(parentId) { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(if (parentId == null) "Ваш аргумент" else "Ваш ответ")
            },
            minLines = 2,
            enabled = !sending,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onPost(text, parentId)
                    text = ""
                },
                enabled = !sending && text.isNotBlank(),
            ) {
                Text(if (sending) "Отправляем…" else "Отправить")
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}
