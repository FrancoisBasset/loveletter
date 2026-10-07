package fr.francoisbasset.loveletter

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.francoisbasset.loveletter.ui.LoveLetterRoot
import fr.francoisbasset.loveletter.ui.theme.LoveLetterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.rgb(66, 27, 44))
        )
        setContent {
            LoveLetterTheme {
                val model: GameViewModel = viewModel(factory = GameViewModel.factory(application))
                LoveLetterRoot(model)
            }
        }
    }
}
