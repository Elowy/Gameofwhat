package com.gameofwhat.arena.game

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.gameofwhat.arena.net.GameNetwork
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Immutable snapshot the UI draws each frame. */
data class RenderState(
    val players: List<PlayerState> = emptyList(),
    val localId: String = "",
    val enemies: List<EnemyState> = emptyList(),
    val powerups: List<PowerUp> = emptyList(),
    val bullets: List<Bullet> = emptyList(),
    val sparks: List<Spark> = emptyList(),
    val phase: String = Phase.PLAYING,
    val wave: Int = 0,
    val score: Int = 0,
    val mapId: Int = 0,
    val level: Int = 1,
    val xpInto: Int = 0,
    val xpSpan: Int = 0,
    val localClassId: Int = 0,
    val localHp: Int = GameConfig.PLAYER_MAX_HP,
    val localMaxHp: Int = GameConfig.PLAYER_MAX_HP,
    val localAlive: Boolean = true,
    val buffDamage: Boolean = false,
    val buffSpeed: Boolean = false,
    val buffFire: Boolean = false,
)

private class HostEnemy(
    val id: String,
    var x: Float,
    var y: Float,
    var hp: Int,
    val maxHp: Int,
    val type: Int,
    val speed: Float,
    val score: Int,
    val xp: Int,
)

/**
 * Runs the local simulation and drives network sync. The host additionally owns the
 * enemy/wave/power-up simulation and shared XP/level. Call [update] once per rendered
 * frame; read [renderState].
 */
class GameEngine(private val net: GameNetwork) {

    val localId: String = net.localId
    private val isHost: Boolean = net.isHost

    private val _renderState = mutableStateOf(RenderState(localId = localId))
    val renderState: State<RenderState> get() = _renderState

    // Local authoritative player.
    private var local: PlayerState = run {
        val seed = net.players.value[localId]
        val color = seed?.colorIndex ?: 0
        val name = seed?.name ?: "Player"
        val cls = seed?.classId ?: 0
        val cdef = Classes.byId(cls)
        val (sx, sy) = spawnSlot(color)
        PlayerState(
            id = localId, name = name, x = sx, y = sy, colorIndex = color, classId = cls,
            hp = cdef.maxHp, maxHp = cdef.maxHp,
        )
    }

    @Volatile private var inX = 0f
    @Volatile private var inY = 0f

    private val bullets = ArrayList<Bullet>()
    private val sparks = ArrayList<Spark>()

    private var fireTimer = 0f
    private var contactTimer = 0f
    private var playerSyncTimer = 0f
    private var enemySyncTimer = 0f
    private var healAccum = 0f

    // Temporary power-up buffs (seconds remaining).
    private var buffDamageTimer = 0f
    private var buffSpeedTimer = 0f
    private var buffFireTimer = 0f

    // Host-only state.
    private val hostEnemies = ArrayList<HostEnemy>()
    private val hostPowerups = ArrayList<PowerUp>()
    private val claimedPickups = HashSet<String>()
    private var wave = 0
    private var score = 0
    private var teamXp = 0
    private var level = 1
    private var waveCooldown = 1.0f
    private var enemySeq = 0
    private var powerupSeq = 0

    init {
        net.sendLocalPlayer(local)
    }

    fun setMoveInput(x: Float, y: Float) {
        inX = x; inY = y
    }

    fun update(dt: Float) {
        val d = dt.coerceIn(0f, 0.05f)
        val meta = net.meta.value
        val lvl = if (isHost) level else meta.level
        val cdef = Classes.byId(local.classId)

        // Keep max HP in sync with class + level (does not refill current HP).
        val newMax = cdef.maxHp + Progression.hpBonus(lvl)
        if (local.maxHp != newMax) local = local.copy(maxHp = newMax)

        if (meta.phase == Phase.PLAYING) {
            updateBuffs(d)
            updateLocalPlayer(d, cdef)
            updateAttack(d, cdef, lvl)
            updateBullets(d)
            updateSupport(d)
            updateContactDamage(d)
            updatePickups()
        }
        updateSparks(d)

        if (isHost) updateHost(d, meta)

        playerSyncTimer -= d
        if (playerSyncTimer <= 0f) {
            playerSyncTimer = 1f / GameConfig.PLAYER_SYNC_HZ
            net.sendLocalPlayer(local)
        }

        publishRenderState(meta)
    }

    // ---------------- buffs ----------------

    private fun updateBuffs(dt: Float) {
        if (buffDamageTimer > 0f) buffDamageTimer -= dt
        if (buffSpeedTimer > 0f) buffSpeedTimer -= dt
        if (buffFireTimer > 0f) buffFireTimer -= dt
    }

