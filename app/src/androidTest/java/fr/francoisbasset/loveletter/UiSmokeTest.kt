package fr.francoisbasset.loveletter

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.francoisbasset.loveletter.engine.Card
import fr.francoisbasset.loveletter.engine.Difficulty
import fr.francoisbasset.loveletter.engine.EventType
import fr.francoisbasset.loveletter.engine.GameConfig
import fr.francoisbasset.loveletter.engine.GameEngine
import fr.francoisbasset.loveletter.engine.PlayCard
import fr.francoisbasset.loveletter.persistence.GameRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Compose taps and activity recreation; engine access is only for setup and assertions. */
@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: GameViewModel

    @Before fun awaitInitialLoad() {
        obtainModel()
        waitForScreen(AppScreen.HOME)
        assertNull(model.uiState.value.error)
    }

    private fun obtainModel() {
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[GameViewModel::class.java]
        }
    }

    private fun waitForScreen(screen: AppScreen, timeout: Long = 30_000) {
        compose.waitUntil(timeoutMillis = timeout) {
            val state = model.uiState.value
            state.screen == screen && !state.busy
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.filesDir, name).outputStream().use { output ->
            assertTrue("PNG capture must be written", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun openSetup() {
        compose.onNodeWithText("Nouvelle partie").performScrollTo().performClick()
        waitForScreen(AppScreen.SETUP)
    }

    private fun configureAndStart(players: Int, difficultyLabel: String, seed: Long) {
        compose.onNodeWithText(players.toString()).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText(difficultyLabel).performScrollTo().performClick()
        compose.onNodeWithText("Graine aléatoire (facultatif)")
            .performScrollTo().performTextReplacement(seed.toString())
        if (players == 6) capture("qa-setup.png")
        val replacesSavedGame = model.uiState.value.hasSavedGame
        compose.onNodeWithText("Entrer à la cour").performScrollTo().assertIsEnabled().performClick()
        if (replacesSavedGame) compose.onNodeWithText("Nouvelle partie").performClick()
        waitForScreen(AppScreen.GAME)
        assertNull(model.uiState.value.error)
    }

    @Test fun homeRulesAndCardEncyclopediaAreNavigable() {
        compose.onNodeWithText("Love Letter").assertIsDisplayed()
        capture("qa-home.png")
        compose.onNodeWithText("Les règles").performScrollTo().performClick()
        waitForScreen(AppScreen.RULES)
        compose.onNodeWithText("Les règles").assertIsDisplayed()
        compose.onNodeWithText("Découvrir les dix personnages").performScrollTo().performClick()
        waitForScreen(AppScreen.CARDS)
        compose.onNodeWithText("Les personnages").assertIsDisplayed()
        compose.onNodeWithText("Chancelier").performScrollTo().performClick()
        compose.onNodeWithText(Card.CHANCELLOR.explanation).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Retour").performScrollTo().performClick()
        waitForScreen(AppScreen.HOME)
        compose.onNodeWithText("Nouvelle partie").performScrollTo().assertIsDisplayed()
    }

    @Test fun setupStartsSixPlayerHardGameWithChosenSeed() {
        openSetup()
        configureAndStart(players = 6, difficultyLabel = "Difficile", seed = 812345L)
        val ui = model.uiState.value
        assertEquals(6, ui.config?.playerCount)
        assertEquals(Difficulty.HARD, ui.config?.difficulty)
        assertEquals(812345L, ui.config?.seed)
        assertEquals(6, ui.game?.players?.size)
        assertEquals(0, ui.game?.currentPlayer)
        assertEquals(2, ui.game?.hand?.size)
        compose.onNodeWithText("Objectif : 3 pions Faveur").performScrollTo().assertIsDisplayed()
    }

    @Test fun legalHandmaidPlaySurvivesSavePauseRecreationAndResume() {
        // Select a reproducible deal with one legal, unambiguous Servante card.
        val seed = (1L..10_000L).first { candidate ->
            val game = GameEngine.newGame(GameConfig(3, Difficulty.NORMAL, candidate))
            game.players[0].hand.count { it == Card.HANDMAID } == 1 &&
                GameEngine.legalActions(game).filterIsInstance<PlayCard>().any {
                    game.players[0].hand[it.cardIndex] == Card.HANDMAID
                }
        }
        openSetup()
        configureAndStart(players = 3, difficultyLabel = "Normale", seed = seed)
        compose.onNodeWithText("VOTRE MAIN").performScrollTo()
        compose.onNodeWithContentDescription("Servante, valeur 4.", substring = true)
            .performScrollTo().performClick()
        compose.onNodeWithText(Card.HANDMAID.explanation).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Jouer Servante").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(timeoutMillis = 30_000) {
            val ui = model.uiState.value
            !ui.busy && ui.game?.history?.any {
                it.type == EventType.PLAY && it.actorId == 0 && it.card == Card.HANDMAID
            } == true
        }
        assertNull(model.uiState.value.error)
        compose.onNodeWithText("Manche ${requireNotNull(model.uiState.value.game).roundNumber}").performScrollTo()
        capture("qa-game.png")
        compose.onNodeWithText("Pause").performScrollTo().performClick()
        compose.onNodeWithText("Accueil").performClick()
        waitForScreen(AppScreen.HOME)
        val paused = requireNotNull(model.uiState.value.game)
        assertTrue(model.uiState.value.hasSavedGame)

        // Verify the actual on-disk save, beyond ViewModel retention during recreation.
        val saved = GameRepository(compose.activity.filesDir).load()
        assertNotNull(saved)
        assertEquals(paused, GameEngine.observation(requireNotNull(saved).state, 0))

        compose.activityRule.scenario.recreate()
        obtainModel()
        waitForScreen(AppScreen.HOME)
        compose.onNodeWithText("Reprendre la partie").performScrollTo().performClick()
        waitForScreen(AppScreen.GAME)
        assertEquals(paused, model.uiState.value.game)
        assertNull(model.uiState.value.error)
        compose.onNodeWithText("Manche ${paused.roundNumber}").performScrollTo().assertIsDisplayed()
    }
}
