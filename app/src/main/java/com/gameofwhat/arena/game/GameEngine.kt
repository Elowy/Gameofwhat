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
    val bullets: List<Bullet> = emptyList(),
    val sparks: List<Spark> = emptyList(),
    val phase: String = Phase.PLAYING,
    val wave: Int = 0,
    val score: Int = 0,
    val mapId: Int = 0,
    val localHp: Int = GameConfig.PLAYER_MAX_HP,
    val localMaxHp: Int = GameConfig.PLAYER_MAX_HP,
    val localAlive: Boolean = true,
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
)

/**
 * Runs the local simulation and drives network sync. The host additionally owns the
 * enemy/wave simulation. Call [update] once per rendered frame; read [renderState].
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
        val (sx, sy) = spawnSlot(color)
        PlayerState(id = localId, name = name, x = sx, y = sy, colorIndex = color)
    }

    // Input from the on-screen joystick, normalised to roughly [-1, 1].
    @Volatile private var inX = 0f
    @Volatile private var inY = 0f

    private val bullets = ArrayList<Bullet>()
    private val sparks = ArrayList<Spark>()

    private var fireTimer = 0f
    private var contactTimer = 0f
    private var playerSyncTimer = 0f
    private var enemySyncTimer = 0f

    // Host-only enemy/wave state.
    private val hostEnemies = ArrayList<HostEnemy>()
    private var wave = 0
    private var score = 0
    private var waveCooldown = 1.0f
    private var enemySeq = 0

    init {
        net.sendLocalPlayer(local)
    }

    fun setMoveInput(x: Float, y: Float) {
        inX = x; inY = y
    }

    fun update(dt: Float) {
        val clamped = dt.coerceIn(0f, 0.05f) // guard against long frame stalls
        val meta = net.meta.value

        if (meta.phase == Phase.PLAYING) {
            updateLocalPlayer(clamped)
            updateBullets(clamped)
            updateContactDamage(clamped)
        }
        updateSparks(clamped)

        if (isHost) updateHost(clamped, meta)

        // Publish local player at a fixed cadence.
        playerSyncTimer -= clamped
        if (playerSyncTimer <= 0f) {
            playerSyncTimer = 1f / GameConfig.PLAYER_SYNC_HZ
            net.sendLocalPlayer(local)
        }

        publishRenderState(meta)
    }

    private fun enemiesForLogic(): List<EnemyState> =
        if (isHost) hostEnemies.map { EnemyState(it.id, it.x, it.y, it.hp, it.maxHp, it.type) }
        else net.enemies.value

    /** All players for targeting/render: remote from network + local authoritative. */
    private fun allPlayers(): List<PlayerState> {
        val remote = net.players.value.values.filter { it.id != localId }
        return remote + local
    }

    private fun updateLocalPlayer(dt: Float) {
        if (!local.alive) return
        var x = local.x + inX * GameConfig.PLAYER_SPEED * dt
        var y = local.y + inY * GameConfig.PLAYER_SPEED * dt
        val r = GameConfig.PLAYER_RADIUS
        val resolved = resolveAgainstObstacles(x, y, r)
        x = resolved.first.coerceIn(r, GameConfig.WORLD_SIZE - r)
        y = resolved.second.coerceIn(r, GameConfig.WORLD_SIZE - r)

        // Aim at the nearest enemy in range, otherwise face the movement direction.
        val target = nearestEnemy(x, y, GameConfig.FIRE_RANGE)
        val angle = when {
            target != null -> atan2(target.y - y, target.x - x)
            inX != 0f || inY != 0f -> atan2(inY, inX)
            else -> local.angle
        }
        local = local.copy(x = x, y = y, angle = angle)
    }

    private fun updateBullets(dt: Float) {
        // Auto-fire toward the nearest enemy in range.
        fireTimer -= dt
        if (local.alive && fireTimer <= 0f) {
            val target = nearestEnemy(local.x, local.y, GameConfig.FIRE_RANGE)
            if (target != null) {
                fireTimer = GameConfig.FIRE_INTERVAL
                val a = atan2(target.y - local.y, target.x - local.x)
                bullets.add(
                    Bullet(
                        x = local.x + cos(a) * GameConfig.PLAYER_RADIUS,
                        y = local.y + sin(a) * GameConfig.PLAYER_RADIUS,
                        vx = cos(a) * GameConfig.BULLET_SPEED,
                        vy = sin(a) * GameConfig.BULLET_SPEED,
                        life = GameConfig.BULLET_LIFE,
                        ownerColorIndex = local.colorIndex,
                    )
                )
            }
        }

        if (bullets.isEmpty()) return
        val enemies = enemiesForLogic()
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
            } else if (obstacles().any { dist(b.x, b.y, it.x, it.y) <= it.r }) {
                // Arrow thuds into a pillar/tree/rock.
                sparks.add(Spark(b.x, b.y, 0.18f, 0.18f))
                consumed = true
            } else {
                for (e in enemies) {
                    val hitR = GameConfig.enemyRadius(e.type) + 6f
                    if (e.hp > 0 && dist(b.x, b.y, e.x, e.y) <= hitR) {
                        net.reportHit(e.id, GameConfig.BULLET_DAMAGE)
                        sparks.add(Spark(b.x, b.y, 0.25f, 0.25f))
                        consumed = true
                        break
                    }
                }
            }
            if (consumed) it.remove()
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

        // Apply pending damage from all clients.
        val hits = net.drainHits()
        if (hits.isNotEmpty()) {
            for (h in hits) {
                val e = hostEnemies.firstOrNull { it.id == h.enemyId } ?: continue
                if (e.hp <= 0) continue
                e.hp -= h.damage
                if (e.hp <= 0) score += e.score
            }
            hostEnemies.removeAll { it.hp <= 0 }
        }

        val targets = allPlayers().filter { it.alive }

        // Game over when everyone is down.
        if (targets.isEmpty() && net.players.value.isNotEmpty()) {
            net.publishMeta(meta.copy(phase = Phase.GAMEOVER, wave = wave, score = score))
            net.publishEnemies(emptyList())
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
            // Slide around obstacles and stay in bounds.
            val er = GameConfig.enemyRadius(e.type)
            val res = resolveAgainstObstacles(e.x, e.y, er)
            e.x = res.first.coerceIn(er, GameConfig.WORLD_SIZE - er)
            e.y = res.second.coerceIn(er, GameConfig.WORLD_SIZE - er)
        }

        // Wave management.
        if (hostEnemies.isEmpty()) {
            waveCooldown -= dt
            if (waveCooldown <= 0f) {
                wave += 1
                spawnWave(wave)
                waveCooldown = 3.0f
            }
        }

        // Publish enemies + meta at a fixed cadence.
        enemySyncTimer -= dt
        if (enemySyncTimer <= 0f) {
            enemySyncTimer = 1f / GameConfig.ENEMY_SYNC_HZ
            net.publishEnemies(
                hostEnemies.map { EnemyState(it.id, it.x, it.y, it.hp, it.maxHp, it.type) }
            )
            net.publishMeta(meta.copy(phase = Phase.PLAYING, wave = wave, score = score))
        }
    }

    private fun spawnWave(wave: Int) {
        val count = (3 + wave).coerceAtMost(GameConfig.MAX_ENEMIES)
        repeat(count) { i ->
            val roll = Random.nextFloat()
            val type = when {
                wave >= 3 && roll < 0.18f -> 2 // tank
                wave >= 2 && roll < 0.45f -> 1 // fast
                else -> 0 // grunt
            }
            val (hp, speed, sc) = when (type) {
                1 -> Triple(30 + wave * 5, GameConfig.FAST_SPEED, 15)
                2 -> Triple(120 + wave * 20, GameConfig.TANK_SPEED, 30)
                else -> Triple(50 + wave * 8, GameConfig.GRUNT_SPEED, 10)
            }
            val (sx, sy) = edgeSpawn(i)
            hostEnemies.add(
                HostEnemy(
                    id = "e${enemySeq++}",
                    x = sx, y = sy, hp = hp, maxHp = hp, type = type, speed = speed, score = sc,
                )
            )
        }
    }

    private fun publishRenderState(meta: RoomMeta) {
        val remote = net.players.value.values.filter { it.id != localId }
        val players = remote + local
        _renderState.value = RenderState(
            players = players,
            localId = localId,
            enemies = enemiesForLogic().filter { it.hp > 0 },
            bullets = ArrayList(bullets),
            sparks = ArrayList(sparks),
            phase = meta.phase,
            wave = if (isHost) wave else meta.wave,
            score = if (isHost) score else meta.score,
            mapId = meta.mapId,
            localHp = local.hp,
            localMaxHp = local.maxHp,
            localAlive = local.alive,
        )
    }

    private fun obstacles(): List<Obstacle> = Maps.byId(net.meta.value.mapId).obstacles

    /** Push a circle of [radius] out of any overlapping obstacle on the current map. */
    private fun resolveAgainstObstacles(x: Float, y: Float, radius: Float): Pair<Float, Float> {
        var nx = x
        var ny = y
        for (o in obstacles()) {
            val dx = nx - o.x
            val dy = ny - o.y
            val d = dist(0f, 0f, dx, dy)
            val min = radius + o.r
            if (d < min) {
                if (d > 0.001f) {
                    val push = min - d
                    nx += dx / d * push
                    ny += dy / d * push
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

    fun dispose() {
        net.close()
    }

    companion object {
        /** Evenly spaced spawn points around the arena centre. */
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
