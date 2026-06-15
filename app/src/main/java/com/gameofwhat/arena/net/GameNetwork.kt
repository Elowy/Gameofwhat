package com.gameofwhat.arena.net

import com.gameofwhat.arena.game.EnemyState
import com.gameofwhat.arena.game.HitEvent
import com.gameofwhat.arena.game.PlayerState
import com.gameofwhat.arena.game.PowerUp
import com.gameofwhat.arena.game.RoomMeta
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport-agnostic contract for a single multiplayer room.
 *
 * The same interface backs both the offline [LocalGameNetwork] (in-memory loopback,
 * single player) and the online [FirebaseGameNetwork]. The game engine talks only to
 * this interface, so gameplay code is identical online and offline.
 *
 * Authority model:
 *  - Each client owns its own player node and pushes it via [sendLocalPlayer].
 *  - The host owns enemies + match metadata and pushes them via [publishEnemies] /
 *    [publishMeta], and applies damage from [drainHits].
 *  - Any client reports damage it dealt locally via [reportHit]; the host reconciles.
 */
interface GameNetwork {
    val roomCode: String
    val localId: String
    val isHost: Boolean

    /** All players currently in the room, keyed by id (includes the local player). */
    val players: StateFlow<Map<String, PlayerState>>

    /** Enemies as last published by the host. */
    val enemies: StateFlow<List<EnemyState>>

    /** Shared match metadata as last published by the host. */
    val meta: StateFlow<RoomMeta>

    /** Power-ups currently on the ground, as last published by the host. */
    val powerups: StateFlow<List<PowerUp>>

    /** Publish this client's player state (rate-limited by the caller). */
    fun sendLocalPlayer(p: PlayerState)

    /** Report damage this client dealt to an enemy. Applied by the host. */
    fun reportHit(enemyId: String, damage: Int)

    /** Report that this client picked up a power-up. Removed by the host. */
    fun reportPickup(powerUpId: String)

    // ---- Host-only ----
    fun publishEnemies(enemies: List<EnemyState>)
    fun publishMeta(meta: RoomMeta)
    fun publishPowerups(powerups: List<PowerUp>)

    /** Drain and clear all hit events received since the last call (host only). */
    fun drainHits(): List<HitEvent>

    /** Drain and clear all power-up pickup claims since the last call (host only). */
    fun drainPickups(): List<String>

    /** Leave the room and release all listeners/resources. */
    fun close()
}
