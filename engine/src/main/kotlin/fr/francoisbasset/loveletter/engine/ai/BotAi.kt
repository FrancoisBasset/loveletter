package fr.francoisbasset.loveletter.engine.ai

import fr.francoisbasset.loveletter.engine.Card
import fr.francoisbasset.loveletter.engine.Difficulty
import fr.francoisbasset.loveletter.engine.EventType
import fr.francoisbasset.loveletter.engine.GameAction
import fr.francoisbasset.loveletter.engine.PlayCard
import fr.francoisbasset.loveletter.engine.PlayerObservation
import fr.francoisbasset.loveletter.engine.PublicPlayer
import fr.francoisbasset.loveletter.engine.ResolveChancellor
import kotlin.math.ln
import kotlin.random.Random

/**
 * Decision boundary: bots receive only the acting player's observation.
 * No hidden hand, reserve card or deck order can be inspected here.
 * Every return value is an action supplied by the rules engine.
 *
 * NORMAL counts visible cards and evaluates immediate effects. HARD additionally
 * conditions on failed public guesses, evaluates expected outcomes for swaps and
 * redraws, estimates showdown chances, and values survival and score threats.
 * These are probabilistic heuristics, not a claim of optimal play.
 */
object BotAi {
    fun chooseAction(
        observation: PlayerObservation,
        difficulty: Difficulty = observation.config.difficulty,
        seed: Long = 0L,
    ): GameAction {
        require(observation.isMyTurn) { "L’IA ne peut agir que pendant son tour." }
        require(observation.legalActions.isNotEmpty()) { "Aucune action légale disponible." }
        val random = Random(seed)
        if (difficulty == Difficulty.EASY) {
            // A beginner still avoids voluntarily discarding the Princess.
            val sensible = observation.legalActions.filterNot {
                it is PlayCard && observation.hand[it.cardIndex] == Card.PRINCESS
            }
            val choices = sensible.ifEmpty { observation.legalActions }
            return choices[random.nextInt(choices.size)]
        }

        val evaluator = Evaluator(observation, difficulty == Difficulty.HARD)
        // Seeded shuffling is used only to break equivalent choices.
        return observation.legalActions.shuffled(random).maxBy { evaluator.score(it) }
    }

    private class Evaluator(private val view: PlayerObservation, private val hard: Boolean) {
        private val self = view.players.first { it.id == view.playerId }
        private val opponents = view.players.filter { !it.eliminated && it.id != view.playerId }
        private val pool = Card.entries.associateWith { it.copies }.toMutableMap().also { counts ->
            val visible = view.hand + view.faceUpRemoved + view.players.flatMap { it.discard } +
                opponents.mapNotNull { it.knownCard }
            visible.forEach { counts[it] = ((counts[it] ?: 0) - 1).coerceAtLeast(0) }
        }
        private val beliefs = opponents.associate { it.id to distribution(it) }
        private val redraw = normalized(pool)
        private val urgency = (1.0 - view.deckCount / 12.0).coerceIn(0.0, 1.0)
        private val mySpy = Card.SPY in self.discard
        private val rivalSpies = opponents.count { Card.SPY in it.discard }

        fun score(action: GameAction): Double = when (action) {
            is ResolveChancellor -> chancellor(action)
            is PlayCard -> play(action)
        }

        private fun play(action: PlayCard): Double {
            val played = view.hand[action.cardIndex]
            val kept = view.hand.filterIndexed { index, _ -> index != action.cardIndex }.single()
            if (played == Card.PRINCESS) return -1_000.0
            val target = action.targetId?.let { id -> view.players.first { it.id == id } }
            val belief = target?.let { beliefs[it.id] }.orEmpty()
            val baseline = handValue(kept)
            val reward = target?.let { eliminationValue(it) } ?: 0.0
            return when (played) {
                Card.SPY -> baseline + if (!mySpy && rivalSpies == 0) {
                    if (hard) 2.8 + urgency * 1.8 else 1.6
                } else 0.0
                Card.GUARD -> baseline + reward * (belief[action.guess] ?: 0.0)
                Card.PRIEST -> baseline + if (belief.isEmpty()) 0.0 else {
                    val uncertainty = -belief.values.filter { it > 0.0 }.sumOf { it * ln(it) }
                    val guardAvailability = (pool[Card.GUARD] ?: 0) / 6.0
                    uncertainty * (if (hard) 0.45 + guardAvailability * 0.8 else 0.45) *
                        (if (view.deckCount == 0) 0.0 else 1.0)
                }
                Card.BARON -> baseline + belief.entries.sumOf { (other, chance) ->
                    chance * when {
                        kept.value > other.value -> reward
                        kept.value < other.value -> -survivalValue()
                        else -> 0.0
                    }
                }
                Card.HANDMAID -> baseline + if (opponents.isEmpty()) 0.0 else {
                    val safety = if (hard) 1.2 + opponents.size * 0.25 +
                        (if (kept == Card.PRINCESS) 0.8 else 0.0) + spySurvivalValue() else 1.5
                    // No further attacks occur once the empty deck ends this turn.
                    if (view.deckCount == 0) 0.0 else safety
                }
                Card.PRINCE -> when {
                    target == null -> baseline
                    target.id == self.id && kept == Card.PRINCESS -> -1_000.0
                    target.id == self.id -> expectedDrawValue() +
                        (if (hard && kept == Card.SPY && !mySpy && rivalSpies == 0) 2.5 else 0.0)
                    else -> baseline + reward * (belief[Card.PRINCESS] ?: 0.0) +
                        // A forced redraw weakens high-value rivals and strengthens low hands.
                        (if (hard) 0.55 else 0.25) * (meanValue(belief) - meanValue(redraw))
                }
                Card.CHANCELLOR -> {
                    // Expectation over at most two draws, without knowing their actual order.
                    val count = minOf(2, view.deckCount)
                    val expected = if (hard) expectedBestOf(kept, count, pool) else
                        baseline + count * 0.55
                    expected + if (count > 0) 0.25 else 0.0
                }
                Card.KING -> if (target == null) baseline else {
                    val acquired = belief.entries.sumOf { (card, chance) ->
                        chance * handValue(card, target.id, kept)
                    }
                    acquired + if (hard) (meanValue(belief) - kept.value) * 0.3 else 0.0
                }
                Card.COUNTESS -> baseline
                Card.PRINCESS -> error("Handled before evaluation")
            }
        }

