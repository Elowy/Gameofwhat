package com.gameofwhat.arena.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gameofwhat.arena.ui.theme.Primary
import kotlin.math.sqrt

/**
 * A floating virtual joystick. Reports a normalised direction vector (each component in
 * [-1, 1]) via [onMove]; (0, 0) when released.
 */
@Composable
fun Joystick(
    modifier: Modifier = Modifier,
    diameter: Dp = 150.dp,
    onMove: (Float, Float) -> Unit,
) {
    var knob by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier
            .size(diameter)
            .pointerInput(Unit) {
                val radius = size.width / 2f
                detectDragGestures(
                    onDragStart = { start ->
                        val v = start - Offset(radius, radius)
                        knob = clampToRadius(v, radius)
                        emit(knob, radius, onMove)
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        knob = clampToRadius(knob + drag, radius)
                        emit(knob, radius, onMove)
                    },
                    onDragEnd = { knob = Offset.Zero; onMove(0f, 0f) },
                    onDragCancel = { knob = Offset.Zero; onMove(0f, 0f) },
                )
            }
    ) {
        val r = size.width / 2f
        val center = Offset(r, r)
        drawCircle(Color.White.copy(alpha = 0.08f), radius = r, center = center)
        drawCircle(
            Primary.copy(alpha = 0.35f),
            radius = r,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
        )
        drawCircle(Primary.copy(alpha = 0.6f), radius = r * 0.42f, center = center + knob)
    }
}

private fun clampToRadius(v: Offset, radius: Float): Offset {
    val len = sqrt(v.x * v.x + v.y * v.y)
    if (len <= radius || len == 0f) return v
    val s = radius / len
    return Offset(v.x * s, v.y * s)
}

private fun emit(knob: Offset, radius: Float, onMove: (Float, Float) -> Unit) {
    val nx = knob.x / radius
    val ny = knob.y / radius
    // Clamp magnitude to 1 so diagonal isn't faster.
    val len = sqrt(nx * nx + ny * ny)
    if (len > 1f) onMove(nx / len, ny / len) else onMove(nx, ny)
}
