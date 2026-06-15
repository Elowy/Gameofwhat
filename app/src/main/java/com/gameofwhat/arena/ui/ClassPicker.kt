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
import androidx.compose.foundation.shape.CircleShape
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
import com.gameofwhat.arena.game.Classes
import com.gameofwhat.arena.ui.theme.Primary

/** Horizontal selector of playable classes. */
@Composable
fun ClassPicker(
    selectedId: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.horizontalScroll(rememberScrollState())) {
        Classes.all.forEach { c ->
            val selected = c.id == selectedId
            Card(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .width(132.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { onSelect(c.id) },
                shape = RoundedCornerShape(12.dp),
                border = if (selected) BorderStroke(2.dp, Primary) else null,
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) Primary.copy(alpha = 0.18f)
                    else Color.White.copy(alpha = 0.05f),
                ),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(c.color))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            c.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Primary else Color.White,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(c.blurb, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "HP ${c.maxHp}  •  SEB ${c.damage}",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
