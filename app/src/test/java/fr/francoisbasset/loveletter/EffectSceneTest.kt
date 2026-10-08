package fr.francoisbasset.loveletter

import fr.francoisbasset.loveletter.core.*
import fr.francoisbasset.loveletter.persistence.SavedSession
import fr.francoisbasset.loveletter.persistence.SessionCodec
import fr.francoisbasset.loveletter.ui.*
import org.junit.Assert.*
import org.junit.Test

/** Presentation privacy is verified from deterministic actions, without reading localized text. */
class EffectSceneTest {
    private fun scenario(
        hands: List<List<Card>>,
        actor: Int = 0,
        draw: List<Card> = listOf(Card.GARDE, Card.PRETRE, Card.SERVANTE, Card.ESPIONNE)
    ): GameState {
        val base = GameEngine.newGame(GameConfig(playerCount = hands.size, firstPlayer = actor), 83L)
        return base.copy(
            players = base.players.map { it.copy(hand = hands[it.id], discard = emptyList(), isProtected = false, isEliminated = false) },
            currentPlayer = actor, drawPile = draw, phase = GamePhase.PLAYING,
            knownCards = emptyList(), exclusions = emptyList(), privateNotes = emptyList(),
            journal = emptyList(), result = null, nextEventId = 1
        )
    }

    private fun scene(before: GameState, action: GameAction): UiEffectScene =
        projectEffectScene(before, GameEngine.apply(before, action), action)

    @Test fun humanBaronWinUsesTheHeldCardInsteadOfThePlayedBaron() {
        val before = scenario(listOf(listOf(Card.BARON, Card.COMTESSE), listOf(Card.PRETRE), listOf(Card.SERVANTE)))
        val duel = scene(before, GameAction.Play(Card.BARON, 1))
        assertEquals(UiEffectKind.BARON, duel.kind)
        assertEquals(listOf(8, 2), duel.participants.map { it.cardValue })
        assertEquals(listOf(false, true), duel.participants.map { it.eliminated })
        assertEquals(listOf(1), duel.eliminatedIds)
        assertEquals(UiEffectOutcome.WIN, duel.outcome)
        assertTrue(duel.privateToHuman)
    }

    @Test fun humanBaronLossRetainsBothPreEliminationCardsInTheSavedSnapshot() {
        val before = scenario(listOf(listOf(Card.BARON, Card.PRETRE), listOf(Card.COMTESSE), listOf(Card.SERVANTE)))
        val action = GameAction.Play(Card.BARON, 1)
        val after = GameEngine.apply(before, action)
        val duel = projectEffectScene(before, after, action)
        assertTrue(after.players[0].hand.isEmpty())
        assertEquals(2, after.players[1].hand.size) // The winner has already drawn for the next turn.
        assertEquals(listOf(2, 8), duel.participants.map { it.cardValue })
        assertEquals(listOf(true, false), duel.participants.map { it.eliminated })
        assertEquals(UiEffectOutcome.WIN, duel.outcome)
        val playback = UiPlayback(9L, 0, before.players[0].name, phase = after.phase,
            title = "Duel", summary = "", scene = duel)
        val restored = SessionCodec.decode(SessionCodec.encode(SavedSession(game = after, playback = playback)))
        assertEquals(duel, restored.playback?.scene)
        assertEquals(listOf(2, 8), restored.playback?.scene?.participants?.map { it.cardValue })
    }

    @Test fun equalHumanBaronHasTwoVisibleCardsAndNoElimination() {
        val before = scenario(listOf(listOf(Card.BARON, Card.SERVANTE), listOf(Card.SERVANTE), listOf(Card.PRETRE)))
        val duel = scene(before, GameAction.Play(Card.BARON, 1))
        assertEquals(listOf(4, 4), duel.participants.map { it.cardValue })
        assertEquals(UiEffectOutcome.DRAW, duel.outcome)
        assertTrue(duel.eliminatedIds.isEmpty())
        assertTrue(duel.participants.none { it.eliminated })
    }

    @Test fun humanTargetIsEntitledToSeeBothBaronCards() {
        val before = scenario(listOf(listOf(Card.PRINCESSE), listOf(Card.BARON, Card.PRETRE), listOf(Card.SERVANTE)), actor = 1)
        val duel = scene(before, GameAction.Play(Card.BARON, 0))
        assertEquals(listOf(1, 0), duel.participants.map { it.playerId })
        assertEquals(listOf(2, 9), duel.participants.map { it.cardValue })
        assertTrue(duel.privateToHuman)
    }

