package com.gameofwhat.arena.game

import kotlin.math.hypot

/** Simple 2D vector in world coordinates. */
data class Vec2(val x: Float = 0f, val y: Float = 0f) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    fun length() = hypot(x, y)
    fun normalized(): Vec2 {
        val l = length()
        return if (l < 1e-4f) Vec2(0f, 0f) else Vec2(x / l, y / l)
    }
}

fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float = hypot(ax - bx, ay - by)

/** Phases of a room/match. Stored as strings so they map cleanly to the database. */
object Phase {
    const val LOBBY = "LOBBY"
    const val PLAYING = "PLAYING"
    const val GAMEOVER = "GAMEOVER"
}

/** Network-shared state of a single player. Immutable; defaults make it DB-friendly. */
data class PlayerState(
    val id: String = "",
    val name: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val angle: Float = 0f,
    val hp: Int = GameConfig.PLAYER_MAX_HP,
    val maxHp: Int = GameConfig.PLAYER_MAX_HP,
    val alive: Boolean = true,
    val colorIndex: Int = 0,
    val score: Int = 0,
)

/** Network-shared state of an enemy. Owned/published by the host. */
data class EnemyState(
    val id: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val hp: Int = 0,
    val maxHp: Int = 1,
    val type: Int = 0, // 0 = grunt, 1 = fast, 2 = tank
)

/** Shared match metadata. Owned/published by the host. */
data class RoomMeta(
    val phase: String = Phase.LOBBY,
    val hostId: String = "",
    val wave: Int = 0,
    val score: Int = 0,
)

/** A damage event reported by a client and applied by the host. */
data class HitEvent(val enemyId: String = "", val damage: Int = 0)

/** Purely local, visual projectile. Never travels over the network. */
data class Bullet(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    var life: Float,
    val ownerColorIndex: Int,
)

/** A short-lived hit/explosion spark for visual feedback. */
data class Spark(var x: Float, var y: Float, var life: Float, val max: Float)
