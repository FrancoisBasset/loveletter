package fr.francoisbasset.loveletter.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random

/**
 * An AI's only input is a redacted [Observation]. It has no reference to GameState,
 * the draw-pile order, the reserved card, or an opponent's unrevealed hand.
 *
 * EASY selects a card, then a legal target/guess at random. NORMAL counts visible
 * copies and uses public exclusions plus its own legitimate Priest/King knowledge.
 * HARD additionally conditions all opponents' hand distributions together on the
 * remaining card multiplicities: opponents cannot all hold the same unique card.
 * Scores are deliberate heuristics, not a claim that this imperfect-information
 * game has been solved. A fixed seed resolves equivalent decisions reproducibly.
 */
object AiPlayer {
    fun choose(
        observation: Observation,
        difficulty: Difficulty = observation.config.difficulty,
        seed: Long
    ): GameAction {
        require(observation.legalActions.isNotEmpty()) { "Aucune action légale pour cette observation." }
        val random = Random(seed)
        val actions = observation.legalActions
        if (actions.size == 1) return actions.single()
        if (difficulty == Difficulty.EASY) return easyChoice(actions, random)

        val beliefs = Beliefs(observation, exact = difficulty == Difficulty.HARD)
        val scored = actions.map { action ->
            action to when (action) {
                is GameAction.Play -> playScore(observation, action, beliefs, difficulty)
                is GameAction.ChancellorChoice -> chancellorScore(observation, action, beliefs, difficulty)
                GameAction.NextRound -> 0.0
            }
        }
        val best = scored.maxOf { it.second }
        // Randomness is restricted to equally good decisions, rather than hiding
        // secret-state access behind apparently random behaviour.
        return scored.filter { abs(it.second - best) < 1e-9 }.random(random).first
    }

    private fun easyChoice(actions: List<GameAction>, random: Random): GameAction {
        val plays = actions.filterIsInstance<GameAction.Play>()
        if (plays.isNotEmpty()) {
            // There are many legal guesses for a Guard. Picking uniformly over all
            // actions would accidentally favour Guards over every other card.
            val card = plays.map { it.card }.distinct().random(random)
            val matching = plays.filter { it.card == card }
            val target = matching.map { it.target }.distinct().random(random)
            return matching.filter { it.target == target }.random(random)
        }
        val choices = actions.filterIsInstance<GameAction.ChancellorChoice>()
        if (choices.isNotEmpty()) {
            val keep = choices.map { it.keep }.distinct().random(random)
            return choices.filter { it.keep == keep }.random(random)
        }
        return actions.random(random)
    }

    private fun playScore(
        observation: Observation,
        action: GameAction.Play,
        beliefs: Beliefs,
        difficulty: Difficulty
    ): Double {
        if (action.card == Card.PRINCESSE) return -10_000.0
        val hard = difficulty == Difficulty.HARD
        val remaining = observation.ownHand.toMutableList().also { it.remove(action.card) }
        val kept = remaining.firstOrNull() ?: return -10_000.0
        val target = observation.players.firstOrNull { it.id == action.target }
        val opponents = observation.players.filter { it.id != observation.viewerId && !it.isEliminated }
        val lastPlay = observation.deckSize == 0 && action.card != Card.CHANCELIER
        val retainWeight = if (lastPlay) 0.8 else if (observation.deckSize <= opponents.size) 0.48 else 0.25
        var score = retainedValue(observation, kept, beliefs, hard) * retainWeight
        val distribution = action.target?.let { beliefs.hand(it) } ?: emptyMap()
        val priority = target?.let {
            if (it.score >= observation.config.victoryThreshold - 1) 1.45
            else if (it.score > observation.players.first { player -> player.id == observation.viewerId }.score) 1.12
            else 1.0
        } ?: 1.0

        score += when (action.card) {
            Card.ESPIONNE -> spyPotential(observation)
            Card.GARDE -> {
                val chance = distribution[action.guess] ?: 0.0
                6.0 * chance * priority
            }
            Card.PRETRE -> {
                // Looking at an already known card gives no information. Information
                // has more value when another turn remains to exploit it.
                val entropy = -distribution.values.filter { it > 0.0 }.sumOf { it * ln(it) }
                val future = if (lastPlay) 0.0 else if (observation.deckSize < opponents.size) 0.22 else 0.65
                entropy * future * if (hard) 1.2 else 1.0
            }
            Card.BARON -> {
                val win = distribution.filterKeys { it.value < kept.value }.values.sum()
                val loss = distribution.filterKeys { it.value > kept.value }.values.sum()
                5.6 * win * priority - (if (hard) 9.4 else 8.0) * loss
            }
            Card.SERVANTE -> if (lastPlay) 0.0 else 1.15 + kept.value * 0.075 + if (kept == Card.PRINCESSE) 0.45 else 0.0
            Card.PRINCE -> when {
                action.target == null -> 0.0
                action.target == observation.viewerId && kept == Card.PRINCESSE -> -10_000.0
                action.target == observation.viewerId -> {
                    val improvement = beliefs.draw.entries.sumOf { (card, probability) ->
                        probability * (retainedValue(observation, card, beliefs, hard) - retainedValue(observation, kept, beliefs, hard))
                    }
                    improvement * 0.58 + if (kept == Card.ESPIONNE) spyPotential(observation) else 0.0
                }
                else -> {
                    val princessChance = distribution[Card.PRINCESSE] ?: 0.0
                    val oldAverage = distribution.entries.sumOf { it.key.value * it.value }
                    val newAverage = beliefs.draw.entries.sumOf { it.key.value * it.value }
                    6.6 * princessChance * priority + (oldAverage - newAverage) * 0.2 * priority
                }
            }
            Card.CHANCELIER -> {
                // It is evaluated without drawing any hidden card. Actual choices
                // are made later, once the legal private Chancellor draw is known.
                val draws = observation.deckSize.coerceAtMost(2)
                val improvement = beliefs.draw.entries.sumOf { (card, probability) ->
                    probability * max(0.0, retainedValue(observation, card, beliefs, hard) - retainedValue(observation, kept, beliefs, hard))
                }
                improvement * draws * 0.6 + if (draws > 0) 0.3 else 0.0
            }
            Card.ROI -> {
                val improvement = distribution.entries.sumOf { (card, probability) ->
                    probability * (retainedValue(observation, card, beliefs, hard) - retainedValue(observation, kept, beliefs, hard))
                }
                improvement * 0.62 - if (kept == Card.PRINCESSE) 0.55 * priority else 0.0
            }
            Card.COMTESSE -> 0.0
            Card.PRINCESSE -> -10_000.0
        }
        if (hard && lastPlay) score += 8.0 * endgameProbability(observation, action, kept, beliefs)
        return score
    }

