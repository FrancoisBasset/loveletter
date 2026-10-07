package fr.francoisbasset.loveletter.core

/** Immutable, deterministic rules engine for the French 21-card Z-Man 2019 edition. */
object GameEngine {
    fun fullDeck(): List<Card> = Card.entries.flatMap { card -> List(card.copies) { card } }

    fun newGame(config: GameConfig = GameConfig(), seed: Long = System.nanoTime()): GameState {
        val random = DeterministicRandom(seed)
        val firstPlayer = config.firstPlayer ?: random.nextInt(config.playerCount)
        val players = List(config.playerCount) { id ->
            PlayerState(id, if (id == 0) config.humanName else listOf("Éloïse", "Arthur", "Marguerite", "Hugues", "Isabeau")[id - 1])
        }
        return startRound(
            GameState(config, players, emptyList(), null, emptyList(), firstPlayer, randomState = random.state),
            firstPlayer
        )
    }

    /** Does not look at the contents of the draw pile to generate an action. */
    fun legalActions(state: GameState): List<GameAction> = when (state.phase) {
        GamePhase.GAME_OVER -> emptyList()
        GamePhase.ROUND_OVER -> listOf(GameAction.NextRound)
        GamePhase.CHANCELLOR -> chancellorActions(state.players[state.currentPlayer].hand)
        GamePhase.PLAYING -> {
            val actor = state.players[state.currentPlayer]
            if (actor.isEliminated || actor.hand.size != 2) emptyList()
            else {
                val forcedCountess = Card.COMTESSE in actor.hand && actor.hand.any { it == Card.PRINCE || it == Card.ROI }
                val cards = if (forcedCountess) listOf(Card.COMTESSE) else actor.hand.distinct()
                cards.flatMap { card ->
                    when (card) {
                        Card.GARDE, Card.PRETRE, Card.BARON, Card.ROI -> {
                            val targets = state.players.filter { it.id != actor.id && !it.isEliminated && !it.isProtected }
                            if (targets.isEmpty()) listOf(GameAction.Play(card))
                            else targets.flatMap { target ->
                                if (card == Card.GARDE) Card.entries.filter { it != Card.GARDE }.map { GameAction.Play(card, target.id, it) }
                                else listOf(GameAction.Play(card, target.id))
                            }
                        }
                        Card.PRINCE -> state.players.filter { !it.isEliminated && (it.id == actor.id || !it.isProtected) }
                            .map { GameAction.Play(card, it.id) }
                        else -> listOf(GameAction.Play(card))
                    }
                }
            }
        }
    }

    fun apply(state: GameState, action: GameAction): GameState {
        require(action in legalActions(state)) { "Action interdite par les règles : $action" }
        return when (action) {
            is GameAction.Play -> play(state, action)
            is GameAction.ChancellorChoice -> {
                val actor = state.players[state.currentPlayer]
                val chosen = updatePlayer(state, actor.copy(hand = listOf(action.keep)))
                    .copy(drawPile = state.drawPile + action.bottom, phase = GamePhase.PLAYING)
                finishTurn(log(chosen, "${actor.name} conserve une carte et remet ${action.bottom.size} carte(s) sous la pioche."))
            }
            GameAction.NextRound -> {
                val lastWinners = requireNotNull(state.result).winners
                val random = DeterministicRandom(state.randomState)
                val starter = if (lastWinners.size == 1) lastWinners.single() else lastWinners[random.nextInt(lastWinners.size)]
                startRound(state.copy(roundNumber = state.roundNumber + 1, randomState = random.state), starter)
            }
        }
    }

    fun observe(state: GameState, viewerId: Int): Observation {
        require(viewerId in state.players.indices) { "Siège inconnu." }
        val revealed = state.phase == GamePhase.ROUND_OVER || state.phase == GamePhase.GAME_OVER
        return Observation(
            viewerId = viewerId,
            config = state.config,
            players = state.players.map {
                PlayerView(it.id, it.name, it.hand.size, if (revealed || it.id == viewerId) it.hand else null,
                    it.discard, it.isProtected, it.isEliminated, it.score)
            },
            ownHand = state.players[viewerId].hand,
            knownCards = state.knownCards.filter { it.observerId == viewerId },
            exclusions = state.exclusions,
            privateNotes = state.privateNotes.filter { it.playerId == viewerId },
            legalActions = if (state.currentPlayer == viewerId || state.phase == GamePhase.ROUND_OVER) legalActions(state) else emptyList(),
            deckSize = state.drawPile.size,
            faceUpRemoved = state.faceUpRemoved,
            currentPlayer = state.currentPlayer,
            roundNumber = state.roundNumber,
            phase = state.phase,
            result = state.result,
            winners = state.winners,
            journal = state.journal
        )
    }

