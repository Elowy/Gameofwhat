package com.gameofwhat.arena.game

/** Tunable gameplay constants. World units are arena-space (see WORLD_SIZE). */
object GameConfig {
    // The arena is a square of this many world units, shown fully on screen.
    const val WORLD_SIZE = 1000f

    // Player
    const val PLAYER_MAX_HP = 100
    const val PLAYER_RADIUS = 22f
    const val PLAYER_SPEED = 230f           // world units / second
    const val FIRE_INTERVAL = 0.28f         // seconds between auto-shots
    const val FIRE_RANGE = 430f             // won't shoot past this distance
    const val BULLET_SPEED = 720f
    const val BULLET_DAMAGE = 25
    const val BULLET_LIFE = 1.1f            // seconds
    const val CONTACT_DAMAGE = 12           // per contact tick
    const val CONTACT_INTERVAL = 0.6f       // seconds between contact damage ticks

    // Enemies (type 0 = goblin, 1 = wolf, 2 = ogre)
    const val ENEMY_RADIUS = 19f
    const val MAX_ENEMIES = 14
    const val GRUNT_SPEED = 70f
    const val FAST_SPEED = 130f
    const val TANK_SPEED = 45f

    /** Per-type collision/render radius. */
    fun enemyRadius(type: Int): Float = when (type) {
        1 -> 16f   // wolf
        2 -> 30f   // ogre
        else -> 19f // goblin
    }

    // Networking cadence
    const val PLAYER_SYNC_HZ = 15f
    const val ENEMY_SYNC_HZ = 12f
}
