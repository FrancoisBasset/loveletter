package fr.francoisbasset.loveletter.engine

import fr.francoisbasset.loveletter.engine.ai.BotAi
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/** Independent properties over complete matches, not fixtures for individual card effects. */
class EngineInvariantTest {
    private val officialCounts = mapOf(
        Card.SPY to 2, Card.GUARD to 6, Card.PRIEST to 2, Card.BARON to 2,
        Card.HANDMAID to 2, Card.PRINCE to 2, Card.CHANCELLOR to 2,
        Card.KING to 1, Card.COUNTESS to 1, Card.PRINCESS to 1,
    )
    private val targeted = setOf(Card.GUARD, Card.PRIEST, Card.BARON, Card.PRINCE, Card.KING)

    private fun roundTrip(state: GameState): GameState {
        val bytes = ByteArrayOutputStream().also { stream ->
            ObjectOutputStream(stream).use { it.writeObject(state) }
        }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as GameState }
    }

    private fun checkState(state: GameState, context: String) {
        val cards = state.deck + listOfNotNull(state.reserve) + state.faceUpRemoved +
            state.players.flatMap { it.hand + it.discard }
        assertEquals(context, officialCounts, cards.groupingBy { it }.eachCount())
        assertEquals(context, (state.players.indices).toList(), state.players.map { it.id })
        assertTrue(context, state.players.any { !it.eliminated })
        assertTrue(context, state.players.filter { it.eliminated }.all { it.hand.isEmpty() && !it.protected })
        assertTrue(context, state.players.all { it.score >= 0 })

        val active = state.phase == Phase.TURN || state.phase == Phase.CHANCELLOR
        if (active) {
            val actor = state.players[state.currentPlayer]
            assertFalse(context, actor.eliminated)
            assertFalse(context, actor.protected)
            if (state.phase == Phase.TURN) assertEquals(context, 2, actor.hand.size)
            else assertTrue(context, actor.hand.size in 2..3)
            assertTrue(context, state.players.filter { !it.eliminated && it.id != actor.id }.all { it.hand.size == 1 })
            assertTrue(context, GameEngine.legalActions(state).isNotEmpty())
            assertNull(context, state.result)
        } else {
            assertTrue(context, state.players.filterNot { it.eliminated }.all { it.hand.size == 1 })
            assertTrue(context, GameEngine.legalActions(state).isEmpty())
            val result = requireNotNull(state.result)
            val alive = state.players.filterNot { it.eliminated }
            val maximum = alive.maxOf { it.hand.single().value }
            assertEquals(context, alive.filter { it.hand.single().value == maximum }.map { it.id }, result.winners)
            val spies = alive.filter { Card.SPY in it.discard }
            assertEquals(context, spies.singleOrNull()?.id, result.spyBonusPlayerId)
            assertEquals(context, alive.associate { it.id to it.hand.single() }, result.revealedHands)
            state.players.forEach { player ->
                val expected = (if (player.id in result.winners) 1 else 0) +
                    (if (result.spyBonusPlayerId == player.id) 1 else 0)
                assertEquals(context, expected, result.pointsAwarded.getValue(player.id))
            }
        }
        val achieved = state.players.filter { it.score >= state.config.pointsToWin }.map { it.id }
        assertEquals(context, achieved, state.gameWinners)
        assertEquals(context, achieved.isNotEmpty(), state.phase == Phase.GAME_OVER)

        for (viewer in state.players.indices) {
            val view = GameEngine.observation(state, viewer)
            assertEquals(context, 0L, view.config.seed)
            assertEquals(context, state.players[viewer].hand, view.hand)
            assertEquals(context, state.history.filter { it.audience == null || viewer in it.audience }, view.history)
            assertTrue(context, view.privateEvents.all { viewer in requireNotNull(it.audience) })
            assertEquals(context, if (viewer == state.currentPlayer) GameEngine.legalActions(state) else emptyList<GameAction>(), view.legalActions)
            view.players.forEach { player ->
                val permitted = state.result?.revealedHands?.get(player.id)
                    ?: state.knowledge.lastOrNull { it.viewerId == viewer && it.subjectId == player.id }?.card
                assertEquals(context, permitted, player.knownCard)
            }
        }
        for (action in GameEngine.legalActions(state).filterIsInstance<PlayCard>()) {
            val actor = state.players[state.currentPlayer]
            val card = actor.hand[action.cardIndex]
            if (Card.COUNTESS in actor.hand && actor.hand.any { it == Card.KING || it == Card.PRINCE }) {
                assertEquals(context, Card.COUNTESS, card)
            }
            if (action.targetId != null) {
                val target = state.players[action.targetId]
                assertFalse(context, target.eliminated)
                assertFalse(context, target.protected)
                assertTrue(context, card == Card.PRINCE || target.id != actor.id)
                if (card == Card.GUARD) {
                    assertNotNull(context, action.guess)
                    assertNotEquals(context, Card.GUARD, action.guess)
                }
            } else if (card in targeted) {
                assertFalse(context, state.players.any {
                    !it.eliminated && !it.protected && (card == Card.PRINCE || it.id != actor.id)
                })
            }
        }
    }

    @Test(timeout = 180_000) fun `all difficulties finish complete matches with invariant public views and scores`() {
        // 75 complete reproducible matches, covering every supported player count and difficulty.
        for (difficulty in Difficulty.entries) for (count in 2..6) for (seed in 2L..6L) {
            var state = GameEngine.newGame(GameConfig(count, difficulty, seed, firstPlayer = (seed % count).toInt()))
            var steps = 0
            while (state.phase != Phase.GAME_OVER && steps < 1500) {
                val context = "difficulty=$difficulty players=$count seed=$seed round=${state.roundNumber} step=$steps"
                checkState(state, context)
                val previous = state
                state = if (state.phase == Phase.ROUND_OVER) {
                    GameEngine.nextRound(state).also { next ->
                        assertTrue(context, next.currentPlayer in requireNotNull(previous.result).winners)
                        assertEquals(context, previous.players.map { it.score }, next.players.map { it.score })
                        assertEquals(context, previous.roundNumber + 1, next.roundNumber)
                    }
                } else {
                    val view = GameEngine.observation(state, state.currentPlayer)
                    val action = BotAi.chooseAction(view, difficulty, seed * 10000 + steps)
                    assertTrue(context, action in GameEngine.legalActions(state))
                    GameEngine.apply(state, action).also { next ->
                        val awards = next.result?.pointsAwarded.orEmpty()
                        next.players.forEach { player ->
                            assertEquals(context, previous.players[player.id].score + (awards[player.id] ?: 0), player.score)
                        }
                    }
                }
                steps++
            }
            checkState(state, "final difficulty=$difficulty players=$count seed=$seed")
            assertEquals("difficulty=$difficulty players=$count seed=$seed steps=$steps", Phase.GAME_OVER, state.phase)
        }
    }

    @Test fun `hidden state permutations cannot change public observation or bot decision`() {
        for (count in 2..6) for (viewer in 0 until count) {
            val original = GameEngine.newGame(GameConfig(count, seed = 718239L, firstPlayer = viewer))
            val hidden = (original.deck + listOfNotNull(original.reserve) +
                original.players.filter { it.id != viewer }.flatMap { it.hand }).reversed().iterator()
            val alternative = original.copy(
                config = original.config.copy(seed = -9328561L), randomState = 892374,
                deck = original.deck.map { hidden.next() }, reserve = hidden.next(),
                players = original.players.map { player ->
                    if (player.id == viewer) player else player.copy(hand = player.hand.map { hidden.next() })
                },
                history = original.history.map { event ->
                    if (event.audience != null && viewer !in event.audience) {
                        event.copy(message = "Un autre message secret", card = Card.PRINCESS)
                    } else event
                },
            )
            assertFalse(hidden.hasNext())
            val visible = GameEngine.observation(original, viewer)
            val sameVisible = GameEngine.observation(alternative, viewer)
            assertEquals(visible, sameVisible)
            for (difficulty in Difficulty.entries) {
                assertEquals(BotAi.chooseAction(visible, difficulty, 7), BotAi.chooseAction(sameVisible, difficulty, 7))
            }
        }
    }

    @Test fun `saving every transition retains exact future including next round shuffle`() {
        var original = GameEngine.newGame(GameConfig(4, Difficulty.NORMAL, seed = 93216))
        var resumed = roundTrip(original)
        var sawNextRound = false
        var sawChancellor = false
        var steps = 0
        while (original.phase != Phase.GAME_OVER && steps < 1500) {
            assertEquals(original, resumed)
            if (original.phase == Phase.ROUND_OVER) {
                original = GameEngine.nextRound(original)
                resumed = GameEngine.nextRound(resumed)
                sawNextRound = true
            } else {
                sawChancellor = sawChancellor || original.phase == Phase.CHANCELLOR
                val actions = GameEngine.legalActions(original)
                val action = actions[(steps * 17 + 3) % actions.size]
                original = GameEngine.apply(original, action)
                resumed = GameEngine.apply(resumed, action)
            }
            resumed = roundTrip(resumed)
            steps++
        }
        assertEquals(original, resumed)
        assertEquals(Phase.GAME_OVER, original.phase)
        assertTrue("Must exercise next-round RNG restoration", sawNextRound)
        assertTrue("Must serialize during a pending Chancellor decision", sawChancellor)
    }

    @Test fun `both threshold winners win even when spy gives one a higher total`() {
        val state = GameState(
            config = GameConfig(4, seed = 36), currentPlayer = 0, roundNumber = 4,
            players = listOf(
                PlayerState(0, "A", listOf(Card.GUARD, Card.PRINCE), discard = listOf(Card.SPY), score = 3),
                PlayerState(1, "B", listOf(Card.PRINCE), score = 3),
                PlayerState(2, "C", listOf(Card.PRIEST)),
                PlayerState(3, "D", listOf(Card.HANDMAID)),
            ), deck = emptyList(), reserve = Card.SPY, faceUpRemoved = emptyList(),
        )
        val finished = GameEngine.apply(state, PlayCard(0, 2, Card.KING))
        assertEquals(listOf(0, 1), requireNotNull(finished.result).winners)
        assertEquals(listOf(5, 4, 0, 0), finished.players.map { it.score })
        assertEquals(listOf(0, 1), finished.gameWinners)
        assertEquals(Phase.GAME_OVER, finished.phase)
    }

    @Test fun `targeting protected eliminated absent or self opponents cannot mutate a turn`() {
        val state = GameState(
            config = GameConfig(3, seed = 812), currentPlayer = 0, roundNumber = 1,
            players = listOf(
                PlayerState(0, "A", listOf(Card.KING, Card.PRIEST)),
                PlayerState(1, "B", listOf(Card.PRINCESS), protected = true),
                PlayerState(2, "C", discard = listOf(Card.GUARD), eliminated = true),
            ), deck = listOf(Card.SPY, Card.HANDMAID), reserve = Card.GUARD, faceUpRemoved = emptyList(),
        )
        val snapshot = roundTrip(state)
        assertEquals(listOf(PlayCard(0), PlayCard(1)), GameEngine.legalActions(state))
        for (cardIndex in 0..1) for (target in listOf(-1, 0, 1, 2, 3, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { GameEngine.apply(state, PlayCard(cardIndex, target)) }
            assertEquals(snapshot, state)
        }
    }

    @Test fun `rejected chancellor permutations and forbidden targets preserve serialized state`() {
        val choosing = GameState(
            config = GameConfig(3, seed = 15), currentPlayer = 0, roundNumber = 1,
            players = listOf(
                PlayerState(0, "A", listOf(Card.COUNTESS, Card.KING, Card.PRINCESS)),
                PlayerState(1, "B", listOf(Card.PRIEST), protected = true),
                PlayerState(2, "C", listOf(Card.GUARD)),
            ), deck = listOf(Card.SPY), reserve = Card.GUARD, faceUpRemoved = emptyList(), phase = Phase.CHANCELLOR,
        )
        val snapshot = roundTrip(choosing)
        for (action in listOf(
            ResolveChancellor(0, listOf(1, 1)), ResolveChancellor(0, listOf(0, 2)),
            ResolveChancellor(1, listOf(2)), ResolveChancellor(3, listOf(0, 1)),
            PlayCard(0), PlayCard(1, 1),
        )) {
            assertThrows(IllegalArgumentException::class.java) { GameEngine.apply(choosing, action) }
            assertEquals(snapshot, choosing)
        }
        // Countess is not compulsory during Chancellor selection; any of three cards can be kept.
        assertEquals(setOf(0, 1, 2), GameEngine.legalActions(choosing).filterIsInstance<ResolveChancellor>().map { it.keepIndex }.toSet())
    }
}
