package com.gameofwhat.arena.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gameofwhat.arena.AppViewModel
import com.gameofwhat.arena.game.GameConfig
import com.gameofwhat.arena.game.GameEngine
import com.gameofwhat.arena.game.Phase
import com.gameofwhat.arena.game.RenderState
import com.gameofwhat.arena.ui.theme.Background
import com.gameofwhat.arena.ui.theme.Danger
import com.gameofwhat.arena.ui.theme.PlayerColors
import com.gameofwhat.arena.ui.theme.Primary
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val EnemyColors = listOf(
    Color(0xFFFF6E6E), // grunt
    Color(0xFFFFF176), // fast
    Color(0xFFBA68C8), // tank
)

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
            HealthBar(rs.localHp, rs.localMaxHp)
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
    // Arena floor + border.
    drawRect(
        color = Color(0xFF11183A),
        topLeft = Offset(sx(0f), sy(0f)),
        size = androidx.compose.ui.geometry.Size(world * scale, world * scale),
    )
    drawRect(
        color = Primary.copy(alpha = 0.4f),
        topLeft = Offset(sx(0f), sy(0f)),
        size = androidx.compose.ui.geometry.Size(world * scale, world * scale),
        style = Stroke(width = 3f),
    )
    // Grid lines.
    val step = world / 10f
    var g = step
    while (g < world) {
        drawLine(Color.White.copy(alpha = 0.05f), Offset(sx(g), sy(0f)), Offset(sx(g), sy(world)))
        drawLine(Color.White.copy(alpha = 0.05f), Offset(sx(0f), sy(g)), Offset(sx(world), sy(g)))
        g += step
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

    // Enemies.
    for (e in rs.enemies) {
        val c = EnemyColors[e.type % EnemyColors.size]
        val r = GameConfig.ENEMY_RADIUS * scale
        drawCircle(c, radius = r, center = Offset(sx(e.x), sy(e.y)))
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = r, center = Offset(sx(e.x), sy(e.y)), style = Stroke(width = 2f))
        // HP bar.
        if (e.hp < e.maxHp) {
            val frac = (e.hp.toFloat() / e.maxHp).coerceIn(0f, 1f)
            val bw = GameConfig.ENEMY_RADIUS * 2f * scale
            val bx = sx(e.x) - bw / 2f
            val by = sy(e.y) - r - 8f
            drawRect(Color.Black.copy(alpha = 0.5f), Offset(bx, by), androidx.compose.ui.geometry.Size(bw, 4f))
            drawRect(Color(0xFFFF8A80), Offset(bx, by), androidx.compose.ui.geometry.Size(bw * frac, 4f))
        }
    }

    // Bullets.
    for (b in rs.bullets) {
        drawCircle(
            color = PlayerColors[b.ownerColorIndex % PlayerColors.size],
            radius = 5f * scale + 2f,
            center = Offset(sx(b.x), sy(b.y)),
        )
    }

    // Players.
    for (p in rs.players) {
        val c = PlayerColors[p.colorIndex % PlayerColors.size]
        val center = Offset(sx(p.x), sy(p.y))
        val r = GameConfig.PLAYER_RADIUS * scale
        val drawColor = if (p.alive) c else c.copy(alpha = 0.25f)
        drawCircle(drawColor, radius = r, center = center)
        // Local player highlight ring.
        if (p.id == rs.localId) {
            drawCircle(Color.White, radius = r + 4f, center = center, style = Stroke(width = 3f))
        }
        // Gun barrel.
        if (p.alive) {
            val end = Offset(center.x + cos(p.angle) * r * 1.6f, center.y + sin(p.angle) * r * 1.6f)
            drawLine(Color.White, center, end, strokeWidth = 5f)
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
