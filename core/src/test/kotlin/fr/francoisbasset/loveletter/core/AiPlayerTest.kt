package fr.francoisbasset.loveletter.core

import org.junit.Assert.*
import org.junit.Test

class AiPlayerTest {
    private fun scenario(): GameState {
        val state = GameEngine.newGame(GameConfig(playerCount = 3), 52L)
        return state.copy(players = listOf(
            state.players[0].copy(hand = listOf(Card.GARDE, Card.SERVANTE)),
            state.players[1].copy(hand = listOf(Card.PRINCESSE)),
            state.players[2].copy(hand = listOf(Card.PRETRE))
        ), knownCards = emptyList(), exclusions = emptyList(), journal = emptyList(), privateNotes = emptyList())
    }

    @Test fun everyDifficultyChoosesOnlyLegalActions() {
        val observation = GameEngine.observe(scenario(), 0)
        for (difficulty in Difficulty.entries) for (seed in 0L..20L) {
            assertTrue(observation.legalActions.contains(AiPlayer.choose(observation, difficulty, seed)))
        }
    }

    @Test fun fixedAiSeedProducesSameDecision() {
        val observation = GameEngine.observe(scenario(), 0)
        for (difficulty in Difficulty.entries) {
            assertEquals(AiPlayer.choose(observation, difficulty, 71L), AiPlayer.choose(observation, difficulty, 71L))
        }
    }

    @Test fun easyDifficultyExploresMoreThanOneCard() {
        val observation = GameEngine.observe(scenario(), 0)
        val cards = (0L..40L).map { (AiPlayer.choose(observation, Difficulty.EASY, it) as GameAction.Play).card }.toSet()
        assertEquals(setOf(Card.GARDE, Card.SERVANTE), cards)
    }

    @Test fun normalAndHardExploitLegallyLearnedPrincess() {
        val state = scenario().copy(knownCards = listOf(KnownCard(0, 1, Card.PRINCESSE)))
        val observation = GameEngine.observe(state, 0)
        for (difficulty in listOf(Difficulty.NORMAL, Difficulty.HARD)) {
            assertEquals(GameAction.Play(Card.GARDE, 1, Card.PRINCESSE), AiPlayer.choose(observation, difficulty, 2L))
        }
    }

    @Test fun normalAndHardUseOnlyPublicCopiesToInferRemainingCard() {
        val base = GameEngine.newGame(GameConfig(playerCount = 3), 12L)
        val visible = GameEngine.fullDeck().toMutableList().also {
            it.remove(Card.GARDE); it.remove(Card.SERVANTE); it.remove(Card.BARON)
        }
        val state = base.copy(
            players = listOf(
                base.players[0].copy(hand = listOf(Card.GARDE, Card.SERVANTE)),
                base.players[1].copy(hand = listOf(Card.BARON)),
                base.players[2].copy(hand = emptyList(), discard = visible, isEliminated = true)
            ), drawPile = emptyList(), reserveCard = null, faceUpRemoved = emptyList(),
            knownCards = emptyList(), exclusions = emptyList(), journal = emptyList(), privateNotes = emptyList()
        )
        val observation = GameEngine.observe(state, 0)
        assertTrue(observation.knownCards.isEmpty())
        for (difficulty in listOf(Difficulty.NORMAL, Difficulty.HARD)) {
            assertEquals(GameAction.Play(Card.GARDE, 1, Card.BARON), AiPlayer.choose(observation, difficulty, 2L))
        }
    }

    @Test fun aiDecisionCannotChangeWhenOnlyUnknownSecretsArePermuted() {
        val state = scenario()
        val changed = state.copy(
            players = state.players.map { player -> when (player.id) {
                1 -> player.copy(hand = listOf(Card.PRETRE))
                2 -> player.copy(hand = listOf(Card.PRINCESSE))
                else -> player
            } }, drawPile = state.drawPile.reversed(), reserveCard = Card.COMTESSE
        )
        val firstObservation = GameEngine.observe(state, 0)
        val secondObservation = GameEngine.observe(changed, 0)
        assertEquals(firstObservation, secondObservation)
        for (difficulty in Difficulty.entries) for (seed in 0L..10L) {
            assertEquals(AiPlayer.choose(firstObservation, difficulty, seed),
                AiPlayer.choose(secondObservation, difficulty, seed))
        }
    }