    private fun chancellorScore(
        observation: Observation,
        action: GameAction.ChancellorChoice,
        beliefs: Beliefs,
        difficulty: Difficulty
    ): Double {
        val hard = difficulty == Difficulty.HARD
        var score = retainedValue(observation, action.keep, beliefs, hard)
        if (hard && action.keep == Card.PRINCESSE && Card.PRINCE in action.bottom) {
            // Cards returned by this very decision are known to become available:
            // do not pretend a Prince is harmless because it is currently in hand.
            val nextCardIsPrince = observation.deckSize == 0 && action.bottom.firstOrNull() == Card.PRINCE
            score -= if (nextCardIsPrince) 1.8 else 0.65
        }
        if (hard) {
            // The front of the returned stack is drawn first. Delay valuable cards
            // rather than handing the next opponent the strongest one immediately.
            action.bottom.forEachIndexed { index, card ->
                score -= card.value * 0.025 / (index + 1)
            }
        }
        return score
    }

    private fun retainedValue(observation: Observation, card: Card, beliefs: Beliefs, hard: Boolean): Double {
        val opponents = observation.players.count { it.id != observation.viewerId && !it.isEliminated }
        val futureTurnLikely = observation.deckSize >= opponents
        var value = card.value.toDouble()
        if (futureTurnLikely) {
            value += when (card) {
                Card.GARDE -> 0.45
                Card.SERVANTE -> 0.65
                Card.CHANCELIER -> 0.65
                Card.ESPIONNE -> 0.3
                else -> 0.0
            }
        }
        if (hard && card == Card.PRINCESSE && observation.deckSize > 0) {
            // A Princess cannot be safely discarded after a Prince, and high value
            // is not automatically best while Princes are still unaccounted for.
            val princeShare = beliefs.draw[Card.PRINCE] ?: 0.0
            val risk = (princeShare * 2.0 * opponents.coerceAtMost(3)).coerceAtMost(0.45)
            value -= risk * 4.0
        }
        return value
    }

    private fun spyPotential(observation: Observation): Double {
        val self = observation.players.first { it.id == observation.viewerId }
        if (Card.ESPIONNE in self.discard) return 0.0 // Multiple Spies still give only one favour.
        val competitors = observation.players.count {
            it.id != observation.viewerId && !it.isEliminated && Card.ESPIONNE in it.discard
        }
        return if (competitors == 0) 1.25 else 0.2
    }

