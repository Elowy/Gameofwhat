package com.gameofwhat.arena

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gameofwhat.arena.ui.GameScreen
import com.gameofwhat.arena.ui.LobbyScreen
import com.gameofwhat.arena.ui.MenuScreen
import com.gameofwhat.arena.ui.Screen
import com.gameofwhat.arena.ui.theme.GameOfWhatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameOfWhatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: AppViewModel = viewModel()
                    val screen by vm.screen

                    when (val s = screen) {
                        is Screen.Menu -> MenuScreen(vm)
                        is Screen.Lobby -> LobbyScreen(vm)
                        is Screen.Game -> GameScreen(vm, s.engine)
                    }
                }
            }
        }
    }
}
