package com.gameofwhat.arena.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gameofwhat.arena.game.Maps
import com.gameofwhat.arena.ui.theme.Primary

/** Horizontal selector of arena variations. */
@Composable
fun MapPicker(
    selectedId: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        Maps.all.forEach { m ->
            val selected = m.id == selectedId
            Card(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .width(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { onSelect(m.id) },
                shape = RoundedCornerShape(12.dp),
                border = if (selected) BorderStroke(2.dp, Primary) else null,
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) Primary.copy(alpha = 0.18f)
                    else Color.White.copy(alpha = 0.05f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Tiny arena preview: floor with an accent dot.
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(m.floor)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(m.accent))
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        m.name,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Primary else Color.White,
                    )
                }
            }
        }
    }
}
