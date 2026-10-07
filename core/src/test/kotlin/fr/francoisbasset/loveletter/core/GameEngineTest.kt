package fr.francoisbasset.loveletter.core

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Regression fixtures describe decisions, independently of the engine implementation. */
class GameEngineTest {
    private fun scenario(
        hands: List<List<Card>>,
        draw: List<Card> = listOf(Card.GARDE, Card.PRETRE, Card.BARON, Card.SERVANTE),
        reserve: Card? = Card.ESPIONNE,
        protected: Set<Int> = emptySet(),
        eliminated: Set<Int> = emptySet(),
        discards: Map<Int, List<Card>> = emptyMap(),
        scores: Map<Int, Int> = emptyMap()
    ): GameState {
        val base = GameEngine.newGame(GameConfig(playerCount = hands.size), 42L)
        return base.copy(
            players = base.players.map { player -> player.copy(
                hand = hands[player.id], discard = discards[player.id].orEmpty(),
                isProtected = player.id in protected, isEliminated = player.id in eliminated,
                score = scores[player.id] ?: 0
            ) }, drawPile = draw, reserveCard = reserve, faceUpRemoved = emptyList(),
            currentPlayer = 0, phase = GamePhase.PLAYING, result = null,
            winners = emptyList(), knownCards = emptyList(), exclusions = emptyList(),
            privateNotes = emptyList(), journal = emptyList()
        )
    }

    private fun play(state: GameState, card: Card, target: Int? = null, guess: Card? = null) =
        GameEngine.apply(state, GameAction.Play(card, target, guess))

    private fun allCards(state: GameState) =
        state.drawPile + listOfNotNull(state.reserveCard) + state.faceUpRemoved +
            state.players.flatMap { it.hand + it.discard }

    @Test fun editionCompositionIsExactlyTwentyOneCards() {
        val expected = mapOf(
            Card.ESPIONNE to 2, Card.GARDE to 6, Card.PRETRE to 2, Card.BARON to 2,
            Card.SERVANTE to 2, Card.PRINCE to 2, Card.CHANCELIER to 2,
            Card.ROI to 1, Card.COMTESSE to 1, Card.PRINCESSE to 1
        )
        assertEquals(expected, Card.entries.associateWith { it.copies })
        assertEquals((0..9).toList(), Card.entries.map { it.value })
        assertEquals(21, expected.values.sum())
    }

    @Test fun namesBelongToFrenchEdition() {
        assertEquals(listOf("Espionne", "Garde", "Prêtre", "Baron", "Servante", "Prince",
            "Chancelier", "Roi", "Comtesse", "Princesse"), Card.entries.map { it.frenchName })
    }

    @Test fun distributionAndTurnDrawAreCorrectForEveryPlayerCount() {
        for (count in 2..6) {
            val state = GameEngine.newGame(GameConfig(playerCount = count, firstPlayer = count - 1), 93L)
            assertEquals(count - 1, state.currentPlayer)
            state.players.forEach { assertEquals(if (it.id == count - 1) 2 else 1, it.hand.size) }
            assertEquals(if (count == 2) 3 else 0, state.faceUpRemoved.size)
            assertNotNull(state.reserveCard)
            assertEquals(21 - 1 - (if (count == 2) 3 else 0) - count - 1, state.drawPile.size)
            assertEquals(Card.entries.associateWith { it.copies }, allCards(state).groupingBy { it }.eachCount())
        }
    }

    @Test fun fixedSeedReproducesEntireInitialState() {
        assertEquals(GameEngine.newGame(GameConfig(), 1729L), GameEngine.newGame(GameConfig(), 1729L))
    }

    @Test fun invalidPlayerCountsAreRejected() {
        for (count in listOf(1, 7)) assertThrows(IllegalArgumentException::class.java) { GameConfig(playerCount = count) }
    }

    @Test fun scoreThresholdDependsOnlyOnPlayerCount() {
        assertEquals(listOf(6, 5, 4, 3, 3), (2..6).map { GameConfig(playerCount = it).victoryThreshold })
    }

    @Test fun guardCorrectGuessEliminatesAndRevealsHand() {
        val state = scenario(listOf(listOf(Card.GARDE, Card.ROI), listOf(Card.PRETRE), listOf(Card.SERVANTE)))
        val after = play(state, Card.GARDE, 1, Card.PRETRE)
        assertTrue(after.players[1].isEliminated)
        assertEquals(emptyList<Card>(), after.players[1].hand)
        assertEquals(listOf(Card.PRETRE), after.players[1].discard)
        assertEquals(2, after.currentPlayer)
    }