    @Test fun spectatorBaronOnlyRevealsPublicEliminatedCardEvenWhenTheRoundEnds() {
        val before = scenario(listOf(listOf(Card.GARDE), listOf(Card.BARON, Card.PRINCESSE), listOf(Card.COMTESSE)),
            actor = 1, draw = emptyList())
        val action = GameAction.Play(Card.BARON, 2)
        val after = GameEngine.apply(before, action)
        assertEquals(GamePhase.ROUND_OVER, after.phase)
        val duel = projectEffectScene(before, after, action)
        assertEquals(listOf(null, 8), duel.participants.map { it.cardValue })
        assertTrue(duel.participants.all { it.nextCardValue == null })
        assertFalse(duel.privateToHuman)
        assertEquals(listOf(2), duel.eliminatedIds)
    }

    @Test fun spectatorEqualBaronKeepsBothCardsHiddenAndDoesNotReuseOldKnowledge() {
        val before = scenario(listOf(listOf(Card.GARDE), listOf(Card.BARON, Card.SERVANTE), listOf(Card.SERVANTE)), actor = 1)
            .copy(knownCards = listOf(KnownCard(0, 1, Card.SERVANTE), KnownCard(0, 2, Card.SERVANTE)))
        val duel = scene(before, GameAction.Play(Card.BARON, 2))
        assertEquals(UiEffectOutcome.DRAW, duel.outcome)
        assertTrue(duel.participants.all { it.cardValue == null && it.nextCardValue == null })
    }

    @Test fun spectatorBaronActorLossRevealsOnlyTheEliminatedActorsCard() {
        val before = scenario(listOf(listOf(Card.GARDE), listOf(Card.BARON, Card.PRETRE), listOf(Card.COMTESSE)), actor = 1)
        val duel = scene(before, GameAction.Play(Card.BARON, 2))
        assertEquals(listOf(2, null), duel.participants.map { it.cardValue })
        assertEquals(listOf(true, false), duel.participants.map { it.eliminated })
        assertFalse(duel.privateToHuman)
    }

    @Test fun twoBaronsInHandRemoveOnlyThePlayedOccurrence() {
        val before = scenario(listOf(listOf(Card.BARON, Card.BARON), listOf(Card.PRETRE), listOf(Card.SERVANTE)))
        assertEquals(listOf(3, 2), scene(before, GameAction.Play(Card.BARON, 1)).participants.map { it.cardValue })
    }

    @Test fun priestRevealsOnlyToTheHumanActor() {
        val human = scenario(listOf(listOf(Card.PRETRE, Card.GARDE), listOf(Card.PRINCESSE), listOf(Card.SERVANTE)))
        val revealed = scene(human, GameAction.Play(Card.PRETRE, 1))
        assertEquals(listOf(null, 9), revealed.participants.map { it.cardValue })
        assertTrue(revealed.privateToHuman)
        val ai = scenario(listOf(listOf(Card.GARDE), listOf(Card.PRETRE, Card.SERVANTE), listOf(Card.PRINCESSE)), actor = 1)
        val secret = scene(ai, GameAction.Play(Card.PRETRE, 2))
        assertTrue(secret.participants.all { it.cardValue == null && it.nextCardValue == null })
        assertFalse(secret.privateToHuman)
    }

    @Test fun kingFreezesBothDirectionsOnlyForHumanParticipants() {
        val before = scenario(listOf(listOf(Card.ROI, Card.SERVANTE), listOf(Card.PRETRE), listOf(Card.COMTESSE)))
        val exchange = scene(before, GameAction.Play(Card.ROI, 1))
        assertEquals(listOf(4, 2), exchange.participants.map { it.cardValue })
        assertEquals(listOf(2, 4), exchange.participants.map { it.nextCardValue })
        assertTrue(exchange.privateToHuman)
        val ai = scenario(listOf(listOf(Card.COMTESSE), listOf(Card.ROI, Card.SERVANTE), listOf(Card.PRETRE)), actor = 1)
        val secret = scene(ai, GameAction.Play(Card.ROI, 2))
        assertTrue(secret.participants.all { it.cardValue == null && it.nextCardValue == null })
        assertFalse(secret.privateToHuman)
    }

