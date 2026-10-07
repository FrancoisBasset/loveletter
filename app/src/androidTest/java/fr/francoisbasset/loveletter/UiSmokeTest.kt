package fr.francoisbasset.loveletter

import android.graphics.Bitmap
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.francoisbasset.loveletter.core.*
import fr.francoisbasset.loveletter.persistence.SavedSession
import fr.francoisbasset.loveletter.persistence.SessionStore
import fr.francoisbasset.loveletter.ui.GamePace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.File

/** A real 360dp Android viewport, legal decisions and public turn playback. */
@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule(order = 0)
    val testName = TestName()

    private var safeHandIndex = 0

    @get:Rule(order = 1)
    val fixture = object : ExternalResource() {
        override fun before() {
            shell("wm size 1080x2400")
            shell("wm density 480")
            val config = GameConfig(playerCount = 2, difficulty = Difficulty.EASY,
                humanName = "Test", firstPlayer = 0)
            // Only the human observation determines the fixture's first legal decision.
            val game = (79L..2079L).asSequence().map { GameEngine.newGame(config, it) }.first { state ->
                GameEngine.observe(state, 0).legalActions.filterIsInstance<GameAction.Play>().any {
                    it.card in listOf(Card.ESPIONNE, Card.SERVANTE, Card.COMTESSE)
                }
            }
            val observation = GameEngine.observe(game, 0)
            val safe = observation.legalActions.filterIsInstance<GameAction.Play>().first {
                it.card in listOf(Card.ESPIONNE, Card.SERVANTE, Card.COMTESSE)
            }
            safeHandIndex = observation.ownHand.indexOf(safe.card)
            store().save(SavedSession(game = game, playerCount = 2, difficulty = "EASY",
                humanName = "Test", firstPlayer = 0, aiSeed = 20261007L,
                pace = if (testName.methodName.startsWith("pause")) GamePace.NORMAL else GamePace.GUIDED))
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
    private fun saved(): SavedSession = requireNotNull(store().load())
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun textExists(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun enabledHand(): Int? = (0..2).firstOrNull { index ->
        compose.onAllNodes(hasTestTag("main_$index") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }

    /** Fixed board controls must be visible without moving the viewport. */
    private fun click(tag: String) {
        compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().performClick()
    }

    /** Only the deliberately scrollable, bounded decision panel may scroll. */
    private fun actionClick(tag: String) {
        compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("actions_scroll")))
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
    }

    private fun resume() {
        compose.waitUntil(20_000) { textExists("Reprendre la partie") }
        // The home screen is an explicitly scrollable reference/configuration screen.
        compose.onNodeWithText("Reprendre la partie").performScrollTo().performClick()
        compose.waitUntil(20_000) { exists("main_0") }
    }

    private fun assertBoardEntirelyVisible(players: Int) {
        compose.waitForIdle()
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val tags = (0 until players).map { "seat_$it" } + listOf("main_0", "main_1").filter { exists(it) }
        assertTrue("La main humaine doit rester dans le même écran que tous les sièges", "main_0" in tags)
        for (tag in tags) {
            val node = compose.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$tag doit être entièrement visible dans le même viewport 360dp",
                bounds.width > 0f && bounds.height > 0f &&
                    bounds.left >= viewport.left - 1f && bounds.top >= viewport.top - 1f &&
                    bounds.right <= viewport.right + 1f && bounds.bottom <= viewport.bottom + 1f)
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // AGP's UTP host plugin pulls this directory before uninstalling the tested app.
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = if (output != null) File(output, "screenshots")
            else File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots")
        assertTrue("Le répertoire de captures doit être accessible", directory.isDirectory || directory.mkdirs())
        val file = File(directory, "$name.png")
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Capture Android indisponible" }
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        assertTrue("La capture doit être écrite", file.isFile && file.length() > 0)
        instrumentation.sendStatus(2, Bundle().apply {
            putString("additionalTestOutputFile_$name", file.absolutePath)
        })
    }

    /** Fingerprint deliberately excludes every secret card and private note. */
    private fun publicProgress(session: SavedSession): List<Any?> = listOf(
        session.game?.journal?.map { it.id }, session.aiDecisionCount,
        session.game?.currentPlayer, session.game?.phase, session.playback?.id
    )

    private fun dismissPrivateNotice() {
        if (textExists("J'ai lu")) compose.onNodeWithText("J'ai lu").assertIsDisplayed().performClick()
    }

    private fun playSafeHumanCard(capture: Boolean = false) {
        click("main_$safeHandIndex")
        if (capture) {
            assertBoardEntirelyVisible(2)
            screenshot("carte-selectionnee")
        }
        actionClick("jouer_carte")
        compose.waitUntil(20_000) { saved().playback?.actorId == 0 }
    }

    @Test
    fun guidedPlaybackRequiresContinueAndCompletesARound() {
        resume()
        assertBoardEntirelyVisible(2)
        screenshot("plateau-2")
        playSafeHumanCard(capture = true)
        val humanProgress = publicProgress(saved())
        compose.onNodeWithTag("main_0").assertIsNotEnabled()
        compose.onNodeWithTag("continuer_tour").assertIsDisplayed()
        SystemClock.sleep(4500)
        compose.waitForIdle()
        assertEquals("En rythme guidé aucune autre action ne doit suivre sans Continuer",
            humanProgress, publicProgress(saved()))
        click("continuer_tour")
        compose.waitUntil(20_000) { saved().aiDecisionCount > 0 && saved().playback?.actorId == 1 }
        dismissPrivateNotice()
        screenshot("lecture-ia")
        val aiProgress = publicProgress(saved())
        SystemClock.sleep(2500)
        compose.waitForIdle()
        assertEquals("L'action IA reste à lire jusqu'à Continuer", aiProgress, publicProgress(saved()))

        var decisions = 0
        while (!exists("manche_suivante") && !exists("nouvelle_partie_fin") && decisions < 100) {
            compose.waitUntil(20_000) {
                exists("continuer_tour") || exists("manche_suivante") || exists("nouvelle_partie_fin") ||
                    textExists("J'ai lu") || exists("chancelier_garder_0") || enabledHand() != null
            }
            when {
                textExists("J'ai lu") -> dismissPrivateNotice()
                exists("continuer_tour") -> click("continuer_tour")
                exists("manche_suivante") || exists("nouvelle_partie_fin") -> break
                exists("chancelier_garder_0") -> {
                    actionClick("chancelier_garder_0")
                    actionClick("chancelier_ordre_0")
                    actionClick("chancelier_confirmer")
                }
                else -> {
                    click("main_${requireNotNull(enabledHand())}")
                    val target = (0..5).firstOrNull { exists("cible_$it") }
                    if (target != null) actionClick("cible_$target")
                    val guess = (0..9).firstOrNull { exists("annonce_$it") }
                    if (guess != null) actionClick("annonce_$guess")
                    else actionClick("jouer_carte")
                }
            }
            decisions++
            compose.waitForIdle()
        }
        assertTrue("Une manche complète doit se terminer par des décisions autorisées", decisions < 100)
        screenshot("fin-manche")
        click("manche_suivante")
        compose.waitUntil(20_000) { saved().game?.roundNumber == 2 && exists("continuer_tour") }
        compose.onAllNodesWithText("Manche 2", substring = true).onFirst().assertIsDisplayed()
        click("continuer_tour")
    }

    @Test
    fun homeRulesAndSixPlayerBoardKeepEverySeatAndHumanHandVisible() {
        compose.waitUntil(20_000) { exists("nouvelle_partie") }
        screenshot("accueil")
        compose.onNodeWithText("Règles").performScrollTo().performClick()
        compose.onNodeWithText("L'objectif").assertExists()
        compose.onNodeWithContentDescription("Retour").performClick()
        compose.onNodeWithText("Cartes").performScrollTo().performClick()
        compose.onNodeWithText("Les personnages").assertExists()
        screenshot("encyclopedie")
        compose.onNodeWithContentDescription("Retour").performClick()
        compose.onNodeWithTag("nouvelle_partie").performScrollTo().performClick()
        compose.onNodeWithText("6 joueurs").performScrollTo().performClick()
        compose.onNodeWithTag("lancer_partie").performScrollTo().performClick()
        compose.waitUntil(20_000) { exists("seat_5") && exists("continuer_tour") }
        assertBoardEntirelyVisible(6)
        screenshot("plateau-6")
        // The initial distribution has its own guided reading step.
        compose.onNodeWithTag("main_0").assertIsNotEnabled()
        click("continuer_tour")
        compose.waitUntil(20_000) { enabledHand() != null }
        click("main_${requireNotNull(enabledHand())}")
        assertBoardEntirelyVisible(6)
        screenshot("plateau-6-action")
    }

    @Test
    fun pauseStopsAutomaticReadingAndAiUntilResumed() {
        resume()
        playSafeHumanCard()
        click("pause_partie")
        compose.waitUntil(20_000) { saved().paused && saved().playback != null }
        val paused = publicProgress(saved())
        compose.onNodeWithTag("main_0").assertIsNotEnabled()
        SystemClock.sleep(4500)
        compose.waitForIdle()
        assertEquals("Pause bloque la lecture automatique et toute nouvelle action IA", paused, publicProgress(saved()))
        click("pause_partie")
        compose.waitUntil(20_000) { !saved().paused && saved().aiDecisionCount > 0 }
        assertTrue("Reprendre réactive réellement les adversaires", saved().aiDecisionCount > 0)
    }
}
