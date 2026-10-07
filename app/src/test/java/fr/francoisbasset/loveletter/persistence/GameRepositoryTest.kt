package fr.francoisbasset.loveletter.persistence

import fr.francoisbasset.loveletter.engine.GameConfig
import fr.francoisbasset.loveletter.engine.GameEngine
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GameRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun emptyStorageHasNoSavedGame() {
        assertNull(GameRepository(temporary.root).load())
    }

    @Test fun completeStateAndUnreadSecretSurviveReloadAndOverwrite() {
        val repository = GameRepository(temporary.root)
        val first = GameEngine.newGame(GameConfig(seed = 812L))
        val saved = SavedGame(first, "Information secrète en attente")
        repository.save(saved)
        assertEquals(saved, GameRepository(temporary.root).load())
        val next = GameEngine.apply(first, GameEngine.legalActions(first).first())
        repository.save(SavedGame(next))
        assertEquals(next, repository.load()?.state)
        assertNull(repository.load()?.secretNotice)
        assertFalse(temporary.root.resolve("court-save-v1.tmp").exists())
    }

    @Test fun corruptStorageReportsFailureWithoutOverwritingOriginal() {
        val file = temporary.root.resolve("court-save-v1.bin")
        file.writeText("corrupted")
        assertTrue(runCatching { GameRepository(temporary.root).load() }.isFailure)
        assertEquals("corrupted", file.readText())
    }
}
