package fr.francoisbasset.loveletter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.francoisbasset.loveletter.engine.*
import fr.francoisbasset.loveletter.engine.ai.BotAi
import fr.francoisbasset.loveletter.persistence.GameRepository
import fr.francoisbasset.loveletter.persistence.SavedGame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AppScreen { HOME, SETUP, GAME, RULES, CARDS }

data class AppUiState(
    val screen: AppScreen = AppScreen.HOME,
    val game: PlayerObservation? = null,
    val config: GameConfig? = null,
    val busy: Boolean = true,
    val secretNotice: String? = null,
    val hasSavedGame: Boolean = false,
    val error: String? = null,
)

/** Serializes transitions on the main dispatcher; only the engine holds complete game data. */
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository(application.filesDir)
    private val saveMutex = Mutex()
    private var gameState: GameState? = null
    private var foreground = true
    private var loaded = false
    private var operation: Job? = null
    private val mutableState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = mutableState.asStateFlow()

    init {
        operation = viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.load() } }
                .onSuccess { saved ->
                    gameState = saved?.state
                    mutableState.update { it.copy(secretNotice = saved?.secretNotice) }
                    publish()
                }
                .onFailure { failure ->
                    mutableState.update { it.copy(error = "La sauvegarde n’a pas pu être chargée. Vous pouvez démarrer une nouvelle partie. ${failure.message.orEmpty()}") }
                }
            loaded = true
            mutableState.update { it.copy(busy = false) }
        }
    }

    fun navigate(screen: AppScreen) {
        if (!loaded) return
        // Menu/help navigation pauses bots, while an already resolved action stays committed.
        operation?.cancel()
        mutableState.update { it.copy(screen = screen, busy = false) }
        if (screen == AppScreen.GAME) runBots()
    }

    fun showHome() = navigate(AppScreen.HOME)
    fun resumeGame() { if (gameState != null) navigate(AppScreen.GAME) }

    fun setForeground(value: Boolean) {
        foreground = value
        if (!loaded) return
        if (!value) {
            operation?.cancel()
            mutableState.update { it.copy(busy = false) }
        } else if (mutableState.value.screen == AppScreen.GAME) runBots()
    }

    fun startGame(config: GameConfig) {
        if (!loaded || mutableState.value.busy) return
        operation?.cancel()
        operation = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, screen = AppScreen.GAME, secretNotice = null, error = null) }
            try {
                commit(GameEngine.newGame(config), emptyList())
                processBots()
            } catch (failure: Exception) { handleFailure(failure) }
            finally { if (currentCoroutineContext().isActive) mutableState.update { it.copy(busy = false) } }
        }
    }

    fun play(action: GameAction) {
        val current = gameState ?: return
        if (mutableState.value.busy || mutableState.value.secretNotice != null || current.currentPlayer != 0) return
        if (action !in GameEngine.legalActions(current)) return
        operation = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            try {
                commit(GameEngine.apply(current, action), current.history)
                processBots()
            } catch (failure: Exception) { handleFailure(failure) }
            finally { if (currentCoroutineContext().isActive) mutableState.update { it.copy(busy = false) } }
        }
    }

    fun continueRound() {
        val current = gameState ?: return
        if (mutableState.value.busy || current.phase != Phase.ROUND_OVER) return
        operation = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, secretNotice = null, error = null) }
            try {
                commit(GameEngine.nextRound(current), current.history)
                processBots()
            } catch (failure: Exception) { handleFailure(failure) }
            finally { if (currentCoroutineContext().isActive) mutableState.update { it.copy(busy = false) } }
        }
    }

    fun acknowledgeSecret() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(secretNotice = null, busy = true) }
        operation = viewModelScope.launch {
            try { persist(); processBots() }
            catch (failure: Exception) { handleFailure(failure) }
            finally { if (currentCoroutineContext().isActive) mutableState.update { it.copy(busy = false) } }
        }
    }

    private fun runBots() {
        operation?.cancel()
        operation = viewModelScope.launch {
            try { processBots() }
            catch (failure: Exception) { handleFailure(failure) }
            finally { if (currentCoroutineContext().isActive) mutableState.update { it.copy(busy = false) } }
        }
    }

    private suspend fun processBots() {
        currentCoroutineContext().ensureActive()
        while (foreground && mutableState.value.screen == AppScreen.GAME && mutableState.value.secretNotice == null) {
            currentCoroutineContext().ensureActive()
            val current = gameState ?: break
            if (current.phase !in listOf(Phase.TURN, Phase.CHANCELLOR) || current.currentPlayer == 0) break
            mutableState.update { it.copy(busy = true) }
            delay(850)
            val observation = GameEngine.observation(current, current.currentPlayer)
            // This independent public seed cannot reconstruct the shuffled hidden deck.
            val botSeed = observation.roundNumber * 1009L + observation.history.size * 31L + observation.playerId
            val action = withContext(Dispatchers.Default) { BotAi.chooseAction(observation, seed = botSeed) }
            currentCoroutineContext().ensureActive()
            commit(GameEngine.apply(current, action), current.history)
        }
        mutableState.update { it.copy(busy = false) }
    }

    private suspend fun commit(next: GameState, previousEvents: List<GameEvent>) {
        gameState = next
        val messages = next.history.drop(previousEvents.size).filter {
            it.audience?.contains(0) == true && it.type in setOf(EventType.REVEAL, EventType.COMPARE, EventType.EXCHANGE)
        }.map { it.message }.distinct()
        mutableState.update { it.copy(secretNotice = messages.takeIf { list -> list.isNotEmpty() }?.joinToString("\n\n")) }
        publish()
        persist()
    }

    private fun publish() {
        val current = gameState
        mutableState.update { it.copy(
            game = current?.let { state -> GameEngine.observation(state, 0) },
            config = current?.config,
            hasSavedGame = current != null,
        ) }
    }

    private suspend fun persist() {
        val current = gameState ?: return
        val saved = SavedGame(current, mutableState.value.secretNotice)
        // Finishes the atomic write even if the user backgrounds the app during this transition.
        withContext(NonCancellable) {
            saveMutex.withLock {
                withContext(Dispatchers.IO) {
                    runCatching { repository.save(saved) }.onFailure { failure ->
                        mutableState.update { it.copy(error = "La sauvegarde a échoué : ${failure.message.orEmpty()}") }
                    }
                }
            }
        }
    }

    private fun handleFailure(failure: Exception) {
        if (failure is CancellationException) throw failure
        mutableState.update { it.copy(error = "Impossible de terminer cette action : ${failure.message.orEmpty()}") }
    }
}