    @Test fun princePublicDiscardDoesNotExposeOpponentReplacement() {
        val before = scenario(listOf(listOf(Card.PRINCE, Card.SERVANTE), listOf(Card.PRETRE), listOf(Card.COMTESSE)))
        val effect = scene(before, GameAction.Play(Card.PRINCE, 1))
        assertEquals(listOf(null, 2), effect.participants.map { it.cardValue })
        assertTrue(effect.participants.all { it.nextCardValue == null })
    }

    @Test fun princeHumanReplacementIsTheEffectDrawNotTheSubsequentTurnDraw() {
        val before = scenario(listOf(listOf(Card.PRETRE), listOf(Card.SERVANTE), listOf(Card.PRINCE, Card.BARON)), actor = 2)
        val action = GameAction.Play(Card.PRINCE, 0)
        val after = GameEngine.apply(before, action)
        assertEquals(listOf(Card.GARDE, Card.PRETRE), after.players[0].hand)
        val target = projectEffectScene(before, after, action).participants.last()
        assertEquals(2, target.cardValue)
        assertEquals(1, target.nextCardValue)
    }

    @Test fun selfPrinceUsesHeldCardAndPrincessDiscardHasNoReplacement() {
        val self = scenario(listOf(listOf(Card.PRINCE, Card.PRINCE), listOf(Card.PRETRE), listOf(Card.SERVANTE)))
        val effect = scene(self, GameAction.Play(Card.PRINCE, 0))
        assertEquals(1, effect.participants.size)
        assertEquals(5, effect.participants.single().cardValue)
        assertEquals(1, effect.participants.single().nextCardValue)
        val princess = scenario(listOf(listOf(Card.PRINCE, Card.SERVANTE), listOf(Card.PRINCESSE), listOf(Card.PRETRE)))
        val discarded = scene(princess, GameAction.Play(Card.PRINCE, 1)).participants.last()
        assertEquals(9, discarded.cardValue)
        assertNull(discarded.nextCardValue)
        assertTrue(discarded.eliminated)
    }

    @Test fun guardGuessIsPublicButIncorrectTargetCardRemainsHidden() {
        val before = scenario(listOf(listOf(Card.GARDE, Card.PRETRE), listOf(Card.PRINCESSE), listOf(Card.SERVANTE)))
        val miss = scene(before, GameAction.Play(Card.GARDE, 1, Card.SERVANTE))
        assertEquals(4, miss.guessedCardValue)
        assertEquals(UiEffectOutcome.MISS, miss.outcome)
        assertTrue(miss.participants.all { it.cardValue == null })
        val hit = scene(before, GameAction.Play(Card.GARDE, 1, Card.PRINCESSE))
        assertEquals(UiEffectOutcome.HIT, hit.outcome)
        assertEquals(9, hit.participants.last().cardValue)
        assertTrue(hit.participants.last().eliminated)
    }

    @Test fun chancellorChoiceDoesNotPublishKeptOrBottomCards() {
        val before = scenario(listOf(listOf(Card.CHANCELIER, Card.PRETRE), listOf(Card.SERVANTE), listOf(Card.COMTESSE)),
            draw = listOf(Card.PRINCESSE, Card.GARDE, Card.ESPIONNE, Card.GARDE))
        val pending = GameEngine.apply(before, GameAction.Play(Card.CHANCELIER))
        val choice = GameAction.ChancellorChoice(Card.PRINCESSE, listOf(Card.PRETRE, Card.GARDE))
        val effect = scene(pending, choice)
        assertTrue(effect.participants.all { it.cardValue == null && it.nextCardValue == null })
        assertFalse(effect.privateToHuman)
        assertEquals(2, effect.cardsDrawn)
    }

    @Test fun effectWithoutEligibleTargetNeverRevealsTheActorsHeldCard() {
        val before = scenario(listOf(listOf(Card.BARON, Card.PRINCESSE), listOf(Card.PRETRE), listOf(Card.SERVANTE)))
            .let { state -> state.copy(players = state.players.map { it.copy(isProtected = it.id != 0) }) }
        val duel = scene(before, GameAction.Play(Card.BARON))
        assertEquals(UiEffectOutcome.NO_TARGET, duel.outcome)
        assertNull(duel.participants.single().cardValue)
        assertFalse(duel.privateToHuman)
    }
}
