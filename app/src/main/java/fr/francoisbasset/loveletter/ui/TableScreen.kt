package fr.francoisbasset.loveletter.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.francoisbasset.loveletter.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun TableScreen(
    state: AppUiState,
    onSelectCard: (Int) -> Unit, onSelectTarget: (Int) -> Unit,
    onPlay: (String) -> Unit, onCancel: () -> Unit,
    onNextRound: () -> Unit, onNewGame: () -> Unit,
    onNoticeRead: () -> Unit
) {
    val table = state.table ?: return
    var detail by remember { mutableStateOf<Int?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showScores by remember { mutableStateOf(false) }
    var dealt by remember(table.round) { mutableStateOf(false) }
    val selectedIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(table.round) { dealt = true }
    LaunchedEffect(state.selectedCard, state.selectedTarget) {
        if (state.selectedCard != null) { delay(260); selectedIntoView.bringIntoView() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(color = Burgundy, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MANCHE ${table.round}", color = Gold, style = MaterialTheme.typography.labelSmall)
                        Text(table.turnLabel, color = Ivory, style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = { showScores = true }) { Icon(Icons.Outlined.EmojiEvents, "Afficher les scores", tint = Gold) }
                }
                Text("${table.goal} faveurs pour gagner · IA ${table.difficulty.label.lowercase()}", color = Ivory.copy(alpha = .76f), style = MaterialTheme.typography.bodySmall)
                if (table.thinking) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = Gold, trackColor = Wine)
            }
        }
        Text("La cour", style = MaterialTheme.typography.titleLarge, color = Burgundy)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(2.dp)) {
            items(table.players.filterNot { it.human }, key = { it.id }) { player -> OpponentPanel(player, onDetail = { detail = it }) }
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CardBack(Modifier.width(34.dp).height(46.dp), small = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    AnimatedContent(table.deckCount, label = "draw pile") { count ->
                        Text("${count} carte${if (count > 1) "s" else ""} dans la pioche", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Les mains adverses restent secrètes", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { showHistory = true }) { Icon(Icons.Outlined.History, "Historique des actions", tint = Wine) }
            }
        }
        if (table.exposedCards.isNotEmpty()) {
            Text("Cartes retirées à 2 joueurs : ${table.exposedCards.joinToString(" · ") { "${cardInfo(it).name} ($it)" }}",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (table.roundFinished) {
            RoundResultPanel(table, onNextRound, onNewGame)
        } else {
            val human = table.players.first { it.human }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (human.alive) "Votre main" else "Vous êtes éliminé", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (human.isProtected) Icon(Icons.Outlined.Shield, "Vous êtes protégé", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                FavorToken(human.score, Modifier.padding(start = 8.dp))
            }
            if (!human.alive) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                    Text("Vous observerez la fin de la manche. Les autres mains seront révélées à son terme.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            } else if (table.chancellor != null) {
                ChancellorPanel(table.chancellor, onPlay)
            } else {
                AnimatedVisibility(dealt, enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 4 }) {
                    AnimatedContent(targetState = table.hand, transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 5 }) togetherWith fadeOut(tween(160))
                }, label = "draw and deal") { hand ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        hand.forEachIndexed { index, value ->
                            val playable = table.humanTurn && table.moves.any { it.cardValue == value }
                            LetterCard(value, Modifier.weight(1f).testTag("main_${index}"),
                                selected = state.selectedCard == value,
                                playable = playable || !table.humanTurn,
                                onClick = { if (playable) onSelectCard(value) else detail = value })
                        }
                    }
                    }
                }
                if (table.humanTurn && state.selectedCard == null) {
                    Text("Touchez une carte jouable pour choisir votre action.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                AnimatedVisibility(visible = state.selectedCard != null && table.humanTurn) {
                    state.selectedCard?.let { SelectedActionPanel(it, state.selectedTarget, table, onSelectTarget, onPlay, onCancel, Modifier.bringIntoViewRequester(selectedIntoView)) }
                }
            }
            if (table.knownCards.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Vos informations privées", style = MaterialTheme.typography.titleMedium)
                        table.knownCards.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        HumanDiscard(table.players.first { it.human }, onDetail = { detail = it })
        if (table.history.isNotEmpty()) {
            Text("Dernières actions", style = MaterialTheme.typography.titleLarge, color = Burgundy)
            table.history.take(3).forEach { message -> Text(message, style = MaterialTheme.typography.bodyMedium, color = Muted) }
            TextButton(onClick = { showHistory = true }) { Text("Tout l'historique") }
        }
        Spacer(Modifier.height(12.dp))
    }
    detail?.let { CardDetailDialog(it) { detail = null } }
    if (showHistory) {
        AlertDialog(onDismissRequest = { showHistory = false }, title = { Text("Journal de la cour") },
            text = { Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                table.history.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            } }, confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Fermer") } })
    }
    if (showScores) {
        AlertDialog(onDismissRequest = { showScores = false }, title = { Text("Les faveurs de la Princesse") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Objectif : ${table.goal} pions Faveur", color = Muted)
                table.players.sortedByDescending { it.score }.forEach { player ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(player.name, Modifier.weight(1f)); FavorToken(player.score)
                    }
                }
            } }, confirmButton = { TextButton(onClick = { showScores = false }) { Text("Fermer") } })
    }
    table.notice?.let { notice ->
        AlertDialog(onDismissRequest = onNoticeRead, icon = { Icon(Icons.Outlined.Lock, null, tint = Wine) },
            title = { Text(notice.title) }, text = { Text(notice.message) },
            confirmButton = { TextButton(onClick = onNoticeRead) { Text("J'ai lu") } })
    }
}

