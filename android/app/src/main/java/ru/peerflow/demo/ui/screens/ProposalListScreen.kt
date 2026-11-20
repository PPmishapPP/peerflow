package ru.peerflow.demo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.peerflow.demo.data.model.CurrentUser
import ru.peerflow.demo.data.model.ProposalSort
import ru.peerflow.demo.data.model.ProposalStage
import ru.peerflow.demo.data.model.ProposalSummary
import ru.peerflow.demo.feature.proposal.ProposalListState
import ru.peerflow.demo.feature.proposal.ProposalViewModel
import ru.peerflow.demo.ui.components.BudgetBadge
import ru.peerflow.demo.ui.components.Pill

/**
 * Главный экран: предложения сообщества двумя секциями
 * (`BR-RANK-1`–`BR-RANK-5`).
 *
 * Данные — с сервера: порядок внутри секций и метрику популярности считает он,
 * потому что это правила сообщества, а не оформление. Экран только показывает
 * то, что пришло, и переключает сортировку.
 */
@Composable
fun ProposalListScreen(
    currentUser: CurrentUser,
    onOpenProposal: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onNewProposal: () -> Unit,
    viewModel: ProposalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Список перезапрашиваем при каждом возврате на экран: пока человек ходил
    // в карточку, другие могли проголосовать, и старые счётчики соврут.
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
                CommunityHeader(
                    currentUser = currentUser,
                    onOpenProfile = onOpenProfile,
                )
                SortRow(selected = sort, onSelect = viewModel::selectSort)

                when (val current = state) {
                    ProposalListState.Loading -> LoadingBox()
                    is ProposalListState.Failure -> FailureBox(
                        message = current.message,
                        onRetry = viewModel::refresh,
                    )
                    is ProposalListState.Loaded -> Sections(
                        state = current,
                        onOpenProposal = onOpenProposal,
                    )
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
            )
            NewProposalButton(
                onClick = onNewProposal,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp),
            )
            if (loading && state is ProposalListState.Loaded) {
                // Тонкая полоска вместо блокирующего круга: список уже есть,
                // и мешать им пользоваться во время перезагрузки незачем.
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
private fun Sections(
    state: ProposalListState.Loaded,
    onOpenProposal: (String) -> Unit,
) {
    val sections = state.sections
    if (sections.isEmpty) {
        EmptyBox()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = "🔧 В работе",
                subtitle = "Решение принято, есть бюджет",
                count = sections.inWork.size,
                accent = Color(0xFF0F766E),
            )
        }
        items(sections.inWork, key = { it.id }) { proposal ->
            ProposalCard(proposal, onClick = { onOpenProposal(proposal.id) })
        }
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader(
                title = "💬 На обсуждении",
                subtitle = "Решаем, делать ли вообще",
                count = sections.discussing.size,
                accent = Color(0xFF4F46E5),
            )
        }
        items(sections.discussing, key = { it.id }) { proposal ->
            ProposalCard(proposal, onClick = { onOpenProposal(proposal.id) })
        }
    }
}

/** Круглая «+» для внесения нового предложения. */
@Composable
private fun NewProposalButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
        shadowElevation = 8.dp,
        modifier = modifier.size(60.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(26.dp)) {
                val w = size.width
                val h = size.height
                val stroke = w * 0.13f
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.5f, h * 0.15f),
                    end = Offset(w * 0.5f, h * 0.85f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.15f, h * 0.5f),
                    end = Offset(w * 0.85f, h * 0.5f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun CommunityHeader(currentUser: CurrentUser, onOpenProfile: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Сообщество «Маяк»",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Решаем вместе. Исполняет — тот, кому доверили.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onOpenProfile() },
            ) {
                InitialsAvatar(name = currentUser.displayName)
                Text(
                    "Профиль",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Аватар по имени: с сервера приходит имя, а не цвет и не инициалы. */
@Composable
private fun InitialsAvatar(name: String, size: Int = 40) {
    val initials = name.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifBlank { "?" }
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SortRow(selected: ProposalSort, onSelect: (ProposalSort) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProposalSort.entries.forEach { value ->
            val active = value == selected
            Surface(
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.clickable { onSelect(value) },
            ) {
                Text(
                    value.label,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    count: Int,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        Surface(color = accent.copy(alpha = 0.12f), shape = CircleShape) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun ProposalCard(proposal: ProposalSummary, onClick: () -> Unit) {
    val inWork = proposal.stage == ProposalStage.IN_WORK
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(text = proposal.stage.label, color = stageColor(proposal.stage))
                Spacer(Modifier.weight(1f))
                Text(
                    "🔥 ${proposal.popularity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                proposal.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BudgetBadge(proposal.budget)
                Pill(
                    text = "👥 ${proposal.participants}",
                    color = MaterialTheme.colorScheme.primary,
                )
                if (proposal.votedByMe) {
                    Pill(
                        text = if (proposal.myVote?.inFavor == true) "✓ За" else "✓ Против",
                        color = if (proposal.myVote?.inFavor == true) {
                            Color(0xFF15803D)
                        } else {
                            Color(0xFFB91C1C)
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "За ${proposal.votesFor} · Против ${proposal.votesAgainst}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    proposal.votingStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            if (inWork) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Принято сообществом — исполнение и отчёты",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF0F766E),
                )
            }
        }
    }
}

private fun stageColor(stage: ProposalStage): Color = when (stage) {
    ProposalStage.IN_WORK -> Color(0xFF0F766E)
    ProposalStage.DISCUSSION -> Color(0xFF4F46E5)
    ProposalStage.CLOSED -> Color(0xFF57534E)
    ProposalStage.REJECTED -> Color(0xFFB91C1C)
}

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun FailureBox(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Не удалось загрузить предложения",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

@Composable
private fun EmptyBox() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Пока предложений нет",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Внесите первое — его обсудят и решат сообща.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}
