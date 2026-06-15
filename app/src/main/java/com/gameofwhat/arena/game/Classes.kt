package com.gameofwhat.arena.game

/** A playable class. Stats are the base values before level scaling is applied. */
data class ClassDef(
    val id: Int,
    val name: String,
    val blurb: String,
    val maxHp: Int,
    val fireInterval: Float,   // seconds between attacks
    val range: Float,          // world units
    val damage: Int,
    val bulletSpeed: Float,
    val moveSpeed: Float,
    val melee: Boolean = false,        // warrior: cleave instead of projectiles
    val pierce: Boolean = false,       // warlock: shots pass through enemies
    val healAuraRadius: Float = 0f,    // priest: heals allies in radius
    val healAuraPerSec: Float = 0f,
    val selfRegenPerSec: Float = 0f,   // paladin
    val color: Long,
)

object Classes {
    val all: List<ClassDef> = listOf(
        ClassDef(
            id = 0, name = "Vadász", blurb = "Gyors, távolsági íjász",
            maxHp = 90, fireInterval = 0.22f, range = 470f, damage = 20,
            bulletSpeed = 780f, moveSpeed = 245f, color = 0xFF66BB6A,
        ),
        ClassDef(
            id = 1, name = "Harcos", blurb = "Közelharc, sok életerő, suhintás",
            maxHp = 170, fireInterval = 0.45f, range = 110f, damage = 42,
            bulletSpeed = 0f, moveSpeed = 235f, melee = true, color = 0xFFEF5350,
        ),
        ClassDef(
            id = 2, name = "Paládin", blurb = "Páncélos tank, lassú öngyógyulás",
            maxHp = 190, fireInterval = 0.4f, range = 300f, damage = 24,
            bulletSpeed = 640f, moveSpeed = 215f, selfRegenPerSec = 4f, color = 0xFFFFCA28,
        ),
        ClassDef(
            id = 3, name = "Pap", blurb = "Gyógyító aura a társaknak",
            maxHp = 105, fireInterval = 0.5f, range = 360f, damage = 15,
            bulletSpeed = 680f, moveSpeed = 225f,
            healAuraRadius = 230f, healAuraPerSec = 6f, color = 0xFFE0F0FF,
        ),
        ClassDef(
            id = 4, name = "Boszorkány", blurb = "Átütő, nagy sebzésű mágia",
            maxHp = 85, fireInterval = 0.5f, range = 440f, damage = 38,
            bulletSpeed = 560f, moveSpeed = 220f, pierce = true, color = 0xFFAB47BC,
        ),
    )

    fun byId(id: Int): ClassDef = all.getOrElse(id) { all[0] }
}

/** Shared team progression: XP and levels (1..[MAX_LEVEL]). */
object Progression {
    const val MAX_LEVEL = 10

    // Cumulative XP required to *reach* each level (index 0 unused).
    private val thresholds = intArrayOf(0, 0, 60, 150, 280, 450, 670, 950, 1300, 1750, 2300)

    fun levelForXp(xp: Int): Int {
        var lvl = 1
        for (l in 2..MAX_LEVEL) if (xp >= thresholds[l]) lvl = l
        return lvl
    }

    /** XP needed from the start of [level] to the next level; 0 at max level. */
    fun xpSpan(level: Int): Int =
        if (level >= MAX_LEVEL) 0 else thresholds[level + 1] - thresholds[level]

    /** XP accumulated into the current [level]. */
    fun xpInto(xp: Int, level: Int): Int =
        if (level >= MAX_LEVEL) 0 else xp - thresholds[level]

    fun damageMultiplier(level: Int): Float = 1f + (level - 1) * 0.12f
    fun hpBonus(level: Int): Int = (level - 1) * 16
}
