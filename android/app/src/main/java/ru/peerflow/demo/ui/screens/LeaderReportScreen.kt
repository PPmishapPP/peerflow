package ru.peerflow.demo.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.peerflow.demo.data.model.Attachment
import ru.peerflow.demo.data.model.BudgetExpense
import ru.peerflow.demo.data.model.Report
import ru.peerflow.demo.data.model.ReportUpload
import ru.peerflow.demo.feature.report.ReportState
import ru.peerflow.demo.feature.report.ReportViewModel
import ru.peerflow.demo.ui.components.BudgetBadge
import ru.peerflow.demo.ui.components.CenteredBox
import ru.peerflow.demo.ui.components.DetailTopBar
import ru.peerflow.demo.ui.components.MessageBanner
import ru.peerflow.demo.ui.components.Pill

/** Сколько байт принимает сервер для файла отчёта. */
private const val MAX_REPORT_BYTES = 10 * 1024 * 1024

/**
 * Расширения, которые принимает сервер.
 *
 * Список повторяет `ReportStorage` на сервере: если бы клиент позволял выбрать
 * больше, человек узнал бы об отказе только после загрузки.
 */
private val ALLOWED_EXTENSIONS = setOf(
    "pdf", "txt", "md", "csv",
    "png", "jpg", "jpeg", "webp",
    "doc", "docx", "xls", "xlsx", "ppt", "pptx",
    "odt", "ods", "odp",
)

/**
 * Отчёт лидера задачи (`BR-LIFE-7`, `BR-BUDGET-4`, `BR-BUDGET-5`).
 *
 * Отдельного «отчёта» как формата нет: отчёт — это сообщение или файл
 * (`BR-LIFE-7`), поэтому экран показывает то, что уже есть у задачи, — файлы,
 * журнал трат и остаток бюджета. Публиковать и тратить может только лидер:
 * право даёт исполнительный мандат (`BR-LIFE-6`), и сервер отвечает `403`
 * всем остальным.
 */