    @Test fun guardWrongGuessKeepsOpponentAlive() {
        val state = scenario(listOf(listOf(Card.GARDE, Card.ROI), listOf(Card.PRETRE)))
        val after = play(state, Card.GARDE, 1, Card.PRINCESSE)
        assertFalse(after.players[1].isEliminated)
        assertTrue(after.exclusions.any { it.targetId == 1 && it.card == Card.PRINCESSE })
    }

    @Test fun guardCannotGuessGuard() {
        val state = scenario(listOf(listOf(Card.GARDE, Card.ROI), listOf(Card.PRETRE)))
        assertFalse(GameEngine.legalActions(state).any { it is GameAction.Play && it.guess == Card.GARDE })
        assertThrows(IllegalArgumentException::class.java) { play(state, Card.GARDE, 1, Card.GARDE) }
    }

    @Test fun guardCanGuessSpy() {
        val state = scenario(listOf(listOf(Card.GARDE, Card.PRETRE), listOf(Card.ESPIONNE)))
        assertTrue(play(state, Card.GARDE, 1, Card.ESPIONNE).players[1].isEliminated)
    }

    @Test fun opponentOnlyEffectsNeverOfferSelfTarget() {
        for (card in listOf(Card.GARDE, Card.PRETRE, Card.BARON, Card.ROI)) {
            val state = scenario(listOf(listOf(card, Card.SERVANTE), listOf(Card.ESPIONNE)))
            assertFalse(GameEngine.legalActions(state).filterIsInstance<GameAction.Play>().any { it.target == 0 })
        }
    }

    @Test fun protectedAndEliminatedPlayersNeverAppearAsTargets() {
        for (card in listOf(Card.GARDE, Card.PRETRE, Card.BARON, Card.PRINCE, Card.ROI)) {
            val state = scenario(listOf(listOf(card, Card.SERVANTE), listOf(Card.PRETRE), emptyList(), listOf(Card.ROI)),
                protected = setOf(1), eliminated = setOf(2))
            val targets = GameEngine.legalActions(state).filterIsInstance<GameAction.Play>().mapNotNull { it.target }
            assertFalse(targets.contains(1)); assertFalse(targets.contains(2))
        }
    }

    @Test fun cardsWithoutLegalOpponentResolveWithoutTarget() {
        for (card in listOf(Card.GARDE, Card.PRETRE, Card.BARON, Card.ROI)) {
            val state = scenario(listOf(listOf(card, Card.ESPIONNE), listOf(Card.PRETRE)), protected = setOf(1))
            assertTrue(GameEngine.legalActions(state).contains(GameAction.Play(card)))
            val after = play(state, card)
            assertFalse(after.players[0].isEliminated)
            assertFalse(after.players[1].isEliminated)
        }
    }

    @Test fun priestKnowledgeIsPrivate() {
        val state = scenario(listOf(listOf(Card.PRETRE, Card.ROI), listOf(Card.COMTESSE), listOf(Card.BARON)))
        val after = play(state, Card.PRETRE, 1)
        assertTrue(GameEngine.observe(after, 0).knownCards.any { it.targetId == 1 && it.card == Card.COMTESSE })
        assertFalse(GameEngine.observe(after, 2).knownCards.any { it.targetId == 1 })
        assertNull(GameEngine.observe(after, 0).players[1].visibleHand)
        assertFalse(GameEngine.observe(after, 2).privateNotes.any { it.playerId == 0 })
    }

    @Test fun baronEliminatesLowerOpponent() {
        val state = scenario(listOf(listOf(Card.BARON, Card.COMTESSE), listOf(Card.PRETRE), listOf(Card.ROI)))
        assertTrue(play(state, Card.BARON, 1).players[1].isEliminated)
    }

    @Test fun baronCanEliminateItsActor() {
        val state = scenario(listOf(listOf(Card.BARON, Card.PRETRE), listOf(Card.COMTESSE), listOf(Card.ROI)))
        val after = play(state, Card.BARON, 1)
        assertTrue(after.players[0].isEliminated)
        assertEquals(listOf(Card.BARON, Card.PRETRE), after.players[0].discard)
    }

