package fr.francoisbasset.loveletter.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.francoisbasset.loveletter.ui.theme.*

/** The table never scrolls. Only a bounded decision panel or reference dialog can scroll. */
@Composable
fun TableScreen(
    state: AppUiState,
    onSelectCard: (Int) -> Unit, onSelectTarget: (Int) -> Unit,
    onPlay: (String) -> Unit, onCancel: () -> Unit,
    onNextRound: () -> Unit, onNewGame: () -> Unit,
    onNoticeRead: () -> Unit,
    onContinue: () -> Unit, onPause: () -> Unit, onPace: (GamePace) -> Unit
) {
    val table = state.table ?: return
    var detail by remember { mutableStateOf<Int?>(null) }
    var history by remember { mutableStateOf(false) }
    var paceDialog by remember { mutableStateOf(false) }
    var publicPlayer by remember { mutableStateOf<UiPlayer?>(null) }
    var eventDetail by remember { mutableStateOf<UiPlayback?>(null) }
    val targets = table.moves.filter { it.cardValue == state.selectedCard }.mapNotNull { it.targetId }.toSet()

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        val landscape = maxWidth >= 560.dp && maxWidth > maxHeight
        val compact = maxHeight < 560.dp
        val decisionHeight = if (landscape) 140.dp else if (compact) 148.dp else 170.dp
        @Composable fun Board(modifier: Modifier) {
            CourtBoard(table, state.selectedTarget, targets, onSelectTarget,
                onPublicPlayer = { publicPlayer = it }, onEvent = { eventDetail = it }, modifier = modifier)
        }
        @Composable fun Lower(modifier: Modifier = Modifier) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlaybackControls(table, state.pace, onContinue, onPause, { paceDialog = true }, { history = true })
                when {
                    table.roundFinished -> RoundResultPanel(table, onNextRound, onNewGame)
                    table.chancellor != null -> ChancellorPanel(table.chancellor, decisionHeight, onPlay)
                    state.selectedCard != null && table.humanTurn -> SelectedActionPanel(
                        state.selectedCard, state.selectedTarget, table, decisionHeight,
                        onSelectTarget, onPlay, onCancel, { detail = state.selectedCard })
                }
                HumanHandStrip(table, state.selectedCard, onSelectCard, compact = state.selectedCard != null || table.chancellor != null || landscape)
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Board(Modifier.weight(.65f).fillMaxHeight())
                Lower(Modifier.weight(.35f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Board(Modifier.weight(1f).fillMaxWidth())
                Lower(Modifier.fillMaxWidth())
            }
        }
    }
    detail?.let { CardDetailDialog(it) { detail = null } }
    if (history) ReferenceDialog("Journal de la cour", table.history) { history = false }
    if (paceDialog) PaceDialog(state.pace, onPace) { paceDialog = false }
    publicPlayer?.let { player ->
        ReferenceDialog(if (player.human) "Votre siège" else "${player.name} · informations publiques", buildList {
            add("${player.score} pion${if (player.score > 1) "s" else ""} Faveur · ${if (!player.alive) "éliminé" else if (player.isProtected) "protégé" else "en lice"}")
            player.revealedHand?.let { add("${if (player.human && !table.roundFinished) "Carte de votre main" else "Main révélée"} : ${cardInfo(it).name} ($it)") }
            add(if (player.discards.isEmpty()) "Aucune carte défaussée." else "Défausse, dans l'ordre :")
            player.discards.forEach { add("$it · ${cardInfo(it).name}") }
        }) { publicPlayer = null }
    }
    eventDetail?.let { event ->
        val info = event.cardValue?.let { cardInfo(it) }
        ReferenceDialog(if (info != null) "${info.value} · ${info.name}" else event.title, buildList {
            add(event.title)
            info?.let { add(it.effect); add(it.explanation) }
            addAll(event.messages.ifEmpty { listOf(event.summary) })
        }) { eventDetail = null }
    }
    table.notice?.let { notice ->
        AlertDialog(onDismissRequest = {}, icon = { Icon(Icons.Outlined.Lock, null, tint = Wine) },
            title = { Text(notice.title) }, text = { Text(notice.message) },
            confirmButton = { TextButton(onClick = onNoticeRead) { Text("J'ai lu") } })
    }
}