    /**
     * Approximate expected chance of sharing the final-card win. Ties count as wins
     * in this edition. Joint counting improves marginals; multiplication here is a
     * strategic approximation, and is intentionally documented as such.
     */
    private fun endgameProbability(
        observation: Observation,
        action: GameAction.Play,
        kept: Card,
        beliefs: Beliefs
    ): Double {
        val opponents = observation.players.filter { it.id != observation.viewerId && !it.isEliminated }
        fun showdown(selfCard: Card, omitted: Int? = null): Double = opponents
            .filter { it.id != omitted }
            .fold(1.0) { result, player -> result * beliefs.hand(player.id).filterKeys { it.value <= selfCard.value }.values.sum() }

        if (action.target == null) return showdown(kept)
        if (action.card == Card.PRINCE && action.target == observation.viewerId) {
            if (kept == Card.PRINCESSE) return 0.0
            return beliefs.draw.entries.sumOf { (replacement, probability) -> probability * showdown(replacement) }
        }
        val targetId = action.target
        val targetHand = beliefs.hand(targetId)
        return targetHand.entries.sumOf { (opponentCard, probability) ->
            val value = when (action.card) {
                Card.GARDE -> if (opponentCard == action.guess) showdown(kept, targetId)
                    else if (kept.value >= opponentCard.value) showdown(kept, targetId) else 0.0
                Card.BARON -> when {
                    kept.value < opponentCard.value -> 0.0
                    else -> showdown(kept, targetId)
                }
                Card.ROI -> if (opponentCard.value >= kept.value) showdown(opponentCard, targetId) else 0.0
                Card.PRINCE -> if (opponentCard == Card.PRINCESSE) showdown(kept, targetId)
                    else beliefs.draw.filterKeys { it.value <= kept.value }.values.sum() * showdown(kept, targetId)
                else -> if (kept.value >= opponentCard.value) showdown(kept, targetId) else 0.0
            }
            probability * value
        }
    }

    private class Beliefs(observation: Observation, exact: Boolean) {
        private val cards = Card.entries.toList()
        private val distributions = mutableMapOf<Int, Map<Card, Double>>()
        val draw: Map<Card, Double>

        init {
            val remaining = IntArray(cards.size) { cards[it].copies }
            fun remove(card: Card) { remaining[card.ordinal]-- }
            observation.ownHand.forEach(::remove)
            observation.faceUpRemoved.forEach(::remove)
            observation.players.forEach { player -> player.discard.forEach(::remove) }
            val opponents = observation.players.filter { it.id != observation.viewerId && !it.isEliminated && it.handSize > 0 }
            val known = observation.knownCards.filter { it.observerId == observation.viewerId }.associate { it.targetId to it.card }
            val unknown = mutableListOf<PlayerView>()
            opponents.forEach { player ->
                val card = player.visibleHand?.singleOrNull() ?: known[player.id]
                if (card != null) {
                    distributions[player.id] = mapOf(card to 1.0)
                    remove(card)
                } else unknown += player
            }
            // Valid observations always respect copy counts. Clamping also makes
            // the evaluator safe for manually built demonstration observations.
            remaining.indices.forEach { remaining[it] = remaining[it].coerceAtLeast(0) }
            val excluded = observation.exclusions.groupBy { it.targetId }.mapValues { (_, exclusions) -> exclusions.map { it.card }.toSet() }
            if (exact && unknown.isNotEmpty()) {
                val totals = Array(unknown.size) { DoubleArray(cards.size) }
                val assignment = IntArray(unknown.size)
                var totalWeight = 0.0
                // Each assignment is weighted by its physical-card multiplicity.
                // The omitted common denominator is the same for all assignments.
                // Counts are restored immediately: this never changes game state.
                fun enumerate(index: Int, weight: Double) {
                    if (index == unknown.size) {
                        totalWeight += weight
                        assignment.indices.forEach { seat -> totals[seat][assignment[seat]] += weight }
                        return
                    }
                    val forbidden = excluded[unknown[index].id].orEmpty()
                    cards.indices.forEach { cardIndex ->
                        if (remaining[cardIndex] > 0 && cards[cardIndex] !in forbidden) {
                            val copies = remaining[cardIndex]
                            remaining[cardIndex]--
                            assignment[index] = cardIndex
                            enumerate(index + 1, weight * copies)
                            remaining[cardIndex]++
                        }
                    }
                }
                enumerate(0, 1.0)
                if (totalWeight > 0.0) unknown.forEachIndexed { seat, player ->
                    distributions[player.id] = cards.indices.filter { totals[seat][it] > 0.0 }
                        .associate { cards[it] to totals[seat][it] / totalWeight }
                }
            }
            unknown.forEach { player ->
                if (player.id !in distributions) {
                    val allowed = cards.filter { remaining[it.ordinal] > 0 && it !in excluded[player.id].orEmpty() }
                    val total = allowed.sumOf { remaining[it.ordinal] }.toDouble()
                    distributions[player.id] = allowed.associateWith { remaining[it.ordinal] / total }
                }
            }
            // The expected cards in unknown opponent hands are subtracted before
            // estimating a future draw. The reserve remains unseen in this pool.
            val drawCounts = DoubleArray(cards.size) { remaining[it].toDouble() }
            unknown.forEach { player -> distributions[player.id].orEmpty().forEach { (card, probability) -> drawCounts[card.ordinal] -= probability } }
            val total = drawCounts.sumOf { it.coerceAtLeast(0.0) }
            draw = if (total > 0.0) cards.associateWith { drawCounts[it.ordinal].coerceAtLeast(0.0) / total }
                else cards.associateWith { if (it == Card.GARDE) 1.0 else 0.0 }
        }

        fun hand(playerId: Int): Map<Card, Double> = distributions[playerId].orEmpty()
    }
}
