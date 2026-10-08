package fr.francoisbasset.loveletter.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
        // Reserve the controls and human hand before allocating the scrollable decisions.
        val decisionHeight = if (landscape) (maxHeight - 148.dp).coerceIn(64.dp, 140.dp)
            else if (compact) 148.dp else 170.dp
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
        PrivateEffectDialog(notice, onNoticeRead)
    }
}

@Composable
private fun PlaybackControls(table: UiTable, pace: GamePace, onContinue: () -> Unit, onPause: () -> Unit, onPace: () -> Unit, onHistory: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp)) {
        val narrow = maxWidth < 300.dp
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (table.playback != null) {
                Button(onClick = onContinue, enabled = !table.paused && table.notice == null,
                    contentPadding = PaddingValues(horizontal = if (narrow) 6.dp else 12.dp),
                    modifier = Modifier.weight(1f).height(42.dp).testTag("continuer_tour")) {
                    Text("Continuer", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                    if (!narrow) {
                        Spacer(Modifier.width(4.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(16.dp))
                    }
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(table.turnLabel, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (table.thinking) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Wine)
                }
            }
            if (narrow) {
                IconButton(onClick = onPace, modifier = Modifier.width(32.dp).height(44.dp).testTag("ouvrir_rythme")) {
                    Icon(Icons.Outlined.Speed, "Rythme de la partie : ${pace.label}", Modifier.size(20.dp), tint = Wine)
                }
            } else {
                TextButton(onClick = onPace, contentPadding = PaddingValues(6.dp),
                    modifier = Modifier.height(44.dp).testTag("ouvrir_rythme")) {
                    Text(when (pace) { GamePace.GUIDED -> "Mon rythme"; else -> pace.label }, style = MaterialTheme.typography.labelMedium)
                    Icon(Icons.Outlined.ExpandMore, null, Modifier.size(14.dp))
                }
            }
            IconButton(onClick = onPause,
                modifier = Modifier.width(if (narrow) 32.dp else 40.dp).height(44.dp).testTag("pause_partie")) {
                Icon(if (table.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    if (table.paused) "Reprendre la partie" else "Mettre la partie en pause", Modifier.size(22.dp), tint = Wine)
            }
            IconButton(onClick = onHistory, modifier = Modifier.width(if (narrow) 32.dp else 36.dp).height(44.dp)) {
                Icon(Icons.Outlined.History, "Historique des actions", tint = Wine, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun HumanHandStrip(table: UiTable, selected: Int?, onSelect: (Int) -> Unit, compact: Boolean) {
    val human = table.players.first { it.human }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (human.alive) Icons.Outlined.Style else Icons.Outlined.Visibility, null, Modifier.size(16.dp), tint = Wine)
            Spacer(Modifier.width(5.dp))
            Text(if (!human.alive) "Spectateur" else "Votre main", style = MaterialTheme.typography.labelMedium,
                color = Burgundy, modifier = Modifier.weight(1f))
            if (table.knownCards.isNotEmpty()) {
                var intelligence by remember { mutableStateOf(false) }
                TextButton(onClick = { intelligence = true }, contentPadding = PaddingValues(4.dp), modifier = Modifier.height(28.dp)) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(13.dp)); Spacer(Modifier.width(3.dp))
                    Text("Vos infos", style = MaterialTheme.typography.labelSmall)
                }
                if (intelligence) ReferenceDialog("Vos informations privées", table.knownCards) { intelligence = false }
            }
        }
        if (table.hand.isNotEmpty()) {
            AnimatedContent(table.hand, transitionSpec = {
                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(220))) togetherWith fadeOut(tween(140))
            }, label = "hand draw") { cards ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cards.forEachIndexed { index, value ->
                        val enabled = table.humanTurn && table.playback == null && !table.paused && table.notice == null && table.chancellor == null && table.moves.any { it.cardValue == value }
                        CompactHandCard(value, enabled, selected == value,
                            Modifier.weight(1f).height(if (compact) 54.dp else 74.dp).testTag("main_$index"),
                            onClick = { onSelect(value) })
                    }
                }
            }
        } else {
            Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (table.roundFinished) Icons.Outlined.DoneAll else Icons.Outlined.Visibility, null, Modifier.size(20.dp), tint = Muted)
                Spacer(Modifier.width(6.dp))
                Text(if (table.roundFinished) "Cartes révélées" else "La manche continue", style = MaterialTheme.typography.labelMedium, color = Muted)
            }
        }
    }
}

