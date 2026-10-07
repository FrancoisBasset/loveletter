package fr.francoisbasset.loveletter.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.francoisbasset.loveletter.ui.theme.*

private fun symbol(value: Int): ImageVector = when (value) {
    0 -> Icons.Outlined.Visibility
    1 -> Icons.Outlined.Security
    2 -> Icons.Outlined.MenuBook
    3 -> Icons.Outlined.Balance
    4 -> Icons.Outlined.Shield
    5 -> Icons.Outlined.Refresh
    6 -> Icons.Outlined.AccountBalance
    7 -> Icons.Outlined.SwapHoriz
    8 -> Icons.Outlined.Diamond
    else -> Icons.Outlined.FavoriteBorder
}

/** A original, abstract card presentation; no publisher image or trade dress. */
@Composable
fun LetterCard(
    value: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    playable: Boolean = true,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val info = cardInfo(value)
    val border by animateColorAsState(if (selected) Wine else Gold.copy(alpha = .55f), label = "card border")
    val scale by animateFloatAsState(if (selected) 1.025f else 1f, label = "card selection")
    val click = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (playable) 1f else .62f }
            .then(click).semantics { contentDescription = "${info.name}, valeur $value. ${info.effect}" },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        shadowElevation = if (selected) 7.dp else 2.dp
    ) {
        Column(Modifier.padding(if (compact) 10.dp else 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(if (compact) 28.dp else 34.dp).background(Burgundy, CircleShape), contentAlignment = Alignment.Center) {
                    Text(value.toString(), color = Ivory, fontWeight = FontWeight.Bold, fontSize = if (compact) 15.sp else 18.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(info.name, style = MaterialTheme.typography.titleMedium,
                    color = Burgundy, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
            }
            if (!compact) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.size(68.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(symbol(value), null, Modifier.size(37.dp), tint = Wine)
                }
                Spacer(Modifier.height(12.dp))
                Text(info.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(8.dp))
                Text(info.effect, style = MaterialTheme.typography.bodySmall, maxLines = 7, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 94.dp))
                if (!playable) {
                    Spacer(Modifier.height(6.dp))
                    Text("Non jouable ce tour", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun CardBack(modifier: Modifier = Modifier, small: Boolean = false) {
    Surface(modifier.semantics { contentDescription = "Carte secrète" }, shape = RoundedCornerShape(if (small) 6.dp else 16.dp),
        color = Burgundy, border = BorderStroke(1.dp, Gold.copy(alpha = .7f))) {
        Canvas(Modifier.fillMaxSize().padding(if (small) 4.dp else 12.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val spacing = if (small) 13.dp.toPx() else 22.dp.toPx()
            var y = -spacing
            while (y < size.height + spacing) {
                var x = -spacing
                while (x < size.width + spacing) {
                    val path = Path().apply {
                        moveTo(x, y - spacing / 2); lineTo(x + spacing / 2, y)
                        lineTo(x, y + spacing / 2); lineTo(x - spacing / 2, y); close()
                    }
                    drawPath(path, Gold.copy(alpha = .15f), style = Stroke(1.dp.toPx()))
                    x += spacing
                }
                y += spacing
            }
            val w = size.width * .6f
            val h = w * .65f
            drawRect(Ivory, Offset(center.x - w / 2, center.y - h / 2), androidx.compose.ui.geometry.Size(w, h))
            val flap = Path().apply {
                moveTo(center.x - w / 2, center.y - h / 2)
                lineTo(center.x, center.y + h * .13f)
                lineTo(center.x + w / 2, center.y - h / 2)
            }
            drawPath(flap, Gold, style = Stroke(if (small) 1.dp.toPx() else 2.dp.toPx()))
        }
    }
}

@Composable
fun FavorToken(score: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Outlined.Favorite, "Pions Faveur", Modifier.size(16.dp), tint = Wine)
        Text(score.toString(), fontWeight = FontWeight.Bold, color = Burgundy)
    }
}

@Composable
fun CardDetailDialog(value: Int, onDismiss: () -> Unit) {
    val info = cardInfo(value)
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(symbol(value), null, tint = Wine) },
        title = { Text("${info.value} · ${info.name}", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("${info.copies} exemplaire${if (info.copies > 1) "s" else ""} dans le paquet", color = Muted)
                Text(info.effect)
                HorizontalDivider()
                Text(info.explanation, style = MaterialTheme.typography.bodyMedium)
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Compris") } })
}
