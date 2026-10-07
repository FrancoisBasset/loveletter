package fr.francoisbasset.loveletter

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.francoisbasset.loveletter.core.*
import fr.francoisbasset.loveletter.persistence.*
import fr.francoisbasset.loveletter.ui.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameViewModel(
    private val store: SessionStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val aiTurnDelay: Long? = null
) : ViewModel() {
    private val mutable = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutable.asStateFlow()
    private var game: GameState? = null
    private var aiJob: Job? = null
    private var driverGeneration = 0L
    private var playback: UiPlayback? = null
    private var lastPlayback: UiPlayback? = null
    private var manuallyPaused = false
    private var foreground = true
    private var acknowledgedNote = 0L
    private var aiDecisionCount = 0L
    private var aiSeed = 0L
    private var actionRevision = 0L
    private var actions = emptyMap<String, GameAction>()
    private var pendingNoteId: Long? = null
    private var referenceReturn = Destination.HOME
    private val saves = Channel<SavedSession>(Channel.UNLIMITED)

    init {
        viewModelScope.launch(ioDispatcher) {
            for (session in saves) {
                try { store.save(session) }
                catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        mutable.value = mutable.value.copy(storageWarning = "La sauvegarde a échoué. La partie continue, mais sa reprise n'est pas garantie.")
                    }
                }
            }
        }
        viewModelScope.launch {
            val loaded = withContext(ioDispatcher) { runCatching { store.load() } }
            loaded.getOrNull()?.let { session ->
                game = session.game
                acknowledgedNote = session.acknowledgedPrivateNote
                aiDecisionCount = session.aiDecisionCount
                aiSeed = session.aiSeed
                playback = session.playback
                lastPlayback = session.lastPlayback ?: session.playback
                manuallyPaused = session.paused
                mutable.value = mutable.value.copy(playerCount = session.playerCount,
                    difficulty = AiLevel.valueOf(session.difficulty), humanName = session.humanName,
                    firstPlayer = session.firstPlayer, pace = session.pace)
            }
            mutable.value = mutable.value.copy(loading = false,
                storageWarning = if (loaded.isFailure) "La sauvegarde est illisible ou incompatible. Vous pouvez commencer une nouvelle partie." else null)
            publish()
        }
    }

    fun navigate(destination: Destination) {
        if (mutable.value.loading) return
        if (destination == Destination.RULES || destination == Destination.CARDS)
            referenceReturn = mutable.value.destination
        stopDriver()
        mutable.value = mutable.value.copy(destination = destination, selectedCard = null, selectedTarget = null)
        publish()
        kickAi()
    }
    fun back() {
        val destination = mutable.value.destination
        if (mutable.value.selectedCard != null && destination == Destination.TABLE) clearSelection()
        else if (destination == Destination.RULES || destination == Destination.CARDS) navigate(referenceReturn)
        else navigate(Destination.HOME)
    }
    fun updateName(name: String) { mutable.value = mutable.value.copy(humanName = name); persist() }
    fun updateCount(count: Int) { if (count in 2..6) { mutable.value = mutable.value.copy(playerCount = count, firstPlayer = mutable.value.firstPlayer?.takeIf { it < count }); persist() } }
    fun updateFirstPlayer(player: Int?) {
        if (player == null || player in 0 until mutable.value.playerCount) { mutable.value = mutable.value.copy(firstPlayer = player); persist() }
    }
    fun updateDifficulty(level: AiLevel) { mutable.value = mutable.value.copy(difficulty = level); persist() }
    fun updatePace(pace: GamePace) {
        stopDriver()
        mutable.value = mutable.value.copy(pace = pace)
        publish(); persist(); kickAi()
    }
    /** Lifecycle visibility is ephemeral; a restored session still retains its chosen manual pause. */
    fun setForeground(isForeground: Boolean) {
        if (foreground == isForeground) return
        stopDriver()
        foreground = isForeground
        publish()
        if (foreground) kickAi()
    }
    fun togglePause() {
        if (game == null || mutable.value.destination != Destination.TABLE) return
        stopDriver()
        manuallyPaused = !manuallyPaused
        publish(); persist(); kickAi()
    }
    fun continuePlayback() {
        if (!foreground || manuallyPaused || playback == null || mutable.value.destination != Destination.TABLE || pendingNoteId != null) return
        stopDriver()
        playback = null
        publish(); persist(); kickAi()
    }
    fun clearError() { mutable.value = mutable.value.copy(error = null) }
    fun clearStorageWarning() { mutable.value = mutable.value.copy(storageWarning = null) }
    fun startGame() {
        stopDriver()
        val ui = mutable.value
        game = GameEngine.newGame(GameConfig(ui.playerCount, Difficulty.valueOf(ui.difficulty.name), ui.humanName.trim().ifBlank { "Vous" }, firstPlayer = ui.firstPlayer), System.nanoTime())
        acknowledgedNote = 0; aiDecisionCount = 0
        aiSeed = java.security.SecureRandom().nextLong()
        manuallyPaused = false
        val fresh = requireNotNull(game)
        playback = UiPlayback(
            id = fresh.nextEventId, actorId = fresh.currentPlayer, actorName = fresh.players[fresh.currentPlayer].name,
            messages = fresh.journal.map { it.message }, phase = fresh.phase,
            title = "Manche 1 · Distribution", summary = "Chaque joueur reçoit une carte. ${fresh.players[fresh.currentPlayer].name} commence."
        )
        lastPlayback = playback
        mutable.value = ui.copy(destination = Destination.TABLE, selectedCard = null, selectedTarget = null, error = null)
        publish(); persist(); kickAi()
    }
    fun resumeGame() { if (game != null) navigate(Destination.TABLE) }
    fun selectCard(value: Int) {
        val table = mutable.value.table ?: return
        if (!table.humanTurn || table.thinking || table.chancellor != null || table.notice != null || table.playback != null || table.paused) return
        if (table.moves.none { it.cardValue == value }) return
        mutable.value = mutable.value.copy(selectedCard = value, selectedTarget = null)
    }
    fun selectTarget(target: Int) {
        val ui = mutable.value
        if (ui.table?.moves?.any { it.cardValue == ui.selectedCard && it.targetId == target } == true)
            mutable.value = ui.copy(selectedTarget = target)
    }
    fun clearSelection() { mutable.value = mutable.value.copy(selectedCard = null, selectedTarget = null) }
    fun performAction(id: String) {
        val current = game ?: return
        val action = actions[id] ?: return
        if (!foreground || current.currentPlayer != 0 || mutable.value.destination != Destination.TABLE || pendingNoteId != null || playback != null || manuallyPaused) return
        transition(action)
        kickAi()
    }
    fun nextRound() {
        val current = game ?: return
        if (!foreground || current.phase != GamePhase.ROUND_OVER || mutable.value.destination != Destination.TABLE || pendingNoteId != null || playback != null || manuallyPaused) return
        transition(GameAction.NextRound)
        kickAi()
    }
    fun acknowledgeNotice() {
        stopDriver()
        pendingNoteId?.let { acknowledgedNote = it }
        pendingNoteId = null
        publish(); persist(); kickAi()
    }

    private fun transition(action: GameAction, aiDecision: Boolean = false): Boolean {
        val current = game ?: return false
        try {
            val next = GameEngine.apply(current, action)
            game = next
            if (aiDecision) aiDecisionCount++
            playback = describeAction(current, next, action)
            lastPlayback = playback
            mutable.value = mutable.value.copy(selectedCard = null, selectedTarget = null)
            actionRevision++
            publish(); persist()
            return true
        } catch (_: IllegalArgumentException) {
            mutable.value = mutable.value.copy(error = "Cette décision n'est plus disponible. Choisissez une action proposée.")
            publish()
            return false
        }
    }

    /** Cancelling a presentation never rolls back or repeats its already committed game action. */
    private fun stopDriver() {
        driverGeneration++
        aiJob?.cancel()
        aiJob = null
    }

    private fun driverAllowed(): Boolean = foreground && mutable.value.destination == Destination.TABLE && pendingNoteId == null && !manuallyPaused

    /** One cancellable driver owns public reading time and AI thinking time. */
    private fun kickAi() {
        if (aiJob?.isActive == true) return
        val current = game ?: return
        if (!driverAllowed()) return
        if (playback != null && mutable.value.pace == GamePace.GUIDED) return
        if (playback == null && (current.currentPlayer == 0 || current.phase !in listOf(GamePhase.PLAYING, GamePhase.CHANCELLOR))) return
        val generation = ++driverGeneration
        aiJob = viewModelScope.launch {
            try {
                while (true) {
                    if (generation != driverGeneration || !driverAllowed()) break
                    val reading = playback
                    if (reading != null) {
                        val duration = mutable.value.pace.readingDelayMs ?: break
                        publish(thinking = false)
                        delay(duration)
                        if (generation != driverGeneration || !driverAllowed() || playback?.id != reading.id) break
                        playback = null
                        publish(); persist()
                        continue
                    }
                    val snapshot = game ?: break
                    if (snapshot.currentPlayer == 0 ||
                        snapshot.phase !in listOf(GamePhase.PLAYING, GamePhase.CHANCELLOR)) break
                    publish(thinking = true)
                    delay(aiTurnDelay ?: mutable.value.pace.thinkingDelayMs)
                    if (generation != driverGeneration || !driverAllowed() || game != snapshot || playback != null) break
                    val observation = GameEngine.observe(snapshot, snapshot.currentPlayer)
                    val seed = aiSeed xor (aiDecisionCount * -7046029254386353131L)
                    val action = withContext(computationDispatcher) { AiPlayer.choose(observation, observation.config.difficulty, seed) }
                    if (generation != driverGeneration || !driverAllowed() || game != snapshot || playback != null) break
                    if (!transition(action, aiDecision = true)) break
                }
            } finally {
                if (generation == driverGeneration) {
                    aiJob = null
                    publish(thinking = false)
                }
            }
        }
    }

    /** No private hand, private note, chosen Chancellor card or King exchange enters this record. */
    private fun describeAction(before: GameState, after: GameState, action: GameAction): UiPlayback {
        val actorId = if (action == GameAction.NextRound) after.currentPlayer else before.currentPlayer
        val actor = before.players[actorId]
        val play = action as? GameAction.Play
        val guess = play?.guess
        val target = play?.target?.let { before.players[it] }
        val messages = after.journal.filter { it.id >= before.nextEventId }.map { it.message }
        val title = when (action) {
            is GameAction.Play -> "${actor.name} joue ${action.card.frenchName}"
            is GameAction.ChancellorChoice -> "${actor.name} termine son Chancelier"
            GameAction.NextRound -> "Manche ${after.roundNumber} · Distribution"
        }
        val summary = when {
            action == GameAction.NextRound -> "${actor.name} commence la nouvelle manche."
            action is GameAction.ChancellorChoice -> "Une carte est conservée secrètement ; les autres retournent sous la pioche."
            guess != null -> "${target?.name} : annonce ${guess.frenchName}."
            target != null -> "${actor.name} choisit ${if (target.id == actor.id) "sa propre main" else target.name}."
            else -> play?.card?.effect.orEmpty()
        }
        return UiPlayback(
            id = after.nextEventId, actorId = actorId, actorName = actor.name,
            cardValue = play?.card?.value ?: if (action is GameAction.ChancellorChoice) Card.CHANCELIER.value else null,
            targetId = target?.id, targetName = target?.name, guessValue = guess?.value,
            messages = messages, eliminatedIds = after.players.filter { it.isEliminated && !before.players[it.id].isEliminated }.map { it.id },
            phase = after.phase, title = title, summary = summary
        )
    }

    private fun publish(thinking: Boolean = false) {
        val current = game
        if (current == null) {
            mutable.value = mutable.value.copy(table = null, hasSavedGame = false)
            return
        }
        val view = GameEngine.observe(current, 0)
        val over = view.phase == GamePhase.ROUND_OVER || view.phase == GamePhase.GAME_OVER
        val unreadNotes = view.privateNotes.filter { it.id > acknowledgedNote }
        val latest = unreadNotes.lastOrNull()
        pendingNoteId = latest?.id
        val blocked = playback != null || manuallyPaused || latest != null
        val legal = if (blocked) emptyList() else view.legalActions
        actions = legal.mapIndexed { index, action -> "${actionRevision}:$index" to action }.toMap()
        val moves = actions.mapNotNull { (id, action) ->
            (action as? GameAction.Play)?.let { UiMove(id, it.card.value, it.target, it.guess?.value) }
        }
        val chancellorActions = actions.filterValues { it is GameAction.ChancellorChoice }
        val chancellor = if (!blocked && view.phase == GamePhase.CHANCELLOR && view.currentPlayer == 0) {
            UiChancellor(view.ownHand.map { it.value }, chancellorActions.map { (id, action) ->
                action as GameAction.ChancellorChoice
                val keepIndex = view.ownHand.indexOf(action.keep)
                val indexed = view.ownHand.mapIndexed { index, card -> index to card }.toMutableList()
                indexed.removeAt(keepIndex)
                val bottom = action.bottom.map { card ->
                    val index = indexed.indexOfFirst { it.second == card }
                    indexed.removeAt(index).first
                }
                UiChancellorChoice(id, keepIndex, bottom)
            })
        } else null
        val result = view.result
        val playerNames = view.players.associate { it.id to it.name }
        val winNames = (if (view.phase == GamePhase.GAME_OVER) view.winners else result?.winners.orEmpty())
            .mapNotNull { playerNames[it] }.joinToString(" et ")
        val resultDescription = result?.let {
            buildString {
                append(if (it.reason == RoundEndReason.LAST_SURVIVOR) "Dernier joueur en lice." else "La plus forte carte gagne ; les ex æquo partagent la victoire.")
                append(" ")
                append(it.pointsAwarded.entries.filter { entry -> entry.value > 0 }.joinToString(" · ") { entry -> "${playerNames[entry.key]} +${entry.value}" })
                it.spyBonusPlayer?.let { id -> append(". Bonus d'Espionne : ${playerNames[id]}.") }
            }
        }
        mutable.value = mutable.value.copy(hasSavedGame = true, table = UiTable(
            round = view.roundNumber, deckCount = view.deckSize, goal = view.config.victoryThreshold,
            players = view.players.map { player -> UiPlayer(player.id, player.name, player.score,
                !player.isEliminated, player.isProtected, player.discard.map { it.value },
                player.visibleHand?.firstOrNull()?.value, player.id == 0, player.id == view.currentPlayer && !over,
                handSize = player.handSize) },
            hand = view.ownHand.map { it.value }, moves = moves,
            history = view.journal.map { "M${it.roundNumber} · ${it.message}" }.reversed(),
            turnLabel = when {
                manuallyPaused -> "Partie en pause"
                playback != null -> "Action de ${playback?.actorName}"
                over -> "Manche terminée"
                view.currentPlayer == 0 -> if (view.phase == GamePhase.CHANCELLOR) "Choisissez votre nouvelle main" else "À vous de jouer"
                else -> "${playerNames[view.currentPlayer]} joue"
            }, humanTurn = view.currentPlayer == 0 && !over && !blocked && latest == null, thinking = thinking && !blocked && latest == null,
            roundFinished = over && !blocked && latest == null, matchFinished = view.phase == GamePhase.GAME_OVER && !blocked && latest == null,
            resultTitle = if (over) "$winNames ${if (view.phase == GamePhase.GAME_OVER) "remporte la partie" else "remporte la manche"}" else null,
            resultDescription = resultDescription, chancellor = chancellor,
            notice = latest?.let { UiPrivateNotice("Information privée", unreadNotes.joinToString("\n\n") { note -> note.text }) },
            exposedCards = view.faceUpRemoved.map { it.value }, difficulty = AiLevel.valueOf(view.config.difficulty.name),
            knownCards = view.knownCards.map { "${playerNames[it.targetId]} : ${it.card.frenchName} (${it.card.value})" },
            playback = playback, paused = manuallyPaused, lastPlayback = lastPlayback
        ))
    }

    private fun persist() {
        val ui = mutable.value
        if (ui.loading) return
        saves.trySend(SavedSession(game = game, acknowledgedPrivateNote = acknowledgedNote,
            aiDecisionCount = aiDecisionCount, aiSeed = aiSeed, playerCount = ui.playerCount,
            difficulty = ui.difficulty.name, humanName = ui.humanName, firstPlayer = ui.firstPlayer,
            pace = ui.pace, playback = playback, paused = manuallyPaused, lastPlayback = lastPlayback))
    }

    override fun onCleared() { saves.close(); super.onCleared() }

    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GameViewModel(SessionStore(application.filesDir)) as T
        }
    }
}
