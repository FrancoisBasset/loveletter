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
            assertNotNull(model.state.value.table?.playback)
            assertFalse(model.state.value.table?.humanTurn == true)
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

    private fun scenario(
        actor: Int = 0,
        hand: List<Card> = listOf(Card.SERVANTE, Card.COMTESSE),
        draw: List<Card> = listOf(Card.GARDE, Card.PRETRE, Card.GARDE, Card.ESPIONNE, Card.SERVANTE, Card.GARDE)
    ): GameState {
        val base = GameEngine.newGame(GameConfig(playerCount = 3, firstPlayer = actor), 54L)
        return base.copy(players = base.players.map {
            it.copy(hand = if (it.id == actor) hand else listOf(if (it.id == 0) Card.PRETRE else Card.BARON),
                discard = emptyList(), isProtected = false, isEliminated = false, score = 0)
        }, currentPlayer = actor, drawPile = draw, knownCards = emptyList(), exclusions = emptyList(),
            privateNotes = emptyList(), journal = emptyList(), result = null, phase = GamePhase.PLAYING)
    }

    private suspend fun TestScope.withModel(
        session: SavedSession,
        body: suspend TestScope.(GameViewModel, SessionStore) -> Unit
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = SessionStore(temporary.newFolder()).also { it.save(session) }
        val model = GameViewModel(store, dispatcher, dispatcher)
        val owner = ViewModelStore().apply { put("playback-test", model) }
        try {
            runCurrent()
            model.resumeGame()
            runCurrent()
            body(model, store)
        } finally {
            owner.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun humanMove(model: GameViewModel, card: Card): String = requireNotNull(model.state.value.table)
        .moves.first { it.cardValue == card.value }.id

    @Test fun guidedIsTheDefaultAndEveryCommittedActionWaitsForContinue() = runTest {
        withModel(SavedSession(game = scenario(), aiSeed = 15L)) { model, store ->
            assertEquals(GamePace.GUIDED, model.state.value.pace)
            model.performAction(humanMove(model, Card.SERVANTE))
            runCurrent()
            val humanPlayback = requireNotNull(model.state.value.table?.playback)
            assertEquals(0, humanPlayback.actorId)
            assertEquals(Card.SERVANTE.value, humanPlayback.cardValue)
            assertTrue(model.state.value.table?.moves?.isEmpty() == true)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(0L, store.load()?.aiDecisionCount)
            model.continuePlayback()
            model.continuePlayback() // A repeated tap never creates a second driver.
            runCurrent()
            advanceTimeBy(1799); runCurrent()
            assertEquals(0L, store.load()?.aiDecisionCount)
            advanceTimeBy(1); runCurrent()
            val aiPlayback = requireNotNull(model.state.value.table?.playback)
            assertEquals(1, aiPlayback.actorId)
            assertNotEquals(humanPlayback.id, aiPlayback.id)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(1L, store.load()?.aiDecisionCount)
            assertEquals(aiPlayback, model.state.value.table?.lastPlayback)
        }
    }

    @Test fun slowModeReadsForFiveSecondsThenThinksForOnePointEightSeconds() = runTest {
        withModel(SavedSession(game = scenario(), aiSeed = 15L, pace = GamePace.SLOW)) { model, store ->
            model.performAction(humanMove(model, Card.SERVANTE)); runCurrent()
            advanceTimeBy(4999); runCurrent()
            assertNotNull(model.state.value.table?.playback)
            assertEquals(0L, store.load()?.aiDecisionCount)
            advanceTimeBy(1); runCurrent()
            assertNull(model.state.value.table?.playback)
            assertNotNull(model.state.value.table?.lastPlayback)
            assertTrue(model.state.value.table?.thinking == true)
            advanceTimeBy(1799); runCurrent()
            assertEquals(0L, store.load()?.aiDecisionCount)
            advanceTimeBy(1); runCurrent()
            assertEquals(1L, store.load()?.aiDecisionCount)
            assertNotNull(model.state.value.table?.playback)
        }
    }

    @Test fun navigationCancelsReadingAndResumeCannotRepeatTheCommittedHumanMove() = runTest {
        withModel(SavedSession(game = scenario(), aiSeed = 15L, pace = GamePace.SLOW)) { model, store ->
            model.performAction(humanMove(model, Card.SERVANTE)); runCurrent()
            val committed = requireNotNull(store.load())
            advanceTimeBy(1000); runCurrent()
            model.navigate(Destination.RULES)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(committed.game, store.load()?.game)
            assertEquals(committed.playback, model.state.value.table?.playback)
            model.back(); runCurrent()
            advanceTimeBy(4999); runCurrent()
            assertEquals(0L, store.load()?.aiDecisionCount)
            advanceTimeBy(1801); runCurrent()
            assertEquals(1L, store.load()?.aiDecisionCount)
            assertEquals(1, store.load()?.game?.players?.get(0)?.discard?.count { it == Card.SERVANTE })
        }
    }

    @Test fun manualPauseCancelsThinkingAndIsPersisted() = runTest {
        withModel(SavedSession(game = scenario(actor = 1), aiSeed = 15L, pace = GamePace.NORMAL)) { model, store ->
            assertTrue(model.state.value.table?.thinking == true)
            advanceTimeBy(500); runCurrent()
            model.togglePause(); runCurrent()
            assertTrue(model.state.value.table?.paused == true)
            assertTrue(store.load()?.paused == true)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(0L, store.load()?.aiDecisionCount)
            model.togglePause(); runCurrent()
            advanceTimeBy(999); runCurrent()
            assertEquals(0L, store.load()?.aiDecisionCount)
            advanceTimeBy(1); runCurrent()
            assertEquals(1L, store.load()?.aiDecisionCount)
        }
    }

    @Test fun switchingFromAutomaticToGuidedFreezesTheCurrentPlayback() = runTest {
        withModel(SavedSession(game = scenario(), aiSeed = 15L, pace = GamePace.NORMAL)) { model, store ->
            model.performAction(humanMove(model, Card.SERVANTE)); runCurrent()
            val event = model.state.value.table?.playback
            advanceTimeBy(1000); runCurrent()
            model.updatePace(GamePace.GUIDED)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(event, model.state.value.table?.playback)
            assertEquals(GamePace.GUIDED, store.load()?.pace)
            assertEquals(0L, store.load()?.aiDecisionCount)
        }
    }

    @Test fun restoredPlaybackIsReadWithoutReapplyingItsAction() = runTest {
        var saved: SavedSession? = null
        withModel(SavedSession(game = scenario(), aiSeed = 15L)) { model, store ->
            model.performAction(humanMove(model, Card.SERVANTE)); runCurrent()
            saved = requireNotNull(store.load())
        }
        val committed = requireNotNull(saved)
        withModel(committed) { model, store ->
            assertEquals(committed.playback, model.state.value.table?.playback)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(committed.game, store.load()?.game)
            model.continuePlayback(); runCurrent()
            assertEquals(committed.game, store.load()?.game)
            assertNull(model.state.value.table?.playback)
            assertEquals(committed.playback, model.state.value.table?.lastPlayback)
            advanceTimeBy(1800); runCurrent()
            assertEquals(1L, store.load()?.aiDecisionCount)
        }
    }

    @Test fun privateNoticeHasPriorityOverAutomaticReadingAndContinue() = runTest {
        val privateState = scenario(hand = listOf(Card.PRETRE, Card.COMTESSE))
        withModel(SavedSession(game = privateState, aiSeed = 15L, pace = GamePace.SLOW)) { model, store ->
            val action = requireNotNull(model.state.value.table).moves.first { it.cardValue == Card.PRETRE.value && it.targetId == 1 }
            model.performAction(action.id); runCurrent()
            assertNotNull(model.state.value.table?.notice)
            val event = requireNotNull(model.state.value.table?.playback)
            assertFalse(event.messages.any { it.contains("possède") })
            model.continuePlayback()
            advanceTimeBy(60_000); runCurrent()
            assertEquals(event, model.state.value.table?.playback)
            assertEquals(0L, store.load()?.aiDecisionCount)
            model.acknowledgeNotice(); runCurrent()
            assertNull(model.state.value.table?.notice)
            advanceTimeBy(4999); runCurrent()
            assertEquals(event, model.state.value.table?.playback)
            advanceTimeBy(1); runCurrent()
            assertNull(model.state.value.table?.playback)
        }
    }

    @Test fun chancellorWaitsBeforeShowingChoicesAndPlaybackNeverDisclosesTheKeptCard() = runTest {
        val initial = scenario(hand = listOf(Card.CHANCELIER, Card.PRETRE),
            draw = listOf(Card.PRINCESSE, Card.COMTESSE, Card.GARDE, Card.SERVANTE))
        withModel(SavedSession(game = initial, aiSeed = 15L)) { model, store ->
            model.performAction(humanMove(model, Card.CHANCELIER)); runCurrent()
            assertNotNull(model.state.value.table?.playback)
            assertNull(model.state.value.table?.chancellor)
            model.continuePlayback()
            val selection = requireNotNull(model.state.value.table?.chancellor)
            val keepPrincess = selection.actions.first { selection.cards[it.keepIndex] == Card.PRINCESSE.value }
            model.performAction(keepPrincess.id); runCurrent()
            val event = requireNotNull(model.state.value.table?.playback)
            assertEquals(Card.CHANCELIER.value, event.cardValue)
            val publicText = (event.messages + event.title + event.summary).joinToString(" ")
            assertFalse(publicText.contains("Princesse"))
            assertFalse(publicText.contains("Comtesse"))
            assertFalse(publicText.contains("Prêtre"))
            assertEquals(listOf(Card.PRINCESSE), store.load()?.game?.players?.get(0)?.hand)
        }
    }

    @Test fun roundResultAndNextRoundWaitForFinalActionPlayback() = runTest {
        val lastTurn = scenario(hand = listOf(Card.SERVANTE, Card.COMTESSE), draw = emptyList())
        withModel(SavedSession(game = lastTurn, aiSeed = 15L)) { model, store ->
            model.performAction(humanMove(model, Card.SERVANTE)); runCurrent()
            val finished = requireNotNull(store.load()?.game)
            assertEquals(GamePhase.ROUND_OVER, finished.phase)
            assertFalse(model.state.value.table?.roundFinished == true)
            model.nextRound(); runCurrent()
            assertEquals(finished, store.load()?.game)
            model.continuePlayback()
            assertTrue(model.state.value.table?.roundFinished == true)
            model.nextRound(); runCurrent()
            assertEquals(2, store.load()?.game?.roundNumber)
            assertNotNull(model.state.value.table?.playback)
            assertNull(model.state.value.table?.playback?.cardValue)
        }
    }

    @Test fun aiChancellorPausesAfterThePlayAndAfterThePrivateChoiceWithoutLeakingIt() = runTest {
        val initial = scenario(actor = 1, hand = listOf(Card.CHANCELIER, Card.CHANCELIER),
            draw = listOf(Card.PRINCESSE, Card.COMTESSE, Card.GARDE, Card.SERVANTE))
        withModel(SavedSession(game = initial, aiSeed = 15L)) { model, store ->
            advanceTimeBy(1800); runCurrent()
            val played = requireNotNull(model.state.value.table?.playback)
            assertEquals(GamePhase.CHANCELLOR, played.phase)
            assertEquals(1L, store.load()?.aiDecisionCount)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(1L, store.load()?.aiDecisionCount)
            model.continuePlayback(); runCurrent()
            advanceTimeBy(1800); runCurrent()
            assertEquals(2L, store.load()?.aiDecisionCount)
            val resolved = requireNotNull(model.state.value.table?.playback)
            assertEquals(1, resolved.actorId)
            assertEquals(Card.CHANCELIER.value, resolved.cardValue)
            assertNotEquals(played.id, resolved.id)
            val publicText = (resolved.messages + resolved.title + resolved.summary).joinToString(" ")
            assertFalse(publicText.contains("Princesse"))
            assertFalse(publicText.contains("Comtesse"))
            assertTrue(model.state.value.table?.knownCards?.isEmpty() == true)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(2L, store.load()?.aiDecisionCount)
        }
    }

    @Test fun finalBaronDuelRequiresPrivateAcknowledgmentThenPublicContinueBeforeTheResult() = runTest {
        val base = GameEngine.newGame(GameConfig(playerCount = 2), 92L)
        val initial = base.copy(players = base.players.map { player ->
            player.copy(hand = if (player.id == 0) listOf(Card.BARON, Card.PRETRE) else listOf(Card.COMTESSE),
                discard = emptyList(), isEliminated = false, isProtected = false, score = 0)
        }, drawPile = emptyList(), journal = emptyList(), privateNotes = emptyList(), knownCards = emptyList())
        withModel(SavedSession(game = initial, playerCount = 2)) { model, store ->
            model.performAction(humanMove(model, Card.BARON)); runCurrent()
            val finished = requireNotNull(store.load()?.game)
            assertEquals(GamePhase.ROUND_OVER, finished.phase)
            assertNotNull(model.state.value.table?.notice)
            val event = requireNotNull(model.state.value.table?.playback)
            assertEquals(listOf(0), event.eliminatedIds)
            assertEquals(listOf(2, 8), event.scene?.participants?.map { it.cardValue })
            assertEquals(event.scene, model.state.value.table?.notice?.scene)
            assertEquals(event.scene, store.load()?.playback?.scene)
            model.continuePlayback(); model.nextRound(); runCurrent()
            assertEquals(event, model.state.value.table?.playback)
            assertEquals(finished, store.load()?.game)
            model.acknowledgeNotice(); model.nextRound(); runCurrent()
            assertNull(model.state.value.table?.notice)
            assertFalse(model.state.value.table?.roundFinished == true)
            assertEquals(finished, store.load()?.game)
            model.continuePlayback()
            assertTrue(model.state.value.table?.roundFinished == true)
            model.nextRound(); runCurrent()
            assertEquals(2, store.load()?.game?.roundNumber)
        }
    }

    @Test fun backgroundSuspendsReadingAndThinkingWithoutReplayingTheHeldAction() = runTest {
        withModel(SavedSession(game = scenario(), aiSeed = 15L, pace = GamePace.NORMAL)) { model, store ->
            model.performAction(humanMove(model, Card.SERVANTE)); runCurrent()
            val committed = requireNotNull(store.load())
            advanceTimeBy(700); runCurrent()
            model.setForeground(false)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(committed.game, store.load()?.game)
            assertEquals(committed.playback, model.state.value.table?.playback)
            assertFalse(model.state.value.table?.thinking == true)
            model.setForeground(true); runCurrent()
            advanceTimeBy(2499); runCurrent()
            assertNotNull(model.state.value.table?.playback)
            advanceTimeBy(1); runCurrent()
            assertNull(model.state.value.table?.playback)
            assertTrue(model.state.value.table?.thinking == true)
            advanceTimeBy(500); runCurrent()
            model.setForeground(false)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(0L, store.load()?.aiDecisionCount)
            assertFalse(model.state.value.table?.thinking == true)
            model.setForeground(true); runCurrent()
            advanceTimeBy(999); runCurrent()
            assertEquals(0L, store.load()?.aiDecisionCount)
            advanceTimeBy(1); runCurrent()
            assertEquals(1L, store.load()?.aiDecisionCount)
            assertEquals(1, store.load()?.game?.players?.get(0)?.discard?.count { it == Card.SERVANTE })
        }
    }

    @Test fun returningToForegroundPreservesManualPause() = runTest {
        withModel(SavedSession(game = scenario(actor = 1), aiSeed = 15L, pace = GamePace.NORMAL, paused = true)) { model, store ->
            model.setForeground(false); model.setForeground(true)
            advanceTimeBy(60_000); runCurrent()
            assertTrue(model.state.value.table?.paused == true)
            assertEquals(0L, store.load()?.aiDecisionCount)
            model.togglePause(); runCurrent()
            advanceTimeBy(1000); runCurrent()
            assertEquals(1L, store.load()?.aiDecisionCount)
        }
    }

    @Test fun legacyPrivateNoticeDoesNotBorrowAnUnrelatedLastPlaybackScene() = runTest {
        val before = scenario(hand = listOf(Card.PRETRE, Card.COMTESSE))
        val pending = GameEngine.apply(before, GameAction.Play(Card.PRETRE, 1))
        val stale = UiPlayback(id = 1, actorId = 0, actorName = "Vous", phase = GamePhase.PLAYING,
            title = "Ancien duel", summary = "", scene = UiEffectScene(UiEffectKind.BARON,
                listOf(UiSceneParticipant(0, "Vous", 9), UiSceneParticipant(1, "Éloïse", 8)),
                privateToHuman = true))
        withModel(SavedSession(game = pending, lastPlayback = stale)) { model, _ ->
            assertNotNull(model.state.value.table?.notice)
            assertNull(model.state.value.table?.notice?.scene)
            assertEquals(stale, model.state.value.table?.lastPlayback)
        }
    }
}
