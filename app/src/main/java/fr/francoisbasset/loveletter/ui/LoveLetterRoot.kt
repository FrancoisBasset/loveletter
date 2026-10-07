package fr.francoisbasset.loveletter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import fr.francoisbasset.loveletter.GameViewModel
import fr.francoisbasset.loveletter.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoveLetterRoot(model: GameViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, model) {
        model.setForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> model.setForeground(true)
                Lifecycle.Event.ON_PAUSE -> model.setForeground(false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); model.setForeground(false) }
    }
    BackHandler(enabled = state.destination != Destination.HOME || state.selectedCard != null) { model.back() }
    Scaffold(containerColor = Ivory, topBar = {
        if (state.destination == Destination.TABLE) {
            Surface(color = Ivory) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = model::back, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.ArrowBack, "Retour") }
                    Column(Modifier.weight(1f)) {
                        Text("Love Letter", style = MaterialTheme.typography.titleLarge, color = Burgundy)
                        Text("Manche ${state.table?.round ?: 1} · ${state.table?.goal ?: 0} faveurs pour gagner", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                    IconButton(onClick = { model.navigate(Destination.RULES) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.HelpOutline, "Lire les règles", tint = Wine) }
                }
            }
        } else if (state.destination != Destination.HOME) TopAppBar(
            title = { Text(when (state.destination) {
                Destination.SETUP -> "Nouvelle partie"
                Destination.TABLE -> "Love Letter"
                Destination.RULES -> "Règles"
                Destination.CARDS -> "Encyclopédie"
                else -> "Love Letter"
            }, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = model::back) { Icon(Icons.Outlined.ArrowBack, "Retour") } },
            actions = {
                if (state.destination == Destination.TABLE) IconButton(onClick = { model.navigate(Destination.RULES) }) { Icon(Icons.Outlined.HelpOutline, "Lire les règles") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Ivory, titleContentColor = Burgundy)
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Ouverture de la cour…")
            } else AnimatedContent(state.destination, label = "screen") { destination ->
                when (destination) {
                    Destination.HOME -> HomeScreen(state.hasSavedGame, { model.navigate(Destination.SETUP) }, model::resumeGame,
                        { model.navigate(Destination.RULES) }, { model.navigate(Destination.CARDS) })
                    Destination.SETUP -> SetupScreen(state, model::updateName, model::updateCount, model::updateDifficulty, model::updateFirstPlayer, model::updatePace, model::startGame)
                    Destination.TABLE -> TableScreen(state, model::selectCard, model::selectTarget, model::performAction, model::clearSelection,
                        model::nextRound, { model.navigate(Destination.SETUP) }, model::acknowledgeNotice,
                        model::continuePlayback, model::togglePause, model::updatePace)
                    Destination.RULES -> RulesScreen()
                    Destination.CARDS -> CardsScreen()
                }
            }
        }
    }
    state.error?.let { error -> AlertDialog(onDismissRequest = model::clearError, title = { Text("Décision indisponible") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = model::clearError) { Text("Revenir au jeu") } }) }
    state.storageWarning?.let { warning -> AlertDialog(onDismissRequest = model::clearStorageWarning, title = { Text("Sauvegarde") }, text = { Text(warning) },
        confirmButton = { TextButton(onClick = model::clearStorageWarning) { Text("Compris") } }) }
}
