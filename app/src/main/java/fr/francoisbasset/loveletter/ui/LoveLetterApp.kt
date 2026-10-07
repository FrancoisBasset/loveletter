package fr.francoisbasset.loveletter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.francoisbasset.loveletter.AppScreen
import fr.francoisbasset.loveletter.GameViewModel
import fr.francoisbasset.loveletter.engine.*
import kotlin.random.Random

@Composable
fun LoveLetterApp(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    var returnToGame by rememberSaveable { mutableStateOf(false) }
    var pauseDialog by remember { mutableStateOf(false) }
    var dismissedError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.error) { if (state.error == null) dismissedError = null }
    val goBack: () -> Unit = {
        if (state.screen == AppScreen.GAME) pauseDialog = true
        else if (returnToGame && state.game != null) {
            returnToGame = false
            viewModel.navigate(AppScreen.GAME)
        } else viewModel.navigate(AppScreen.HOME)
    }
    BackHandler(enabled = state.screen != AppScreen.HOME, onBack = goBack)
    LoveLetterTheme {
        Surface(color = Ink) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF362333), Ink, Color(0xFF1D2029)))
                ).windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                AnimatedContent(
                    targetState = state.screen,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                    label = "navigation",
                ) { screen ->
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            canResume = state.hasSavedGame,
                            onNew = { returnToGame = false; viewModel.navigate(AppScreen.SETUP) },
                            onResume = viewModel::resumeGame,
                            onRules = { returnToGame = false; viewModel.navigate(AppScreen.RULES) },
                            onCards = { returnToGame = false; viewModel.navigate(AppScreen.CARDS) },
                        )
                        AppScreen.SETUP -> SetupScreen(
                            hasSavedGame = state.hasSavedGame,
                            onStart = viewModel::startGame,
                            onBack = goBack,
                        )
                        AppScreen.RULES -> RulesScreen(onBack = goBack, onCards = { viewModel.navigate(AppScreen.CARDS) })
                        AppScreen.CARDS -> EncyclopediaScreen(onBack = goBack)
                        AppScreen.GAME -> state.game?.let { game ->
                            GameScreen(
                                game = game,
                                busy = state.busy,
                                onPlay = viewModel::play,
                                onNextRound = viewModel::continueRound,
                                onPause = { pauseDialog = true },
                                onRules = { returnToGame = true; viewModel.navigate(AppScreen.RULES) },
                                onHome = viewModel::showHome,
                                onNew = { viewModel.navigate(AppScreen.SETUP) },
                            )
                        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
        if (pauseDialog) AlertDialog(
            onDismissRequest = { pauseDialog = false },
            title = { Text("Mettre la cour en pause ?", fontFamily = FontFamily.Serif) },
            text = { Text("Votre partie est enregistrée automatiquement. Vous pourrez la reprendre depuis l’accueil.") },
            confirmButton = { TextButton(onClick = { pauseDialog = false; returnToGame = false; viewModel.showHome() }) { Text("Accueil") } },
            dismissButton = { TextButton(onClick = { pauseDialog = false }) { Text("Continuer") } },
        )
        state.secretNotice?.takeIf { state.screen == AppScreen.GAME }?.let { notice ->
            AlertDialog(
                onDismissRequest = {},
                icon = { CourtEmblem(2, Modifier.size(56.dp)) },
                title = { Text("Pour vos yeux seulement", fontFamily = FontFamily.Serif) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(notice)
                    Text("Les adversaires attendent que vous ayez lu cette information.", color = Muted, style = MaterialTheme.typography.bodySmall)
                } },
                confirmButton = { TextButton(onClick = viewModel::acknowledgeSecret) { Text("J’ai vu") } },
            )
        }
        if (state.error != null && state.error != dismissedError) AlertDialog(
            onDismissRequest = { dismissedError = state.error },
            title = { Text("Un instant…") },
            text = { Text(state.error.orEmpty()) },
            confirmButton = { TextButton(onClick = { dismissedError = state.error }) { Text("Compris") } },
        )
    }
}