@Composable
private fun OpponentPanel(player: UiPlayer, onDetail: (Int) -> Unit) {
    val color by animateColorAsState(if (player.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, label = "active player")
    val alpha by animateFloatAsState(if (player.alive) 1f else .6f, label = "eliminated player")
    Surface(Modifier.width(162.dp), shape = RoundedCornerShape(16.dp), color = color,
        border = BorderStroke(if (player.active) 2.dp else 1.dp, if (player.active) Wine else Gold.copy(alpha = .3f))) {
        Column(Modifier.padding(12.dp).graphicsLayer { this.alpha = alpha }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(player.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                FavorToken(player.score)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (player.revealedHand != null) {
                    Surface(onClick = { onDetail(player.revealedHand) }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("${player.revealedHand} · ${cardInfo(player.revealedHand).name}", Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                } else if (player.alive) CardBack(Modifier.width(27.dp).height(36.dp), small = true)
                Text(if (!player.alive) "Éliminé" else if (player.isProtected) "Protégé" else if (player.active) "À son tour" else "En lice",
                    color = if (player.isProtected) MaterialTheme.colorScheme.tertiary.copy(alpha = alpha) else Muted.copy(alpha = alpha),
                    style = MaterialTheme.typography.bodySmall)
                if (player.isProtected) Icon(Icons.Outlined.Shield, "Protection", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
            }
            Text("Défausse : ${if (player.discards.isEmpty()) "—" else player.discards.joinToString(" · ") { it.toString() }}",
                style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 2)
            if (player.discards.isNotEmpty()) TextButton(onClick = { onDetail(player.discards.last()) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(28.dp)) {
                Text(cardInfo(player.discards.last()).name, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedActionPanel(value: Int, targetId: Int?, table: UiTable, onTarget: (Int) -> Unit, onPlay: (String) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val info = cardInfo(value)
    val moves = table.moves.filter { it.cardValue == value }
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${info.value} · ${info.name}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, "Annuler la sélection") }
            }
            Text(info.effect, style = MaterialTheme.typography.bodyMedium)
            Text(info.explanation, color = Muted, style = MaterialTheme.typography.bodySmall)
            val targets = moves.mapNotNull { it.targetId }.distinct()
            if (targets.isEmpty()) {
                moves.firstOrNull()?.let { move -> Button(onClick = { onPlay(move.id) }, Modifier.fillMaxWidth().testTag("jouer_carte")) { Text("Jouer ${info.name}") } }
            } else {
                Text("Choisissez une cible", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    targets.forEach { id ->
                        val name = table.players.first { it.id == id }.name
                        FilterChip(selected = targetId == id, onClick = { onTarget(id) }, label = { Text(if (id == 0) "$name (vous)" else name) }, modifier = Modifier.testTag("cible_$id"))
                    }
                }
                if (targetId != null) {
                    val options = moves.filter { it.targetId == targetId }
                    if (options.any { it.guessValue != null }) {
                        Text("Quelle carte annoncez-vous ?", style = MaterialTheme.typography.titleMedium)
                        Text("L'annonce résout immédiatement l'effet du Garde.", style = MaterialTheme.typography.bodySmall, color = Muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            options.forEach { move -> move.guessValue?.let { guess ->
                                OutlinedButton(onClick = { onPlay(move.id) }, modifier = Modifier.testTag("annonce_$guess")) { Text("$guess · ${cardInfo(guess).name}") }
                            } }
                        }
                    } else options.firstOrNull()?.let { move ->
                        Button(onClick = { onPlay(move.id) }, modifier = Modifier.fillMaxWidth().testTag("jouer_carte")) {
                            Text("Jouer sur ${table.players.first { it.id == targetId }.name}")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChancellorPanel(selection: UiChancellor, onPlay: (String) -> Unit) {
    var keep by remember(selection) { mutableStateOf<Int?>(null) }
    var order by remember(selection, keep) { mutableStateOf<String?>(null) }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Le choix du Chancelier", style = MaterialTheme.typography.titleLarge)
            Text("1. Choisissez la carte que vous gardez.", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selection.actions.map { it.keepIndex }.distinct().forEach { index ->
                    val card = cardInfo(selection.cards[index])
                    FilterChip(selected = keep == index, onClick = { keep = index; order = null }, label = { Text("${card.value} · ${card.name}") }, modifier = Modifier.testTag("chancelier_garder_$index"))
                }
            }
            keep?.let { chosen ->
                Text("2. Choisissez l'ordre sous la pioche.", style = MaterialTheme.typography.bodyMedium)
                Text("La première carte indiquée sera repiochée avant la suivante. Aucune carte ne vient de la réserve.", style = MaterialTheme.typography.bodySmall, color = Muted)
                val choices = selection.actions.filter { it.keepIndex == chosen }
                choices.forEachIndexed { index, action ->
                    val text = if (action.bottomIndices.isEmpty()) "Aucune carte à remettre" else action.bottomIndices.joinToString(" → ") { cardInfo(selection.cards[it]).name }
                    Surface(onClick = { order = action.id }, shape = RoundedCornerShape(12.dp),
                        color = if (order == action.id) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, if (order == action.id) Wine else Gold), modifier = Modifier.testTag("chancelier_ordre_$index")) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = order == action.id, onClick = { order = action.id })
                            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Button(onClick = { order?.let(onPlay) }, enabled = order != null,
                    modifier = Modifier.fillMaxWidth().testTag("chancelier_confirmer")) { Text("Garder ${cardInfo(selection.cards[chosen]).name} et terminer") }
            }
        }
    }
}

@Composable
private fun HumanDiscard(player: UiPlayer, onDetail: (Int) -> Unit) {
    if (player.discards.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Votre défausse", style = MaterialTheme.typography.titleMedium, color = Burgundy)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(player.discards) { value -> LetterCard(value, Modifier.width(150.dp), compact = true, onClick = { onDetail(value) }) }
        }
    }
}

@Composable
private fun RoundResultPanel(table: UiTable, onNext: () -> Unit, onNew: () -> Unit) {
    var entered by remember(table.round, table.matchFinished) { mutableStateOf(false) }
    LaunchedEffect(table.round, table.matchFinished) { entered = true }
    val progress by animateFloatAsState(if (entered) 1f else 0f, tween(700), label = "victory glow")
    Surface(modifier = Modifier.graphicsLayer { alpha = progress; translationY = 24f * (1f - progress) },
        color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(22.dp)) {
        Box {
            if (table.matchFinished) Canvas(Modifier.matchParentSize()) {
                repeat(14) { index ->
                    val x = size.width * ((index * 37 % 100) / 100f)
                    val y = size.height * ((index * 23 % 100) / 100f)
                    drawCircle(Gold.copy(alpha = .3f * progress), 3.dp.toPx(), Offset(x, y))
                }
            }
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (table.matchFinished) Icons.Outlined.EmojiEvents else Icons.Outlined.FavoriteBorder, null, Modifier.size(42.dp), tint = Wine)
                Text(table.resultTitle.orEmpty(), style = MaterialTheme.typography.headlineMedium, color = Burgundy)
                Text(table.resultDescription.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                table.players.forEach { player ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(player.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        player.revealedHand?.let { Text("${cardInfo(it).name} ($it)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp)) }
                        FavorToken(player.score)
                    }
                }
                if (table.matchFinished) Button(onClick = onNew, Modifier.fillMaxWidth().testTag("nouvelle_partie_fin")) { Text("Une nouvelle partie") }
                else Button(onClick = onNext, Modifier.fillMaxWidth().testTag("manche_suivante")) { Text("Manche suivante") }
            }
        }
    }
}
