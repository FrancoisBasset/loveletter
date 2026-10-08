package fr.francoisbasset.loveletter

import android.graphics.Bitmap
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.francoisbasset.loveletter.core.*
import fr.francoisbasset.loveletter.persistence.SavedSession
import fr.francoisbasset.loveletter.persistence.SessionStore
import fr.francoisbasset.loveletter.ui.GamePace
import fr.francoisbasset.loveletter.ui.UiPlayback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.min

/** Real actions and real Android viewports; no card identity is inferred from screen pixels. */
@RunWith(AndroidJUnit4::class)
class VisualEffectTest {
    @get:Rule(order = 0)
    val testName = TestName()

    private lateinit var before: GameState
    private var baronHandIndex = 0
    private var actorValue = 0
    private var targetValue = 0

    @get:Rule(order = 1)
    val fixture = object : ExternalResource() {
        override fun before() {
            // Both 320dp and 360dp portraits, plus a genuinely short 640×360dp landscape.
            val size = when {
                testName.methodName.contains("Small") -> "960x1920"
                testName.methodName.contains("Landscape") -> "1920x1080"
                else -> "1080x2400"
            }
            shell("wm size $size")
            shell("wm density 480")
            val aiOnly = testName.methodName.startsWith("aiOnly")
            val actorId = if (aiOnly) 1 else 0
            val targetId = if (aiOnly) 2 else 1
            val config = GameConfig(playerCount = 3, difficulty = Difficulty.EASY,
                humanName = "Test", firstPlayer = actorId)
            // Three seats prevent a single elimination from revealing all surviving hands.
            // Seeds only prepare the fixture; the human duel is played through the UI below.
            before = (1L..20_000L).asSequence().map { GameEngine.newGame(config, it) }.first { state ->
                val hand = state.players[actorId].hand
                val kept = hand.singleOrNull { it != Card.BARON }
                Card.BARON in hand && kept != null && kept.value >= 5 &&
                    state.players[targetId].hand.single().value in 1..4
            }
            baronHandIndex = before.players[actorId].hand.indexOf(Card.BARON)
            actorValue = before.players[actorId].hand.first { it != Card.BARON }.value
            targetValue = before.players[targetId].hand.single().value
            val session = if (aiOnly) {
                val action = GameAction.Play(Card.BARON, targetId)
                val after = GameEngine.apply(before, action)
                val playback = UiPlayback(
                    id = after.nextEventId, actorId = actorId, actorName = before.players[actorId].name,
                    cardValue = Card.BARON.value, targetId = targetId, targetName = before.players[targetId].name,
                    messages = after.journal.filter { it.id >= before.nextEventId }.map { it.message },
                    eliminatedIds = listOf(targetId), phase = after.phase,
                    title = "${before.players[actorId].name} joue Baron", summary = "Duel secret",
                    scene = projectEffectScene(before, after, action)
                )
                SavedSession(game = after, playback = playback, lastPlayback = playback,
                    playerCount = 3, difficulty = "EASY", humanName = "Test", firstPlayer = actorId,
                    pace = GamePace.GUIDED, aiSeed = 20261008L)
            } else {
                SavedSession(game = before, playerCount = 3, difficulty = "EASY", humanName = "Test",
                    firstPlayer = actorId, pace = GamePace.GUIDED, aiSeed = 20261008L)
            }
            store().save(session)
        }

        override fun after() {
            shell("wm size reset")
            shell("wm density reset")
        }
    }

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun store() = SessionStore(InstrumentationRegistry.getInstrumentation().targetContext.filesDir)
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun resume() {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Reprendre la partie").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Reprendre la partie").performScrollTo().performClick()
        compose.waitUntil(20_000) { exists("main_0") }
    }

    private fun actionClick(tag: String) {
        compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("actions_scroll")))
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
    }

    private fun withinPrivateDuel(tag: String) = hasTestTag(tag) and
        hasAnyAncestor(hasTestTag("effet_prive"))

    private fun assertWithin(outer: Rect, inner: Rect, label: String) {
        assertTrue("$label doit être entièrement visible", inner.width > 0f && inner.height > 0f &&
            inner.left >= outer.left - 1f && inner.top >= outer.top - 1f &&
            inner.right <= outer.right + 1f && inner.bottom <= outer.bottom + 1f)
    }

    private fun assertSideBySide(privateDuel: Boolean) {
        compose.waitForIdle()
        val actor = if (privateDuel) withinPrivateDuel("baron_card_actor") else hasTestTag("baron_card_actor")
        val target = if (privateDuel) withinPrivateDuel("baron_card_target") else hasTestTag("baron_card_target")
        val left = compose.onNode(actor).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val right = compose.onNode(target).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val scene = if (privateDuel) withinPrivateDuel("effect_baron") else hasTestTag("effect_baron")
        val bounds = compose.onNode(scene).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertWithin(bounds, left, "La carte du joueur")
        assertWithin(bounds, right, "La carte de l'adversaire")
        assertTrue("Le Baron montre deux cartes côte à côte, sans superposition", left.right <= right.left + 1f)
        val overlap = min(left.bottom, right.bottom) - maxOf(left.top, right.top)
        assertTrue("Les deux cartes sont alignées sur la même rangée", overlap >= min(left.height, right.height) * .8f)
        val verdict = if (privateDuel) withinPrivateDuel("baron_verdict") else hasTestTag("baron_verdict")
        compose.onNode(verdict).assertIsDisplayed()
    }

    private fun playHumanDuel(screenshotName: String) {
        resume()
        val configuration = compose.activity.resources.configuration
        when {
            testName.methodName.contains("Landscape") -> assertTrue("Le test utilise réellement un écran paysage",
                configuration.screenWidthDp > configuration.screenHeightDp)
            testName.methodName.contains("Small") -> assertTrue("Le petit écran fait au plus 320dp de large",
                configuration.screenWidthDp <= 320)
        }
        compose.onNodeWithTag("main_$baronHandIndex").assertIsDisplayed().assertIsEnabled().performClick()
        actionClick("cible_1")
        actionClick("jouer_carte")
        compose.waitUntil(20_000) { exists("effet_prive") }
        assertSideBySide(privateDuel = true)
        compose.onNode(withinPrivateDuel("scene_card_$actorValue")).assertIsDisplayed()
        compose.onNode(withinPrivateDuel("scene_card_$targetValue")).assertIsDisplayed()
        compose.onNode(withinPrivateDuel("scene_card_hidden")).assertDoesNotExist()
        screenshot(screenshotName)
        // The private reveal and playback are separate, deliberate reading steps.
        compose.onNodeWithText("J'ai lu").assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(20_000) { !exists("effet_prive") }
        compose.onNodeWithTag("continuer_tour").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("main_0").assertIsDisplayed().assertIsNotEnabled()
        compose.waitUntil(20_000) { store().load()?.game?.players?.get(1)?.isEliminated == true }
        val session = requireNotNull(store().load())
        assertEquals("L'animation n'applique le Baron qu'une fois", 1,
            session.game?.players?.get(0)?.discard?.count { it == Card.BARON })
        assertTrue("Le duel élimine réellement la cible", session.game?.players?.get(1)?.isEliminated == true)
        assertEquals("Le rythme guidé ne lance pas encore l'IA", 0L, session.aiDecisionCount)
    }

    @Test
    fun humanBaronShowsBothCardsSideBySideInPortrait() = playHumanDuel("duel-baron")

    @Test
    fun humanBaronShowsBothCardsOnSmallPortrait() = playHumanDuel("duel-baron-petit")

    @Test
    fun humanBaronShowsBothCardsInLandscape() = playHumanDuel("duel-baron-paysage")

    @Test
    fun aiOnlyBaronKeepsTheWinningCardSecret() {
        resume()
        compose.waitUntil(20_000) { exists("effect_baron") }
        assertSideBySide(privateDuel = false)
        compose.onNodeWithTag("effet_prive").assertDoesNotExist()
        val actor = hasAnyAncestor(hasTestTag("baron_card_actor"))
        compose.onNode(hasTestTag("scene_card_hidden") and actor).assertIsDisplayed()
        compose.onNode(hasTestTag("scene_card_$actorValue") and hasAnyAncestor(hasTestTag("effect_baron")))
            .assertDoesNotExist()
        compose.onNode(hasTestTag("scene_card_$targetValue") and hasAnyAncestor(hasTestTag("baron_card_target")))
            .assertIsDisplayed()
        // The discarded losing card is public; the surviving AI's identity remains private.
        val scene = requireNotNull(requireNotNull(store().load()).playback?.scene)
        assertEquals(null, scene.participants[0].cardValue)
        assertEquals(targetValue, scene.participants[1].cardValue)
        compose.onNodeWithTag("continuer_tour").assertIsDisplayed().assertIsEnabled()
        screenshot("duel-baron-secret")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = if (output != null) File(output, "screenshots")
            else File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots")
        assertTrue("Le répertoire de captures doit être accessible", directory.isDirectory || directory.mkdirs())
        val file = File(directory, "$name.png")
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        assertTrue("La capture doit être écrite", file.isFile && file.length() > 0)
        instrumentation.sendStatus(2, Bundle().apply {
            putString("additionalTestOutputFile_$name", file.absolutePath)
        })
    }
}
