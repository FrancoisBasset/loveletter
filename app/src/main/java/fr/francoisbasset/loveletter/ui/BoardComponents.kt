package fr.francoisbasset.loveletter.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.francoisbasset.loveletter.ui.theme.*

/** Clockwise seating, with the human at the near edge. Every seat is always composed and visible. */
@Composable
fun CourtBoard(table: UiTable, selectedTarget: Int?, legalTargets: Set<Int>, onTarget: (Int) -> Unit,
    onPublicPlayer: (UiPlayer) -> Unit, onEvent: (UiPlayback) -> Unit, modifier: Modifier = Modifier) {
    val byId = table.players.associateBy { it.id }
    val seating = when (table.players.size) {
        2 -> listOf(1) to listOf(0)
        3 -> listOf(1, 2) to listOf(0)
        4 -> listOf(1, 2) to listOf(0, 3)
        5 -> listOf(2, 3) to listOf(1, 0, 4)
        else -> listOf(2, 3, 4) to listOf(1, 0, 5)
    }
    val actor = table.playback?.actorId ?: table.players.firstOrNull { it.active }?.id
    val target = selectedTarget ?: table.playback?.targetId
    Surface(modifier.testTag("plateau"), color = Color(0xFFEFE1CC), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .5f))) {
        BoxWithConstraints(Modifier.padding(6.dp)) {
            val seatHeight = if (maxHeight < 340.dp) 64.dp else 74.dp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                @Composable fun Seats(ids: List<Int>) {
                    Row(Modifier.fillMaxWidth().height(seatHeight), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ids.forEach { id -> byId[id]?.let { player ->
                            SeatPanel(player, actor == id, target == id, id in legalTargets,
                                Modifier.weight(1f).fillMaxHeight(), onClick = { if (id in legalTargets) onTarget(id) else onPublicPlayer(player) })
                        } }
                    }
                }
                Seats(seating.first)
                CentreStage(table, onEvent, Modifier.weight(1f).fillMaxWidth())
                Seats(seating.second)
            }
        }
    }
}

@Composable
private fun SeatPanel(player: UiPlayer, acting: Boolean, targeted: Boolean, targetable: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val background by animateColorAsState(when { targeted -> MaterialTheme.colorScheme.secondaryContainer; acting -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surface }, label = "seat turn")
    val alpha by animateFloatAsState(if (player.alive) 1f else .58f, tween(350), label = "seat elimination")
    val border = when { targeted -> MaterialTheme.colorScheme.secondary; acting -> Wine; targetable -> Wine.copy(alpha = .65f); else -> Gold.copy(alpha = .45f) }
    Surface(onClick = onClick, modifier = modifier.testTag("seat_${player.id}").semantics {
        contentDescription = "${player.name}, ${player.score} faveurs, ${if (!player.alive) "éliminé" else if (player.isProtected) "protégé" else "en lice"}. Défausse ${player.discards.joinToString()}${if (targetable) ". Cible possible" else ""}"
    }, color = background, shape = RoundedCornerShape(11.dp), border = BorderStroke(if (targeted || acting) 2.dp else 1.dp, border)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp).graphicsLayer { this.alpha = alpha }, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (player.human) "Vous" else player.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Burgundy, modifier = Modifier.weight(1f))
                if (targetable) Icon(Icons.Outlined.MyLocation, null, Modifier.size(12.dp), tint = Wine)
            }
            Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (player.revealedHand != null) {
                    Box(Modifier.size(21.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) { Text(player.revealedHand.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Burgundy) }
                } else if (player.alive) {
                    Box(Modifier.width((15 + (player.handSize.coerceIn(1, 3) - 1) * 5).dp).height(21.dp)) {
                        repeat(player.handSize.coerceIn(1, 3)) { index -> CardBack(Modifier.offset(x = (index * 5).dp).width(15.dp).height(21.dp), small = true) }
                    }
                } else Icon(Icons.Outlined.Close, "Éliminé", Modifier.size(21.dp), tint = Wine)
                if (player.isProtected) Icon(Icons.Outlined.Shield, "Protection", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
                if (acting) Icon(Icons.Outlined.ArrowRight, "Joueur dont l'action est présentée", Modifier.size(12.dp), tint = Wine)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.Favorite, "Pions Faveur", Modifier.size(12.dp), tint = Wine)
                Text(player.score.toString(), style = MaterialTheme.typography.labelLarge, color = Burgundy)
            }
            Text("D : ${if (player.discards.isEmpty()) "—" else player.discards.joinToString("·")}", fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted)
        }
    }
}