    private fun startRound(previous: GameState, starter: Int): GameState {
        val random = DeterministicRandom(previous.randomState)
        val deck = fullDeck().toMutableList()
        for (i in deck.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val old = deck[i]
            deck[i] = deck[j]
            deck[j] = old
        }
        val reserve = deck.removeAt(0)
        val removed = if (previous.config.playerCount == 2) List(3) { deck.removeAt(0) } else emptyList()
        val players = previous.players.map { it.copy(hand = listOf(deck.removeAt(0)), discard = emptyList(), isProtected = false, isEliminated = false) }
        val state = previous.copy(
            players = players, drawPile = deck.toList(), reserveCard = reserve, faceUpRemoved = removed,
            currentPlayer = starter, phase = GamePhase.PLAYING, result = null, winners = emptyList(),
            randomState = random.state, knownCards = emptyList(), exclusions = emptyList(), privateNotes = emptyList()
        )
        return beginTurn(log(state, "Manche ${state.roundNumber} : ${state.players[starter].name} commence."), starter)
    }

    private fun beginTurn(state: GameState, actorId: Int): GameState {
        check(state.drawPile.isNotEmpty()) { "Une manche terminée ne doit pas commencer un nouveau tour." }
        val actor = state.players[actorId]
        check(!actor.isEliminated && actor.hand.size == 1)
        return updatePlayer(state, actor.copy(hand = actor.hand + state.drawPile.first(), isProtected = false))
            .copy(drawPile = state.drawPile.drop(1), currentPlayer = actorId, phase = GamePhase.PLAYING)
    }