@Composable
fun LeaderReportScreen(
    proposalId: String,
    currentUserId: String,
    onBack: () -> Unit,
    onCloseTask: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var text by remember { mutableStateOf("") }
    var upload by remember { mutableStateOf<ReportUpload?>(null) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(proposalId) { viewModel.load(proposalId) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailTopBar(title = "Отчёт лидера", onBack = onBack)

            when (val current = state) {
                ReportState.Loading -> CenteredBox { CircularProgressIndicator() }

                is ReportState.Failure -> CenteredBox {
                    Text(
                        "Не удалось открыть отчёт",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = viewModel::refresh) { Text("Повторить") }
                }

                is ReportState.Loaded -> {
                    val iAmLeader = current.closure.leaderId == currentUserId

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            ReportHeader(
                                leaderName = current.closure.leaderName,
                                report = current.report,
                            )
                        }
                        message?.let { text ->
                            item {
                                MessageBanner(text = text, onDismiss = viewModel::dismissMessage)
                            }
                        }
                        item { ReportHeading("Файлы отчёта") }
                        if (current.report.files.isEmpty()) {
                            item { EmptyHint("Лидер ещё ничего не приложил.") }
                        } else {
                            items(current.report.files.size) { index ->
                                FileRow(current.report.files[index])
                            }
                        }
                        item { ReportHeading("Траты бюджета") }
                        if (current.report.expenses.isEmpty()) {
                            item { EmptyHint("Трат ещё не было.") }
                        } else {
                            items(current.report.expenses.size) { index ->
                                ExpenseRow(current.report.expenses[index])
                            }
                        }
                        if (iAmLeader) {
                            item { ReportHeading("Опубликовать отчёт") }
                            item {
                                PublishReportCard(
                                    text = text,
                                    upload = upload,
                                    sending = sending,
                                    onTextChange = { text = it },
                                    onUploadChange = { upload = it },
                                    onSubmit = {
                                        viewModel.postReport(text, upload)
                                        // Поля чистим сразу: отчёт уже ушёл, и
                                        // повторная отправка того же текста была
                                        // бы случайным дублем.
                                        text = ""
                                        upload = null
                                    },
                                )
                            }
                            item { ReportHeading("Записать трату") }
                            item {
                                SpendCard(
                                    amount = amount,
                                    note = note,
                                    sending = sending,
                                    remaining = current.report.remaining,
                                    onAmountChange = { amount = it.filter(Char::isDigit) },
                                    onNoteChange = { note = it },
                                    onSubmit = {
                                        viewModel.spend(amount.toLongOrNull() ?: 0L, note)
                                        amount = ""
                                        note = ""
                                    },
                                )
                            }
                            item {
                                Button(
                                    onClick = onCloseTask,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Лидер запускает закрытие задачи →") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Шапка отчёта: кто лидер и сколько бюджета осталось (`BR-BUDGET-5`). */
@Composable
private fun ReportHeader(leaderName: String?, report: Report) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        leaderName ?: "Лидер не выбран",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Лидер задачи · исполняет с мандатом",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                BudgetBadge(report.budget)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { report.spentShare },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = if (report.isExhausted) Color(0xFFB91C1C)
                else MaterialTheme.colorScheme.primary,
                trackColor = Color(0xFFE5E7EB),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Потрачено ${report.spent} · осталось ${report.remaining}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

/** Один приложенный файл. */
@Composable
private fun FileRow(file: Attachment) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(file.fileName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${file.readableSize} · ${file.authorName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Pill(text = file.extension.uppercase().ifBlank { "ФАЙЛ" }, color = Color(0xFF0369A1))
        }
    }
}

/** Одна трата бюджета (`BR-BUDGET-4`). */
@Composable
private fun ExpenseRow(expense: BudgetExpense) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.note?.takeIf { it.isNotBlank() } ?: "Без пояснения",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    expense.memberName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Text(
                "−${expense.amount}",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFB91C1C),
            )
        }
    }
}

/** Публикация отчёта: текст и файл одной отправкой (`BR-LIFE-7`). */
@Composable
private fun PublishReportCard(
    text: String,
    upload: ReportUpload?,
    sending: Boolean,
    onTextChange: (String) -> Unit,
    onUploadChange: (ReportUpload?) -> Unit,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickError by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { readReport(context, uri) }
            result.fold(
                onSuccess = { file ->
                    pickError = null
                    onUploadChange(file)
                },
                onFailure = { error ->
                    pickError = error.message ?: "не удалось прочитать файл"
                },
            )
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("Что сделано") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        upload?.fileName ?: "Файл не приложен",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF0369A1),
                    )
                    Text(
                        "PDF, офисные документы, картинки или текст — до 10 МБ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !sending,
                    onClick = { launcher.launch(arrayOf("*/*")) },
                ) {
                    Text(if (upload == null) "Приложить файл" else "Заменить файл")
                }
                if (upload != null) {
                    TextButton(enabled = !sending, onClick = { onUploadChange(null) }) {
                        Text("Убрать")
                    }
                }
            }
            pickError?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSubmit,
                enabled = !sending && (text.isNotBlank() || upload != null),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Опубликовать отчёт") }
            Spacer(Modifier.height(6.dp))
            Text(
                "Отчёт — это сообщение или файл. Он появится и в обсуждении задачи.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

/** Запись траты бюджета (`BR-BUDGET-4`). */
@Composable
private fun SpendCard(
    amount: String,
    note: String,
    sending: Boolean,
    remaining: Long,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text("Сумма") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = { Text("На что") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSubmit,
                enabled = !sending && amount.isNotBlank() && remaining > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Записать трату") }
            Spacer(Modifier.height(6.dp))
            Text(
                if (remaining > 0) {
                    "Остаток бюджета: $remaining"
                } else {
                    "Бюджет исчерпан: тратить больше нечего"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun ReportHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
}

/**
 * Прочитать выбранный файл в байты.
 *
 * Экран отдаёт ViewModel готовые данные: так ViewModel не знает про
 * `ContentResolver` и остаётся тестируемой. Проверки повторяют серверные —
 * человек узнаёт об отказе до загрузки.
 */
private fun readReport(context: Context, uri: Uri): Result<ReportUpload> = runCatching {
    val mimeType = context.contentResolver.getType(uri)
        ?.lowercase()
        ?.substringBefore(';')
        ?: "application/octet-stream"

    val name = displayName(context, uri) ?: "отчёт"
    val extension = name.substringAfterLast('.', "").lowercase()
    require(extension in ALLOWED_EXTENSIONS) {
        "такой файл не принимается: нужен документ, таблица, картинка или текст"
    }

    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("не удалось открыть файл")
    require(bytes.size <= MAX_REPORT_BYTES) { "файл больше 10 МБ" }

    ReportUpload(bytes = bytes, fileName = name, mimeType = mimeType)
}

/** Имя файла для человека: `uri.lastPathSegment` — внутренний идентификатор. */
private fun displayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
    }.getOrNull()