    @Test fun baronTieDoesNotEliminateAnyone() {
        val state = scenario(listOf(listOf(Card.BARON, Card.PRINCE), listOf(Card.PRINCE), listOf(Card.ROI)))
        val after = play(state, Card.BARON, 1)
        assertFalse(after.players[0].isEliminated); assertFalse(after.players[1].isEliminated)
    }

    @Test fun baronComparisonIsNotRevealedToThirdSeat() {
        val state = scenario(listOf(listOf(Card.BARON, Card.PRINCE), listOf(Card.PRINCE), listOf(Card.ROI)))
        val after = play(state, Card.BARON, 1)
        assertTrue(GameEngine.observe(after, 0).knownCards.any { it.targetId == 1 && it.card == Card.PRINCE })
        assertTrue(GameEngine.observe(after, 1).knownCards.any { it.targetId == 0 && it.card == Card.PRINCE })
        assertTrue(GameEngine.observe(after, 2).knownCards.isEmpty())
    }

    @Test fun handmaidProtectionExpiresAtStartOfNextTurn() {
        val state = scenario(listOf(listOf(Card.SERVANTE, Card.ROI), listOf(Card.PRETRE)),
            draw = listOf(Card.PRETRE, Card.BARON, Card.GARDE))
        val afterProtection = play(state, Card.SERVANTE)
        assertTrue(afterProtection.players[0].isProtected)
        val afterOpponent = play(afterProtection, Card.PRETRE)
        assertEquals(0, afterOpponent.currentPlayer)
        assertFalse(afterOpponent.players[0].isProtected)
        assertEquals(2, afterOpponent.players[0].hand.size)
    }

    @Test fun princeOffersSelfEvenIfActorHasProtectionFlag() {
        val state = scenario(listOf(listOf(Card.PRINCE, Card.PRETRE), listOf(Card.ROI)), protected = setOf(0, 1))
        val choices = GameEngine.legalActions(state).filterIsInstance<GameAction.Play>().filter { it.card == Card.PRINCE }
        assertEquals(listOf(GameAction.Play(Card.PRINCE, 0)), choices)
    }

    @Test fun princeCanTargetSelfWhenOpponentIsAvailable() {
        val state = scenario(listOf(listOf(Card.PRINCE, Card.PRETRE), listOf(Card.ROI)))
        assertTrue(GameEngine.legalActions(state).contains(GameAction.Play(Card.PRINCE, 0)))
        assertTrue(GameEngine.legalActions(state).contains(GameAction.Play(Card.PRINCE, 1)))
    }

    @Test fun princeDiscardsWithoutApplyingHandmaidEffect() {
        val state = scenario(listOf(listOf(Card.PRINCE, Card.ROI), listOf(Card.SERVANTE), listOf(Card.COMTESSE)),
            draw = listOf(Card.PRETRE, Card.BARON, Card.GARDE))
        val after = play(state, Card.PRINCE, 1)
        assertEquals(listOf(Card.SERVANTE), after.players[1].discard)
        assertTrue(after.players[1].hand.contains(Card.PRETRE))
        assertFalse(after.players[1].isProtected)
    }

    @Test fun princeDiscardingPrincessEliminatesWithoutRedraw() {
        val state = scenario(listOf(listOf(Card.PRINCE, Card.ROI), listOf(Card.PRINCESSE), listOf(Card.COMTESSE)))
        val after = play(state, Card.PRINCE, 1)
        assertTrue(after.players[1].isEliminated)
        assertTrue(after.players[1].hand.isEmpty())
        assertEquals(state.drawPile.drop(1), after.drawPile) // Only the next surviving seat draws.
    }

    @Test fun princeUsesHiddenReserveWhenDrawPileIsEmpty() {
        val state = scenario(listOf(listOf(Card.PRINCE, Card.GARDE), listOf(Card.ROI), listOf(Card.COMTESSE)),
            draw = emptyList(), reserve = Card.PRETRE)
        val after = play(state, Card.PRINCE, 1)
        assertEquals(listOf(Card.PRETRE), after.players[1].hand)
        assertNull(after.reserveCard)
        assertEquals(GamePhase.ROUND_OVER, after.phase)
        assertEquals(listOf(2), after.result!!.winners)
    }

