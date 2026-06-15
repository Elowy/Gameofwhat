package com.gameofwhat.arena.net

import com.gameofwhat.arena.game.EnemyState
import com.gameofwhat.arena.game.GameConfig
import com.gameofwhat.arena.game.HitEvent
import com.gameofwhat.arena.game.Phase
import com.gameofwhat.arena.game.PlayerState
import com.gameofwhat.arena.game.RoomMeta
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.random.Random

/** Maximum players a room accepts. */
private const val MAX_PLAYERS = 6
private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no easily-confused chars

class RoomNotFoundException : Exception("Room not found")
class RoomFullException : Exception("Room is full")

/**
 * Firebase Realtime Database backed room. See [GameNetwork] for the authority model.
 * Tree layout:
 *
 *   rooms/{CODE}/meta            -> { phase, hostId, wave, score }
 *   rooms/{CODE}/players/{id}    -> PlayerState
 *   rooms/{CODE}/enemies/{id}    -> EnemyState   (host writes)
 *   rooms/{CODE}/hits/{pushId}   -> { enemyId, damage }  (clients push, host drains)
 */
class FirebaseGameNetwork private constructor(
    override val roomCode: String,
    override val localId: String,
    override val isHost: Boolean,
    private val roomRef: DatabaseReference,
) : GameNetwork {

    private val _players = MutableStateFlow<Map<String, PlayerState>>(emptyMap())
    private val _enemies = MutableStateFlow<List<EnemyState>>(emptyList())
    private val _meta = MutableStateFlow(RoomMeta())

    override val players: StateFlow<Map<String, PlayerState>> = _players.asStateFlow()
    override val enemies: StateFlow<List<EnemyState>> = _enemies.asStateFlow()
    override val meta: StateFlow<RoomMeta> = _meta.asStateFlow()

    private val pendingHits = ArrayDeque<HitEvent>()

    private val playersRef = roomRef.child("players")
    private val enemiesRef = roomRef.child("enemies")
    private val metaRef = roomRef.child("meta")
    private val hitsRef = roomRef.child("hits")
    private val myPlayerRef = playersRef.child(localId)

    private val playersListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val map = HashMap<String, PlayerState>()
            for (child in snapshot.children) {
                parsePlayer(child)?.let { map[it.id] = it }
            }
            _players.value = map
        }
        override fun onCancelled(error: DatabaseError) {}
    }

    private val enemiesListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val list = ArrayList<EnemyState>()
            for (child in snapshot.children) parseEnemy(child)?.let { list.add(it) }
            _enemies.value = list
        }
        override fun onCancelled(error: DatabaseError) {}
    }

    private val metaListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            _meta.value = parseMeta(snapshot)
        }
        override fun onCancelled(error: DatabaseError) {}
    }

    // Host drains hits from a value listener and clears the node after reading.
    private val hitsListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (!snapshot.hasChildren()) return
            synchronized(pendingHits) {
                for (child in snapshot.children) {
                    val enemyId = child.child("enemyId").value as? String ?: continue
                    val dmg = (child.child("damage").value as? Number)?.toInt() ?: continue
                    pendingHits.add(HitEvent(enemyId, dmg))
                }
            }
            hitsRef.removeValue()
        }
        override fun onCancelled(error: DatabaseError) {}
    }

    private fun attachListeners() {
        playersRef.addValueEventListener(playersListener)
        enemiesRef.addValueEventListener(enemiesListener)
        metaRef.addValueEventListener(metaListener)
        if (isHost) hitsRef.addValueEventListener(hitsListener)
        // Remove our player node automatically if we disconnect unexpectedly.
        myPlayerRef.onDisconnect().removeValue()
    }

    override fun sendLocalPlayer(p: PlayerState) {
        myPlayerRef.setValue(playerToMap(p))
    }

    override fun reportHit(enemyId: String, damage: Int) {
        hitsRef.push().setValue(mapOf("enemyId" to enemyId, "damage" to damage))
    }

    override fun publishEnemies(enemies: List<EnemyState>) {
        if (!isHost) return
        val map = HashMap<String, Any>()
        for (e in enemies) map[e.id] = enemyToMap(e)
        enemiesRef.setValue(map)
    }

    override fun publishMeta(meta: RoomMeta) {
        if (!isHost) return
        metaRef.setValue(metaToMap(meta))
    }

    override fun drainHits(): List<HitEvent> = synchronized(pendingHits) {
        val out = pendingHits.toList()
        pendingHits.clear()
        out
    }

    override fun close() {
        playersRef.removeEventListener(playersListener)
        enemiesRef.removeEventListener(enemiesListener)
        metaRef.removeEventListener(metaListener)
        if (isHost) hitsRef.removeEventListener(hitsListener)
        myPlayerRef.onDisconnect().cancel()
        myPlayerRef.removeValue()
        // The host tears the whole room down when it leaves.
        if (isHost) roomRef.removeValue()
    }

    companion object {
        private fun db() = FirebaseDatabase.getInstance()

        private fun randomCode(): String =
            (1..4).map { CODE_CHARS[Random.nextInt(CODE_CHARS.length)] }.joinToString("")

        suspend fun createRoom(name: String, mapId: Int): FirebaseGameNetwork {
            val root = db().getReference("rooms")
            // Find an unused 4-char code.
            var code = randomCode()
            repeat(8) {
                val exists = root.child(code).child("meta").get().await().exists()
                if (!exists) return@repeat
                code = randomCode()
            }
            val localId = UUID.randomUUID().toString()
            val roomRef = root.child(code)
            roomRef.child("meta").setValue(
                metaToMap(RoomMeta(phase = Phase.LOBBY, hostId = localId, mapId = mapId))
            ).await()
            val net = FirebaseGameNetwork(code, localId, isHost = true, roomRef)
            net.attachListeners()
            val me = PlayerState(id = localId, name = name.ifBlank { "Host" }, colorIndex = 0)
            net.myPlayerRef.setValue(playerToMap(me)).await()
            return net
        }

        suspend fun joinRoom(code: String, name: String): FirebaseGameNetwork {
            val normalized = code.trim().uppercase()
            val roomRef = db().getReference("rooms").child(normalized)
            val metaSnap = roomRef.child("meta").get().await()
            if (!metaSnap.exists()) throw RoomNotFoundException()

            val playersSnap = roomRef.child("players").get().await()
            val count = playersSnap.childrenCount.toInt()
            if (count >= MAX_PLAYERS) throw RoomFullException()

            val localId = UUID.randomUUID().toString()
            val net = FirebaseGameNetwork(normalized, localId, isHost = false, roomRef)
            net.attachListeners()
            val me = PlayerState(
                id = localId,
                name = name.ifBlank { "Player" },
                colorIndex = count % 6,
            )
            net.myPlayerRef.setValue(playerToMap(me)).await()
            return net
        }

        // ---- (de)serialisation helpers (manual, to keep models immutable) ----

        private fun playerToMap(p: PlayerState): Map<String, Any> = mapOf(
            "id" to p.id,
            "name" to p.name,
            "x" to p.x.toDouble(),
            "y" to p.y.toDouble(),
            "angle" to p.angle.toDouble(),
            "hp" to p.hp,
            "maxHp" to p.maxHp,
            "alive" to p.alive,
            "colorIndex" to p.colorIndex,
            "score" to p.score,
        )

        private fun enemyToMap(e: EnemyState): Map<String, Any> = mapOf(
            "id" to e.id,
            "x" to e.x.toDouble(),
            "y" to e.y.toDouble(),
            "hp" to e.hp,
            "maxHp" to e.maxHp,
            "type" to e.type,
        )

        private fun metaToMap(m: RoomMeta): Map<String, Any> = mapOf(
            "phase" to m.phase,
            "hostId" to m.hostId,
            "wave" to m.wave,
            "score" to m.score,
            "mapId" to m.mapId,
        )

        private fun parsePlayer(s: DataSnapshot): PlayerState? {
            val id = s.child("id").value as? String ?: s.key ?: return null
            return PlayerState(
                id = id,
                name = s.str("name", "Player"),
                x = s.float("x"),
                y = s.float("y"),
                angle = s.float("angle"),
                hp = s.int("hp", GameConfig.PLAYER_MAX_HP),
                maxHp = s.int("maxHp", GameConfig.PLAYER_MAX_HP),
                alive = s.bool("alive", true),
                colorIndex = s.int("colorIndex", 0),
                score = s.int("score", 0),
            )
        }

        private fun parseEnemy(s: DataSnapshot): EnemyState? {
            val id = s.child("id").value as? String ?: s.key ?: return null
            return EnemyState(
                id = id,
                x = s.float("x"),
                y = s.float("y"),
                hp = s.int("hp", 1),
                maxHp = s.int("maxHp", 1),
                type = s.int("type", 0),
            )
        }

        private fun parseMeta(s: DataSnapshot): RoomMeta = RoomMeta(
            phase = s.str("phase", Phase.LOBBY),
            hostId = s.str("hostId", ""),
            wave = s.int("wave", 0),
            score = s.int("score", 0),
            mapId = s.int("mapId", 0),
        )

        private fun DataSnapshot.float(key: String, def: Float = 0f): Float =
            (child(key).value as? Number)?.toFloat() ?: def
        private fun DataSnapshot.int(key: String, def: Int = 0): Int =
            (child(key).value as? Number)?.toInt() ?: def
        private fun DataSnapshot.str(key: String, def: String = ""): String =
            child(key).value as? String ?: def
        private fun DataSnapshot.bool(key: String, def: Boolean): Boolean =
            child(key).value as? Boolean ?: def
    }
}
