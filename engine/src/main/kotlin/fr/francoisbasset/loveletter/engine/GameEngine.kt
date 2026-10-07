package fr.francoisbasset.loveletter.engine

/** Deterministic, immutable rules engine for the French 21-card edition (2019). */
object GameEngine {
    fun newGame(config: GameConfig): GameState {
        val players = List(config.playerCount) { id -> PlayerState(id, if (id == 0) "Vous" else "Adversaire $id") }
        return startRound(GameState(config, players, config.firstPlayer, 0, emptyList(), null, emptyList()), config.firstPlayer)
    }

    fun legalActions(state: GameState): List<GameAction> {
        if (state.phase == Phase.ROUND_OVER || state.phase == Phase.GAME_OVER) return emptyList()
        val actor = state.players[state.currentPlayer]
        if (actor.eliminated) return emptyList()
        if (state.phase == Phase.CHANCELLOR) {
            return actor.hand.indices.flatMap { keep ->
                permutations(actor.hand.indices.filter { it != keep }).map { ResolveChancellor(keep, it) }
            }
        }
        val forcedCountess = Card.COUNTESS in actor.hand && actor.hand.any { it == Card.KING || it == Card.PRINCE }
        return actor.hand.indices.filter { !forcedCountess || actor.hand[it] == Card.COUNTESS }.flatMap { index ->
            val card = actor.hand[index]
            if (card !in TARGETED_CARDS) return@flatMap listOf(PlayCard(index))
            val targets = state.players.filter { !it.eliminated && !it.protected && (card == Card.PRINCE || it.id != actor.id) }
            if (targets.isEmpty()) return@flatMap listOf(PlayCard(index))
            targets.flatMap { target ->
                if (card == Card.GUARD) Card.entries.filter { it != Card.GUARD }.map { PlayCard(index, target.id, it) }
                else listOf(PlayCard(index, target.id))
            }
        }
    }

    fun apply(state: GameState, action: GameAction): GameState {
        require(action in legalActions(state)) { "Action interdite dans cet état de la partie : $action" }
        val turn = Turn(state)
        when (action) {
            is PlayCard -> turn.play(action)
            is ResolveChancellor -> turn.resolveChancellor(action)
        }
        return turn.state
    }

    fun nextRound(state: GameState): GameState {
        require(state.phase == Phase.ROUND_OVER) { "La manche suivante ne peut commencer qu’après une manche terminée." }
        val winners = requireNotNull(state.result).winners
        val random = StableRandom(state.randomState)
        val first = winners[if (winners.size == 1) 0 else random.nextInt(winners.size)]
        return startRound(state.copy(randomState = random.state), first)
    }

    fun observation(state: GameState, playerId: Int): PlayerObservation {
        require(playerId in state.players.indices)
        return PlayerObservation(
            playerId = playerId,
            // The public configuration must not disclose the seed used to shuffle secret cards.
            config = state.config.copy(seed = 0L),
            players = state.players.map { player ->
                PublicPlayer(player.id, player.name, player.hand.size, player.discard.toList(), player.protected,
                    player.eliminated, player.score,
                    state.result?.revealedHands?.get(player.id)
                        ?: state.knowledge.lastOrNull { it.viewerId == playerId && it.subjectId == player.id }?.card)
            },
            hand = state.players[playerId].hand.toList(), currentPlayer = state.currentPlayer,
            roundNumber = state.roundNumber, deckCount = state.deck.size, faceUpRemoved = state.faceUpRemoved.toList(),
            phase = state.phase, legalActions = if (playerId == state.currentPlayer) legalActions(state) else emptyList(),
            result = state.result, gameWinners = state.gameWinners.toList(),
            history = state.history.filter { it.audience == null || playerId in it.audience },
        )
    }

