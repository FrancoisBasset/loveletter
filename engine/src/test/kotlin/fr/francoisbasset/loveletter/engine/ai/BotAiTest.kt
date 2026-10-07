package fr.francoisbasset.loveletter.engine.ai

import fr.francoisbasset.loveletter.engine.*
import org.junit.Assert.*
import org.junit.Test

class BotAiTest {
    @Test fun `all difficulties complete legal games for every supported player count`() {
        Difficulty.entries.forEach { difficulty ->
            for (players in 2..6) for (seed in 0L..3L) {
                var state = GameEngine.newGame(GameConfig(players, difficulty, seed))
                var actions = 0
                while (state.phase != Phase.GAME_OVER && actions < 2_000) {
                    if (state.phase == Phase.ROUND_OVER) {
                        state = GameEngine.nextRound(state)
                    } else {
                        val view = GameEngine.observation(state, state.currentPlayer)
                        val action = BotAi.chooseAction(view, difficulty, seed + actions)
                        assertTrue("$difficulty / $players players / seed $seed", action in view.legalActions)
                        state = GameEngine.apply(state, action)
                    }
                    actions++
                }
                assertEquals("Game did not terminate: $difficulty / $players / $seed", Phase.GAME_OVER, state.phase)
                assertTrue(state.gameWinners.isNotEmpty())
                assertTrue(state.gameWinners.all { state.players[it].score >= state.config.pointsToWin })
            }
        }
    }

    @Test fun `seeded choices are reproducible for every difficulty`() {
        val state = GameEngine.newGame(GameConfig(playerCount = 6, seed = 42))
        val view = GameEngine.observation(state, state.currentPlayer)
        Difficulty.entries.forEach { difficulty ->
            for (seed in 0L..20L) {
                assertEquals(BotAi.chooseAction(view, difficulty, seed), BotAi.chooseAction(view, difficulty, seed))
            }
        }
    }

    @Test fun `easy uses seeded variation while avoiding voluntary Princess elimination`() {
        val view = observation(listOf(Card.GUARD, Card.PRINCESS))
        val choices = (0L..50L).map { BotAi.chooseAction(view, Difficulty.EASY, it) }.toSet()
        assertTrue(choices.size > 1)
        assertTrue(choices.all { (it as PlayCard).cardIndex == 0 })
    }

    @Test fun `normal and hard use a legitimately known card for a certain Guard hit`() {
        val view = observation(listOf(Card.GUARD, Card.BARON), knownCard = Card.KING)
        listOf(Difficulty.NORMAL, Difficulty.HARD).forEach {
            assertEquals(PlayCard(0, 1, Card.KING), BotAi.chooseAction(view, it, 12))
        }
    }

    @Test fun `normal counts public discards to prefer the most likely Guard guess`() {
        val exhausted = listOf(Card.SPY, Card.SPY, Card.PRIEST, Card.PRIEST,
            Card.BARON, Card.BARON, Card.HANDMAID, Card.HANDMAID,
            Card.PRINCE, Card.PRINCE, Card.COUNTESS)
        val view = observation(listOf(Card.GUARD, Card.PRINCESS), discard = exhausted)
        assertEquals(PlayCard(0, 1, Card.CHANCELLOR), BotAi.chooseAction(view, Difficulty.NORMAL, 0))
    }

    @Test fun `hard conditions on a failed guess until that opponent changes their hand`() {
        val exhausted = listOf(Card.SPY, Card.SPY, Card.PRIEST, Card.PRIEST,
            Card.BARON, Card.BARON, Card.HANDMAID, Card.HANDMAID,
            Card.PRINCE, Card.PRINCE, Card.COUNTESS)
        val view = observation(listOf(Card.GUARD, Card.PRINCESS), discard = exhausted).copy(history = listOf(
            GameEvent("Échec public", 1, EventType.GUESS, 0, 1, Card.CHANCELLOR, success = false)
        ))
        assertEquals(PlayCard(0, 1, Card.KING), BotAi.chooseAction(view, Difficulty.HARD, 0))
        val afterDraw = view.copy(history = view.history + GameEvent("Pioche", 1, EventType.DRAW, 1))
        assertEquals(PlayCard(0, 1, Card.CHANCELLOR), BotAi.chooseAction(afterDraw, Difficulty.HARD, 0))
    }