    private fun play(original: GameState, action: GameAction.Play): GameState {
        val actorId = original.currentPlayer
        val actor = original.players[actorId]
        var state = original.copy(
            knownCards = original.knownCards.filterNot { it.targetId == actorId && it.card == action.card },
            exclusions = original.exclusions.filterNot { it.targetId == actorId }
        )
        state = updatePlayer(state, actor.copy(hand = removeOnce(actor.hand, action.card), discard = actor.discard + action.card))
        state = log(state, "${actor.name} joue ${action.card.frenchName}.")
        val targetId = action.target
        when (action.card) {
            Card.ESPIONNE, Card.COMTESSE -> Unit
            Card.PRINCESSE -> state = eliminate(state, actorId, "a défaussé la Princesse")
            Card.SERVANTE -> state = log(updatePlayer(state, state.players[actorId].copy(isProtected = true)), "${actor.name} est protégé jusqu'à son prochain tour.")
            Card.GARDE -> if (targetId != null) {
                val target = state.players[targetId]
                val guess = requireNotNull(action.guess)
                state = log(state, "${actor.name} annonce ${guess.frenchName} pour ${target.name}.")
                if (target.hand.single() == guess) state = eliminate(state, targetId, "a été reconnu par le Garde")
                else state = log(state.copy(exclusions = (state.exclusions + ExcludedCard(targetId, guess)).distinct()), "La réponse est incorrecte.")
            } else state = log(state, "Aucun adversaire ne peut être ciblé.")
            Card.PRETRE -> if (targetId != null) {
                val target = state.players[targetId]
                state = know(state, actorId, targetId, target.hand.single())
                state = note(state, actorId, "Le Prêtre révèle : ${target.name} possède ${target.hand.single().frenchName} (${target.hand.single().value}).")
                state = log(state, "${actor.name} regarde secrètement la carte de ${target.name}.")
            } else state = log(state, "Aucun adversaire ne peut être ciblé.")
            Card.BARON -> if (targetId != null) {
                val own = state.players[actorId].hand.single()
                val other = state.players[targetId].hand.single()
                state = know(know(state, actorId, targetId, other), targetId, actorId, own)
                state = note(state, actorId, "Baron : votre ${own.frenchName} (${own.value}) contre ${state.players[targetId].name} : ${other.frenchName} (${other.value}).")
                state = note(state, targetId, "Baron : votre ${other.frenchName} (${other.value}) contre ${actor.name} : ${own.frenchName} (${own.value}).")
                state = log(state, "${actor.name} et ${state.players[targetId].name} comparent secrètement leurs cartes.")
                state = when {
                    own.value < other.value -> eliminate(state, actorId, "a perdu la comparaison du Baron")
                    own.value > other.value -> eliminate(state, targetId, "a perdu la comparaison du Baron")
                    else -> log(state, "Les valeurs sont égales : personne n'est éliminé.")
                }
            } else state = log(state, "Aucun adversaire ne peut être ciblé.")
            Card.PRINCE -> {
                val target = state.players[requireNotNull(targetId)]
                val discarded = target.hand.single()
                state = invalidate(state, target.id)
                state = updatePlayer(state, state.players[target.id].copy(hand = emptyList(), discard = target.discard + discarded))
                state = log(state, "Le Prince fait défausser ${discarded.frenchName} à ${target.name}.")
                if (discarded == Card.PRINCESSE) state = eliminate(state, target.id, "a défaussé la Princesse")
                else {
                    val card = if (state.drawPile.isNotEmpty()) state.drawPile.first() else requireNotNull(state.reserveCard) { "La réserve a déjà été utilisée." }
                    state = if (state.drawPile.isNotEmpty()) state.copy(drawPile = state.drawPile.drop(1)) else state.copy(reserveCard = null)
                    state = updatePlayer(state, state.players[target.id].copy(hand = listOf(card)))
                    state = log(state, "${target.name} reçoit une nouvelle carte secrète.")
                }
            }
            Card.CHANCELIER -> {
                // Clearing knowledge is essential: the chosen card is private, including keeping the old one.
                state = invalidate(state, actorId)
                val count = minOf(2, state.drawPile.size)
                if (count > 0) {
                    state = updatePlayer(state, state.players[actorId].copy(hand = state.players[actorId].hand + state.drawPile.take(count)))
                        .copy(drawPile = state.drawPile.drop(count), phase = GamePhase.CHANCELLOR)
                    return log(state, "${actor.name} pioche $count carte(s) pour le Chancelier et choisit secrètement laquelle garder.")
                }
                state = log(state, "La pioche est vide : le Chancelier ne pioche aucune carte.")
            }
            Card.ROI -> if (targetId != null) {
                val own = state.players[actorId].hand.single()
                val other = state.players[targetId].hand.single()
                state = invalidate(invalidate(state, actorId), targetId)
                state = updatePlayer(updatePlayer(state, state.players[actorId].copy(hand = listOf(other))), state.players[targetId].copy(hand = listOf(own)))
                // Each participant knows where the card they gave away went; spectators do not.
                state = know(know(state, actorId, targetId, own), targetId, actorId, other)
                state = note(state, actorId, "Roi : vous donnez ${own.frenchName} et recevez ${other.frenchName} de ${state.players[targetId].name}.")
                state = note(state, targetId, "Roi : vous donnez ${other.frenchName} et recevez ${own.frenchName} de ${actor.name}.")
                state = log(state, "${actor.name} échange secrètement sa carte avec ${state.players[targetId].name}.")
            } else state = log(state, "Aucun adversaire ne peut être ciblé.")
        }
        return finishTurn(state)
    }

    private fun finishTurn(state: GameState): GameState {
        val active = state.players.filterNot { it.isEliminated }
        if (active.size <= 1) return finishRound(state, RoundEndReason.LAST_SURVIVOR)
        if (state.drawPile.isEmpty()) return finishRound(state, RoundEndReason.HIGHEST_CARD)
        val next = (1..state.players.size).map { (state.currentPlayer + it) % state.players.size }.first { !state.players[it].isEliminated }
        return beginTurn(state, next)
    }

