package fr.francoisbasset.loveletter.persistence

import fr.francoisbasset.loveletter.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun resumesUnfinishedChancellorWithoutDrawingAgainOrChangingRandomSequence() {
        val initial = (0L..500L).map { GameEngine.newGame(GameConfig(playerCount = 3), it) }
            .first { state -> GameAction.Play(Card.CHANCELIER) in GameEngine.legalActions(state) }
        val pending = GameEngine.apply(initial, GameAction.Play(Card.CHANCELIER))
        assertEquals(GamePhase.CHANCELLOR, pending.phase)
        val payload = SavedSession(game = pending, aiSeed = 87234, aiDecisionCount = 8, acknowledgedPrivateNote = 12,
            playerCount = 3, difficulty = "HARD", humanName = "Test", firstPlayer = null)
        val store = SessionStore(temporary.newFolder())
        store.save(payload)
        val restored = requireNotNull(store.load())
        assertEquals(payload, restored)
        val choice = GameEngine.legalActions(pending).filterIsInstance<GameAction.ChancellorChoice>().last()
        assertEquals(GameEngine.apply(pending, choice), GameEngine.apply(requireNotNull(restored.game), choice))
    }

    @Test fun interruptedTemporaryWriteDoesNotReplaceTheLastCommittedSnapshot() {
        val directory = temporary.newFolder()
        val store = SessionStore(directory)
        val previous = SavedSession(game = GameEngine.newGame(GameConfig(playerCount = 2), 16L))
        store.save(previous)
        java.io.File(directory, "session-v1.tmp").writeText("{truncated")
        assertEquals(previous, store.load())
        val newest = previous.copy(aiDecisionCount = 2, humanName = "Une autre lettre")
        store.save(newest)
        assertEquals(newest, store.load())
    }

    @Test fun rejectsUnknownVersionInsteadOfSilentlyLosingTheGame() {
        val future = SessionCodec.encode(SavedSession()).replace("\"formatVersion\":1", "\"formatVersion\":2")
        assertThrows(IllegalArgumentException::class.java) { SessionCodec.decode(future) }
    }

    @Test fun rejectsInconsistentSettings() {
        assertThrows(IllegalArgumentException::class.java) { SessionCodec.decode(SessionCodec.encode(SavedSession(playerCount = 6, firstPlayer = 6))) }
        assertThrows(IllegalArgumentException::class.java) { SessionCodec.decode(SessionCodec.encode(SavedSession(difficulty = "IMPOSSIBLE"))) }
    }
}