@Composable
private fun CentreStage(table: UiTable, onEvent: (UiPlayback) -> Unit, modifier: Modifier) {
    val event = table.playback ?: table.lastPlayback
    val resultText = event?.let { action ->
        val outcomes = action.messages.dropWhile { message -> message == "${action.title}." || message == action.title }
        outcomes.joinToString(" ").ifBlank { action.summary }
    }
    BoxWithConstraints(modifier) {
        val compact = maxHeight < 175.dp
        val veryCompact = maxHeight < 130.dp
        Canvas(Modifier.fillMaxSize()) {
            drawOval(Burgundy.copy(alpha = .045f), Offset(0f, 0f), Size(size.width, size.height))
            drawOval(Gold.copy(alpha = .2f), Offset(4.dp.toPx(), 4.dp.toPx()), Size((size.width - 8.dp.toPx()).coerceAtLeast(0f), (size.height - 8.dp.toPx()).coerceAtLeast(0f)), style = Stroke(1.dp.toPx()))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(event?.let {
                val actionLabel = if (it.targetName != null) "${it.actorName} → ${it.targetName}" else it.title
                if (table.playback == null) "Dernière action : $actionLabel" else actionLabel
            } ?: "La cour vous attend",
                style = MaterialTheme.typography.labelLarge, color = Burgundy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Column(Modifier.width(55.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!veryCompact) CardBack(Modifier.width(27.dp).height(37.dp), small = true)
                    Text(table.deckCount.toString(), style = MaterialTheme.typography.titleLarge, color = Burgundy)
                    Text("Pioche", style = MaterialTheme.typography.labelSmall, color = Muted)
                    if (table.exposedCards.isNotEmpty() && !veryCompact) Text("Retirées\n${table.exposedCards.joinToString("·")}", fontSize = 11.sp, lineHeight = 13.sp, color = Muted)
                }
                Spacer(Modifier.width(12.dp))
                if (event?.cardValue != null) {
                    Surface(onClick = { onEvent(event) }, modifier = Modifier.width(if (compact) 128.dp else 160.dp).heightIn(max = if (compact) 108.dp else 160.dp).fillMaxHeight().testTag("carte_jouee"),
                        shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(2.dp, Wine.copy(alpha = .75f)), shadowElevation = 3.dp) {
                        Column(Modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(event.cardValue.toString(), style = MaterialTheme.typography.headlineMedium, color = Wine, fontWeight = FontWeight.Bold)
                                Text(cardInfo(event.cardValue).name, style = MaterialTheme.typography.titleMedium, color = Burgundy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (!veryCompact) {
                                Icon(Icons.Outlined.MailOutline, null, Modifier.size(if (compact) 20.dp else 30.dp), tint = Gold)
                                Text(cardInfo(event.cardValue).effect, style = MaterialTheme.typography.bodySmall, maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis)
                            }
                            event.guessValue?.let { Text("Annonce : ${cardInfo(it).name}", style = MaterialTheme.typography.labelSmall, color = Wine, maxLines = 1) }
                        }
                    }
                } else {
                    Box(Modifier.width(130.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (!veryCompact) CardBack(Modifier.width(60.dp).height(82.dp))
                        else Icon(Icons.Outlined.MailOutline, null, Modifier.size(38.dp), tint = Wine)
                    }
                }
            }
            Text(if (table.paused) "Partie en pause" else resultText ?: table.turnLabel,
                style = if (veryCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = Burgundy, maxLines = if (veryCompact) 1 else if (table.playback != null) 3 else 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth())
        }
    }
}