    @Test fun `Chancellor resolves with a legal permutation and retains the strongest final hand`() {
        val hand = listOf(Card.GUARD, Card.PRINCESS, Card.SPY)
        val actions = listOf(
            ResolveChancellor(0, listOf(1, 2)), ResolveChancellor(0, listOf(2, 1)),
            ResolveChancellor(1, listOf(0, 2)), ResolveChancellor(1, listOf(2, 0)),
            ResolveChancellor(2, listOf(0, 1)), ResolveChancellor(2, listOf(1, 0)),
        )
        val view = observation(listOf(Card.GUARD, Card.PRINCESS)).copy(
            hand = hand, phase = Phase.CHANCELLOR, deckCount = 0, legalActions = actions
        )
        listOf(Difficulty.NORMAL, Difficulty.HARD).forEach { difficulty ->
            val chosen = BotAi.chooseAction(view, difficulty, 0) as ResolveChancellor
            assertEquals(1, chosen.keepIndex)
            assertEquals(setOf(0, 2), chosen.returnOrder.toSet())
            assertTrue(chosen in view.legalActions)
        }
    }

    @Test fun `changing every unavailable secret leaves the bot observation and choices unchanged`() {
        val original = GameEngine.newGame(GameConfig(playerCount = 4, seed = 13))
        val other = original.players[1]
        val secretAltered = original.copy(
            config = original.config.copy(seed = 9_876_543),
            randomState = 12_345,
            reserve = original.deck.last(),
            players = original.players.map { player ->
                if (player.id == 1) player.copy(hand = listOf(original.deck.first())) else player
            },
            deck = (listOf(other.hand.single()) + original.deck.drop(1).dropLast(1) + listOfNotNull(original.reserve)).reversed(),
            history = original.history + GameEvent("Secret d’un adversaire", 1, EventType.REVEAL, 1, 2,
                Card.PRINCESS, audience = setOf(1)),
        )
        val before = GameEngine.observation(original, 0)
        val after = GameEngine.observation(secretAltered, 0)
        assertEquals(before, after)
        assertEquals(0L, before.config.seed)
        Difficulty.entries.forEach { difficulty ->
            assertEquals(BotAi.chooseAction(before, difficulty, 71), BotAi.chooseAction(after, difficulty, 71))
        }
    }

    @Test fun `forced Countess and protected targets are respected without duplicating rules`() {
        val initial = GameEngine.newGame(GameConfig(playerCount = 3, seed = 1))
        val forced = initial.copy(players = initial.players.map {
            when (it.id) {
                0 -> it.copy(hand = listOf(Card.PRINCE, Card.COUNTESS))
                1 -> it.copy(protected = true)
                else -> it
            }
        })
        val view = GameEngine.observation(forced, 0)
        Difficulty.entries.forEach { assertEquals(PlayCard(1), BotAi.chooseAction(view, it)) }
        val guarded = forced.copy(players = forced.players.map {
            if (it.id == 0) it.copy(hand = listOf(Card.GUARD, Card.PRINCESS)) else it
        })
        val guardedView = GameEngine.observation(guarded, 0)
        Difficulty.entries.forEach {
            val action = BotAi.chooseAction(guardedView, it) as PlayCard
            assertEquals(2, action.targetId)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bot rejects a nonacting observation`() {
        val state = GameEngine.newGame(GameConfig(seed = 55))
        BotAi.chooseAction(GameEngine.observation(state, 1))
    }

    @Test fun `hard recognizes a known Princess as a Prince elimination target`() {
        val view = observation(listOf(Card.PRINCE, Card.SPY), knownCard = Card.PRINCESS)
        val action = BotAi.chooseAction(view, Difficulty.HARD)
        assertEquals(PlayCard(0, 1), action)
    }

    private fun observation(hand: List<Card>, knownCard: Card? = null, discard: List<Card> = emptyList()): PlayerObservation {
        val initial = GameEngine.newGame(GameConfig(playerCount = 2, seed = 98))
        val state = initial.copy(
            players = listOf(
                PlayerState(0, "IA", hand = hand),
                PlayerState(1, "Adversaire", hand = listOf(knownCard ?: Card.KING), discard = discard),
            ),
            knowledge = if (knownCard == null) emptyList() else listOf(CardKnowledge(0, 1, knownCard)),
            faceUpRemoved = emptyList(), history = emptyList(),
        )
        return GameEngine.observation(state, 0)
    }
}
