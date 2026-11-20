package ru.peerflow.demo.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.peerflow.demo.data.model.AvatarUpload

/** Сколько байт готовы принять: столько же, сколько принимает сервер. */
private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024

/** Расширения, которые понимает сервер. */
private val ALLOWED_MIME = setOf("image/png", "image/jpeg", "image/webp")

/**
 * Выбор фотографии профиля.
 *
 * Экран отдаёт ViewModel уже готовые байты: так ViewModel остаётся
 * тестируемой и ничего не знает про `ContentResolver`.
 */
@Composable
fun AvatarPicker(
    avatar: AvatarUpload?,
    enabled: Boolean,
    onPicked: (AvatarUpload?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { readAvatar(context, uri) }
            result.fold(
                onSuccess = { upload ->
                    error = null
                    onPicked(upload)
                },
                onFailure = { throwable ->
                    error = throwable.message ?: "не удалось прочитать файл"
                },
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Column(
                    modifier = Modifier.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (avatar == null) "нет" else "есть",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = avatar?.fileName ?: "Фотография не выбрана",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "PNG, JPEG или WebP, до 5 МБ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                enabled = enabled,
                onClick = {
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                Text(if (avatar == null) "Выбрать фото" else "Заменить фото")
            }
            if (avatar != null) {
                TextButton(enabled = enabled, onClick = { onPicked(null) }) {
                    Text("Убрать")
                }
            }
        }

        error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Прочитать изображение из выбранного файла.
 *
 * Возвращаем понятную ошибку вместо исключения: человек видит «файл слишком
 * большой», а не стектрейс.
 */
private fun readAvatar(context: Context, uri: Uri): Result<AvatarUpload> = runCatching {
    val mimeType = context.contentResolver.getType(uri)
        ?.lowercase()
        ?.substringBefore(';')
        ?: "image/jpeg"
    require(mimeType in ALLOWED_MIME) { "поддерживаются только PNG, JPEG и WebP" }

    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("не удалось открыть файл")
    require(bytes.size <= MAX_AVATAR_BYTES) { "файл больше 5 МБ" }

    AvatarUpload(
        bytes = bytes,
        fileName = displayName(context, uri) ?: "avatar",
        mimeType = mimeType,
    )
}

/**
 * Имя файла для отображения и для multipart-запроса.
 *
 * `uri.lastPathSegment` даёт внутренний идентификатор, а человеку нужно
 * настоящее имя — его и берём из `OpenableColumns`.
 */
private fun displayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
    }.getOrNull()