    private fun startRound(previous: GameState, firstPlayer: Int): GameState {
        val random = StableRandom(previous.randomState)
        val shuffled = Card.fullDeck().toMutableList()
        for (i in shuffled.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val value = shuffled[i]; shuffled[i] = shuffled[j]; shuffled[j] = value
        }
        val reserve = shuffled.removeAt(0)
        val faceUp = if (previous.players.size == 2) List(3) { shuffled.removeAt(0) } else emptyList()
        val players = previous.players.map { it.copy(hand = listOf(shuffled.removeAt(0)), discard = emptyList(), protected = false, eliminated = false) }
        val round = previous.roundNumber + 1
        val turn = Turn(previous.copy(players = players, currentPlayer = firstPlayer, roundNumber = round,
            deck = shuffled.toList(), reserve = reserve, faceUpRemoved = faceUp, phase = Phase.TURN,
            result = null, gameWinners = emptyList(), knowledge = emptyList(), randomState = random.state))
        turn.event("Manche $round : ${turn.subjectVerb(firstPlayer, "commence", "commencez")}.", EventType.ROUND_START, firstPlayer)
        players.forEach { turn.event("Votre carte distribuée : ${it.hand.single().frenchName}.", EventType.DRAW, it.id, card = it.hand.single(), audience = setOf(it.id)) }
        turn.beginTurn(firstPlayer)
        return turn.state
    }

    private class Turn(var state: GameState) {
        private val actorId get() = state.currentPlayer
        private fun player(id: Int) = state.players[id]
        fun subjectVerb(id: Int, thirdPerson: String, secondPerson: String): String =
            if (id == 0) "Vous $secondPerson" else "${player(id).name} $thirdPerson"
        private fun named(id: Int): String = if (id == 0) "vous" else player(id).name
        private fun handOf(id: Int): String = if (id == 0) "votre carte" else "la carte de ${player(id).name}"
        private fun pairVerb(first: Int, second: Int, thirdPerson: String, secondPerson: String): String =
            if (first == 0 || second == 0) "Vous et ${player(if (first == 0) second else first).name} $secondPerson"
            else "${player(first).name} et ${player(second).name} $thirdPerson"
        private fun updatePlayer(id: Int, transform: (PlayerState) -> PlayerState) {
            state = state.copy(players = state.players.map { if (it.id == id) transform(it) else it })
        }

        private fun invalidate(vararg ids: Int) {
            state = state.copy(knowledge = state.knowledge.filterNot { it.subjectId in ids })
        }

        private fun know(viewer: Int, subject: Int, card: Card) {
            state = state.copy(knowledge = state.knowledge.filterNot { it.viewerId == viewer && it.subjectId == subject } + CardKnowledge(viewer, subject, card))
        }

        fun event(message: String, type: EventType, actor: Int? = null, target: Int? = null,
                  card: Card? = null, audience: Set<Int>? = null, success: Boolean? = null) {
            state = state.copy(history = state.history + GameEvent(message, state.roundNumber, type, actor, target, card, audience, success))
        }

        fun beginTurn(id: Int) {
            state = state.copy(currentPlayer = id, phase = Phase.TURN)
            updatePlayer(id) { it.copy(protected = false) }
            draw(id)
        }

        private fun draw(id: Int, useReserve: Boolean = false): Card? {
            val card: Card
            if (state.deck.isNotEmpty()) {
                card = state.deck.first()
                state = state.copy(deck = state.deck.drop(1))
            } else if (useReserve && state.reserve != null) {
                card = requireNotNull(state.reserve)
                state = state.copy(reserve = null)
            } else return null
            invalidate(id)
            updatePlayer(id) { it.copy(hand = it.hand + card) }
            event("${subjectVerb(id, "pioche", "piochez")} une carte.", EventType.DRAW, id)
            event("Vous piochez : ${card.frenchName}.", EventType.DRAW, id, card = card, audience = setOf(id))
            return card
        }

