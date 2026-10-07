package fr.francoisbasset.loveletter.ui

import fr.francoisbasset.loveletter.core.Card
import fr.francoisbasset.loveletter.core.GamePhase
import kotlinx.serialization.Serializable

enum class Destination { HOME, SETUP, TABLE, RULES, CARDS }
@Serializable
enum class GamePace(val label: String, val explanation: String, val thinkingDelayMs: Long, val readingDelayMs: Long?) {
    GUIDED("À mon rythme", "Chaque action reste visible jusqu'à votre appui sur Continuer.", 1800L, null),
    SLOW("Lent", "Les adversaires réfléchissent, puis chaque action reste visible cinq secondes.", 1800L, 5000L),
    NORMAL("Fluide", "Une seconde de réflexion et deux secondes et demie pour lire chaque action.", 1000L, 2500L)
}
enum class AiLevel(val label: String, val explanation: String) {
    EASY("Facile", "Des décisions simples et variées, pour découvrir le jeu."),
    NORMAL("Normale", "Observe les défausses et exploite les informations connues."),
    HARD("Difficile", "Estime les cartes restantes et choisit ses risques.")
}

data class CardInfo(
    val value: Int, val name: String, val copies: Int, val subtitle: String,
    val effect: String, val explanation: String
)

/** Original text summaries. No publisher artwork or card facsimile is embedded. */
private val Subtitles = listOf("Un discret avantage", "Devinez un secret", "Un regard privilégié", "Le duel des influences", "À l'abri des regards", "Une nouvelle lettre", "Choisissez votre destin", "Échangez les secrets", "Une obligation de cour", "Le risque suprême")
val CardCatalogue = Card.entries.map { card ->
    CardInfo(card.value, card.frenchName, card.copies, Subtitles[card.value], card.effect, card.explanation)
}
fun cardInfo(value: Int) = CardCatalogue.first { it.value == value }

data class UiPlayer(
    val id: Int, val name: String, val score: Int, val alive: Boolean,
    val isProtected: Boolean, val discards: List<Int>, val revealedHand: Int? = null,
    val human: Boolean = false, val active: Boolean = false, val handSize: Int = 1
)
data class UiMove(
    val id: String, val cardValue: Int, val targetId: Int? = null,
    val guessValue: Int? = null, val label: String = ""
)
data class UiPrivateNotice(val title: String, val message: String)
data class UiChancellor(
    val cards: List<Int>, val actions: List<UiChancellorChoice>
)
data class UiChancellorChoice(val id: String, val keepIndex: Int, val bottomIndices: List<Int>)
/** Persisted presentation of one committed action, containing public information only. */
@Serializable
data class UiPlayback(
    val id: Long,
    val actorId: Int,
    val actorName: String,
    val cardValue: Int? = null,
    val targetId: Int? = null,
    val targetName: String? = null,
    val guessValue: Int? = null,
    val messages: List<String> = emptyList(),
    val eliminatedIds: List<Int> = emptyList(),
    val phase: GamePhase,
    val title: String,
    val summary: String
)
data class UiTable(
    val round: Int, val deckCount: Int, val goal: Int, val players: List<UiPlayer>,
    val hand: List<Int>, val moves: List<UiMove>, val history: List<String>,
    val turnLabel: String, val humanTurn: Boolean, val thinking: Boolean,
    val roundFinished: Boolean, val matchFinished: Boolean, val resultTitle: String?,
    val resultDescription: String?, val chancellor: UiChancellor?,
    val notice: UiPrivateNotice?, val exposedCards: List<Int>,
    val difficulty: AiLevel, val knownCards: List<String> = emptyList(),
    val playback: UiPlayback? = null, val paused: Boolean = false,
    val lastPlayback: UiPlayback? = null
)
data class AppUiState(
    val destination: Destination = Destination.HOME,
    val hasSavedGame: Boolean = false,
    val playerCount: Int = 3,
    val difficulty: AiLevel = AiLevel.NORMAL,
    val pace: GamePace = GamePace.GUIDED,
    val humanName: String = "François",
    val firstPlayer: Int? = 0,
    val table: UiTable? = null,
    val selectedCard: Int? = null,
    val selectedTarget: Int? = null,
    val error: String? = null,
    val storageWarning: String? = null,
    val loading: Boolean = true
)
