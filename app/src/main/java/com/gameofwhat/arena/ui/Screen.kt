package com.gameofwhat.arena.ui

import com.gameofwhat.arena.game.GameEngine

/** Top-level navigation destinations. */
sealed interface Screen {
    data object Menu : Screen
    data object Lobby : Screen
    data class Game(val engine: GameEngine) : Screen
}
