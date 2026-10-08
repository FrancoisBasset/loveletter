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
            val seatHeight = if (maxHeight < 270.dp) 53.dp else if (maxHeight < 340.dp) 67.dp else 76.dp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                @Composable fun Seats(ids: List<Int>) {
                    Row(Modifier.fillMaxWidth().height(seatHeight), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ids.forEach { id -> byId[id]?.let { player ->
                            SeatPanel(player, actor == id, target == id, id in legalTargets,
                                Modifier.weight(1f).fillMaxHeight(), onClick = { if (id in legalTargets) onTarget(id) else onPublicPlayer(player) }, compact = seatHeight < 60.dp)
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
private fun SeatPanel(player: UiPlayer, acting: Boolean, targeted: Boolean, targetable: Boolean, modifier: Modifier, onClick: () -> Unit, compact: Boolean = false) {
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
            if (!compact) Row(Modifier.fillMaxWidth().height(15.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Layers, "Défausse", Modifier.size(11.dp), tint = Muted)
                if (player.discards.isEmpty()) Text("—", fontSize = 10.sp, color = Muted)
                else {
                    player.discards.takeLast(5).forEach { rank ->
                        Box(Modifier.width(13.dp).fillMaxHeight().background(Gold.copy(alpha = .16f), RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                            Text(rank.toString(), fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, color = Burgundy)
                        }
                    }
                    if (player.discards.size > 5) Text("+${player.discards.size - 5}", fontSize = 9.sp, color = Muted)
                }
            }
        }
    }
}

/** The centre tells the action through cards, movement and symbols; details remain one tap away. */
@Composable
private fun CentreStage(table: UiTable, onEvent: (UiPlayback) -> Unit, modifier: Modifier) {
    val event = table.playback ?: table.lastPlayback
    BoxWithConstraints(modifier) {
        val tiny = maxHeight < 130.dp
        Canvas(Modifier.fillMaxSize()) {
            drawOval(Burgundy.copy(alpha = .035f), Offset.Zero, Size(size.width, size.height))
            drawOval(Gold.copy(alpha = .22f), Offset(3.dp.toPx(), 3.dp.toPx()),
                Size((size.width - 6.dp.toPx()).coerceAtLeast(0f), (size.height - 6.dp.toPx()).coerceAtLeast(0f)), style = Stroke(1.dp.toPx()))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().height(if (tiny) 27.dp else 32.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (event != null) {
                    Surface(onClick = { onEvent(event) },
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("carte_jouee"),
                        shape = RoundedCornerShape(20.dp), color = Burgundy) {
                        Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val rank = event.cardValue
                            if (rank != null) {
                                Text(rank.toString(), color = Gold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                Icon(cardSymbol(rank), null, Modifier.size(16.dp), tint = Gold)
                            } else Icon(Icons.Outlined.MailOutline, null, Modifier.size(16.dp), tint = Gold)
                            Text(if (rank != null) cardInfo(rank).name else "Distribution", style = MaterialTheme.typography.labelMedium,
                                color = Ivory, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.Info, "Détails de l'action", Modifier.size(15.dp), tint = Ivory.copy(alpha = .7f))
                        }
                    }
                } else Text("À vous de jouer", style = MaterialTheme.typography.labelLarge, color = Burgundy, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .8f)) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp).semantics {
                        contentDescription = "${table.deckCount} cartes dans la pioche"
                    }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Layers, null, Modifier.size(15.dp), tint = Wine)
                        Text(table.deckCount.toString(), style = MaterialTheme.typography.labelLarge, color = Burgundy)
                        if (table.exposedCards.isNotEmpty()) {
                            VerticalDivider(Modifier.height(12.dp), color = Gold.copy(alpha = .45f))
                            Row(Modifier.semantics { contentDescription = "Cartes retirées : ${table.exposedCards.joinToString()}" }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                table.exposedCards.forEach { rank ->
                                    Text(rank.toString(), style = MaterialTheme.typography.labelSmall, color = Muted)
                                }
                            }
                        }
                    }
                }
            }
            if (event != null) {
                EffectScene(event.scene ?: legacyScene(event), Modifier.weight(1f).fillMaxWidth(), animationKey = event.id)
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.TouchApp, null, Modifier.size(if (tiny) 29.dp else 40.dp), tint = Wine)
                        Text("Choisissez une carte", style = MaterialTheme.typography.titleMedium, color = Burgundy)
                    }
                }
            }
            if (table.paused) Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Outlined.Pause, null, Modifier.size(12.dp), tint = Wine)
                Text("En pause", style = MaterialTheme.typography.labelSmall, color = Wine)
            }
        }
    }
}

/** Older saved actions have no scene. Show only already-public facts, never reconstruct hidden cards. */
private fun legacyScene(event: UiPlayback): UiEffectScene {
    val kind = when (event.cardValue) {
        0 -> UiEffectKind.SPY; 1 -> UiEffectKind.GUARD; 2 -> UiEffectKind.PRIEST
        3 -> UiEffectKind.BARON; 4 -> UiEffectKind.HANDMAID; 5 -> UiEffectKind.PRINCE
        6 -> UiEffectKind.CHANCELLOR; 7 -> UiEffectKind.KING; 8 -> UiEffectKind.COUNTESS
        9 -> UiEffectKind.PRINCESS; else -> UiEffectKind.DEAL
    }
    val participants = buildList {
        add(UiSceneParticipant(event.actorId, event.actorName, eliminated = event.actorId in event.eliminatedIds))
        if (event.targetId != null && event.targetId != event.actorId)
            add(UiSceneParticipant(event.targetId, event.targetName.orEmpty(), eliminated = event.targetId in event.eliminatedIds))
    }
    val outcome = when {
        event.targetId == null && kind in listOf(UiEffectKind.GUARD, UiEffectKind.PRIEST, UiEffectKind.BARON, UiEffectKind.PRINCE, UiEffectKind.KING) -> UiEffectOutcome.NO_TARGET
        kind == UiEffectKind.GUARD -> if (event.targetId in event.eliminatedIds) UiEffectOutcome.HIT else UiEffectOutcome.MISS
        kind == UiEffectKind.BARON -> if (event.eliminatedIds.isNotEmpty()) UiEffectOutcome.WIN else UiEffectOutcome.DRAW
        kind == UiEffectKind.HANDMAID -> UiEffectOutcome.PROTECTED
        kind == UiEffectKind.KING -> UiEffectOutcome.EXCHANGED
        kind in listOf(UiEffectKind.PRINCE, UiEffectKind.COUNTESS, UiEffectKind.PRINCESS) -> UiEffectOutcome.DISCARDED
        else -> UiEffectOutcome.RESOLVED
    }
    return UiEffectScene(kind, participants, outcome = outcome, guessedCardValue = event.guessValue, eliminatedIds = event.eliminatedIds)
}