@Composable
private fun PlaybackControls(table: UiTable, pace: GamePace, onContinue: () -> Unit, onPause: () -> Unit, onPace: () -> Unit, onHistory: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (table.playback != null) {
            Button(onClick = onContinue, enabled = !table.paused && table.notice == null,
                contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.weight(1f).height(42.dp).testTag("continuer_tour")) {
                Text("Continuer"); Spacer(Modifier.width(4.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(16.dp))
            }
        } else {
            Column(Modifier.weight(1f)) {
                Text(table.turnLabel, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (table.thinking) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Wine)
            }
        }
        TextButton(onClick = onPace, contentPadding = PaddingValues(6.dp), modifier = Modifier.height(44.dp).testTag("ouvrir_rythme")) {
            Text(when (pace) { GamePace.GUIDED -> "Mon rythme"; else -> pace.label }, style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(14.dp))
        }
        IconButton(onClick = onPause, modifier = Modifier.size(40.dp).testTag("pause_partie")) {
            Icon(if (table.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                if (table.paused) "Reprendre la partie" else "Mettre la partie en pause", tint = Wine)
        }
        IconButton(onClick = onHistory, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.History, "Historique des actions", tint = Wine, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun HumanHandStrip(table: UiTable, selected: Int?, onSelect: (Int) -> Unit, compact: Boolean) {
    val human = table.players.first { it.human }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (!human.alive) "Vous êtes éliminé · observez la manche" else "Votre main", style = MaterialTheme.typography.labelMedium, color = Burgundy, modifier = Modifier.weight(1f))
            if (table.knownCards.isNotEmpty()) {
                var intelligence by remember { mutableStateOf(false) }
                TextButton(onClick = { intelligence = true }, contentPadding = PaddingValues(4.dp), modifier = Modifier.height(24.dp)) { Text("Vos infos", style = MaterialTheme.typography.labelSmall) }
                if (intelligence) ReferenceDialog("Vos informations privées", table.knownCards) { intelligence = false }
            }
        }
        if (table.hand.isNotEmpty()) {
            AnimatedContent(table.hand, label = "hand draw") { cards ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cards.forEachIndexed { index, value ->
                        val enabled = table.humanTurn && table.playback == null && !table.paused && table.notice == null && table.chancellor == null && table.moves.any { it.cardValue == value }
                        CompactHandCard(value, enabled, selected == value,
                            Modifier.weight(1f).height(if (compact) 48.dp else 66.dp).testTag("main_$index"),
                            onClick = { onSelect(value) })
                    }
                }
            }
        } else {
            Text(if (table.roundFinished) "Les mains de fin de manche sont révélées sur le plateau." else "Votre siège reste visible sur le plateau.", style = MaterialTheme.typography.bodySmall, color = Muted,
                modifier = Modifier.heightIn(min = 30.dp))
        }
    }
}