    private fun damageMult() = if (buffDamageTimer > 0f) GameConfig.DAMAGE_BUFF_MULT else 1f
    private fun speedMult() = if (buffSpeedTimer > 0f) GameConfig.SPEED_BUFF_MULT else 1f
    private fun fireMult() = if (buffFireTimer > 0f) GameConfig.RAPIDFIRE_BUFF_MULT else 1f

    // ---------------- local player ----------------

    private fun updateLocalPlayer(dt: Float, cdef: ClassDef) {
        if (!local.alive) return
        val speed = cdef.moveSpeed * speedMult()
        var x = local.x + inX * speed * dt
        var y = local.y + inY * speed * dt
        val r = GameConfig.PLAYER_RADIUS
        val resolved = resolveAgainstObstacles(x, y, r)
        x = resolved.first.coerceIn(r, GameConfig.WORLD_SIZE - r)
        y = resolved.second.coerceIn(r, GameConfig.WORLD_SIZE - r)

        val target = nearestEnemy(x, y, cdef.range)
        val angle = when {
            target != null -> atan2(target.y - y, target.x - x)
            inX != 0f || inY != 0f -> atan2(inY, inX)
            else -> local.angle
        }
        local = local.copy(x = x, y = y, angle = angle)
    }

    private fun updateAttack(dt: Float, cdef: ClassDef, lvl: Int) {
        fireTimer -= dt
        if (!local.alive || fireTimer > 0f) return
        val target = nearestEnemy(local.x, local.y, cdef.range) ?: return
        fireTimer = cdef.fireInterval * fireMult()
        val dmg = (cdef.damage * Progression.damageMultiplier(lvl) * damageMult())
            .toInt().coerceAtLeast(1)
        val a = atan2(target.y - local.y, target.x - local.x)

        if (cdef.melee) {
            // Cleave: hit every enemy within reach.
            for (e in enemiesForLogic()) {
                if (e.hp > 0 &&
                    dist(local.x, local.y, e.x, e.y) <= cdef.range + GameConfig.enemyRadius(e.type)
                ) {
                    net.reportHit(e.id, dmg)
                }
            }
            sparks.add(
                Spark(local.x + cos(a) * cdef.range * 0.6f, local.y + sin(a) * cdef.range * 0.6f, 0.2f, 0.2f)
            )
        } else {
            bullets.add(
                Bullet(
                    x = local.x + cos(a) * GameConfig.PLAYER_RADIUS,
                    y = local.y + sin(a) * GameConfig.PLAYER_RADIUS,
                    vx = cos(a) * cdef.bulletSpeed,
                    vy = sin(a) * cdef.bulletSpeed,
                    life = GameConfig.BULLET_LIFE,
                    ownerColorIndex = local.colorIndex,
                    damage = dmg,
                    pierce = cdef.pierce,
                )
            )
        }
    }