    @Test fun princeDiscardingPrincessDoesNotConsumeReserve() {
        val state = scenario(listOf(listOf(Card.PRINCE, Card.GARDE), listOf(Card.PRINCESSE)),
            draw = emptyList(), reserve = Card.PRETRE)
        assertEquals(Card.PRETRE, play(state, Card.PRINCE, 1).reserveCard)
    }

    @Test fun kingExchangesOnlyRemainingCards() {
        val state = scenario(listOf(listOf(Card.ROI, Card.PRETRE), listOf(Card.COMTESSE), listOf(Card.SERVANTE)))
        val after = play(state, Card.ROI, 1)
        assertEquals(listOf(Card.COMTESSE), after.players[0].hand)
        assertTrue(after.players[1].hand.contains(Card.PRETRE))
        assertEquals(listOf(Card.ROI), after.players[0].discard)
        assertTrue(GameEngine.observe(after, 2).knownCards.isEmpty())
    }

    @Test fun countessIsMandatoryWithPrinceAndKing() {
        for (partner in listOf(Card.PRINCE, Card.ROI)) {
            val state = scenario(listOf(listOf(Card.COMTESSE, partner), listOf(Card.PRETRE)))
            assertEquals(listOf(GameAction.Play(Card.COMTESSE)), GameEngine.legalActions(state))
            assertThrows(IllegalArgumentException::class.java) { play(state, partner, 1) }
        }
    }

    @Test fun countessMayBePlayedVoluntarilyAndDoesNotRevealCompanion() {
        val state = scenario(listOf(listOf(Card.COMTESSE, Card.PRETRE), listOf(Card.ROI), listOf(Card.SERVANTE)))
        val after = play(state, Card.COMTESSE)
        assertEquals(listOf(Card.PRETRE), after.players[0].hand)
        assertNull(GameEngine.observe(after, 2).players[0].visibleHand)
    }

    @Test fun princessPlayEliminatesAndDiscardsRemainingHand() {
        val state = scenario(listOf(listOf(Card.PRINCESSE, Card.ESPIONNE), listOf(Card.ROI), listOf(Card.PRETRE)))
        val after = play(state, Card.PRINCESSE)
        assertTrue(after.players[0].isEliminated)
        assertEquals(listOf(Card.PRINCESSE, Card.ESPIONNE), after.players[0].discard)
        assertTrue(after.players[0].hand.isEmpty())
    }

    @Test fun chancellorDrawsTwoBeforeAskingForPrivateChoice() {
        val state = scenario(listOf(listOf(Card.CHANCELIER, Card.PRETRE), listOf(Card.ROI)),
            draw = listOf(Card.PRINCESSE, Card.COMTESSE, Card.GARDE, Card.BARON))
        val after = play(state, Card.CHANCELIER)
        assertEquals(GamePhase.CHANCELLOR, after.phase)
        assertEquals(listOf(Card.PRETRE, Card.PRINCESSE, Card.COMTESSE), after.players[0].hand)
        assertEquals(listOf(Card.GARDE, Card.BARON), after.drawPile)
        assertTrue(GameEngine.observe(after, 1).legalActions.isEmpty())
        assertNull(GameEngine.observe(after, 1).players[0].visibleHand)
    }

    @Test fun chancellorPreservesChosenBottomOrder() {
        val state = scenario(listOf(listOf(Card.CHANCELIER, Card.PRETRE), listOf(Card.ROI)),
            draw = listOf(Card.PRINCESSE, Card.COMTESSE, Card.GARDE, Card.BARON))
        val pending = play(state, Card.CHANCELIER)
        val after = GameEngine.apply(pending,
            GameAction.ChancellorChoice(Card.PRINCESSE, listOf(Card.COMTESSE, Card.PRETRE)))
        assertEquals(listOf(Card.PRINCESSE), after.players[0].hand)
        assertEquals(listOf(Card.BARON, Card.COMTESSE, Card.PRETRE), after.drawPile)
    }

    @Test fun chancellorWithOneCardKeepsOneAndReturnsOne() {
        val state = scenario(listOf(listOf(Card.CHANCELIER, Card.PRETRE), listOf(Card.ROI)), draw = listOf(Card.PRINCESSE))
        val pending = play(state, Card.CHANCELIER)
        assertEquals(GamePhase.CHANCELLOR, pending.phase)
        assertEquals(2, pending.players[0].hand.size)
        val after = GameEngine.apply(pending, GameAction.ChancellorChoice(Card.PRINCESSE, listOf(Card.PRETRE)))
        assertEquals(GamePhase.PLAYING, after.phase)
        assertEquals(2, after.players[1].hand.size)
        assertTrue(after.players[1].hand.contains(Card.PRETRE))
    }