        fun play(action: PlayCard) {
            val id = actorId
            val card = player(id).hand[action.cardIndex]
            invalidate(id)
            updatePlayer(id) { it.copy(hand = it.hand.filterIndexed { index, _ -> index != action.cardIndex }, discard = it.discard + card) }
            event("${subjectVerb(id, "joue", "jouez")} ${card.frenchName}.", EventType.PLAY, id, card = card)
            val target = action.targetId
            when (card) {
                Card.SPY, Card.COUNTESS -> Unit
                Card.GUARD -> if (target != null) {
                    val guess = requireNotNull(action.guess)
                    val correct = player(target).hand.single() == guess
                    event("${subjectVerb(id, "annonce", "annoncez")} ${guess.frenchName} pour ${named(target)} : ${if (correct) "trouvé !" else "raté."}", EventType.GUESS, id, target, guess, success = correct)
                    if (correct) eliminate(target)
                }
                Card.PRIEST -> if (target != null) {
                    val seen = player(target).hand.single()
                    know(id, target, seen)
                    event("${subjectVerb(id, "regarde", "regardez")} ${handOf(target)}.", EventType.REVEAL, id, target)
                    event("${handOf(target).replaceFirstChar { it.uppercaseChar() }} est ${seen.frenchName}.", EventType.REVEAL, id, target, seen, setOf(id))
                }
                Card.BARON -> if (target != null) {
                    val ours = player(id).hand.single()
                    val theirs = player(target).hand.single()
                    know(id, target, theirs); know(target, id, ours)
                    event("${pairVerb(id, target, "comparent leurs", "comparez vos")} cartes en secret.", EventType.COMPARE, id, target)
                    event("Comparaison : ${subjectVerb(id, "a", "avez")} ${ours.frenchName} ; ${subjectVerb(target, "a", "avez")} ${theirs.frenchName}.", EventType.COMPARE, id, target, audience = setOf(id, target))
                    when {
                        ours.value < theirs.value -> eliminate(id)
                        ours.value > theirs.value -> eliminate(target)
                        else -> event("Les valeurs sont égales : personne n’est éliminé.", EventType.COMPARE, id, target)
                    }
                }
                Card.HANDMAID -> {
                    updatePlayer(id) { it.copy(protected = true) }
                    event("${subjectVerb(id, "est protégé jusqu’au début de son prochain tour", "êtes protégé jusqu’au début de votre prochain tour")}.", EventType.PROTECTION, id)
                }
                Card.PRINCE -> if (target != null) {
                    val discarded = player(target).hand.single()
                    invalidate(target)
                    updatePlayer(target) { it.copy(hand = emptyList(), discard = it.discard + discarded) }
                    event("${subjectVerb(target, "défausse", "défaussez")} ${discarded.frenchName}.", EventType.DISCARD, id, target, discarded)
                    if (discarded == Card.PRINCESS) eliminate(target) else draw(target, useReserve = true)
                }
                Card.CHANCELLOR -> {
                    val before = player(id).hand.size
                    repeat(2) { draw(id) }
                    if (player(id).hand.size > before) {
                        state = state.copy(phase = Phase.CHANCELLOR)
                        event("${subjectVerb(id, "choisit une carte et remet", "choisissez une carte et remettez")} les autres au fond de la pioche.", EventType.CHANCELLOR, id)
                        return
                    }
                }
                Card.KING -> if (target != null) {
                    val ours = player(id).hand.single()
                    val theirs = player(target).hand.single()
                    invalidate(id, target)
                    updatePlayer(id) { it.copy(hand = listOf(theirs)) }
                    updatePlayer(target) { it.copy(hand = listOf(ours)) }
                    know(id, target, ours); know(target, id, theirs)
                    event("${pairVerb(id, target, "échangent leurs", "échangez vos")} mains.", EventType.EXCHANGE, id, target)
                    event("Votre nouvelle carte après l’échange : ${theirs.frenchName}.", EventType.EXCHANGE, id, target, theirs, setOf(id))
                    event("Votre nouvelle carte après l’échange : ${ours.frenchName}.", EventType.EXCHANGE, id, target, ours, setOf(target))
                }
                Card.PRINCESS -> eliminate(id)
            }
            if (card in TARGETED_CARDS && target == null) event("Aucune cible autorisée : ${card.frenchName} n’a pas d’effet.", EventType.PLAY, id)
            finishTurn()
        }

