package com.gameofwhat.arena.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gameofwhat.arena.AppViewModel
import com.gameofwhat.arena.game.Classes
import com.gameofwhat.arena.game.GameConfig
import com.gameofwhat.arena.game.GameEngine
import com.gameofwhat.arena.game.Maps
import com.gameofwhat.arena.game.Phase
import com.gameofwhat.arena.game.Progression
import com.gameofwhat.arena.game.RenderState
import com.gameofwhat.arena.ui.theme.Background
import com.gameofwhat.arena.ui.theme.Danger
import com.gameofwhat.arena.ui.theme.PlayerColors
import com.gameofwhat.arena.ui.theme.Primary
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Medieval monster palette, indexed by type: 0 goblin, 1 wolf, 2 ogre.
private val EnemyBody = listOf(Color(0xFF7CB342), Color(0xFFB0BEC5), Color(0xFF8E24AA))
private val EnemyDark = listOf(Color(0xFF33691E), Color(0xFF546E7A), Color(0xFF4A148C))

// Power-up palette, indexed by type: 0 heal, 1 damage, 2 speed, 3 rapid-fire.
private val PowerColors = listOf(Color(0xFF66BB6A), Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFFFFEE58))
private val PowerLabels = listOf("+", "⚔", "»", "↯")

@Composable
fun GameScreen(vm: AppViewModel, engine: GameEngine) {
    val rs by engine.renderState

    // Drive the simulation once per rendered frame.
    LaunchedEffect(engine) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) engine.update((now - last) / 1_000_000_000f)
                last = now
            }
        }
    }

    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {

        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = min(size.width, size.height) / GameConfig.WORLD_SIZE
            val offX = (size.width - GameConfig.WORLD_SIZE * scale) / 2f
            val offY = (size.height - GameConfig.WORLD_SIZE * scale) / 2f
            drawArena(rs, scale, offX, offY, textPaint)
        }

        // HUD: wave + score + local HP.
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Hullám ${rs.wave}   •   Pont ${rs.score}",
                color = Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                "${Classes.byId(rs.localClassId).name}  •  ${Maps.byId(rs.mapId).name}",
                color = Primary.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            XpBar(rs.level, rs.xpInto, rs.xpSpan)
            HealthBar(rs.localHp, rs.localMaxHp)
            BuffRow(rs)
            if (!rs.localAlive && rs.phase == Phase.PLAYING) {
                Text("Kiestél — várj a társaidra!", color = Danger, fontSize = 13.sp)
            }
        }

        // Movement joystick.
        if (rs.localAlive && rs.phase == Phase.PLAYING) {
            Joystick(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(28.dp),
                onMove = engine::setMoveInput,
            )
        }

        if (rs.phase == Phase.GAMEOVER) {
            GameOverOverlay(rs) { vm.backToMenu() }
        }
    }
}

@Composable
private fun XpBar(level: Int, xpInto: Int, xpSpan: Int) {
    val frac = if (xpSpan > 0) xpInto.toFloat() / xpSpan else 1f
    val maxed = level >= Progression.MAX_LEVEL
    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (maxed) 1f else frac.coerceIn(0f, 1f))
                .background(Color(0xFF7C4DFF), RoundedCornerShape(6.dp))
                .padding(vertical = 6.dp)
        )
        Text(
            if (maxed) "Szint $level (MAX)" else "Szint $level",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun BuffRow(rs: RenderState) {
    if (!rs.buffDamage && !rs.buffSpeed && !rs.buffFire) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (rs.buffDamage) BuffChip("Sebzés", Color(0xFFEF5350))
        if (rs.buffSpeed) BuffChip("Gyorsaság", Color(0xFF42A5F5))
        if (rs.buffFire) BuffChip("Gyorstűz", Color(0xFFFFEE58))
    }
}

