package fr.francoisbasset.loveletter.core

import kotlinx.serialization.Serializable

/** All names and quantities belong exclusively to the French Z-Man 2019 edition. */
@Serializable
enum class Card(val value: Int, val frenchName: String, val copies: Int, val effect: String, val explanation: String) {
    ESPIONNE(0, "Espionne", 2, "Peut vous rapporter une faveur à la fin de la manche.", "Si vous êtes le seul joueur encore en lice à avoir joué ou défaussé une Espionne, vous recevez une faveur supplémentaire. Deux Espionnes ne donnent qu'une faveur."),
    GARDE(1, "Garde", 6, "Devinez la carte d'un autre joueur, sauf Garde.", "Choisissez un adversaire encore en lice et non protégé. Annoncez un personnage autre que Garde : une bonne réponse l'élimine. S'il n'existe aucune cible, la carte n'a pas d'effet."),
    PRETRE(2, "Prêtre", 2, "Regardez secrètement la carte d'un adversaire.", "Choisissez un autre joueur encore en lice et non protégé. Vous seul découvrez sa carte. Cette information peut devenir périmée après un échange, une nouvelle carte ou un Chancelier."),
    BARON(3, "Baron", 2, "Comparez secrètement votre carte à celle d'un adversaire.", "Comparez votre carte conservée à celle d'un autre joueur encore en lice et non protégé. La valeur la plus faible entraîne l'élimination de son propriétaire. Une égalité n'élimine personne."),
    SERVANTE(4, "Servante", 2, "Protégez-vous des cartes des autres joueurs.", "La protection dure jusqu'au début de votre prochain tour. Les autres joueurs ne peuvent pas vous cibler. Votre propre Prince peut toujours vous cibler."),
    PRINCE(5, "Prince", 2, "Un joueur défausse sa carte et en pioche une nouvelle.", "Vous pouvez choisir un adversaire non protégé ou vous-même. La carte défaussée n'applique pas son effet, sauf la Princesse qui élimine son propriétaire. Si la pioche est vide, utilisez la carte réservée au début de la manche."),
    CHANCELIER(6, "Chancelier", 2, "Piochez deux cartes, gardez-en une et replacez les autres sous la pioche.", "Prenez jusqu'à deux cartes disponibles, puis gardez une carte parmi elles et votre carte actuelle. Remettez les autres sous la pioche dans l'ordre de votre choix. La réserve n'est pas utilisée. La Comtesse n'impose aucun choix pendant cet effet."),
    ROI(7, "Roi", 1, "Échangez votre carte avec celle d'un adversaire.", "Choisissez un autre joueur encore en lice et non protégé. Échangez votre carte conservée avec la sienne. L'échange reste secret pour les autres joueurs."),
    COMTESSE(8, "Comtesse", 1, "À jouer obligatoirement avec le Prince ou le Roi en main.", "Si vos deux cartes sont la Comtesse et le Prince, ou la Comtesse et le Roi, vous devez jouer la Comtesse. Vous pouvez également la jouer librement. Elle n'a aucun autre effet et vous ne révélez pas votre carte conservée."),
    PRINCESSE(9, "Princesse", 1, "Vous êtes éliminé si vous la jouez ou la défaussez.", "Toute défausse de la Princesse vous élimine immédiatement. Si vous possédez encore une carte, elle est aussi défaussée sans appliquer son effet. Gardez la Princesse pour sa grande valeur, en évitant le Prince.")
}

@Serializable enum class Difficulty { EASY, NORMAL, HARD }
@Serializable enum class GamePhase { PLAYING, CHANCELLOR, ROUND_OVER, GAME_OVER }
@Serializable enum class RoundEndReason { LAST_SURVIVOR, HIGHEST_CARD }

@Serializable
data class GameConfig(
    val playerCount: Int = 4,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val humanName: String = "Vous",
    val firstPlayer: Int? = 0
) {
    init {
        require(playerCount in 2..6) { "Cette édition se joue de 2 à 6 joueurs." }
        require(firstPlayer == null || firstPlayer in 0 until playerCount) { "Premier joueur invalide." }
        require(humanName.isNotBlank()) { "Le nom ne peut pas être vide." }
    }

    val victoryThreshold: Int get() = when (playerCount) { 2 -> 6; 3 -> 5; 4 -> 4; else -> 3 }
}

@Serializable
data class PlayerState(
    val id: Int,
    val name: String,
    val hand: List<Card> = emptyList(),
    val discard: List<Card> = emptyList(),
    val isProtected: Boolean = false,
    val isEliminated: Boolean = false,
    val score: Int = 0
)

@Serializable
sealed class GameAction {
    @Serializable data class Play(val card: Card, val target: Int? = null, val guess: Card? = null) : GameAction()
    @Serializable data class ChancellorChoice(val keep: Card, val bottom: List<Card>) : GameAction()
    @Serializable data object NextRound : GameAction()
}

@Serializable data class KnownCard(val observerId: Int, val targetId: Int, val card: Card)
@Serializable data class ExcludedCard(val targetId: Int, val card: Card)
@Serializable data class PrivateNote(val id: Long, val playerId: Int, val roundNumber: Int, val text: String)
@Serializable data class LogEntry(val id: Long, val roundNumber: Int, val message: String)

@Serializable
data class RoundResult(
    val winners: List<Int>,
    val spyBonusPlayer: Int?,
    val reason: RoundEndReason,
    val revealedHands: Map<Int, List<Card>>,
    val pointsAwarded: Map<Int, Int>
)

/** Private authoritative snapshot. Never pass this to the AI or render opponent hands from it. */
@Serializable
data class GameState(
    val config: GameConfig,
    val players: List<PlayerState>,
    val drawPile: List<Card>,
    val reserveCard: Card?,
    val faceUpRemoved: List<Card>,
    val currentPlayer: Int,
    val roundNumber: Int = 1,
    val phase: GamePhase = GamePhase.PLAYING,
    val result: RoundResult? = null,
    val winners: List<Int> = emptyList(),
    val randomState: Long,
    val journal: List<LogEntry> = emptyList(),
    val knownCards: List<KnownCard> = emptyList(),
    val exclusions: List<ExcludedCard> = emptyList(),
    val privateNotes: List<PrivateNote> = emptyList(),
    val nextEventId: Long = 1L
)

@Serializable
data class PlayerView(
    val id: Int,
    val name: String,
    val handSize: Int,
    val visibleHand: List<Card>?,
    val discard: List<Card>,
    val isProtected: Boolean,
    val isEliminated: Boolean,
    val score: Int
)

/** All information a seat is entitled to know; legalActions never inspect unrevealed cards. */
@Serializable
data class Observation(
    val viewerId: Int,
    val config: GameConfig,
    val players: List<PlayerView>,
    val ownHand: List<Card>,
    val knownCards: List<KnownCard>,
    val exclusions: List<ExcludedCard>,
    val privateNotes: List<PrivateNote>,
    val legalActions: List<GameAction>,
    val deckSize: Int,
    val faceUpRemoved: List<Card>,
    val currentPlayer: Int,
    val roundNumber: Int,
    val phase: GamePhase,
    val result: RoundResult?,
    val winners: List<Int>,
    val journal: List<LogEntry>
)