    @Test fun aiRespectsForcedCountessAndProtectedTargets() {
        val base = scenario()
        val forced = base.copy(players = base.players.map {
            if (it.id == 0) it.copy(hand = listOf(Card.COMTESSE, Card.PRINCE)) else it
        })
        for (difficulty in Difficulty.entries) {
            assertEquals(GameAction.Play(Card.COMTESSE), AiPlayer.choose(GameEngine.observe(forced, 0), difficulty, 1L))
        }
        val protected = base.copy(players = base.players.map { if (it.id == 1) it.copy(isProtected = true) else it })
        for (difficulty in Difficulty.entries) for (seed in 0L..10L) {
            val action = AiPlayer.choose(GameEngine.observe(protected, 0), difficulty, seed)
            assertFalse(action is GameAction.Play && action.target == 1)
        }
    }

    @Test fun normalAndHardAvoidVoluntaryPrincessElimination() {
        val base = scenario()
        val state = base.copy(players = base.players.map {
            if (it.id == 0) it.copy(hand = listOf(Card.PRINCESSE, Card.SERVANTE)) else it
        })
        for (difficulty in listOf(Difficulty.NORMAL, Difficulty.HARD)) {
            assertEquals(GameAction.Play(Card.SERVANTE), AiPlayer.choose(GameEngine.observe(state, 0), difficulty, 5L))
        }
    }

    @Test fun aiDoesNotOfferAnActionForNonActiveSeat() {
        val observation = GameEngine.observe(scenario(), 1)
        assertThrows(IllegalArgumentException::class.java) { AiPlayer.choose(observation, Difficulty.EASY, 1L) }
    }

    @Test fun chancellorChoicesAreLegalAtAllDifficulties() {
        val base = scenario()
        val state = base.copy(
            players = base.players.map { if (it.id == 0) it.copy(hand = listOf(Card.CHANCELIER, Card.PRETRE)) else it },
            drawPile = listOf(Card.COMTESSE, Card.PRINCE, Card.GARDE, Card.BARON)
        )
        val pending = GameEngine.apply(state, GameAction.Play(Card.CHANCELIER))
        val observation = GameEngine.observe(pending, 0)
        for (difficulty in Difficulty.entries) {
            val choice = AiPlayer.choose(observation, difficulty, 42L)
            assertTrue(choice is GameAction.ChancellorChoice)
            assertTrue(observation.legalActions.contains(choice))
        }
    }

    @Test fun completeMatchesPreserveCardsAndTerminateForEveryPlayerCountAndDifficulty() {
        val composition = Card.entries.associateWith { it.copies }
        for (count in 2..6) for (difficulty in Difficulty.entries) for (seed in 1L..4L) {
            var state = GameEngine.newGame(GameConfig(playerCount = count, difficulty = difficulty, firstPlayer = null), seed)
            var decisions = 0
            while (state.phase != GamePhase.GAME_OVER && decisions < 600) {
                val actualCards = state.drawPile + listOfNotNull(state.reserveCard) + state.faceUpRemoved +
                    state.players.flatMap { it.hand + it.discard }
                assertEquals("Card conservation, $count players/$difficulty/seed $seed/decision $decisions",
                    composition, actualCards.groupingBy { it }.eachCount())
                state.players.filter { it.isEliminated }.forEach { assertTrue(it.hand.isEmpty()) }
                if (state.phase == GamePhase.PLAYING) {
                    assertFalse(state.players[state.currentPlayer].isEliminated)
                    assertEquals(2, state.players[state.currentPlayer].hand.size)
                }
                val action = if (state.phase == GamePhase.ROUND_OVER) GameAction.NextRound else {
                    val observation = GameEngine.observe(state, state.currentPlayer)
                    assertTrue(observation.legalActions.isNotEmpty())
                    AiPlayer.choose(observation, difficulty, seed * 1000 + decisions)
                }
                assertTrue(GameEngine.legalActions(state).contains(action))
                state = GameEngine.apply(state, action)
                decisions++
            }
            assertEquals("Match failed to finish, $count players/$difficulty/seed $seed", GamePhase.GAME_OVER, state.phase)
            assertTrue(state.winners.isNotEmpty())
            assertEquals(state.players.filter { it.score >= state.config.victoryThreshold }.map { it.id }, state.winners)
        }
    }
}
