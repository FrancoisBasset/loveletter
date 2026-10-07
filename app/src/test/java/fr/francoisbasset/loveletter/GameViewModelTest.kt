package fr.francoisbasset.loveletter

import androidx.lifecycle.ViewModelStore
import fr.francoisbasset.loveletter.core.*
import fr.francoisbasset.loveletter.persistence.*
import fr.francoisbasset.loveletter.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun leavingTheTableCancelsAiAndRepeatedResumeCannotAddAnotherTurn() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = SessionStore(temporary.newFolder())
        // The AI starts; the entire case remains reproducible through the persisted seed.
        val firstTurn = (0L..500L).map { GameEngine.newGame(GameConfig(playerCount = 2, firstPlayer = 1), it) }
            .first { candidate ->
                val action = AiPlayer.choose(GameEngine.observe(candidate, 1), seed = 15L)
                GameEngine.apply(candidate, action).phase != GamePhase.CHANCELLOR
            }
        val saved = SavedSession(game = firstTurn,
            aiSeed = 15L, playerCount = 2, firstPlayer = 1)
        store.save(saved)
        val model = GameViewModel(store, dispatcher, dispatcher, aiTurnDelay = 1000)
        val owner = ViewModelStore().apply { put("test", model) }
        try {
            advanceUntilIdle()
            model.resumeGame()
            runCurrent()
            model.navigate(Destination.HOME)
            advanceTimeBy(3000); runCurrent()
            assertEquals(saved.game, store.load()?.game)
            model.resumeGame()
            model.resumeGame()
            advanceUntilIdle()
            val once = requireNotNull(store.load())
            val observation = GameEngine.observe(requireNotNull(saved.game), 1)
            val expectedMove = AiPlayer.choose(observation, observation.config.difficulty, saved.aiSeed)
            val expected = GameEngine.apply(firstTurn, expectedMove)
            assertEquals(expected, once.game)
            assertEquals(1L, once.aiDecisionCount)
            assertTrue(model.state.value.table?.humanTurn == true || model.state.value.table?.roundFinished == true)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun restoresPrivateDecisionAndDoesNotApplyAnOldActionIdToANewRound() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = SessionStore(temporary.newFolder())
        val initial = (0L..500L).map { GameEngine.newGame(GameConfig(playerCount = 3), it) }
            .first { GameAction.Play(Card.CHANCELIER) in GameEngine.legalActions(it) }
        val pending = GameEngine.apply(initial, GameAction.Play(Card.CHANCELIER))
        store.save(SavedSession(game = pending, aiSeed = 232L))
        val model = GameViewModel(store, dispatcher, dispatcher, aiTurnDelay = 1000)
        val owner = ViewModelStore().apply { put("test", model) }
        try {
            advanceUntilIdle()
            model.resumeGame()
            val selection = requireNotNull(model.state.value.table?.chancellor)
            assertEquals(pending.players[0].hand.map { it.value }, selection.cards)
            val oldAction = selection.actions.first().id
            model.performAction(oldAction)
            // Stop before an AI decision and attempt a repeated tap of the stale action.
            model.navigate(Destination.HOME)
            advanceUntilIdle()
            val expected = requireNotNull(store.load()).game
            model.performAction(oldAction)
            advanceUntilIdle()
            assertEquals(expected, store.load()?.game)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
}