    private fun finishRound(state: GameState, reason: RoundEndReason): GameState {
        val active = state.players.filterNot { it.isEliminated }
        check(active.isNotEmpty()) { "Une manche doit conserver au moins un joueur." }
        val winningValue = active.maxOf { it.hand.single().value }
        val roundWinners = active.filter { it.hand.single().value == winningValue }.map { it.id }
        val spies = active.filter { Card.ESPIONNE in it.discard }
        val spy = spies.singleOrNull()?.id
        val awarded = state.players.associate { it.id to ((if (it.id in roundWinners) 1 else 0) + (if (it.id == spy) 1 else 0)) }
        val players = state.players.map { it.copy(score = it.score + awarded.getValue(it.id)) }
        val matchWinners = players.filter { it.score >= state.config.victoryThreshold }.map { it.id }
        val result = RoundResult(roundWinners, spy, reason, state.players.associate { it.id to it.hand }, awarded)
        var finished = state.copy(players = players, result = result, winners = matchWinners,
            phase = if (matchWinners.isEmpty()) GamePhase.ROUND_OVER else GamePhase.GAME_OVER)
        finished = log(finished, "Fin de manche : ${roundWinners.joinToString { state.players[it].name }} gagne(nt) une faveur.")
        if (spy != null) finished = log(finished, "${state.players[spy].name} reçoit une faveur supplémentaire grâce à l'Espionne.")
        if (matchWinners.isNotEmpty()) finished = log(finished, "Victoire finale : ${matchWinners.joinToString { state.players[it].name }}.")
        return finished
    }

    private fun eliminate(state: GameState, playerId: Int, reason: String): GameState {
        val player = state.players[playerId]
        var result = updatePlayer(invalidate(state, playerId), player.copy(hand = emptyList(), discard = player.discard + player.hand, isEliminated = true, isProtected = false))
        if (player.hand.isNotEmpty()) result = log(result, "${player.name} défausse ${player.hand.joinToString { it.frenchName }}.")
        return log(result, "${player.name} est éliminé : $reason.")
    }

    private fun invalidate(state: GameState, targetId: Int): GameState = state.copy(
        knownCards = state.knownCards.filterNot { it.targetId == targetId },
        exclusions = state.exclusions.filterNot { it.targetId == targetId }
    )

    private fun know(state: GameState, observer: Int, target: Int, card: Card): GameState = state.copy(
        knownCards = state.knownCards.filterNot { it.observerId == observer && it.targetId == target } + KnownCard(observer, target, card)
    )

    private fun updatePlayer(state: GameState, player: PlayerState): GameState = state.copy(players = state.players.map { if (it.id == player.id) player else it })

    private fun log(state: GameState, message: String): GameState = state.copy(
        journal = (state.journal + LogEntry(state.nextEventId, state.roundNumber, message)).takeLast(120), nextEventId = state.nextEventId + 1
    )

    private fun note(state: GameState, playerId: Int, text: String): GameState = state.copy(
        privateNotes = (state.privateNotes + PrivateNote(state.nextEventId, playerId, state.roundNumber, text)).takeLast(80), nextEventId = state.nextEventId + 1
    )

    private fun removeOnce(cards: List<Card>, card: Card): List<Card> {
        val mutable = cards.toMutableList()
        check(mutable.remove(card))
        return mutable
    }

    private fun chancellorActions(hand: List<Card>): List<GameAction> = hand.distinct().flatMap { keep ->
        permutations(removeOnce(hand, keep)).map { GameAction.ChancellorChoice(keep, it) }
    }.distinct()

    private fun permutations(cards: List<Card>): List<List<Card>> = if (cards.isEmpty()) listOf(emptyList()) else cards.distinct().flatMap { first ->
        permutations(removeOnce(cards, first)).map { listOf(first) + it }
    }
}

/** SplitMix64 state is persisted in GameState, including shuffles and tied next-round starters. */
internal class DeterministicRandom(seed: Long) {
    var state: Long = seed
        private set

    fun nextLong(): Long {
        state += -7046029254386353131L
        var value = state
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        val divisor = bound.toLong()
        var bits: Long
        var value: Long
        do {
            bits = nextLong() ushr 1
            value = bits % divisor
        } while (bits - value + divisor - 1 < 0L)
        return value.toInt()
    }
}