    @Test fun chancellorWithEmptyDeckDoesNotUseReserve() {
        val state = scenario(listOf(listOf(Card.CHANCELIER, Card.PRINCESSE), listOf(Card.ROI)), draw = emptyList())
        val after = play(state, Card.CHANCELIER)
        assertEquals(GamePhase.ROUND_OVER, after.phase)
        assertEquals(Card.ESPIONNE, after.reserveCard)
        assertEquals(listOf(Card.PRINCESSE), after.players[0].hand)
    }

    @Test fun chancellorTemporarilyEmptyDeckDoesNotEndRound() {
        val state = scenario(listOf(listOf(Card.CHANCELIER, Card.PRETRE), listOf(Card.ROI)),
            draw = listOf(Card.PRINCESSE, Card.COMTESSE))
        val pending = play(state, Card.CHANCELIER)
        assertTrue(pending.drawPile.isEmpty())
        assertEquals(GamePhase.CHANCELLOR, pending.phase)
        assertNull(pending.result)
        val after = GameEngine.apply(pending, GameAction.ChancellorChoice(Card.PRINCESSE, listOf(Card.COMTESSE, Card.PRETRE)))
        assertEquals(GamePhase.PLAYING, after.phase)
        assertEquals(1, after.drawPile.size)
    }

    @Test fun countessDoesNotRestrictChancellorChoice() {
        val state = scenario(listOf(listOf(Card.CHANCELIER, Card.COMTESSE), listOf(Card.PRETRE)),
            draw = listOf(Card.PRINCE, Card.ROI, Card.GARDE))
        val pending = play(state, Card.CHANCELIER)
        val legal = GameEngine.legalActions(pending)
        assertTrue(legal.contains(GameAction.ChancellorChoice(Card.ROI, listOf(Card.COMTESSE, Card.PRINCE))))
        assertTrue(legal.contains(GameAction.ChancellorChoice(Card.COMTESSE, listOf(Card.PRINCE, Card.ROI))))
    }