@Composable
private fun BuffChip(label: String, color: Color) {
    Text(
        label,
        color = Color.Black,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun HealthBar(hp: Int, maxHp: Int) {
    val frac = if (maxHp > 0) hp.toFloat() / maxHp else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(frac.coerceIn(0f, 1f))
                .background(
                    if (frac > 0.3f) Color(0xFF81C784) else Danger,
                    RoundedCornerShape(6.dp),
                )
                .padding(vertical = 7.dp)
        )
        Text(
            "HP $hp",
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun GameOverOverlay(rs: RenderState, onMenu: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("VÉGE", fontSize = 44.sp, fontWeight = FontWeight.Black, color = Danger)
            Text("Elért szint: ${rs.level}", fontSize = 18.sp, color = Color.White)
            Text("Elért hullám: ${rs.wave}", fontSize = 18.sp, color = Color.White)
            Text("Pontszám: ${rs.score}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Primary)
            Button(onClick = onMenu, modifier = Modifier.padding(top = 12.dp)) {
                Text("Vissza a menübe")
            }
        }
    }
}

// ---------------- Canvas drawing ----------------

private fun DrawScope.drawArena(
    rs: RenderState,
    scale: Float,
    offX: Float,
    offY: Float,
    paint: Paint,
) {
    fun sx(wx: Float) = offX + wx * scale
    fun sy(wy: Float) = offY + wy * scale

    val world = GameConfig.WORLD_SIZE
    val map = Maps.byId(rs.mapId)
    fun size(w: Float, h: Float) = androidx.compose.ui.geometry.Size(w, h)

    // Arena floor + border.
    drawRect(Color(map.floor), Offset(sx(0f), sy(0f)), size(world * scale, world * scale))
    // Grid lines.
    val step = world / 10f
    var g = step
    while (g < world) {
        drawLine(Color(map.grid), Offset(sx(g), sy(0f)), Offset(sx(g), sy(world)))
        drawLine(Color(map.grid), Offset(sx(0f), sy(g)), Offset(sx(world), sy(g)))
        g += step
    }
    drawRect(
        Color(map.border), Offset(sx(0f), sy(0f)),
        size(world * scale, world * scale), style = Stroke(width = 4f),
    )

    // Obstacles (pillars / trees / crates / rocks).
    for (o in map.obstacles) {
        val cx = sx(o.x)
        val cy = sy(o.y)
        val r = o.r * scale
        when (map.decoType) {
            1 -> { // tree
                drawRect(Color(0xFF5D4037), Offset(cx - r * 0.18f, cy), size(r * 0.36f, r * 1.1f))
                drawCircle(Color(0xFF2E7D32), r, Offset(cx, cy))
                drawCircle(Color(0xFF66BB6A), r * 0.55f, Offset(cx - r * 0.3f, cy - r * 0.3f))
            }
            2 -> { // crate / stone block
                val s = size(r * 1.7f, r * 1.7f)
                val tl = Offset(cx - r * 0.85f, cy - r * 0.85f)
                drawRoundRect(Color(0xFF3A3A4A), tl, s, CornerRadius(6f, 6f))
                drawRoundRect(Color(map.accent).copy(alpha = 0.6f), tl, s, CornerRadius(6f, 6f), style = Stroke(2f))
            }
            3 -> { // jagged rock
                drawCircle(Color(0xFF3E2723), r, Offset(cx, cy))
                drawCircle(Color(map.accent), r, Offset(cx, cy), style = Stroke(3f))
                drawCircle(Color(0xFF5D4037), r * 0.5f, Offset(cx - r * 0.25f, cy - r * 0.25f))
            }
            else -> { // stone pillar
                drawCircle(Color(0xFF8D8475), r, Offset(cx, cy))
                drawCircle(Color(0xFF5B554B), r, Offset(cx, cy), style = Stroke(3f))
                drawCircle(Color(0xFFBDB5A3), r * 0.45f, Offset(cx - r * 0.25f, cy - r * 0.25f))
            }
        }
    }

    // Sparks (under entities).
    for (s in rs.sparks) {
        val a = (s.life / s.max).coerceIn(0f, 1f)
        drawCircle(
            color = Color(0xFFFFE082).copy(alpha = a),
            radius = (1f - a) * 26f * scale + 4f,
            center = Offset(sx(s.x), sy(s.y)),
            style = Stroke(width = 3f),
        )
    }

    // Monsters: goblin / wolf / ogre.
    for (e in rs.enemies) {
        val t = e.type % EnemyBody.size
        val center = Offset(sx(e.x), sy(e.y))
        val r = GameConfig.enemyRadius(e.type) * scale
        drawCircle(EnemyBody[t], radius = r, center = center)
        drawCircle(EnemyDark[t], radius = r, center = center, style = Stroke(width = 2.5f))
        // Eyes for a touch of menace.
        val eo = r * 0.38f
        val eye = if (t == 1) Color(0xFFFF5252) else Color.Black
        drawCircle(eye, r * 0.16f, Offset(center.x - eo, center.y - r * 0.15f))
        drawCircle(eye, r * 0.16f, Offset(center.x + eo, center.y - r * 0.15f))
        // HP bar.
        if (e.hp < e.maxHp) {
            val frac = (e.hp.toFloat() / e.maxHp).coerceIn(0f, 1f)
            val bw = r * 2f
            val bx = center.x - bw / 2f
            val by = center.y - r - 8f
            drawRect(Color.Black.copy(alpha = 0.5f), Offset(bx, by), size(bw, 4f))
            drawRect(Color(0xFFFF8A80), Offset(bx, by), size(bw * frac, 4f))
        }
    }

    // Power-ups on the ground.
    for (pu in rs.powerups) {
        val t = pu.type % PowerColors.size
        val center = Offset(sx(pu.x), sy(pu.y))
        val r = GameConfig.POWERUP_RADIUS * scale
        drawCircle(PowerColors[t].copy(alpha = 0.25f), r * 1.5f, center)
        drawCircle(PowerColors[t], r, center)
        drawCircle(Color.White, r, center, style = Stroke(2.5f))
        paint.color = android.graphics.Color.WHITE
        paint.textSize = r * 1.4f
        drawContext.canvas.nativeCanvas.drawText(PowerLabels[t], center.x, center.y + r * 0.5f, paint)
    }

    // Arrows.
    for (b in rs.bullets) {
        val len = b.vx * b.vx + b.vy * b.vy
        val inv = if (len > 0f) 1f / kotlin.math.sqrt(len) else 0f
        val dx = b.vx * inv
        val dy = b.vy * inv
        val head = Offset(sx(b.x), sy(b.y))
        val tail = Offset(head.x - dx * 22f, head.y - dy * 22f)
        val color = PlayerColors[b.ownerColorIndex % PlayerColors.size]
        drawLine(Color(0xFF6D4C41), tail, head, strokeWidth = 4f) // shaft
        drawCircle(color, radius = 4f, center = head)               // arrowhead
    }

    // Players.
    for (p in rs.players) {
        val c = PlayerColors[p.colorIndex % PlayerColors.size]
        val center = Offset(sx(p.x), sy(p.y))
        val r = GameConfig.PLAYER_RADIUS * scale
        val drawColor = if (p.alive) c else c.copy(alpha = 0.25f)
        drawCircle(drawColor, radius = r, center = center)
        // Class-coloured core.
        drawCircle(Color(Classes.byId(p.classId).color), radius = r * 0.5f, center = center)
        // Local player highlight ring.
        if (p.id == rs.localId) {
            drawCircle(Color.White, radius = r + 4f, center = center, style = Stroke(width = 3f))
        }
        // Drawn bow / aim direction.
        if (p.alive) {
            val end = Offset(center.x + cos(p.angle) * r * 1.6f, center.y + sin(p.angle) * r * 1.6f)
            drawLine(Color(0xFFEFE0B0), center, end, strokeWidth = 5f)
        }
        // Name + HP bar.
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 30f
        drawContext.canvas.nativeCanvas.drawText(p.name, center.x, center.y - r - 14f, paint)
        if (p.alive && p.hp < p.maxHp) {
            val frac = (p.hp.toFloat() / p.maxHp).coerceIn(0f, 1f)
            val bw = GameConfig.PLAYER_RADIUS * 2f * scale
            val bx = center.x - bw / 2f
            val by = center.y - r - 8f
            drawRect(Color.Black.copy(alpha = 0.5f), Offset(bx, by), androidx.compose.ui.geometry.Size(bw, 5f))
            drawRect(Color(0xFF81C784), Offset(bx, by), androidx.compose.ui.geometry.Size(bw * frac, 5f))
        }
    }
}