        private fun chancellor(action: ResolveChancellor): Double {
            val kept = view.hand[action.keepIndex]
            var value = handValue(kept)
            if (hard && view.deckCount < view.players.count { !it.eliminated }) {
                // Returned cards become future draws in this documented top-to-bottom order.
                // Avoid giving the earliest opponent a high card when the pile is short.
                val alive = view.players.filter { !it.eliminated }.map { it.id }.sorted()
                val current = alive.indexOf(view.playerId)
                action.returnOrder.forEachIndexed { position, handIndex ->
                    val drawsAway = view.deckCount + position + 1
                    val receiver = alive[(current + drawsAway) % alive.size]
                    val card = view.hand[handIndex]
                    val weight = 0.12 / (position + 1)
                    value += if (receiver == view.playerId) card.value * weight else -card.value * weight
                }
            }
            return value
        }

        private fun handValue(card: Card, exchangedWith: Int? = null, replacement: Card? = null): Double {
            if (!hard) return card.value * 0.43
            // One-step showdown estimate: tied highest hands are also winners in this edition.
            val showdown = opponents.fold(1.0) { chance, other ->
                val distribution = if (other.id == exchangedWith && replacement != null) {
                    mapOf(replacement to 1.0)
                } else beliefs.getValue(other.id)
                chance * distribution.filterKeys { it.value <= card.value }.values.sum()
            }
            val tactical = when (card) {
                Card.GUARD -> 0.6
                Card.PRIEST -> 0.2
                Card.BARON -> 0.15
                Card.HANDMAID -> 0.5
                Card.PRINCE -> 0.35
                Card.CHANCELLOR -> 0.6
                Card.SPY -> if (!mySpy && rivalSpies == 0) 0.75 else 0.0
                Card.PRINCESS -> -0.35
                else -> 0.0
            }
            return card.value * 0.38 + showdown * (1.0 + urgency * 4.0) + tactical * (1.0 - urgency)
        }

        private fun expectedDrawValue(): Double = redraw.entries.sumOf { (card, chance) -> chance * handValue(card) }

        /** Exhaustive probability-weighted local outcomes; draws are without replacement. */
        private fun expectedBestOf(kept: Card, draws: Int, counts: Map<Card, Int>): Double {
            val total = counts.values.sum()
            if (draws == 0 || total == 0) return handValue(kept)
            return counts.entries.filter { it.value > 0 }.sumOf { (draw, copies) ->
                val best = if (handValue(draw) > handValue(kept)) draw else kept
                val next = counts.toMutableMap().also { it[draw] = copies - 1 }
                copies.toDouble() / total * expectedBestOf(best, draws - 1, next)
            }
        }

        private fun eliminationValue(target: PublicPlayer): Double {
            val immediateWin = if (opponents.size == 1) 10.0 else 5.0
            if (!hard) return immediateWin
            val closingMatch = if (target.score >= view.pointsToWin - 1) 1.8 else 0.0
            val spyRival = if (Card.SPY in target.discard && rivalSpies == 1 && mySpy) 2.5 else 0.0
            return immediateWin + closingMatch + spyRival + target.score * 0.25
        }

        private fun survivalValue(): Double = 9.0 + if (hard) {
            spySurvivalValue() + if (self.score >= view.pointsToWin - 1) 3.0 else 0.0
        } else 0.0

        private fun spySurvivalValue(): Double = if (mySpy && rivalSpies == 0) 2.2 else 0.0

        private fun distribution(target: PublicPlayer): Map<Card, Double> {
            target.knownCard?.let { return mapOf(it to 1.0) }
            if (!hard) return normalized(pool)
            val excluded = mutableSetOf<Card>()
            view.history.filter { it.roundNumber == view.roundNumber }.forEach { event ->
                when {
                    // Drawing, playing and swapping all invalidate previous negative evidence.
                    event.type in setOf(EventType.DRAW, EventType.PLAY, EventType.CHANCELLOR) &&
                        event.actorId == target.id -> excluded.clear()
                    event.type == EventType.EXCHANGE &&
                        (event.actorId == target.id || event.targetId == target.id) -> excluded.clear()
                    event.type == EventType.DISCARD &&
                        (event.actorId == target.id || event.targetId == target.id) -> excluded.clear()
                    event.type == EventType.GUESS && event.targetId == target.id &&
                        event.success == false && event.card != null -> excluded.add(event.card)
                }
            }
            val constrained = pool.mapValues { (card, count) -> if (card in excluded) 0 else count }
            return normalized(if (constrained.values.sum() > 0) constrained else pool)
        }

        private fun normalized(counts: Map<Card, Int>): Map<Card, Double> {
            val total = counts.values.sum()
            // Defensive fallback also makes standalone observations safe for UI previews.
            return if (total == 0) Card.entries.associateWith { 1.0 / Card.entries.size }
            else counts.mapValues { (_, count) -> count.toDouble() / total }
        }

        private fun meanValue(distribution: Map<Card, Double>): Double =
            distribution.entries.sumOf { (card, chance) -> card.value * chance }
    }
}
