package ru.peerflow.demo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Верхняя панель внутренних экранов: «назад» и название.
 *
 * Общая для карточки задачи, отчёта и закрытия: один и тот же экран не должен
 * выглядеть по-разному в зависимости от того, куда человек зашёл.
 */
@Composable
fun DetailTopBar(title: String, onBack: () -> Unit) {
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
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
    }
}

/** Центрированное содержимое: загрузка, ошибка, пустое состояние. */
@Composable
fun CenteredBox(content: @Composable () -> Unit) {
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

/**
 * Плашка с сообщением об ошибке поверх уже показанных данных.
 *
 * Показываем поверх, а не вместо: сетевой сбой не должен стирать файлы и
 * счётчики, которые человек уже видел.
 */
@Composable
fun MessageBanner(text: String, onDismiss: () -> Unit) {
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("Понятно") }
        }
    }
}