@Composable
private fun CompactHandCard(value: Int, enabled: Boolean, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val info = cardInfo(value)
    val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tween(180), label = "hand selection color")
    val emblemTurn by animateFloatAsState(if (selected) -10f else 0f, tween(220), label = "hand emblem turn")
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.semantics { contentDescription = "Votre ${info.name}, valeur $value${if (selected) ", sélectionnée" else ""}" },
        color = color, shape = RoundedCornerShape(13.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Wine else Gold.copy(alpha = .55f))) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(32.dp).background(Burgundy, CircleShape), contentAlignment = Alignment.Center) {
                Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = Ivory, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(info.name, style = MaterialTheme.typography.labelLarge, color = Burgundy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(cardSymbol(value), null, Modifier.size(19.dp).graphicsLayer { rotationZ = emblemTurn }, tint = Wine)
            }
        }
    }
}

private fun actionIntent(value: Int): String = when (value) {
    0 -> "Entrer dans l'ombre"
    1 -> "Deviner la carte"
    2 -> "Voir un secret"
    3 -> "Comparer les cartes"
    4 -> "Se protéger"
    5 -> "Changer de carte"
    6 -> "Choisir sa carte"
    7 -> "Échanger les mains"
    8 -> "Défausser la Comtesse"
    else -> "Quitter la manche"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedActionPanel(value: Int, targetId: Int?, table: UiTable, height: androidx.compose.ui.unit.Dp,
    onTarget: (Int) -> Unit, onPlay: (String) -> Unit, onCancel: () -> Unit, onDetail: () -> Unit) {
    val info = cardInfo(value)
    val moves = table.moves.filter { it.cardValue == value }
    Surface(Modifier.fillMaxWidth().height(height).testTag("panneau_actions"),
        color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 5.dp)
            .verticalScroll(rememberScrollState()).testTag("actions_scroll"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(cardSymbol(value), null, Modifier.size(24.dp), tint = Wine)
                Spacer(Modifier.width(7.dp))
                Text(actionIntent(value), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2)
                IconButton(onClick = onDetail, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Info, "Détails de ${info.name}", Modifier.size(20.dp))
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Close, "Annuler la sélection", Modifier.size(20.dp))
                }
            }
            val targets = moves.mapNotNull { it.targetId }.distinct()
            if (targets.isEmpty()) moves.firstOrNull()?.let { move ->
                Button(onClick = { onPlay(move.id) }, contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("jouer_carte")) {
                    Icon(cardSymbol(value), null, Modifier.size(19.dp)); Spacer(Modifier.width(7.dp)); Text("Jouer ${info.name}")
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    targets.forEach { id ->
                        val player = table.players.first { it.id == id }
                        val name = if (player.human) "Vous" else player.name
                        FilterChip(selected = targetId == id, onClick = { onTarget(id) },
                            label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = { Icon(if (targetId == id) Icons.Outlined.CheckCircle else Icons.Outlined.PersonOutline,
                                null, Modifier.size(17.dp)) },
                            modifier = Modifier.height(44.dp).testTag("cible_$id")
                                .semantics { contentDescription = "Cibler $name" })
                    }
                }
                if (targetId == null) {
                    Text("Choisissez une cible", style = MaterialTheme.typography.labelMedium, color = Muted)
                }
                targetId?.let { selectedTarget ->
                    val options = moves.filter { it.targetId == selectedTarget }
                    if (options.any { it.guessValue != null }) {
                        Text("Quelle carte ?", style = MaterialTheme.typography.labelMedium, color = Burgundy)
                        options.filter { it.guessValue != null }.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { move ->
                                    VisualCardTile(requireNotNull(move.guessValue), Modifier.weight(1f).height(66.dp)
                                        .testTag("annonce_${move.guessValue}"), onClick = { onPlay(move.id) })
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else options.firstOrNull()?.let { move ->
                        Button(onClick = { onPlay(move.id) }, contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("jouer_carte")) {
                            Icon(cardSymbol(value), null, Modifier.size(19.dp)); Spacer(Modifier.width(7.dp))
                            Text("Jouer", maxLines = 1)
                            Icon(Icons.Outlined.ArrowForward, null, Modifier.padding(horizontal = 5.dp).size(18.dp))
                            Text(table.players.first { it.id == selectedTarget }.let { if (it.human) "Vous" else it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChancellorPanel(selection: UiChancellor, height: androidx.compose.ui.unit.Dp, onPlay: (String) -> Unit) {
    var keep by remember(selection) { mutableStateOf<Int?>(null) }
    var order by remember(selection, keep) { mutableStateOf<String?>(null) }
    Surface(Modifier.fillMaxWidth().height(height).testTag("panneau_chancelier"),
        color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()).testTag("actions_scroll"),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.AccountBalance, null, Modifier.size(22.dp), tint = Wine)
                Text("1 · À garder", style = MaterialTheme.typography.titleSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                selection.actions.map { it.keepIndex }.distinct().forEach { index ->
                    VisualCardTile(selection.cards[index], Modifier.weight(1f).height(66.dp).testTag("chancelier_garder_$index"),
                        selected = keep == index, onClick = { keep = index; order = null })
                }
            }
            keep?.let { chosen ->
                Text("2 · Sous la pioche", style = MaterialTheme.typography.titleSmall)
                Text("Première repiochée → dernière", style = MaterialTheme.typography.labelSmall, color = Muted)
                selection.actions.filter { it.keepIndex == chosen }.forEachIndexed { index, action ->
                    val selected = order == action.id
                    val description = action.bottomIndices.joinToString(" puis ") { cardInfo(selection.cards[it]).name }
                    Surface(onClick = { order = action.id },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag("chancelier_ordre_$index")
                            .semantics { contentDescription = if (description.isEmpty()) "Ne rien remettre" else "Repiocher $description" },
                        shape = RoundedCornerShape(11.dp),
                        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Wine else Gold)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                null, Modifier.size(20.dp), tint = Wine)
                            if (action.bottomIndices.isEmpty()) Text("Aucune carte", style = MaterialTheme.typography.labelMedium)
                            action.bottomIndices.forEachIndexed { position, cardIndex ->
                                if (position > 0) Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp), tint = Wine)
                                val card = cardInfo(selection.cards[cardIndex])
                                Surface(Modifier.weight(1f).height(44.dp), color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, Gold.copy(alpha = .65f)), shape = RoundedCornerShape(7.dp)) {
                                    Row(Modifier.padding(5.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(card.value.toString(), style = MaterialTheme.typography.titleLarge, color = Burgundy)
                                        Icon(cardSymbol(card.value), null, Modifier.size(20.dp), tint = Wine)
                                        Text(card.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                Button(onClick = { order?.let(onPlay) }, enabled = order != null, contentPadding = PaddingValues(6.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("chancelier_confirmer")) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
                    Text("Garder ${cardInfo(selection.cards[chosen]).name}")
                }
            }
        }
    }
}

@Composable
private fun RoundResultPanel(table: UiTable, onNext: () -> Unit, onNew: () -> Unit) {
    var entered by remember(table.round, table.matchFinished) { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    LaunchedEffect(table.round, table.matchFinished) { entered = true }
    val alpha by animateFloatAsState(if (entered) 1f else 0f, tween(450), label = "round result")
    val trophyScale by animateFloatAsState(if (entered) 1f else .6f, tween(550), label = "result trophy")
    Surface(Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
        color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(34.dp).graphicsLayer { scaleX = trophyScale; scaleY = trophyScale }, tint = Wine)
                Text(table.resultTitle.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Burgundy,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { details = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Info, "Détails du résultat", Modifier.size(20.dp), tint = Wine)
                }
            }
            if (table.pointsAwarded.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    table.pointsAwarded.filterValues { it > 0 }.entries.sortedBy { if (it.key in table.roundWinners) 0 else 1 }.forEach { (id, points) ->
                        val player = table.players.first { it.id == id }
                        val name = if (player.human) "Vous" else player.name
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.semantics { contentDescription = "$name gagne $points faveur${if (points > 1) "s" else ""}" }) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(if (id in table.roundWinners) Icons.Outlined.EmojiEvents else Icons.Outlined.Visibility,
                                    null, Modifier.size(15.dp), tint = Wine)
                                Text(name, style = MaterialTheme.typography.labelMedium, color = Burgundy)
                                Text("+$points", style = MaterialTheme.typography.labelMedium, color = Wine, fontWeight = FontWeight.Bold)
                                Icon(Icons.Outlined.Favorite, null, Modifier.size(13.dp), tint = Wine)
                            }
                        }
                    }
                }
            }
            Button(onClick = if (table.matchFinished) onNew else onNext, contentPadding = PaddingValues(6.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp).testTag(if (table.matchFinished) "nouvelle_partie_fin" else "manche_suivante")) {
                Text(if (table.matchFinished) "Une nouvelle partie" else "Manche suivante")
                Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }
    if (details) ReferenceDialog(table.resultTitle.orEmpty(), listOf(table.resultDescription.orEmpty())) { details = false }
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
