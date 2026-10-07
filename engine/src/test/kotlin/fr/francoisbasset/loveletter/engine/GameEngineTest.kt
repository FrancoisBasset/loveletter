package fr.francoisbasset.loveletter.engine

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class GameEngineTest {
    private fun state(
        hands: List<List<Card>>,
        deck: List<Card> = listOf(Card.GUARD, Card.PRIEST, Card.BARON, Card.HANDMAID),
        protected: Set<Int> = emptySet(),
        discards: Map<Int, List<Card>> = emptyMap(),
        scores: Map<Int, Int> = emptyMap(),
    ): GameState = GameState(
        config = GameConfig(hands.size, seed = 42L),
        players = hands.mapIndexed { id, hand -> PlayerState(id, "Joueur $id", hand, discards[id].orEmpty(), id in protected, score = scores[id] ?: 0) },
        currentPlayer = 0, roundNumber = 1, deck = deck, reserve = Card.SPY, faceUpRemoved = emptyList(),
    )

    @Test fun `deck contains exactly the twenty one cards of this edition`() {
        val counts = Card.fullDeck().groupingBy { it }.eachCount()
        assertEquals(21, Card.fullDeck().size)
        assertEquals(listOf(2, 6, 2, 2, 2, 2, 2, 1, 1, 1), Card.entries.map { counts.getValue(it) })
        assertEquals((0..9).toList(), Card.entries.map { it.value })
        assertEquals("Chancelier", Card.CHANCELLOR.frenchName)
    }

    @Test fun `setup reserves one card and two player game removes three face up`() {
        for (count in 2..6) {
            val game = GameEngine.newGame(GameConfig(count, seed = 3))
            assertNotNull(game.reserve)
            assertEquals(if (count == 2) 3 else 0, game.faceUpRemoved.size)
            assertEquals(2, game.players.first().hand.size)
            assertTrue(game.players.drop(1).all { it.hand.size == 1 })
            assertEquals(21 - 1 - count - 1 - game.faceUpRemoved.size, game.deck.size)
            assertEquals(Card.fullDeck().groupingBy { it }.eachCount(),
                (game.deck + listOfNotNull(game.reserve) + game.faceUpRemoved + game.players.flatMap { it.hand }).groupingBy { it }.eachCount())
        }
    }

    @Test fun `same seed produces exactly same game`() {
        assertEquals(GameEngine.newGame(GameConfig(6, seed = 1234)), GameEngine.newGame(GameConfig(6, seed = 1234)))
        assertNotEquals(GameEngine.newGame(GameConfig(6, seed = 1234)).deck, GameEngine.newGame(GameConfig(6, seed = 1235)).deck)
    }

    @Test fun `guard cannot name guard and has no self target`() {
        val game = state(listOf(listOf(Card.GUARD, Card.PRIEST), listOf(Card.BARON), listOf(Card.PRINCESS)))
        val actions = GameEngine.legalActions(game).filterIsInstance<PlayCard>().filter { it.cardIndex == 0 }
        assertEquals(18, actions.size)
        assertTrue(actions.none { it.guess == Card.GUARD || it.targetId == 0 })
    }

    @Test fun `successful guard eliminates and discards target hand`() {
        val game = state(listOf(listOf(Card.GUARD, Card.COUNTESS), listOf(Card.PRINCESS), listOf(Card.BARON)))
        val next = GameEngine.apply(game, PlayCard(0, 1, Card.PRINCESS))
        assertTrue(next.players[1].eliminated)
        assertEquals(listOf(Card.PRINCESS), next.players[1].discard)
        assertEquals(2, next.currentPlayer)
        assertEquals(emptyList<Card>(), next.players[1].hand)
        assertEquals(listOf(Card.GUARD), next.players[0].discard)
    }

    @Test fun `failed guard does not reveal secret card`() {
        val game = state(listOf(listOf(Card.GUARD, Card.COUNTESS), listOf(Card.PRINCESS), listOf(Card.BARON)))
        val next = GameEngine.apply(game, PlayCard(0, 2, Card.PRIEST))
        assertFalse(next.players[2].eliminated)
        assertNull(GameEngine.observation(next, 0).players[2].knownCard)
        assertTrue(GameEngine.observation(next, 0).history.none { it.card == Card.BARON })
    }

    @Test fun `priest reveals card only to acting player`() {
        val game = state(listOf(listOf(Card.PRIEST, Card.COUNTESS), listOf(Card.GUARD), listOf(Card.PRINCESS)))
        val next = GameEngine.apply(game, PlayCard(0, 2))
        assertEquals(Card.PRINCESS, GameEngine.observation(next, 0).players[2].knownCard)
        assertNull(GameEngine.observation(next, 1).players[2].knownCard)
        assertTrue(GameEngine.observation(next, 0).history.any { it.type == EventType.REVEAL && it.card == Card.PRINCESS })
        assertTrue(GameEngine.observation(next, 1).history.none { it.type == EventType.REVEAL && it.card == Card.PRINCESS })
    }

    @Test fun `baron lower value is eliminated without revealing winning hand publicly`() {
        val game = state(listOf(listOf(Card.BARON, Card.KING), listOf(Card.GUARD), listOf(Card.PRINCE)))
        val next = GameEngine.apply(game, PlayCard(0, 2))
        assertTrue(next.players[2].eliminated)
        assertFalse(next.players[0].eliminated)
        assertTrue(GameEngine.observation(next, 1).history.none { it.message.contains("Roi") || it.card == Card.KING })
    }

    @Test fun `baron can eliminate the actor`() {
        val next = GameEngine.apply(state(listOf(listOf(Card.BARON, Card.GUARD), listOf(Card.COUNTESS), listOf(Card.PRINCE))), PlayCard(0, 2))
        assertTrue(next.players[0].eliminated)
        assertEquals(listOf(Card.BARON, Card.GUARD), next.players[0].discard)
    }

    @Test fun `baron ties leave both players alive`() {
        val next = GameEngine.apply(state(listOf(listOf(Card.BARON, Card.PRIEST), listOf(Card.COUNTESS), listOf(Card.PRIEST))), PlayCard(0, 2))
        assertTrue(next.players.none { it.eliminated })
        assertEquals(Card.PRIEST, GameEngine.observation(next, 0).players[2].knownCard)
    }

    @Test fun `handmaid protects through other turns and expires at own next turn`() {
        val game = state(listOf(listOf(Card.HANDMAID, Card.PRIEST), listOf(Card.GUARD)))
        val protected = GameEngine.apply(game, PlayCard(0))
        assertTrue(protected.players[0].protected)
        assertEquals(listOf(PlayCard(0), PlayCard(1)), GameEngine.legalActions(protected))
        val expired = GameEngine.apply(protected, PlayCard(0))
        assertEquals(0, expired.currentPlayer)
        assertFalse(expired.players[0].protected)
    }

    @Test fun `protected players never offered as targets`() {
        for (card in listOf(Card.GUARD, Card.PRIEST, Card.BARON, Card.KING)) {
            val game = state(listOf(listOf(card, Card.SPY), listOf(Card.PRINCESS)), protected = setOf(1))
            assertEquals(listOf(PlayCard(0)), GameEngine.legalActions(game).filterIsInstance<PlayCard>().filter { it.cardIndex == 0 })
        }
    }

    @Test fun `prince must choose self when all opponents protected`() {
        val game = state(listOf(listOf(Card.PRINCE, Card.GUARD), listOf(Card.PRINCESS)), protected = setOf(1))
        assertEquals(listOf(PlayCard(0, 0)), GameEngine.legalActions(game).filterIsInstance<PlayCard>().filter { it.cardIndex == 0 })
    }

    @Test fun `prince discards target card without applying its effect`() {
        val game = state(listOf(listOf(Card.PRINCE, Card.KING), listOf(Card.SPY), listOf(Card.HANDMAID)), deck = listOf(Card.PRIEST, Card.GUARD, Card.BARON))
        val next = GameEngine.apply(game, PlayCard(0, 2))
        assertEquals(listOf(Card.HANDMAID), next.players[2].discard)
        assertEquals(listOf(Card.PRIEST), next.players[2].hand)
        assertFalse(next.players[2].protected)
    }

    @Test fun `prince forcing princess discards eliminates without redrawing`() {
        val game = state(listOf(listOf(Card.PRINCE, Card.GUARD), listOf(Card.PRIEST), listOf(Card.PRINCESS)))
        val next = GameEngine.apply(game, PlayCard(0, 2))
        assertTrue(next.players[2].eliminated)
        assertEquals(game.deck.drop(1), next.deck) // Only next player's normal turn draw.
    }

    @Test fun `prince uses reserve when deck empty and round then ends`() {
        val game = state(listOf(listOf(Card.PRINCE, Card.KING), listOf(Card.GUARD)), deck = emptyList())
        val next = GameEngine.apply(game, PlayCard(0, 1))
        assertNull(next.reserve)
        assertEquals(listOf(Card.SPY), next.players[1].hand)
        assertEquals(Phase.ROUND_OVER, next.phase)
    }

    @Test fun `chancellor keeps one and puts others at bottom in requested order`() {
        val game = state(listOf(listOf(Card.CHANCELLOR, Card.PRINCESS), listOf(Card.GUARD), listOf(Card.PRIEST)), deck = listOf(Card.COUNTESS, Card.PRINCE, Card.BARON, Card.SPY))
        val choosing = GameEngine.apply(game, PlayCard(0))
        assertEquals(Phase.CHANCELLOR, choosing.phase)
        assertEquals(listOf(Card.PRINCESS, Card.COUNTESS, Card.PRINCE), choosing.players[0].hand)
        assertEquals(6, GameEngine.legalActions(choosing).size)
        val next = GameEngine.apply(choosing, ResolveChancellor(1, listOf(2, 0)))
        assertEquals(listOf(Card.COUNTESS), next.players[0].hand)
        assertEquals(listOf(Card.SPY, Card.PRINCE, Card.PRINCESS), next.deck)
        assertFalse(next.players[0].eliminated)
        assertEquals(listOf(Card.CHANCELLOR), next.players[0].discard)
    }

    @Test fun `chancellor handles only one remaining card and delays exhaustion until effect ends`() {
        val game = state(listOf(listOf(Card.CHANCELLOR, Card.GUARD), listOf(Card.PRIEST)), deck = listOf(Card.PRINCESS))
        val choosing = GameEngine.apply(game, PlayCard(0))
        assertEquals(Phase.CHANCELLOR, choosing.phase)
        assertEquals(2, GameEngine.legalActions(choosing).size)
        val next = GameEngine.apply(choosing, ResolveChancellor(1, listOf(0)))
        assertEquals(Phase.TURN, next.phase)
        assertEquals(emptyList<Card>(), next.deck) // Opponent draws returned card; can still play its turn.
    }

    @Test fun `chancellor with no available cards has no effect`() {
        val next = GameEngine.apply(state(listOf(listOf(Card.CHANCELLOR, Card.PRINCESS), listOf(Card.GUARD)), deck = emptyList()), PlayCard(0))
        assertEquals(Phase.ROUND_OVER, next.phase)
        assertEquals(listOf(Card.PRINCESS), next.players[0].hand)
    }

    @Test fun `king swaps cards and private knowledge excludes witnesses`() {
        val game = state(listOf(listOf(Card.KING, Card.PRIEST), listOf(Card.GUARD), listOf(Card.PRINCESS)))
        val next = GameEngine.apply(game, PlayCard(0, 2))
        assertEquals(listOf(Card.PRINCESS), next.players[0].hand)
        assertEquals(listOf(Card.PRIEST), next.players[2].hand)
        assertEquals(Card.PRIEST, GameEngine.observation(next, 0).players[2].knownCard)
        assertEquals(Card.PRINCESS, GameEngine.observation(next, 2).players[0].knownCard)
        assertNull(GameEngine.observation(next, 1).players[0].knownCard)
        assertTrue(GameEngine.observation(next, 1).history.none { it.card == Card.PRINCESS })
    }

    @Test fun `countess mandatory only alongside prince or king`() {
        for (other in Card.entries.filterNot { it == Card.COUNTESS }) {
            val game = state(listOf(listOf(other, Card.COUNTESS), listOf(Card.GUARD)))
            val indices = GameEngine.legalActions(game).filterIsInstance<PlayCard>().map { it.cardIndex }.toSet()
            assertEquals(if (other == Card.PRINCE || other == Card.KING) setOf(1) else setOf(0, 1), indices)
        }
    }

    @Test fun `playing countess does not reveal other card or forced status`() {
        val game = state(listOf(listOf(Card.PRINCE, Card.COUNTESS), listOf(Card.GUARD), listOf(Card.SPY)))
        val next = GameEngine.apply(game, PlayCard(1))
        assertTrue(GameEngine.observation(next, 2).history.none { it.message.contains("Prince") || it.card == Card.PRINCE })
    }

    @Test fun `princess played eliminates actor and discards remaining card`() {
        val game = state(listOf(listOf(Card.PRINCESS, Card.KING), listOf(Card.GUARD), listOf(Card.PRIEST)))
        val next = GameEngine.apply(game, PlayCard(0))
        assertTrue(next.players[0].eliminated)
        assertEquals(listOf(Card.PRINCESS, Card.KING), next.players[0].discard)
    }

    @Test fun `last survivor wins regardless of value or remaining deck`() {
        val next = GameEngine.apply(state(listOf(listOf(Card.PRINCESS, Card.KING), listOf(Card.SPY))), PlayCard(0))
        assertEquals(listOf(1), next.result!!.winners)
        assertEquals(1, next.players[1].score)
        assertEquals(Phase.ROUND_OVER, next.phase)
    }

    @Test fun `exhaustion tie awards all winners ignoring discarded values`() {
        val game = state(listOf(listOf(Card.SPY, Card.PRIEST), listOf(Card.PRIEST), listOf(Card.GUARD)), deck = emptyList(), discards = mapOf(1 to listOf(Card.PRINCE)))
        val next = GameEngine.apply(game, PlayCard(0))
        assertEquals(listOf(0, 1), next.result!!.winners)
        assertEquals(2, next.players[0].score) // Winner plus unique living Spy.
        assertEquals(1, next.players[1].score)
    }

    @Test fun `spy bonus also goes to nonwinner and two spies still only one bonus`() {
        val game = state(listOf(listOf(Card.GUARD, Card.PRIEST), listOf(Card.PRINCESS)), deck = emptyList(), discards = mapOf(0 to listOf(Card.SPY, Card.SPY)))
        val next = GameEngine.apply(game, PlayCard(0, 1, Card.BARON))
        assertEquals(listOf(1), next.result!!.winners)
        assertEquals(0, next.result!!.spyBonusPlayerId)
        assertEquals(listOf(1, 1), next.players.map { it.score })
    }

    @Test fun `two alive spy users cancel the bonus`() {
        val game = state(listOf(listOf(Card.GUARD, Card.PRIEST), listOf(Card.PRINCESS)), deck = emptyList(), discards = mapOf(0 to listOf(Card.SPY), 1 to listOf(Card.SPY)))
        val next = GameEngine.apply(game, PlayCard(0, 1, Card.BARON))
        assertNull(next.result!!.spyBonusPlayerId)
        assertEquals(listOf(0, 1), next.players.map { it.score })
    }

    @Test fun `eliminated spy user neither gains nor cancels bonus`() {
        val game = state(listOf(listOf(Card.GUARD, Card.PRIEST), listOf(Card.PRINCESS), listOf(Card.COUNTESS)), deck = emptyList(), discards = mapOf(0 to listOf(Card.SPY), 1 to listOf(Card.SPY)))
        val next = GameEngine.apply(game, PlayCard(0, 1, Card.PRINCESS))
        assertEquals(0, next.result!!.spyBonusPlayerId)
        assertEquals(0, next.players[1].score)
    }

    @Test fun `discarding spy through prince counts for bonus`() {
        val game = state(listOf(listOf(Card.PRINCE, Card.PRIEST), listOf(Card.SPY)), deck = emptyList()).copy(reserve = Card.COUNTESS)
        val next = GameEngine.apply(game, PlayCard(0, 1))
        assertEquals(1, next.result!!.spyBonusPlayerId)
        assertEquals(2, next.players[1].score)
    }

    @Test fun `spy bonus alone can finish match and simultaneous winners supported`() {
        val game = state(listOf(listOf(Card.GUARD, Card.PRIEST), listOf(Card.PRINCESS)), deck = emptyList(), discards = mapOf(0 to listOf(Card.SPY)), scores = mapOf(0 to 5, 1 to 5))
        val next = GameEngine.apply(game, PlayCard(0, 1, Card.BARON))
        assertEquals(Phase.GAME_OVER, next.phase)
        assertEquals(listOf(0, 1), next.gameWinners)
        assertTrue(GameEngine.legalActions(next).isEmpty())
    }

    @Test fun `victory thresholds follow this edition`() {
        assertEquals(listOf(6, 5, 4, 3, 3), (2..6).map { GameConfig(it).pointsToWin })
    }

    @Test fun `next round preserves scores resets hands and is started by previous winner`() {
        val over = GameEngine.apply(state(listOf(listOf(Card.PRINCESS, Card.KING), listOf(Card.SPY))), PlayCard(0))
        val next = GameEngine.nextRound(over)
        assertEquals(2, next.roundNumber)
        assertEquals(1, next.currentPlayer)
        assertEquals(listOf(0, 1), next.players.map { it.score })
        assertTrue(next.players.all { !it.eliminated && !it.protected && it.discard.isEmpty() })
        assertNull(next.result)
        assertEquals(2, next.players[1].hand.size)
    }

    @Test fun `ties choose a round winner reproducibly to start next round`() {
        val game = state(listOf(listOf(Card.GUARD, Card.PRIEST), listOf(Card.PRIEST)), deck = emptyList())
        val over = GameEngine.apply(game, PlayCard(0, 1, Card.BARON))
        val next = GameEngine.nextRound(over)
        assertTrue(next.currentPlayer in over.result!!.winners)
        assertEquals(next, GameEngine.nextRound(over))
    }

    @Test fun `knowledge invalidated when observed hand changes`() {
        val game = state(listOf(listOf(Card.PRIEST, Card.COUNTESS), listOf(Card.GUARD), listOf(Card.PRINCESS)))
        val known = GameEngine.apply(game, PlayCard(0, 2))
        assertEquals(Card.PRINCESS, GameEngine.observation(known, 0).players[2].knownCard)
        val changed = GameEngine.apply(known, PlayCard(0, 0, Card.SPY))
        assertEquals(2, changed.currentPlayer)
        assertNull(GameEngine.observation(changed, 0).players[2].knownCard)
    }

    @Test fun `observation contains no seed deck reserve or unobserved opponent identity`() {
        val original = state(listOf(listOf(Card.PRIEST, Card.COUNTESS), listOf(Card.GUARD), listOf(Card.PRINCESS)))
        val alternative = original.copy(
            config = original.config.copy(seed = 999999), randomState = 200,
            deck = original.deck.reversed(), reserve = Card.KING,
            players = original.players.map { if (it.id == 2) it.copy(hand = listOf(Card.SPY)) else it },
        )
        assertEquals(GameEngine.observation(original, 0), GameEngine.observation(alternative, 0))
        assertEquals(0L, GameEngine.observation(original, 0).config.seed)
        assertTrue(GameEngine.observation(original, 1).legalActions.isEmpty())
    }

    @Test fun `invalid actions rejected and immutable original untouched`() {
        val game = state(listOf(listOf(Card.GUARD, Card.PRIEST), listOf(Card.PRINCESS)))
        val snapshot = game.copy()
        for (action in listOf(PlayCard(5), PlayCard(0, 0, Card.PRINCESS), PlayCard(0, 1, Card.GUARD), ResolveChancellor(0, listOf(1)))) {
            try { GameEngine.apply(game, action); fail("Expected invalid action: $action") } catch (_: IllegalArgumentException) { }
            assertEquals(snapshot, game)
        }
    }

    @Test fun `save serialization preserves state and deterministic future`() {
        val game = GameEngine.newGame(GameConfig(5, seed = 76543))
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { output -> output.writeObject(game) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as GameState }
        assertEquals(game, restored)
        val action = GameEngine.legalActions(game).first()
        assertEquals(GameEngine.apply(game, action), GameEngine.apply(restored, action))
    }

    @Test fun `random legal play conserves all cards and completes matches across player counts`() {
        for (count in 2..6) for (seed in 1L..25L) {
            var game = GameEngine.newGame(GameConfig(count, seed = seed))
            val random = kotlin.random.Random(seed)
            var steps = 0
            while (game.phase != Phase.GAME_OVER && steps++ < 1500) {
                val actualCards = game.deck + listOfNotNull(game.reserve) + game.faceUpRemoved + game.players.flatMap { it.hand + it.discard }
                assertEquals("count=$count seed=$seed step=$steps", Card.fullDeck().groupingBy { it }.eachCount(), actualCards.groupingBy { it }.eachCount())
                game = if (game.phase == Phase.ROUND_OVER) GameEngine.nextRound(game) else {
                    val actions = GameEngine.legalActions(game)
                    assertTrue(actions.isNotEmpty())
                    GameEngine.apply(game, actions[random.nextInt(actions.size)])
                }
            }
            assertEquals("count=$count seed=$seed", Phase.GAME_OVER, game.phase)
        }
    }
}
