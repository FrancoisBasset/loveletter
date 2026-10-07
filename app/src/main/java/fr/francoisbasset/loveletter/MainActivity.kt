package fr.francoisbasset.loveletter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import fr.francoisbasset.loveletter.ui.LoveLetterApp

class MainActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LoveLetterApp(gameViewModel) }
    }

    override fun onStart() { super.onStart(); gameViewModel.setForeground(true) }
    override fun onStop() { gameViewModel.setForeground(false); super.onStop() }
}
