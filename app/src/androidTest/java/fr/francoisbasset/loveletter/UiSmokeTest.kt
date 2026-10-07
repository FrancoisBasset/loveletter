package fr.francoisbasset.loveletter

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import fr.francoisbasset.loveletter.core.*
import fr.francoisbasset.loveletter.persistence.SavedSession
import fr.francoisbasset.loveletter.persistence.SessionStore
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.io.File

/** Actual Android navigation and a complete round from a seeded saved session. */
@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule(order = 0)
    val fixture = object : ExternalResource() {
        override fun before() {
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            SessionStore(directory).save(SavedSession(
                game = GameEngine.newGame(GameConfig(playerCount = 2, difficulty = Difficulty.EASY,
                    humanName = "Test", firstPlayer = 0), 20261007L),
                playerCount = 2, difficulty = "EASY", humanName = "Test", firstPlayer = 0
            ))
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots")
        directory.mkdirs()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test
    fun savedGameIsPlayableThroughACompleteRoundAndNextDistribution() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Reprendre la partie").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reprendre la partie").performScrollTo().performClick()
        compose.waitUntil(20_000) { exists("main_0") }
        screenshot("table")

        var decisions = 0
        while (!exists("manche_suivante") && !exists("nouvelle_partie_fin") && decisions < 100) {
            compose.waitUntil(20_000) {
                exists("manche_suivante") || exists("nouvelle_partie_fin") ||
                    compose.onAllNodesWithText("J'ai lu").fetchSemanticsNodes().isNotEmpty() ||
                    exists("chancelier_garder_0") ||
                    compose.onAllNodesWithText("À vous de jouer").fetchSemanticsNodes().isNotEmpty()
            }
            if (exists("manche_suivante") || exists("nouvelle_partie_fin")) break
            if (compose.onAllNodesWithText("J'ai lu").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithText("J'ai lu").performClick()
            } else if (exists("chancelier_garder_0")) {
                click("chancelier_garder_0")
                click("chancelier_ordre_0")
                click("chancelier_confirmer")
            } else {
                click("main_0")
                // A mandatory Countess can make the first card inspection-only.
                if (compose.onAllNodesWithText("Compris").fetchSemanticsNodes().isNotEmpty()) {
                    compose.onNodeWithText("Compris").performClick()
                    click("main_1")
                }
                val target = (0..5).firstOrNull { exists("cible_$it") }
                if (target != null) click("cible_$target")
                val guess = (0..9).firstOrNull { exists("annonce_$it") }
                if (guess != null) click("annonce_$guess")
                else if (exists("jouer_carte")) click("jouer_carte")
            }
            decisions++
            compose.waitForIdle()
        }
        assertTrue("La manche doit se terminer après des décisions légales", decisions < 100)
        screenshot("fin-manche")
        compose.onNodeWithTag("manche_suivante").performScrollTo().assertExists().performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("MANCHE 2").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeConfigurationRulesAndEncyclopediaAreAccessible() {
        compose.waitUntil(20_000) { exists("nouvelle_partie") }
        screenshot("accueil")
        compose.onNodeWithText("Règles").performScrollTo().performClick()
        compose.onNodeWithText("L'objectif").assertExists()
        compose.onNodeWithContentDescription("Retour").performClick()
        compose.onNodeWithText("Cartes").performScrollTo().performClick()
        compose.onNodeWithText("Les personnages").assertExists()
        screenshot("encyclopedie")
        compose.onNodeWithContentDescription("Retour").performClick()
        click("nouvelle_partie")
        compose.onNodeWithText("6 joueurs").performScrollTo().performClick()
        click("lancer_partie")
        compose.waitUntil(20_000) { exists("main_0") }
        compose.onNodeWithText("MANCHE 1").assertExists()
    }
}
