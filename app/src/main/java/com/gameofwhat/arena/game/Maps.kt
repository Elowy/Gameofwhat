package com.gameofwhat.arena.game

/** A circular, impassable obstacle in world coordinates. */
data class Obstacle(val x: Float, val y: Float, val r: Float)

/**
 * An arena variation. Colours are stored as ARGB [Long] values so this stays free of
 * any UI dependency; the renderer wraps them in Compose `Color`.
 *
 * [decoType]: 0 = stone pillar, 1 = tree, 2 = crate/block, 3 = jagged rock.
 */
data class GameMap(
    val id: Int,
    val name: String,
    val floor: Long,
    val grid: Long,
    val border: Long,
    val accent: Long,
    val decoType: Int,
    val obstacles: List<Obstacle>,
)

object Maps {

    val all: List<GameMap> = listOf(
        // 0 — Castle courtyard: stone floor, four corner pillars.
        GameMap(
            id = 0,
            name = "Várudvar",
            floor = 0xFF2B2B33,
            grid = 0x14FFFFFF,
            border = 0xFFB0A07A,
            accent = 0xFFD7C9A0,
            decoType = 0,
            obstacles = listOf(
                Obstacle(260f, 260f, 52f),
                Obstacle(740f, 260f, 52f),
                Obstacle(260f, 740f, 52f),
                Obstacle(740f, 740f, 52f),
            ),
        ),
        // 1 — Dark forest: grass floor, scattered trees.
        GameMap(
            id = 1,
            name = "Sötét erdő",
            floor = 0xFF15301C,
            grid = 0x12000000,
            border = 0xFF3E7A4B,
            accent = 0xFF6BBF73,
            decoType = 1,
            obstacles = listOf(
                Obstacle(220f, 300f, 46f),
                Obstacle(800f, 250f, 50f),
                Obstacle(660f, 760f, 48f),
                Obstacle(300f, 820f, 44f),
                Obstacle(500f, 170f, 42f),
                Obstacle(830f, 560f, 46f),
                Obstacle(170f, 640f, 44f),
            ),
        ),
        // 2 — Dungeon: dark stone, blocky pillars forming lanes around an open centre.
        GameMap(
            id = 2,
            name = "Tömlöc",
            floor = 0xFF1A1A22,
            grid = 0x16FFFFFF,
            border = 0xFF6E6E86,
            accent = 0xFF9AA0C0,
            decoType = 2,
            obstacles = listOf(
                Obstacle(500f, 230f, 50f),
                Obstacle(500f, 770f, 50f),
                Obstacle(230f, 500f, 50f),
                Obstacle(770f, 500f, 50f),
                Obstacle(300f, 300f, 40f),
                Obstacle(700f, 700f, 40f),
            ),
        ),
        // 3 — Lava cavern: dark rock floor, jagged spires.
        GameMap(
            id = 3,
            name = "Lávabarlang",
            floor = 0xFF241016,
            grid = 0x1AFF6E40,
            border = 0xFFFF7043,
            accent = 0xFFFFB300,
            decoType = 3,
            obstacles = listOf(
                Obstacle(330f, 340f, 54f),
                Obstacle(700f, 360f, 46f),
                Obstacle(360f, 720f, 46f),
                Obstacle(690f, 690f, 54f),
                Obstacle(520f, 500f, 40f),
            ),
        ),
    )

    fun byId(id: Int): GameMap = all.getOrElse(id) { all[0] }
}