    @Test fun chancellorRejectsMissingOrInventedReturnedCards() {
        val state = scenario(listOf(listOf(Card.CHANCELIER, Card.PRETRE), listOf(Card.ROI)),
            draw = listOf(Card.PRINCESSE, Card.COMTESSE, Card.GARDE))
        val pending = play(state, Card.CHANCELIER)
        assertThrows(IllegalArgumentException::class.java) {
            GameEngine.apply(pending, GameAction.ChancellorChoice(Card.PRINCESSE, listOf(Card.PRETRE)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameEngine.apply(pending, GameAction.ChancellorChoice(Card.PRINCESSE, listOf(Card.PRETRE, Card.PRETRE)))
        }
    }

    @Test fun duplicateChancellorCardsProduceOnlyDistinctLegalChoices() {
        val state = scenario(listOf(listOf(Card.CHANCELIER, Card.GARDE), listOf(Card.ROI)),
            draw = listOf(Card.GARDE, Card.PRETRE, Card.BARON))
        val pending = play(state, Card.CHANCELIER)
        assertEquals(setOf(
            GameAction.ChancellorChoice(Card.GARDE, listOf(Card.GARDE, Card.PRETRE)),
            GameAction.ChancellorChoice(Card.GARDE, listOf(Card.PRETRE, Card.GARDE)),
            GameAction.ChancellorChoice(Card.PRETRE, listOf(Card.GARDE, Card.GARDE))
        ), GameEngine.legalActions(pending).toSet())
        assertEquals(3, GameEngine.legalActions(pending).size)
    }

    @Test fun lastDrawStillResolvesItsCardEffect() {
        val state = scenario(listOf(listOf(Card.BARON, Card.PRETRE), listOf(Card.GARDE)), draw = emptyList())
        val after = play(state, Card.BARON, 1)
        assertTrue(after.players[1].isEliminated)
        assertEquals(RoundEndReason.LAST_SURVIVOR, after.result!!.reason)
        assertEquals(listOf(0), after.result!!.winners)
    }

    @Test fun depletedDeckAwardsHighestSurvivingHand() {
        val state = scenario(listOf(listOf(Card.ESPIONNE, Card.PRETRE), listOf(Card.COMTESSE)), draw = emptyList())
        val after = play(state, Card.ESPIONNE)
        assertEquals(listOf(1), after.result!!.winners)
        assertEquals(RoundEndReason.HIGHEST_CARD, after.result!!.reason)
    }

    @Test fun equalHandsShareRoundRegardlessOfDiscardTotals() {
        val state = scenario(listOf(listOf(Card.ESPIONNE, Card.PRINCE), listOf(Card.PRINCE)), draw = emptyList(),
            discards = mapOf(0 to listOf(Card.PRETRE), 1 to listOf(Card.COMTESSE)))
        val after = play(state, Card.ESPIONNE)
        assertEquals(listOf(0, 1), after.result!!.winners)
        assertEquals(2, after.players[0].score)
        assertEquals(1, after.players[1].score)
    }

    @Test fun spyBonusCanBeWonWithoutWinningRound() {
        val state = scenario(listOf(listOf(Card.ESPIONNE, Card.PRETRE), listOf(Card.COMTESSE)), draw = emptyList())
        val after = play(state, Card.ESPIONNE)
        assertEquals(0, after.result!!.spyBonusPlayer)
        assertEquals(listOf(1), after.result!!.winners)
        assertEquals(mapOf(0 to 1, 1 to 1), after.result!!.pointsAwarded)
    }

    @Test fun soleSurvivorReceivesWinAndSpyBonus() {
        val state = scenario(listOf(listOf(Card.PRINCE, Card.GARDE), listOf(Card.PRINCESSE)),
            discards = mapOf(0 to listOf(Card.ESPIONNE)))
        val after = play(state, Card.PRINCE, 1)
        assertEquals(2, after.players[0].score)
        assertEquals(mapOf(0 to 2, 1 to 0), after.result!!.pointsAwarded)
    }

    @Test fun twoSpiesOnSameSurvivorGiveOnlyOneBonus() {
        val state = scenario(listOf(listOf(Card.ESPIONNE, Card.PRETRE), listOf(Card.COMTESSE)),
            draw = emptyList(), discards = mapOf(0 to listOf(Card.ESPIONNE)))
        assertEquals(1, play(state, Card.ESPIONNE).players[0].score)
    }

    @Test fun spiesOnTwoSurvivorsCancelBonus() {
        val state = scenario(listOf(listOf(Card.ESPIONNE, Card.PRETRE), listOf(Card.COMTESSE)),
            draw = emptyList(), discards = mapOf(1 to listOf(Card.ESPIONNE)))
        val after = play(state, Card.ESPIONNE)
        assertNull(after.result!!.spyBonusPlayer)
        assertEquals(0, after.players[0].score)
    }

    @Test fun eliminatedSpyHolderDoesNotBlockSurvivorBonus() {
        val state = scenario(listOf(listOf(Card.ESPIONNE, Card.PRETRE), listOf(Card.PRINCE), emptyList()),
            draw = emptyList(), eliminated = setOf(2), discards = mapOf(2 to listOf(Card.ESPIONNE)))
        assertEquals(0, play(state, Card.ESPIONNE).result!!.spyBonusPlayer)
    }

    @Test fun eliminatedSpyHolderNeverReceivesBonus() {
        val state = scenario(listOf(listOf(Card.PRINCESSE, Card.ESPIONNE), listOf(Card.ROI)), draw = emptyList())
        val after = play(state, Card.PRINCESSE)
        assertNull(after.result!!.spyBonusPlayer)
        assertEquals(0, after.players[0].score)
    }

    @Test fun spyStillInHandDoesNotQualifyForBonus() {
        val state = scenario(listOf(listOf(Card.SERVANTE, Card.ESPIONNE), listOf(Card.ROI)), draw = emptyList())
        val after = play(state, Card.SERVANTE)
        assertNull(after.result!!.spyBonusPlayer)
        assertEquals(0, after.players[0].score)
    }

    @Test fun spyDiscardedByPrinceQualifiesForBonus() {
        val state = scenario(listOf(listOf(Card.PRINCE, Card.GARDE), listOf(Card.ESPIONNE), listOf(Card.ROI)),
            draw = emptyList(), reserve = Card.PRETRE)
        val after = play(state, Card.PRINCE, 1)
        assertEquals(1, after.result!!.spyBonusPlayer)
        assertEquals(1, after.players[1].score)
    }

    @Test fun spyAndRoundWinnerCanBothWinMatchSimultaneously() {
        val state = scenario(listOf(listOf(Card.ESPIONNE, Card.PRETRE), listOf(Card.COMTESSE)),
            draw = emptyList(), scores = mapOf(0 to 5, 1 to 5))
        val after = play(state, Card.ESPIONNE)
        assertEquals(GamePhase.GAME_OVER, after.phase)
        assertEquals(listOf(0, 1), after.winners)
        assertEquals(6, after.players[0].score)
        assertEquals(6, after.players[1].score)
        assertTrue(GameEngine.legalActions(after).isEmpty())
    }

    @Test fun nextRoundRestoresPlayersAndKeepsScores() {
        val state = scenario(listOf(listOf(Card.GARDE, Card.PRETRE), listOf(Card.PRINCESSE)))
        val finished = play(state, Card.GARDE, 1, Card.PRINCESSE)
        val after = GameEngine.apply(finished, GameAction.NextRound)
        assertEquals(2, after.roundNumber)
        assertEquals(0, after.currentPlayer)
        assertFalse(after.players.any { it.isEliminated || it.isProtected })
        assertTrue(after.players.all { it.discard.isEmpty() })
        assertEquals(listOf(1, 0), after.players.map { it.score })
        assertEquals(Card.entries.associateWith { it.copies }, allCards(after).groupingBy { it }.eachCount())
    }

    @Test fun nextRoundStartsWithRoundWinnerNotSpyBonusRecipient() {
        val state = scenario(listOf(listOf(Card.ESPIONNE, Card.PRETRE), listOf(Card.COMTESSE)), draw = emptyList())
        val finished = play(state, Card.ESPIONNE)
        val after = GameEngine.apply(finished, GameAction.NextRound)
        assertEquals(1, after.currentPlayer)
    }

    @Test fun afterTieNextStarterComesFromTiedWinners() {
        val state = scenario(listOf(listOf(Card.ESPIONNE, Card.PRINCE), listOf(Card.PRINCE), listOf(Card.PRETRE)), draw = emptyList())
        val finished = play(state, Card.ESPIONNE)
        assertTrue(GameEngine.apply(finished, GameAction.NextRound).currentPlayer in listOf(0, 1))
    }

    @Test fun forbiddenActionsAreRejectedByEngine() {
        val state = scenario(listOf(listOf(Card.GARDE, Card.PRETRE), listOf(Card.ROI)))
        assertThrows(IllegalArgumentException::class.java) { play(state, Card.PRINCESSE) }
        assertThrows(IllegalArgumentException::class.java) { play(state, Card.GARDE, 0, Card.ROI) }
        assertThrows(IllegalArgumentException::class.java) { GameEngine.apply(state, GameAction.NextRound) }
    }

    @Test fun ordinaryLegalActionsDoNotInspectUnknownHandsOrDeckOrder() {
        val state = scenario(listOf(listOf(Card.GARDE, Card.CHANCELIER), listOf(Card.PRETRE), listOf(Card.ROI)))
        val changed = state.copy(
            players = state.players.map { if (it.id == 1) it.copy(hand = listOf(Card.PRINCESSE)) else it },
            drawPile = state.drawPile.reversed(), reserveCard = Card.COMTESSE
        )
        assertEquals(GameEngine.legalActions(state), GameEngine.legalActions(changed))
        assertEquals(GameEngine.observe(state, 0), GameEngine.observe(changed, 0))
    }

    @Test fun nonActiveSeatNeverReceivesAnotherSeatsLegalActions() {
        val state = GameEngine.newGame(GameConfig(playerCount = 4), 13L)
        assertTrue(GameEngine.observe(state, 1).legalActions.isEmpty())
        assertTrue(GameEngine.observe(state, 2).legalActions.isEmpty())
    }

    @Test fun hiddenReserveAndOpponentHandsAreAbsentFromObservation() {
        val state = GameEngine.newGame(GameConfig(playerCount = 6), 11L)
        val observation = GameEngine.observe(state, 0)
        assertEquals(state.players[0].hand, observation.ownHand)
        observation.players.filter { it.id != 0 }.forEach { assertNull(it.visibleHand) }
        assertTrue(observation.knownCards.isEmpty())
    }

    @Test fun princeInvalidatesPreviouslyKnownReplacedCard() {
        val state = scenario(listOf(listOf(Card.PRINCE, Card.GARDE), listOf(Card.PRETRE), listOf(Card.ROI)))
            .copy(knownCards = listOf(KnownCard(2, 1, Card.PRETRE)))
        val after = play(state, Card.PRINCE, 1)
        assertFalse(after.knownCards.any { it.targetId == 1 })
    }

    @Test fun kingInvalidatesThirdPartyKnowledgeOfBothHands() {
        val state = scenario(listOf(listOf(Card.ROI, Card.PRETRE), listOf(Card.COMTESSE), listOf(Card.BARON)))
            .copy(knownCards = listOf(KnownCard(2, 0, Card.PRETRE), KnownCard(2, 1, Card.COMTESSE)))
        val after = play(state, Card.ROI, 1)
        assertTrue(GameEngine.observe(after, 2).knownCards.isEmpty())
    }

    @Test fun playingKnownCardInvalidatesCertaintyAboutItsReplacement() {
        val state = scenario(listOf(listOf(Card.SERVANTE, Card.PRETRE), listOf(Card.ROI), listOf(Card.BARON)))
            .copy(knownCards = listOf(KnownCard(2, 0, Card.SERVANTE)))
        val after = play(state, Card.SERVANTE)
        assertFalse(after.knownCards.any { it.targetId == 0 })
    }

    @Test fun chancellorInvalidatesPriorKnowledgeBeforePrivateChoice() {
        val state = scenario(listOf(listOf(Card.CHANCELIER, Card.PRETRE), listOf(Card.ROI), listOf(Card.BARON)))
            .copy(knownCards = listOf(KnownCard(2, 0, Card.PRETRE)))
        val after = play(state, Card.CHANCELIER)
        assertFalse(after.knownCards.any { it.targetId == 0 })
        assertTrue(GameEngine.observe(after, 2).legalActions.isEmpty())
    }

    @Test fun observationsRevealSurvivingHandsOnlyAfterRoundEnds() {
        val state = scenario(listOf(listOf(Card.SERVANTE, Card.PRETRE), listOf(Card.ROI)), draw = emptyList())
        assertNull(GameEngine.observe(state, 0).players[1].visibleHand)
        val finished = play(state, Card.SERVANTE)
        assertEquals(listOf(Card.ROI), GameEngine.observe(finished, 0).players[1].visibleHand)
    }

    @Test fun snapshotSerializationPreservesAllSecretsAndRandomState() {
        val state = GameEngine.newGame(GameConfig(playerCount = 6, firstPlayer = null), 48931L)
        val restored = Json.decodeFromString<GameState>(Json.encodeToString(state))
        assertEquals(state, restored)
        val action = GameEngine.legalActions(state).first()
        assertEquals(GameEngine.apply(state, action), GameEngine.apply(restored, action))
    }

    @Test fun pendingChancellorCanBeSavedAndResumedWithoutChangingResults() {
        val state = scenario(listOf(listOf(Card.CHANCELIER, Card.PRETRE), listOf(Card.ROI)),
            draw = listOf(Card.PRINCESSE, Card.COMTESSE, Card.GARDE))
        val pending = play(state, Card.CHANCELIER)
        val restored = Json.decodeFromString<GameState>(Json.encodeToString(pending))
        val action = GameAction.ChancellorChoice(Card.PRINCESSE, listOf(Card.COMTESSE, Card.PRETRE))
        assertEquals(GameEngine.apply(pending, action), GameEngine.apply(restored, action))
    }

    @Test fun nextRoundAfterSavedTieUsesSameRandomStarterAndShuffle() {
        val state = scenario(listOf(listOf(Card.ESPIONNE, Card.PRINCE), listOf(Card.PRINCE), listOf(Card.PRETRE)), draw = emptyList())
        val finished = play(state, Card.ESPIONNE)
        val restored = Json.decodeFromString<GameState>(Json.encodeToString(finished))
        assertEquals(GameEngine.apply(finished, GameAction.NextRound), GameEngine.apply(restored, GameAction.NextRound))
    }
}
