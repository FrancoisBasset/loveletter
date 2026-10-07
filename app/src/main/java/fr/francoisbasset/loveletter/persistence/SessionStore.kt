package fr.francoisbasset.loveletter.persistence

import fr.francoisbasset.loveletter.core.GameState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class SavedSession(
    val formatVersion: Int = 1,
    val game: GameState? = null,
    val acknowledgedPrivateNote: Long = 0,
    val aiDecisionCount: Long = 0,
    val aiSeed: Long = 0,
    val playerCount: Int = 3,
    val difficulty: String = "NORMAL",
    val humanName: String = "François",
    val firstPlayer: Int? = 0
)

object SessionCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(session: SavedSession): String = json.encodeToString(session)
    fun decode(text: String): SavedSession {
        val session = json.decodeFromString<SavedSession>(text)
        require(session.formatVersion == 1) { "Version de sauvegarde non prise en charge : ${session.formatVersion}" }
        require(session.playerCount in 2..6) { "Nombre de joueurs invalide dans la sauvegarde." }
        require(session.difficulty in listOf("EASY", "NORMAL", "HARD")) { "Difficulté invalide dans la sauvegarde." }
        require(session.firstPlayer == null || session.firstPlayer in 0 until session.playerCount) { "Premier joueur invalide dans la sauvegarde." }
        require(session.aiDecisionCount >= 0) { "Compteur IA invalide dans la sauvegarde." }
        return session
    }
}

/** Private application storage. A synced temporary file is atomically promoted. */
class SessionStore(directory: File) {
    private val file = File(directory, "session-v1.json")
    private val temporary = File(directory, "session-v1.tmp")

    fun load(): SavedSession? = if (file.exists()) SessionCodec.decode(file.readText()) else null

    fun save(session: SavedSession) {
        file.parentFile?.mkdirs()
        FileOutputStream(temporary).use { stream ->
            stream.write(SessionCodec.encode(session).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