        fun resolveChancellor(action: ResolveChancellor) {
            val hand = player(actorId).hand
            val kept = hand[action.keepIndex]
            val returned = action.returnOrder.map { hand[it] }
            invalidate(actorId)
            updatePlayer(actorId) { it.copy(hand = listOf(kept)) }
            state = state.copy(deck = state.deck + returned, phase = Phase.TURN)
            event("${subjectVerb(actorId, "replace", "replacez")} ${returned.size} carte(s) au fond de la pioche.", EventType.CHANCELLOR, actorId)
            event("Vous gardez ${kept.frenchName}. Cartes remises, du dessus vers le fond : ${returned.joinToString { it.frenchName }}.", EventType.CHANCELLOR, actorId, card = kept, audience = setOf(actorId))
            finishTurn()
        }

        private fun eliminate(id: Int) {
            invalidate(id)
            val discarded = player(id).hand
            updatePlayer(id) { it.copy(hand = emptyList(), discard = it.discard + discarded, eliminated = true, protected = false) }
            discarded.forEach { event("${subjectVerb(id, "défausse", "défaussez")} ${it.frenchName}.", EventType.DISCARD, id, id, it) }
            event("${subjectVerb(id, "est éliminé", "êtes éliminé")} de la manche.", EventType.ELIMINATION, id)
        }

        private fun finishTurn() {
            val alive = state.players.filterNot { it.eliminated }
            if (alive.size <= 1 || state.deck.isEmpty()) {
                finishRound(alive)
            } else {
                val next = (1..state.players.size).map { (actorId + it) % state.players.size }.first { !player(it).eliminated }
                beginTurn(next)
            }
        }

        private fun finishRound(alive: List<PlayerState>) {
            check(alive.isNotEmpty()) { "Une manche doit conserver au moins un joueur." }
            val maximum = alive.maxOf { it.hand.single().value }
            val winners = alive.filter { it.hand.single().value == maximum }.map { it.id }
            val spy = alive.filter { Card.SPY in it.discard }.singleOrNull()?.id
            val points = state.players.associate { it.id to (if (it.id in winners) 1 else 0) + (if (it.id == spy) 1 else 0) }
            val reason = if (alive.size == 1) "Dernier joueur en jeu" else "La pioche est épuisée : la plus forte main gagne, à égalité tous les joueurs concernés gagnent."
            val result = RoundResult(winners, spy, points, alive.associate { it.id to it.hand.single() }, reason)
            state = state.copy(players = state.players.map { it.copy(score = it.score + points.getValue(it.id)) }, result = result, phase = Phase.ROUND_OVER)
            event("Manche terminée. ${winners.joinToString { player(it).name }} : +1 pion Faveur.", EventType.ROUND_END)
            if (spy != null) event("${subjectVerb(spy, "reçoit", "recevez")} +1 pion Faveur grâce à l’Espionne.", EventType.SPY_BONUS, spy)
            val gameWinners = state.players.filter { it.score >= state.config.pointsToWin }.map { it.id }
            if (gameWinners.isNotEmpty()) {
                state = state.copy(phase = Phase.GAME_OVER, gameWinners = gameWinners)
                val victory = if (gameWinners.size == 1) subjectVerb(gameWinners.single(), "remporte la partie", "remportez la partie")
                    else "Victoire partagée entre ${gameWinners.joinToString(" et ") { named(it) }}"
                event("Partie terminée : $victory !", EventType.GAME_END)
            }
        }
    }

    private val TARGETED_CARDS = setOf(Card.GUARD, Card.PRIEST, Card.BARON, Card.PRINCE, Card.KING)
    private fun permutations(values: List<Int>): List<List<Int>> = if (values.isEmpty()) listOf(emptyList())
        else values.flatMap { value -> permutations(values - value).map { listOf(value) + it } }
}

/** Small stable PRNG whose entire state survives process death without platform dependencies. */
private class StableRandom(seed: Long) {
    var state: Long = if (seed == 0L) -7046029254386353131L else seed
        private set
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        var x = state
        x = x xor (x ushr 12); x = x xor (x shl 25); x = x xor (x ushr 27)
        state = x
        val bits = (x * 2685821657736338717L) ushr 1
        return (bits % bound.toLong()).toInt()
    }
}
