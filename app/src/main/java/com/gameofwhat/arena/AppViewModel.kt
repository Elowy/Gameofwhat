package com.gameofwhat.arena

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gameofwhat.arena.game.GameEngine
import com.gameofwhat.arena.game.Phase
import com.gameofwhat.arena.net.FirebaseGameNetwork
import com.gameofwhat.arena.net.GameNetwork
import com.gameofwhat.arena.net.LocalGameNetwork
import com.gameofwhat.arena.net.RoomFullException
import com.gameofwhat.arena.net.RoomNotFoundException
import com.gameofwhat.arena.ui.Screen
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Holds navigation state and owns the active [GameNetwork]/[GameEngine]. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val _screen = mutableStateOf<Screen>(Screen.Menu)
    val screen: State<Screen> = _screen

    private val _playerName = mutableStateOf("")
    val playerName: State<String> = _playerName

    private val _busy = mutableStateOf(false)
    val busy: State<Boolean> = _busy

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _selectedMapId = mutableStateOf(0)
    val selectedMapId: State<Int> = _selectedMapId

    private val _selectedClassId = mutableStateOf(0)
    val selectedClassId: State<Int> = _selectedClassId

    /** True when a real Firebase config (google-services.json) is present. */
    val firebaseAvailable: Boolean =
        FirebaseApp.getApps(getApplication()).isNotEmpty()

    private var net: GameNetwork? = null
    private var metaJob: Job? = null

    fun setPlayerName(name: String) { _playerName.value = name.take(14) }

    fun setSelectedMap(id: Int) { _selectedMapId.value = id }

    /** Pick the local player's class. In the lobby this also updates the shared node. */
    fun setSelectedClass(id: Int) {
        _selectedClassId.value = id
        val n = net ?: return
        val p = n.players.value[n.localId] ?: return
        n.sendLocalPlayer(p.copy(classId = id))
    }

    fun clearError() { _error.value = null }

    /** Offline single-player practice — no Firebase required. */
    fun startPractice() {
        cleanup()
        val n = LocalGameNetwork(
            _playerName.value.ifBlank { "You" }, _selectedMapId.value, _selectedClassId.value,
        )
        net = n
        _screen.value = Screen.Game(GameEngine(n))
    }

    fun createRoom() {
        if (!ensureFirebase()) return
        _busy.value = true
        viewModelScope.launch {
            try {
                cleanup()
                val n = FirebaseGameNetwork.createRoom(
                    _playerName.value, _selectedMapId.value, _selectedClassId.value,
                )
                net = n
                observeMeta(n)
                _screen.value = Screen.Lobby
            } catch (e: Exception) {
                _error.value = "Nem sikerült létrehozni a szobát: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun joinRoom(code: String) {
        if (!ensureFirebase()) return
        if (code.isBlank()) { _error.value = "Adj meg egy szobakódot."; return }
        _busy.value = true
        viewModelScope.launch {
            try {
                cleanup()
                val n = FirebaseGameNetwork.joinRoom(code, _playerName.value, _selectedClassId.value)
                net = n
                observeMeta(n)
                _screen.value = Screen.Lobby
            } catch (e: RoomNotFoundException) {
                _error.value = "Nincs ilyen szoba: ${code.uppercase()}"
            } catch (e: RoomFullException) {
                _error.value = "A szoba megtelt."
            } catch (e: Exception) {
                _error.value = "Csatlakozás sikertelen: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    /** Host changes the arena for everyone in the lobby. */
    fun hostSetMap(id: Int) {
        val n = net ?: return
        if (!n.isHost) return
        _selectedMapId.value = id
        n.publishMeta(n.meta.value.copy(mapId = id))
    }

    /** Host starts the match for everyone in the lobby. */
    fun hostStartMatch() {
        val n = net ?: return
        if (!n.isHost) return
        n.publishMeta(n.meta.value.copy(phase = Phase.PLAYING, hostId = n.localId))
        enterGame()
    }

    val currentNet: GameNetwork? get() = net

    fun backToMenu() {
        cleanup()
        _screen.value = Screen.Menu
    }

    private fun observeMeta(n: GameNetwork) {
        metaJob?.cancel()
        metaJob = viewModelScope.launch {
            n.meta.collectLatest { meta ->
                // Non-host clients follow the host into the match automatically.
                if (!n.isHost && meta.phase == Phase.PLAYING &&
                    _screen.value is Screen.Lobby
                ) {
                    enterGame()
                }
            }
        }
    }

    private fun enterGame() {
        val n = net ?: return
        if (_screen.value is Screen.Game) return
        _screen.value = Screen.Game(GameEngine(n))
    }

    private fun ensureFirebase(): Boolean {
        if (!firebaseAvailable) {
            _error.value = "Az online mód Firebase beállítást igényel " +
                "(google-services.json). Addig próbáld ki a Gyakorlás módot!"
            return false
        }
        return true
    }

    private fun cleanup() {
        metaJob?.cancel(); metaJob = null
        (screen.value as? Screen.Game)?.engine?.dispose() ?: net?.close()
        net = null
    }

    override fun onCleared() {
        cleanup()
        super.onCleared()
    }
}
