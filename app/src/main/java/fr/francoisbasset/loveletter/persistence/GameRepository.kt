package fr.francoisbasset.loveletter.persistence

import fr.francoisbasset.loveletter.engine.GameState
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Private, local-only save. No network permission and no Android cloud backup. */
data class SavedGame(val state: GameState, val secretNotice: String? = null, val format: Int = 1) : Serializable

class GameRepository(private val directory: File) {
    private val saveFile get() = File(directory, "court-save-v1.bin")

    fun load(): SavedGame? {
        if (!saveFile.exists()) return null
        return ObjectInputStream(saveFile.inputStream().buffered()).use {
            val saved = it.readObject() as SavedGame
            require(saved.format == 1) { "Version de sauvegarde incompatible." }
            saved
        }
    }

    @Synchronized
    fun save(saved: SavedGame) {
        directory.mkdirs()
        val temporary = File(directory, "court-save-v1.tmp")
        ObjectOutputStream(temporary.outputStream().buffered()).use { it.writeObject(saved) }
        Files.move(temporary.toPath(), saveFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