    private fun updateBullets(dt: Float) {
        if (bullets.isEmpty()) return
        val enemies = enemiesForLogic()
        val obs = obstacles()
        val it = bullets.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.life -= dt
            var consumed = false
            if (b.life <= 0f ||
                b.x < 0f || b.x > GameConfig.WORLD_SIZE ||
                b.y < 0f || b.y > GameConfig.WORLD_SIZE
            ) {
                consumed = true
            } else if (obs.any { o -> dist(b.x, b.y, o.x, o.y) <= o.r }) {
                sparks.add(Spark(b.x, b.y, 0.18f, 0.18f))
                consumed = true
            } else {
                for (e in enemies) {
                    if (e.hp > 0 && e.id !in b.hitIds &&
                        dist(b.x, b.y, e.x, e.y) <= GameConfig.enemyRadius(e.type) + 6f
                    ) {
                        net.reportHit(e.id, b.damage)
                        b.hitIds.add(e.id)
                        sparks.add(Spark(b.x, b.y, 0.25f, 0.25f))
                        if (!b.pierce) { consumed = true; break }
                    }
                }
            }
            if (consumed) it.remove()
        }
    }

    private fun updateSupport(dt: Float) {
        if (!local.alive || local.hp >= local.maxHp) {
            healAccum = 0f
            return
        }
        val cdef = Classes.byId(local.classId)
        var rate = cdef.selfRegenPerSec
        // Heal from any nearby priest aura (including self if priest).
        for (p in allPlayers()) {
            if (!p.alive) continue
            val pc = Classes.byId(p.classId)
            if (pc.healAuraPerSec > 0f && dist(local.x, local.y, p.x, p.y) <= pc.healAuraRadius) {
                rate += pc.healAuraPerSec
            }
        }
        if (rate <= 0f) return
        healAccum += rate * dt
        if (healAccum >= 1f) {
            val add = healAccum.toInt()
            healAccum -= add
            local = local.copy(hp = (local.hp + add).coerceAtMost(local.maxHp))
        }
    }

    private fun updateContactDamage(dt: Float) {
        if (!local.alive) return
        contactTimer -= dt
        if (contactTimer > 0f) return
        val touching = enemiesForLogic().any {
            it.hp > 0 &&
                dist(it.x, it.y, local.x, local.y) <=
                GameConfig.enemyRadius(it.type) + GameConfig.PLAYER_RADIUS
        }
        if (touching) {
            contactTimer = GameConfig.CONTACT_INTERVAL
            val hp = (local.hp - GameConfig.CONTACT_DAMAGE).coerceAtLeast(0)
            local = local.copy(hp = hp, alive = hp > 0)
            sparks.add(Spark(local.x, local.y, 0.3f, 0.3f))
            if (!local.alive) net.sendLocalPlayer(local)
        }
    }

    private fun updatePickups() {
        if (!local.alive) return
        for (pu in powerupsForLogic()) {
            if (pu.id in claimedPickups) continue
            if (dist(local.x, local.y, pu.x, pu.y) <=
                GameConfig.PLAYER_RADIUS + GameConfig.POWERUP_RADIUS
            ) {
                applyPowerup(pu.type)
                net.reportPickup(pu.id)
                claimedPickups.add(pu.id)
            }
        }
    }

    private fun applyPowerup(type: Int) {
        when (type) {
            0 -> {
                val hp = (local.hp + GameConfig.HEAL_PICKUP_AMOUNT).coerceAtMost(local.maxHp)
                local = local.copy(hp = hp)
                net.sendLocalPlayer(local)
            }
            1 -> buffDamageTimer = GameConfig.BUFF_DURATION
            2 -> buffSpeedTimer = GameConfig.BUFF_DURATION
            else -> buffFireTimer = GameConfig.BUFF_DURATION
        }
        sparks.add(Spark(local.x, local.y, 0.35f, 0.35f))
    }

    private fun updateSparks(dt: Float) {
        if (sparks.isEmpty()) return
        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.life -= dt
            if (s.life <= 0f) it.remove()
        }
    }

    // ---------------- Host simulation ----------------

    private fun updateHost(dt: Float, meta: RoomMeta) {
        if (meta.phase != Phase.PLAYING) return

        // Apply pending damage.
        val hits = net.drainHits()
        if (hits.isNotEmpty()) {
            for (h in hits) {
                val e = hostEnemies.firstOrNull { it.id == h.enemyId } ?: continue
                if (e.hp <= 0) continue
                e.hp -= h.damage
                if (e.hp <= 0) {
                    score += e.score
                    teamXp += e.xp
                    maybeDropPowerup(e.x, e.y)
                }
            }
            hostEnemies.removeAll { it.hp <= 0 }
            level = Progression.levelForXp(teamXp)
        }

        // Remove claimed power-ups.
        val claimed = net.drainPickups()
        if (claimed.isNotEmpty()) hostPowerups.removeAll { it.id in claimed }

        val targets = allPlayers().filter { it.alive }

        if (targets.isEmpty() && net.players.value.isNotEmpty()) {
            net.publishMeta(meta.copy(phase = Phase.GAMEOVER, wave = wave, score = score, xp = teamXp, level = level))
            net.publishEnemies(emptyList())
            net.publishPowerups(emptyList())
            return
        }

        // Move enemies toward their nearest living player.
        for (e in hostEnemies) {
            val t = targets.minByOrNull { dist(it.x, it.y, e.x, e.y) } ?: continue
            val dx = t.x - e.x
            val dy = t.y - e.y
            val len = dist(0f, 0f, dx, dy)
            if (len > 1f) {
                e.x += dx / len * e.speed * dt
                e.y += dy / len * e.speed * dt
            }
            val er = GameConfig.enemyRadius(e.type)
            val res = resolveAgainstObstacles(e.x, e.y, er)
            e.x = res.first.coerceIn(er, GameConfig.WORLD_SIZE - er)
            e.y = res.second.coerceIn(er, GameConfig.WORLD_SIZE - er)
        }

        if (hostEnemies.isEmpty()) {
            waveCooldown -= dt
            if (waveCooldown <= 0f) {
                wave += 1
                spawnWave(wave)
                waveCooldown = 3.0f
            }
        }

        enemySyncTimer -= dt
        if (enemySyncTimer <= 0f) {
            enemySyncTimer = 1f / GameConfig.ENEMY_SYNC_HZ
            net.publishEnemies(
                hostEnemies.map { EnemyState(it.id, it.x, it.y, it.hp, it.maxHp, it.type) }
            )
            net.publishPowerups(ArrayList(hostPowerups))
            net.publishMeta(
                meta.copy(phase = Phase.PLAYING, wave = wave, score = score, xp = teamXp, level = level)
            )
        }
    }

    private fun maybeDropPowerup(x: Float, y: Float) {
        if (Random.nextFloat() > GameConfig.POWERUP_DROP_CHANCE) return
        hostPowerups.add(PowerUp(id = "pu${powerupSeq++}", x = x, y = y, type = Random.nextInt(4)))
    }

    private fun spawnWave(wave: Int) {
        val count = (3 + wave).coerceAtMost(GameConfig.MAX_ENEMIES)
        repeat(count) { i ->
            val roll = Random.nextFloat()
            val type = when {
                wave >= 3 && roll < 0.18f -> 2 // ogre
                wave >= 2 && roll < 0.45f -> 1 // wolf
                else -> 0 // goblin
            }
            val (hp, speed, sc, xp) = when (type) {
                1 -> Quad(30 + wave * 5, GameConfig.FAST_SPEED, 15, 18 + wave * 2)
                2 -> Quad(120 + wave * 20, GameConfig.TANK_SPEED, 30, 40 + wave * 3)
                else -> Quad(50 + wave * 8, GameConfig.GRUNT_SPEED, 10, 12 + wave * 2)
            }
            val (sx, sy) = edgeSpawn(i)
            hostEnemies.add(
                HostEnemy("e${enemySeq++}", sx, sy, hp, hp, type, speed, sc, xp)
            )
        }
    }

    // ---------------- shared helpers ----------------

    private fun enemiesForLogic(): List<EnemyState> =
        if (isHost) hostEnemies.map { EnemyState(it.id, it.x, it.y, it.hp, it.maxHp, it.type) }
        else net.enemies.value

    private fun powerupsForLogic(): List<PowerUp> =
        (if (isHost) hostPowerups else net.powerups.value).filter { it.id !in claimedPickups }

    private fun allPlayers(): List<PlayerState> {
        val remote = net.players.value.values.filter { it.id != localId }
        return remote + local
    }

    private fun obstacles(): List<Obstacle> = Maps.byId(net.meta.value.mapId).obstacles

    private fun resolveAgainstObstacles(x: Float, y: Float, radius: Float): Pair<Float, Float> {
        var nx = x
        var ny = y
        for (o in obstacles()) {
            val dx = nx - o.x
            val dy = ny - o.y
            val dd = dist(0f, 0f, dx, dy)
            val min = radius + o.r
            if (dd < min) {
                if (dd > 0.001f) {
                    val push = min - dd
                    nx += dx / dd * push
                    ny += dy / dd * push
                } else {
                    nx = o.x + min
                }
            }
        }
        return nx to ny
    }

    private fun nearestEnemy(x: Float, y: Float, range: Float): EnemyState? =
        enemiesForLogic()
            .filter { it.hp > 0 && dist(x, y, it.x, it.y) <= range }
            .minByOrNull { dist(x, y, it.x, it.y) }

    private fun publishRenderState(meta: RoomMeta) {
        val remote = net.players.value.values.filter { it.id != localId }
        val players = remote + local
        val lvl = if (isHost) level else meta.level
        val xp = if (isHost) teamXp else meta.xp
        _renderState.value = RenderState(
            players = players,
            localId = localId,
            enemies = enemiesForLogic().filter { it.hp > 0 },
            powerups = powerupsForLogic(),
            bullets = ArrayList(bullets),
            sparks = ArrayList(sparks),
            phase = meta.phase,
            wave = if (isHost) wave else meta.wave,
            score = if (isHost) score else meta.score,
            mapId = meta.mapId,
            level = lvl,
            xpInto = Progression.xpInto(xp, lvl),
            xpSpan = Progression.xpSpan(lvl),
            localClassId = local.classId,
            localHp = local.hp,
            localMaxHp = local.maxHp,
            localAlive = local.alive,
            buffDamage = buffDamageTimer > 0f,
            buffSpeed = buffSpeedTimer > 0f,
            buffFire = buffFireTimer > 0f,
        )
    }

    fun dispose() {
        net.close()
    }

    companion object {
        private fun spawnSlot(index: Int): Pair<Float, Float> {
            val c = GameConfig.WORLD_SIZE / 2f
            val r = 120f
            val a = index / 6f * (2f * Math.PI).toFloat()
            return (c + cos(a) * r) to (c + sin(a) * r)
        }

        private fun edgeSpawn(seed: Int): Pair<Float, Float> {
            val s = GameConfig.WORLD_SIZE
            return when ((seed + Random.nextInt(4)) % 4) {
                0 -> Random.nextFloat() * s to 0f
                1 -> Random.nextFloat() * s to s
                2 -> 0f to Random.nextFloat() * s
                else -> s to Random.nextFloat() * s
            }
        }
    }
}

/** Small helper to destructure four values from [spawnWave]. */
private data class Quad(val a: Int, val b: Float, val c: Int, val d: Int)
