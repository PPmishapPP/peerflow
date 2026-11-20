package ru.peerflow.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.peerflow.demo.data.CommunityMember

@Composable
fun MemberAvatar(
    member: CommunityMember,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(member.avatarColor.copy(alpha = 0.18f), CircleShape)
            .border(1.dp, member.avatarColor.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = member.initials,
            color = member.avatarColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Репутация рядом с аватаром. */
@Composable
fun ReputationBadge(points: Int, modifier: Modifier = Modifier) {
    Pill(
        text = "★ $points",
        color = Color(0xFFB45309),
        modifier = modifier,
    )
}

@Composable
fun LegitimacyBadge(percent: Int, modifier: Modifier = Modifier) {
    val color = when {
        percent >= 70 -> Color(0xFF7C3AED)
        percent >= 40 -> Color(0xFFEA580C)
        else -> Color(0xFFB91C1C)
    }
    Pill(text = "⚖ $percent%", color = color, modifier = modifier)
}

@Composable
fun BudgetBadge(amount: Int, modifier: Modifier = Modifier) {
    Pill(text = "₽ $amount", color = Color(0xFF0369A1), modifier = modifier)
}

/** Бюджет, пришедший с сервера: там сумма 64-битная (`BR-BUDGET-2`). */
@Composable
fun BudgetBadge(amount: Long, modifier: Modifier = Modifier) {
    BudgetBadge(amount.toString(), modifier)
}

@Composable
fun BudgetBadge(amount: String, modifier: Modifier = Modifier) {
    Pill(text = "₽ $amount", color = Color(0xFF0369A1), modifier = modifier)
}

@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ReactionChip(
    symbol: String,
    count: Int,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = color.copy(alpha = if (active) 0.22f else 0.08f),
                shape = RoundedCornerShape(50),
            )
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = color.copy(alpha = if (active) 0.9f else 0.25f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = symbol,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = count.toString(),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