@Composable
private fun CompactHandCard(value: Int, enabled: Boolean, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val info = cardInfo(value)
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.semantics { contentDescription = "Votre ${info.name}, valeur $value" },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(13.dp), border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Wine else Gold.copy(alpha = .55f))) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = Burgundy, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.titleMedium, color = Burgundy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (selected) "Carte sélectionnée" else if (enabled) "Touchez pour jouer" else "En attente", style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedActionPanel(value: Int, targetId: Int?, table: UiTable, height: androidx.compose.ui.unit.Dp,
    onTarget: (Int) -> Unit, onPlay: (String) -> Unit, onCancel: () -> Unit, onDetail: () -> Unit) {
    val info = cardInfo(value)
    val moves = table.moves.filter { it.cardValue == value }
    Surface(Modifier.fillMaxWidth().height(height).testTag("panneau_actions"), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp).verticalScroll(rememberScrollState()).testTag("actions_scroll"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${info.value} · ${info.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDetail, contentPadding = PaddingValues(4.dp), modifier = Modifier.height(28.dp)) { Text("Détails", style = MaterialTheme.typography.labelMedium) }
                IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, "Annuler la sélection", Modifier.size(18.dp)) }
            }
            Text(info.effect, style = MaterialTheme.typography.bodySmall)
            val targets = moves.mapNotNull { it.targetId }.distinct()
            if (targets.isEmpty()) moves.firstOrNull()?.let { move ->
                Button(onClick = { onPlay(move.id) }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.fillMaxWidth().height(36.dp).testTag("jouer_carte")) { Text("Jouer ${info.name}") }
            } else {
                Text("Cible : touchez un siège ou choisissez ici", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    targets.forEach { id ->
                        val name = if (id == 0) "Vous" else table.players.first { it.id == id }.name
                        FilterChip(selected = targetId == id, onClick = { onTarget(id) }, label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.height(32.dp).testTag("cible_$id"))
                    }
                }
                targetId?.let { selectedTarget ->
                    val options = moves.filter { it.targetId == selectedTarget }
                    if (options.any { it.guessValue != null }) {
                        Text("Annonce du Garde · touchez pour annoncer", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            options.forEach { move -> move.guessValue?.let { guess ->
                                OutlinedButton(onClick = { onPlay(move.id) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp).testTag("annonce_$guess")) { Text("$guess ${cardInfo(guess).name}", style = MaterialTheme.typography.labelMedium) }
                            } }
                        }
                    } else options.firstOrNull()?.let { move ->
                        Button(onClick = { onPlay(move.id) }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.fillMaxWidth().height(36.dp).testTag("jouer_carte")) { Text("Jouer sur ${table.players.first { it.id == selectedTarget }.name}") }
                    }
                }
            }
            HorizontalDivider(color = Gold.copy(alpha = .45f))
            Text(info.explanation, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChancellorPanel(selection: UiChancellor, height: androidx.compose.ui.unit.Dp, onPlay: (String) -> Unit) {
    var keep by remember(selection) { mutableStateOf<Int?>(null) }
    var order by remember(selection, keep) { mutableStateOf<String?>(null) }
    Surface(Modifier.fillMaxWidth().height(height).testTag("panneau_chancelier"), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()).testTag("actions_scroll"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Chancelier · 1. Gardez une carte", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                selection.actions.map { it.keepIndex }.distinct().forEach { index ->
                    val card = cardInfo(selection.cards[index])
                    FilterChip(selected = keep == index, onClick = { keep = index; order = null }, label = { Text("${card.value} ${card.name}", style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.height(32.dp).testTag("chancelier_garder_$index"))
                }
            }
            keep?.let { chosen ->
                Text("2. Ordre sous la pioche (première repiochée → dernière)", style = MaterialTheme.typography.bodySmall)
                selection.actions.filter { it.keepIndex == chosen }.forEachIndexed { index, action ->
                    val label = if (action.bottomIndices.isEmpty()) "Rien à remettre" else action.bottomIndices.joinToString(" → ") { cardInfo(selection.cards[it]).name }
                    Surface(onClick = { order = action.id }, modifier = Modifier.fillMaxWidth().testTag("chancelier_ordre_$index"), shape = RoundedCornerShape(9.dp),
                        color = if (order == action.id) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, if (order == action.id) Wine else Gold)) {
                        Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (order == action.id) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp)); Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Button(onClick = { order?.let(onPlay) }, enabled = order != null, contentPadding = PaddingValues(6.dp), modifier = Modifier.fillMaxWidth().height(36.dp).testTag("chancelier_confirmer")) { Text("Garder ${cardInfo(selection.cards[chosen]).name}") }
            }
        }
    }
}

@Composable
private fun RoundResultPanel(table: UiTable, onNext: () -> Unit, onNew: () -> Unit) {
    var entered by remember(table.round, table.matchFinished) { mutableStateOf(false) }
    LaunchedEffect(table.round, table.matchFinished) { entered = true }
    val alpha by animateFloatAsState(if (entered) 1f else 0f, tween(450), label = "round result")
    Surface(Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }, color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(table.resultTitle.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Burgundy, maxLines = 2)
            Text(table.resultDescription.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Button(onClick = if (table.matchFinished) onNew else onNext, contentPadding = PaddingValues(6.dp), modifier = Modifier.fillMaxWidth().height(36.dp)
                .testTag(if (table.matchFinished) "nouvelle_partie_fin" else "manche_suivante")) { Text(if (table.matchFinished) "Une nouvelle partie" else "Manche suivante") }
        }
    }
}

@Composable
private fun ReferenceDialog(title: String, paragraphs: List<String>, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } })
}

@Composable
fun PaceDialog(selected: GamePace, onPace: (GamePace) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Le rythme de la partie") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GamePace.entries.forEach { pace ->
                Surface(onClick = { onPace(pace); onDismiss() }, shape = RoundedCornerShape(12.dp), color = if (selected == pace) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().testTag("rythme_${pace.name}")) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (selected == pace) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(20.dp), tint = Wine)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(pace.label, style = MaterialTheme.typography.titleMedium)
                            Text(pace.explanation, style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                    }
                }
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } })
}
