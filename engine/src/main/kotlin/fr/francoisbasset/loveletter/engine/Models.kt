package fr.francoisbasset.loveletter.engine

import java.io.Serializable

/** French Z-Man Games 2019 edition, 21 character cards, two to six players. */
enum class Card(val frenchName: String, val value: Int, val copies: Int, val summary: String, val explanation: String) : Serializable {
    SPY("Espionne", 0, 2, "Peut rapporter un pion Faveur supplémentaire.", "À la fin de la manche, si vous êtes encore en jeu et le seul joueur encore en jeu à avoir joué ou défaussé au moins une Espionne, gagnez un pion Faveur supplémentaire. Deux Espionnes ne donnent qu’un seul bonus."),
    GUARD("Garde", 1, 6, "Devinez la carte d’un autre joueur, sauf Garde.", "Choisissez un autre joueur non protégé et annoncez un personnage autre que le Garde. Si sa carte correspond, ce joueur est éliminé. Sinon, sa carte reste secrète."),
    PRIEST("Prêtre", 2, 2, "Regardez secrètement la main d’un adversaire.", "Choisissez un autre joueur non protégé et regardez sa carte sans la montrer aux autres joueurs."),
    BARON("Baron", 3, 2, "Comparez secrètement vos cartes.", "Choisissez un autre joueur non protégé. Comparez les valeurs de vos cartes restantes : le joueur qui a la plus faible valeur est éliminé. En cas d’égalité, personne n’est éliminé."),
    HANDMAID("Servante", 4, 2, "Vous êtes protégé jusqu’à votre prochain tour.", "Jusqu’au début de votre prochain tour, les effets des cartes des autres joueurs ne peuvent pas vous cibler."),
    PRINCE("Prince", 5, 2, "Un joueur défausse sa carte, puis en pioche une.", "Choisissez un joueur non protégé, vous compris. Il défausse sa main sans en appliquer l’effet, sauf la Princesse qui l’élimine. Sinon il pioche une carte, ou prend la carte écartée si la pioche est vide. Si tous les autres joueurs sont protégés, vous devez vous choisir."),
    CHANCELLOR("Chancelier", 6, 2, "Piochez deux cartes, gardez-en une, remettez les autres au fond.", "Piochez jusqu’à deux cartes. Gardez une carte parmi celles de votre main et placez les autres au fond de la pioche dans l’ordre choisi, sans les révéler. Il ne s’agit pas de défausses. La Comtesse n’impose aucun choix pendant cet effet."),
    KING("Roi", 7, 1, "Échangez votre main avec celle d’un adversaire.", "Choisissez un autre joueur non protégé et échangez vos cartes restantes sans les montrer aux autres joueurs."),
    COUNTESS("Comtesse", 8, 1, "Doit être jouée avec le Roi ou un Prince en main.", "Si votre main contient la Comtesse et le Roi ou un Prince au moment de jouer, vous devez jouer la Comtesse. Vous pouvez aussi la jouer librement. N’annoncez pas l’autre carte de votre main."),
    PRINCESS("Princesse", 9, 1, "Vous êtes éliminé si vous la jouez ou la défaussez.", "Dès que vous jouez ou défaussez la Princesse, vous êtes éliminé de la manche. La remettre au fond de la pioche avec le Chancelier n’est pas une défausse.");

    companion object { fun fullDeck(): List<Card> = entries.flatMap { card -> List(card.copies) { card } } }
}

enum class Difficulty : Serializable { EASY, NORMAL, HARD }
enum class Phase : Serializable { TURN, CHANCELLOR, ROUND_OVER, GAME_OVER }

data class GameConfig(val playerCount: Int = 4, val difficulty: Difficulty = Difficulty.NORMAL, val seed: Long = System.currentTimeMillis(), val firstPlayer: Int = 0) : Serializable {
    init { require(playerCount in 2..6); require(firstPlayer in 0 until playerCount) }
    val pointsToWin: Int get() = when (playerCount) { 2 -> 6; 3 -> 5; 4 -> 4; else -> 3 }
}

sealed interface GameAction : Serializable
data class PlayCard(val cardIndex: Int, val targetId: Int? = null, val guess: Card? = null) : GameAction
/** returnOrder lists remaining hand indices in their new top-to-bottom order at the bottom of the deck. */
data class ResolveChancellor(val keepIndex: Int, val returnOrder: List<Int>) : GameAction

data class PlayerState(val id: Int, val name: String, val hand: List<Card> = emptyList(), val discard: List<Card> = emptyList(), val protected: Boolean = false, val eliminated: Boolean = false, val score: Int = 0) : Serializable
data class CardKnowledge(val viewerId: Int, val subjectId: Int, val card: Card) : Serializable
enum class EventType : Serializable { ROUND_START, DRAW, PLAY, GUESS, REVEAL, COMPARE, PROTECTION, DISCARD, ELIMINATION, EXCHANGE, CHANCELLOR, ROUND_END, SPY_BONUS, GAME_END }
data class GameEvent(val message: String, val roundNumber: Int, val type: EventType, val actorId: Int? = null, val targetId: Int? = null, val card: Card? = null, val audience: Set<Int>? = null, val success: Boolean? = null) : Serializable
data class RoundResult(val winners: List<Int>, val spyBonusPlayerId: Int?, val pointsAwarded: Map<Int, Int>, val revealedHands: Map<Int, Card>, val reason: String) : Serializable

data class GameState(
    val config: GameConfig, val players: List<PlayerState>, val currentPlayer: Int,
    val roundNumber: Int, val deck: List<Card>, val reserve: Card?, val faceUpRemoved: List<Card>,
    val phase: Phase = Phase.TURN, val result: RoundResult? = null,
    val gameWinners: List<Int> = emptyList(), val history: List<GameEvent> = emptyList(),
    val knowledge: List<CardKnowledge> = emptyList(), val randomState: Long = config.seed,
) : Serializable

data class PublicPlayer(val id: Int, val name: String, val handCount: Int, val discard: List<Card>, val protected: Boolean, val eliminated: Boolean, val score: Int, val knownCard: Card? = null) : Serializable

/** This is the ONLY information supplied to a bot or rendered by the human UI. */
data class PlayerObservation(
    val playerId: Int, val config: GameConfig, val players: List<PublicPlayer>,
    val hand: List<Card>, val currentPlayer: Int, val roundNumber: Int, val deckCount: Int,
    val faceUpRemoved: List<Card>, val phase: Phase, val legalActions: List<GameAction>,
    val result: RoundResult?, val gameWinners: List<Int>, val history: List<GameEvent>,
) : Serializable {
    val isMyTurn: Boolean get() = currentPlayer == playerId && phase in listOf(Phase.TURN, Phase.CHANCELLOR)
    val pointsToWin: Int get() = config.pointsToWin
    val privateEvents: List<GameEvent> get() = history.filter { it.audience != null }
}
