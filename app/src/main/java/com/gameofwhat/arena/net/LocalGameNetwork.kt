package com.gameofwhat.arena.net

import com.gameofwhat.arena.game.EnemyState
import com.gameofwhat.arena.game.HitEvent
import com.gameofwhat.arena.game.PlayerState
import com.gameofwhat.arena.game.Phase
import com.gameofwhat.arena.game.RoomMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory loopback "network" for the offline practice mode. The local player is the
 * host and there are no remote players, but the full host simulation runs, so the game
 * is completely playable without any Firebase setup.
 */
class LocalGameNetwork(playerName: String) : GameNetwork {

    override val roomCode: String = "SOLO"
    override val localId: String = "local-player"
    override val isHost: Boolean = true

    private val _players = MutableStateFlow(
        mapOf(localId to PlayerState(id = localId, name = playerName, colorIndex = 0))
    )
    private val _enemies = MutableStateFlow<List<EnemyState>>(emptyList())
    private val _meta = MutableStateFlow(RoomMeta(phase = Phase.PLAYING, hostId = localId))

    override val players: StateFlow<Map<String, PlayerState>> = _players.asStateFlow()
    override val enemies: StateFlow<List<EnemyState>> = _enemies.asStateFlow()
    override val meta: StateFlow<RoomMeta> = _meta.asStateFlow()

    private val pendingHits = ArrayDeque<HitEvent>()

    override fun sendLocalPlayer(p: PlayerState) {
        _players.value = _players.value.toMutableMap().apply { put(p.id, p) }
    }

    override fun reportHit(enemyId: String, damage: Int) {
        synchronized(pendingHits) { pendingHits.add(HitEvent(enemyId, damage)) }
    }

    override fun publishEnemies(enemies: List<EnemyState>) {
        _enemies.value = enemies
    }

    override fun publishMeta(meta: RoomMeta) {
        _meta.value = meta
    }

    override fun drainHits(): List<HitEvent> = synchronized(pendingHits) {
        val out = pendingHits.toList()
        pendingHits.clear()
        out
    }

    override fun close() {
        pendingHits.clear()
    }
}
