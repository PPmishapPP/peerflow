package ru.peerflow.demo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.peerflow.demo.data.DemoData

/**
 * Экран-имитация обычного мессенджера. Нужен, чтобы показать «боль»:
 * реальная тема тонет в потоке шумных сообщений.
 */
@Composable
fun PainMessengerScreen(onContinue: () -> Unit) {
    Surface(color = Color(0xFF0E1621), modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                MessengerHeader()
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(DemoData.chaoticChat) { line ->
                        ChatBubble(line.author, line.text, line.self)
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                MessengerInput()
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text("Посмотреть, как это решает PeerFlow →")
                }
            }
            JumpToLatest(
                count = 1024,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 152.dp),
            )
        }
    }
}

@Composable
private fun MessengerHeader() {
    Surface(color = Color(0xFF17212B)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF2AABEE), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("Б", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)) {
                Text(
                    "Аренда помещения для лекции",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "«Маяк» · 42 участника",
                    color = Color(0xFF7D8E9E),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun JumpToLatest(count: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Кружок как в Telegram: подсказка «вниз, к последнему сообщению».
        Surface(
            color = Color(0xFF17212B),
            shape = CircleShape,
            shadowElevation = 6.dp,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    val w = size.width
                    val h = size.height
                    val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
                    // Шеврон вниз.
                    drawLine(
                        color = Color(0xFFEAEAEA),
                        start = Offset(w * 0.2f, h * 0.35f),
                        end = Offset(w * 0.5f, h * 0.65f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color(0xFFEAEAEA),
                        start = Offset(w * 0.5f, h * 0.65f),
                        end = Offset(w * 0.8f, h * 0.35f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        // Синий овал с числом чуть выше, слегка перекрывает кружок сверху.
        Surface(
            color = Color(0xFF2AABEE),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-16).dp),
        ) {
            Text(
                count.toString(),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun ChatBubble(author: String, text: String, self: Boolean) {
    val bubbleColor = when (author) {
        "Лев" -> Color(0xFF3B1F26)
        "Дина" -> Color(0xFF1E3A34)
        else -> Color(0xFF17212B)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (self) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(bubbleColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                author,
                color = Color(0xFF2AABEE),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text,
                color = Color(0xFFEAEAEA),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MessengerInput() {
    Surface(color = Color(0xFF17212B)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(0xFF0E1621),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    "Написать сообщение…",
                    color = Color(0xFF7D8E9E),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