@Composable
private fun HomeScreen(canResume: Boolean, onNew: () -> Unit, onResume: () -> Unit, onRules: () -> Unit, onCards: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Eyebrow("Intrigues • Déduction • Faveur")
        Spacer(Modifier.height(34.dp))
        Box(Modifier.height(225.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            PlayingCard(4, "Servante", "Une protection précieuse", Modifier.offset(x = (-62).dp, y = 8.dp).graphicsRotation(-12f), width = 128.dp)
            PlayingCard(9, "Princesse", "Le cœur de la cour", Modifier.offset(x = 57.dp, y = 4.dp).graphicsRotation(12f), width = 128.dp)
            Box(Modifier.offset(y = 73.dp).size(58.dp).clip(CircleShape).background(Wine).border(2.dp, Gold, CircleShape), contentAlignment = Alignment.Center) {
                Text("L", color = Gold, fontFamily = FontFamily.Serif, fontSize = 35.sp)
            }
        }
        Spacer(Modifier.height(25.dp))
        Text("Love Letter", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text("Une lettre. Mille intentions.", color = Gold, fontFamily = FontFamily.Serif, fontSize = 20.sp)
        Spacer(Modifier.height(13.dp))
        Text("Approchez la cour, déjouez vos rivaux et remportez les faveurs de la princesse.", color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(30.dp))
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) { Text("Nouvelle partie") }
        if (canResume) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onResume, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("Reprendre la partie") }
        }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onRules, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Les règles") }
            TextButton(onClick = onCards, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Les cartes") }
        }
        Spacer(Modifier.height(24.dp))
        Text("ADAPTATION INDÉPENDANTE", color = Muted, style = MaterialTheme.typography.labelSmall)
        Text("Édition française Z-Man Games · 2019", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        Text("Illustrations originales · Jeu hors ligne", color = Muted.copy(alpha = .7f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
    }
}

private fun Modifier.graphicsRotation(degrees: Float): Modifier = rotate(degrees)

@Composable
private fun SetupScreen(hasSavedGame: Boolean, onStart: (GameConfig) -> Unit, onBack: () -> Unit) {
    var players by rememberSaveable { mutableIntStateOf(4) }
    var difficultyName by rememberSaveable { mutableStateOf(Difficulty.NORMAL.name) }
    var randomFirst by rememberSaveable { mutableStateOf(false) }
    var seedText by rememberSaveable { mutableStateOf("") }
    var pendingConfig by remember { mutableStateOf<GameConfig?>(null) }
    val difficulty = Difficulty.valueOf(difficultyName)
    val validSeed = seedText.isBlank() || seedText.toLongOrNull() != null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(23.dp)) {
        ScreenHeading("À la cour", "Préparez votre prochaine partie", onBack)
        CourtPanel {
            Eyebrow("01 / Les invités")
            Text("Combien de joueurs ?", style = MaterialTheme.typography.titleLarge)
            Text("Vous et ${players - 1} adversaire${if (players > 2) "s" else ""} contrôlé${if (players > 2) "s" else ""} par l’IA.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                (2..6).forEach { count ->
                    FilterChip(selected = count == players, onClick = { players = count }, label = { Text(count.toString(), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                }
            }
        }
        CourtPanel {
            Eyebrow("02 / Vos adversaires")
            Text("Un peu de défi", style = MaterialTheme.typography.titleLarge)
            Difficulty.entries.forEach { level ->
                val (title, description) = when (level) {
                    Difficulty.EASY -> "Facile" to "Des choix simples, pour découvrir les personnages."
                    Difficulty.NORMAL -> "Normale" to "Observe les cartes jouées et les informations connues."
                    Difficulty.HARD -> "Difficile" to "Évalue les probabilités et les conséquences de ses choix."
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { difficultyName = level.name }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = difficulty == level, onClick = { difficultyName = level.name })
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Les IA ne voient jamais les cartes secrètes auxquelles elles n’ont pas droit.", style = MaterialTheme.typography.bodySmall, color = Teal)
        }
        CourtPanel {
            Eyebrow("03 / La première lettre")
            Text("Qui commence ?", style = MaterialTheme.typography.titleLarge)
            Text("Dans le jeu de table, la dernière personne ayant écrit une lettre commence. Choisissez ici votre point de départ.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = !randomFirst, onClick = { randomFirst = false }, label = { Text("Vous") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = randomFirst, onClick = { randomFirst = true }, label = { Text("Au hasard") }, modifier = Modifier.heightIn(min = 48.dp))
            }
            OutlinedTextField(
                value = seedText,
                onValueChange = { seedText = it },
                label = { Text("Graine aléatoire (facultatif)") },
                supportingText = { Text(if (validSeed) "Pour rejouer un même mélange initial." else "Saisissez un nombre entier valide.") },
                isError = !validSeed,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            enabled = validSeed,
            onClick = {
                val seed = seedText.toLongOrNull() ?: System.currentTimeMillis()
                val config = GameConfig(players, difficulty, seed, if (randomFirst) Random(seed).nextInt(players) else 0)
                if (hasSavedGame) pendingConfig = config else onStart(config)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
        ) { Text("Entrer à la cour") }
        Spacer(Modifier.height(6.dp))
    }
    pendingConfig?.let { config -> AlertDialog(
        onDismissRequest = { pendingConfig = null },
        title = { Text("Commencer une nouvelle partie ?") },
        text = { Text("La partie enregistrée sera remplacée par celle-ci.") },
        confirmButton = { TextButton(onClick = { pendingConfig = null; onStart(config) }) { Text("Nouvelle partie") } },
        dismissButton = { TextButton(onClick = { pendingConfig = null }) { Text("Annuler") } },
    ) }
}

@Composable
private fun GameScreen(
    game: PlayerObservation,
    busy: Boolean,
    onPlay: (GameAction) -> Unit,
    onNextRound: () -> Unit,
    onPause: () -> Unit,
    onRules: () -> Unit,
    onHome: () -> Unit,
    onNew: () -> Unit,
) {
    var inspectPlayerId by remember { mutableStateOf<Int?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val current = game.players.firstOrNull { it.id == game.currentPlayer }
    val me = game.players.first { it.id == game.playerId }
    val roundOver = game.phase == Phase.ROUND_OVER || game.phase == Phase.GAME_OVER
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Love Letter")
                Text("Manche ${game.roundNumber}", style = MaterialTheme.typography.headlineMedium)
            }
            TextButton(onClick = onRules, modifier = Modifier.heightIn(min = 48.dp)) { Text("Règles") }
            TextButton(onClick = onPause, modifier = Modifier.heightIn(min = 48.dp)) { Text("Pause") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            game.players.filter { it.id != game.playerId }.forEach { player ->
                PlayerTile(player, active = game.currentPlayer == player.id && !roundOver, onClick = { inspectPlayerId = player.id })
            }
        }
        CourtPanel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                AnimatedContent(targetState = game.deckCount, transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = .92f)) togetherWith fadeOut(tween(180)) }, label = "pioche") { count ->
                    CardBack(count = count)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Eyebrow(if (roundOver) "La cour a décidé" else "Au centre de la table")
                    Text(if (roundOver) "Manche terminée" else if (game.isMyTurn) "À vous de jouer" else "${current?.name ?: "L’adversaire"} réfléchit…", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Text("Objectif : ${game.pointsToWin} pions Faveur", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("Une carte de réserve au départ", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            AnimatedVisibility(busy && !roundOver) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = Gold, trackColor = Wine) }
            if (game.faceUpRemoved.isNotEmpty()) {
                HorizontalDivider(color = Gold.copy(alpha = .12f), modifier = Modifier.padding(vertical = 4.dp))
                Text("Cartes écartées face visible", color = Muted, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    game.faceUpRemoved.forEach { card -> Token("${card.value} · ${card.frenchName}") }
                }
            }
        }
        if (roundOver) {
            RoundResultPanel(game, onNextRound, onHome, onNew)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Votre main")
                    Text(if (me.eliminated) "Vous observez la cour" else if (game.phase == Phase.CHANCELLOR && game.isMyTurn) "Le choix du Chancelier" else "Une carte, une intention", style = MaterialTheme.typography.titleLarge)
                }
                Token("${me.score} / ${game.pointsToWin} ♥")
            }
            AnimatedVisibility(me.protected && !me.eliminated) { Token("Protégé jusqu’à votre prochain tour", Teal) }
            AnimatedContent(targetState = me.eliminated, label = "élimination") { eliminated ->
                if (eliminated) CourtPanel(Modifier.fillMaxWidth()) {
                    Text("Votre lettre n’atteindra pas la princesse cette fois.", style = MaterialTheme.typography.titleMedium)
                    Text("Vous reviendrez à la prochaine manche. Les autres joueurs poursuivent la partie.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                } else if (game.phase == Phase.CHANCELLOR && game.isMyTurn) ChancellorChoices(game, busy, onPlay)
                else HandAndActions(game, busy, onPlay)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { inspectPlayerId = game.playerId }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Votre défausse") }
            OutlinedButton(onClick = { showHistory = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Historique") }
        }
        AnimatedContent(targetState = game.history.lastOrNull(), transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith fadeOut() }, label = "carte jouée") { event ->
            if (event != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("✦", color = Gold)
                Text(event.message, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    inspectPlayerId?.let { id ->
        game.players.firstOrNull { it.id == id }?.let { player ->
            AlertDialog(
                onDismissRequest = { inspectPlayerId = null },
                title = { Text(player.name, fontFamily = FontFamily.Serif) },
                text = { Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${player.score} pion${if (player.score == 1) "" else "s"} Faveur · ${if (player.eliminated) "Éliminé de la manche" else if (player.protected) "Protégé" else "En jeu"}")
                    player.knownCard?.let { Text("Information connue : ${it.frenchName} (${it.value}).", color = Teal) }
                    Eyebrow("Défausse publique · ordre de jeu")
                    if (player.discard.isEmpty()) EmptyState("Aucune carte jouée.")
                    player.discard.forEachIndexed { index, card ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${index + 1}.", color = Muted)
                            Column { Text("${card.value} · ${card.frenchName}", color = Gold); Text(card.summary, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                } },
                confirmButton = { TextButton(onClick = { inspectPlayerId = null }) { Text("Fermer") } },
            )
        }
    }
    if (showHistory) AlertDialog(
        onDismissRequest = { showHistory = false },
        title = { Text("Les échos de la cour", fontFamily = FontFamily.Serif) },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            game.history.asReversed().forEach { event ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Eyebrow("Manche ${event.roundNumber}${if (event.audience != null) " · Pour vous" else ""}", color = if (event.audience != null) Teal else Gold)
                    Text(event.message, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(Modifier.padding(top = 10.dp), color = Gold.copy(alpha = .1f))
                }
            }
        } },
        confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Fermer") } },
    )
}

@Composable
private fun PlayerTile(player: PublicPlayer, active: Boolean, onClick: () -> Unit) {
    val opacity by animateFloatAsState(if (player.eliminated) .48f else 1f, label = "état du joueur")
    val background by animateColorAsState(if (active) Wine.copy(alpha = .7f) else Panel, label = "joueur actif")
    Column(
        Modifier.width(140.dp).alpha(opacity).clip(RoundedCornerShape(16.dp)).background(background)
            .border(1.dp, if (active) Gold.copy(alpha = .8f) else Gold.copy(alpha = .1f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(13.dp)
            .semantics { contentDescription = "${player.name}, ${player.score} pions, ${if (player.eliminated) "éliminé" else if (player.protected) "protégé" else "en jeu"}. Voir sa défausse." },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(player.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${player.score} ♥", color = Gold, style = MaterialTheme.typography.labelMedium)
        }
        Text(if (player.eliminated) "Éliminé" else if (player.protected) "◇ Protégé" else if (active) "À son tour" else "Dans la cour", color = if (player.protected) Teal else Muted, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (player.eliminated) "—" else "▱", color = Gold, fontSize = 19.sp)
            Text(player.knownCard?.let { "${it.value} · ${it.frenchName}" } ?: "${player.handCount} carte${if (player.handCount > 1) "s" else ""} cachée${if (player.handCount > 1) "s" else ""}", color = if (player.knownCard != null) Teal else Muted, style = MaterialTheme.typography.bodySmall)
        }
        Text(player.discard.lastOrNull()?.let { "${it.frenchName} · voir tout ›" } ?: "Voir la défausse ›", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HandAndActions(game: PlayerObservation, busy: Boolean, onPlay: (GameAction) -> Unit) {
    var selectedIndex by remember(game.roundNumber, game.hand) { mutableIntStateOf(-1) }
    var targetId by remember(selectedIndex, game.legalActions) { mutableStateOf<Int?>(null) }
    var guess by remember(selectedIndex, targetId, game.legalActions) { mutableStateOf<Card?>(null) }
    val allActions = game.legalActions.filterIsInstance<PlayCard>()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AnimatedContent(targetState = game.hand, transitionSpec = { (fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it / 5 })) togetherWith fadeOut(tween(130)) }, label = "distribution et nouvelle carte") { hand ->
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                hand.forEachIndexed { index, card ->
                    PlayingCard(card.value, card.frenchName, card.summary, selected = selectedIndex == index, enabled = !game.isMyTurn || allActions.any { it.cardIndex == index }, onClick = { selectedIndex = index })
                }
            }
        }
        if (selectedIndex !in game.hand.indices) {
            Text(if (game.isMyTurn) "Touchez une carte pour lire son effet et la jouer." else "Touchez votre carte pour consulter son effet.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        } else {
            val card = game.hand[selectedIndex]
            val cardActions = allActions.filter { it.cardIndex == selectedIndex }
            CourtPanel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(card.frenchName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Token("Valeur ${card.value}")
                }
                Text(card.explanation, style = MaterialTheme.typography.bodyMedium)
                if (game.isMyTurn && cardActions.isEmpty()) {
                    Text("Cette carte ne peut pas être jouée maintenant. Sélectionnez une carte autorisée.", color = Gold, style = MaterialTheme.typography.bodySmall)
                }
                if (cardActions.isNotEmpty()) {
                    val targets = cardActions.mapNotNull { it.targetId }.distinct()
                    val chosenTarget = targetId?.takeIf { it in targets } ?: targets.singleOrNull()
                    if (targets.isNotEmpty()) {
                        Eyebrow("Choisir une cible")
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            targets.forEach { id ->
                                val player = game.players.first { it.id == id }
                                FilterChip(selected = id == chosenTarget, onClick = { targetId = id }, label = { Text(if (id == game.playerId) "Vous-même" else player.name) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                            }
                        }
                    }
                    val targeted = cardActions.filter { it.targetId == chosenTarget }
                    val guesses = targeted.mapNotNull { it.guess }.distinct()
                    if (guesses.isNotEmpty()) {
                        Eyebrow("Quel personnage détient votre cible ?")
                        // Choices come exclusively from the engine's legal actions.
                        guesses.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { choice ->
                                    FilterChip(selected = guess == choice, onClick = { guess = choice }, label = { Text("${choice.value} · ${choice.frenchName}") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    val action = targeted.firstOrNull { it.guess == guess }
                    if (targets.isEmpty()) Text("Aucun choix de cible nécessaire.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { action?.let(onPlay) }, enabled = action != null && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(13.dp)) {
                        Text("Jouer ${card.frenchName}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChancellorChoices(game: PlayerObservation, busy: Boolean, onPlay: (GameAction) -> Unit) {
    var keep by remember(game.hand) { mutableIntStateOf(-1) }
    var chosenOrder by remember(keep, game.hand) { mutableIntStateOf(-1) }
    val legal = game.legalActions.filterIsInstance<ResolveChancellor>()
    val choices = legal.filter { it.keepIndex == keep }
    val selected = if (choices.size == 1) choices.first() else choices.getOrNull(chosenOrder)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Choisissez la carte à conserver. Les autres retournent sous la pioche, dans l’ordre que vous décidez.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            game.hand.forEachIndexed { index, card ->
                PlayingCard(card.value, card.frenchName, card.summary, selected = keep == index, enabled = legal.any { it.keepIndex == index }, onClick = { if (legal.any { it.keepIndex == index }) keep = index })
            }
        }
        if (keep in game.hand.indices) CourtPanel(Modifier.fillMaxWidth()) {
            Eyebrow("Vous gardez ${game.hand[keep].frenchName}")
            Text(game.hand[keep].explanation, style = MaterialTheme.typography.bodyMedium)
            if (choices.any { it.returnOrder.isNotEmpty() }) {
                Text("Ordre sous la pioche", style = MaterialTheme.typography.titleMedium)
                Text("La première carte indiquée sera piochée avant la seconde.", color = Muted, style = MaterialTheme.typography.bodySmall)
                choices.forEachIndexed { index, resolution ->
                    val label = resolution.returnOrder.mapIndexed { order, cardIndex -> "${order + 1}. ${game.hand[cardIndex].frenchName}" }.joinToString(" → ")
                    FilterChip(selected = selected == resolution, onClick = { chosenOrder = index }, label = { Text(label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                }
            }
            Button(onClick = { selected?.let(onPlay) }, enabled = selected != null && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Conserver ${game.hand[keep].frenchName}") }
        }
    }
}

@Composable
private fun RoundResultPanel(game: PlayerObservation, onNextRound: () -> Unit, onHome: () -> Unit, onNew: () -> Unit) {
    val result = game.result ?: return
    val final = game.phase == Phase.GAME_OVER
    val names = (if (final) game.gameWinners else result.winners).map { id -> game.players.first { it.id == id }.name }
    var appeared by remember(game.roundNumber, final) { mutableStateOf(false) }
    LaunchedEffect(game.roundNumber, final) { appeared = true }
    CourtPanel(Modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = appeared, enter = fadeIn(tween(550)) + scaleIn(initialScale = .7f, animationSpec = tween(550))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CourtEmblem(9, Modifier.size(82.dp)) }
        }
        Eyebrow(if (final) "Les faveurs de la princesse" else "La lettre est arrivée")
        Text(if (final) "Victoire de partie" else "Victoire de manche", style = MaterialTheme.typography.headlineMedium)
        Text(names.joinToString(" et "), color = Gold, style = MaterialTheme.typography.titleLarge)
        Text(result.reason, color = Muted, style = MaterialTheme.typography.bodyMedium)
        result.spyBonusPlayerId?.let { id ->
            Token("Espionne : +1 pour ${game.players.first { it.id == id }.name}", Teal)
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Gold.copy(alpha = .18f))
        game.players.sortedByDescending { it.score }.forEach { player ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(player.name, style = MaterialTheme.typography.titleMedium)
                    Text(result.revealedHands[player.id]?.let { "${it.frenchName} · valeur ${it.value}" } ?: "Éliminé", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                result.pointsAwarded[player.id]?.takeIf { it > 0 }?.let { points -> Text("+$points  ", color = Teal, style = MaterialTheme.typography.labelMedium) }
                Token("${player.score} / ${game.pointsToWin} ♥")
            }
        }
        Spacer(Modifier.height(4.dp))
        if (final) {
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Rejouer") }
            TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Retour à l’accueil") }
        } else Button(onClick = onNextRound, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Manche suivante") }
    }
}

@Composable
private fun RulesScreen(onBack: () -> Unit, onCards: () -> Unit) {
    val sections = listOf(
        "Le but du jeu" to "Faites parvenir votre lettre à la princesse. Chaque manche gagnée rapporte un pion Faveur. Un bonus d’Espionne peut s’y ajouter. La partie s’achève à la fin d’une manche dès qu’au moins un joueur atteint l’objectif ; tous ceux qui l’atteignent gagnent.",
        "Une édition, un seul paquet" to "Cette adaptation suit l’édition française Z-Man Games de 2019 : 21 cartes, 10 personnages, de 2 à 6 joueurs. L’objectif est de 6 pions à 2 joueurs, 5 à 3, 4 à 4 et 3 à 5 ou 6.",
        "La mise en place" to "Le paquet est mélangé. Une carte est écartée face cachée. À 2 joueurs, trois cartes supplémentaires sont écartées face visible. Chacun reçoit une carte. Pour la première manche, la personne qui a écrit une lettre le plus récemment commence ; l’application propose aussi un tirage au sort.",
        "À votre tour" to "Votre éventuelle protection s’arrête au début de votre tour. Piochez une carte, puis jouez l’une des deux et appliquez son effet. La carte jouée rejoint votre défausse, visible de tous. La Comtesse impose son jeu lorsqu’elle accompagne un Roi ou un Prince.",
        "Cibles et protection" to "La Servante empêche les autres joueurs de vous cibler jusqu’à votre prochain tour. Les joueurs éliminés ne peuvent plus être choisis. S’il n’existe aucune cible valide, une carte ciblant un adversaire reste jouable sans effet. Le Prince peut vous cibler : si tous les autres sont protégés, vous devez vous choisir.",
        "Être éliminé" to "Défaussez votre main, face visible, sans résoudre son effet. Vous ne rejouez plus avant la manche suivante. Jouer ou défausser la Princesse vous élimine immédiatement. La remettre sous la pioche grâce au Chancelier n’est pas une défausse.",
        "Fin de manche et égalités" to "Si un seul joueur reste en jeu, il gagne immédiatement. Sinon, après le tour qui vide la pioche, les survivants révèlent leur carte. La valeur la plus haute gagne. En cas d’égalité, chacun des ex æquo reçoit un pion : on n’additionne pas les défausses dans cette édition.",
        "Le bonus de l’Espionne" to "À la fin de la manche, si un seul survivant a joué ou défaussé une Espionne, il gagne un pion supplémentaire, même sans avoir gagné la manche. Il ne reçoit qu’un bonus, même avec les deux Espionnes. Une Espionne restée en main ou remise sous la pioche ne compte pas.",
        "La manche suivante" to "Toutes les cartes sont rassemblées, puis mélangées. Le gagnant de la manche précédente commence ; s’ils sont plusieurs, l’application choisit au hasard parmi eux. Les pions Faveur sont conservés.",
        "Le jeu numérique" to "Un humain affronte des IA, qui n’utilisent que leurs cartes, leurs informations autorisées et les informations publiques. La partie est sauvegardée automatiquement. Les choix d’action sont fournis par le moteur et respectent les cibles, protections et obligations en vigueur.",
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenHeading("Les règles", "Édition française Z-Man Games · 2019", onBack)
        CourtPanel(Modifier.fillMaxWidth()) {
            Eyebrow("La cour en trois gestes")
            Text("Piochez. Choisissez. Déjouez.", style = MaterialTheme.typography.headlineMedium)
            Text("Une seule carte dans la main, mais toute la table à observer.", color = Muted)
        }
        sections.forEachIndexed { index, (title, body) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow("${(index + 1).toString().padStart(2, '0')} / $title")
                Text(body, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(Modifier.padding(top = 10.dp), color = Gold.copy(alpha = .12f))
            }
        }
        Button(onClick = onCards, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Découvrir les dix personnages") }
        Text("Résumé reformulé pour cette adaptation indépendante. Les noms du jeu et des personnages appartiennent à leurs titulaires respectifs. Les visuels présentés sont des créations originales.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EncyclopediaScreen(onBack: () -> Unit) {
    var expandedName by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeading("Les personnages", "21 cartes · 10 rôles à connaître", onBack)
        Text("Les valeurs départagent les survivants. Les effets peuvent changer le destin d’une manche.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        Card.entries.forEach { card ->
            val expanded = expandedName == card.name
            CourtPanel(Modifier.fillMaxWidth().clickable { expandedName = if (expanded) null else card.name }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CourtEmblem(card.value, Modifier.size(54.dp))
                    Column(Modifier.weight(1f)) {
                        Text(card.frenchName, style = MaterialTheme.typography.titleLarge)
                        Text("Valeur ${card.value} · ${card.copies} exemplaire${if (card.copies > 1) "s" else ""}", color = Gold, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (expanded) "−" else "+", color = Gold, fontSize = 25.sp, modifier = Modifier.padding(7.dp))
                }
                Text(card.summary, style = MaterialTheme.typography.bodyMedium)
                AnimatedVisibility(expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalDivider(color = Gold.copy(alpha = .15f))
                        Text(card.explanation, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
