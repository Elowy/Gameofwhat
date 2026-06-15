package com.gameofwhat.arena.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gameofwhat.arena.AppViewModel
import com.gameofwhat.arena.game.Maps
import com.gameofwhat.arena.ui.theme.PlayerColors
import com.gameofwhat.arena.ui.theme.Primary

@Composable
fun LobbyScreen(vm: AppViewModel) {
    val net = vm.currentNet ?: return
    val players by net.players.collectAsState()
    val meta by net.meta.collectAsState()
    val isHost = net.isHost

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("SZOBAKÓD", color = Primary.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
        Text(
            net.roomCode,
            fontSize = 56.sp,
            fontWeight = FontWeight.Black,
            color = Primary,
        )
        Text("Oszd meg a kódot a barátaiddal!", fontSize = 13.sp)

        Spacer(Modifier.height(8.dp))
        Text("Játékosok (${players.size})", fontWeight = FontWeight.Bold)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            players.values.sortedBy { it.colorIndex }.forEach { p ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(PlayerColors[p.colorIndex % PlayerColors.size])
                        )
                        Text(
                            p.name + if (p.id == net.localId) " (te)" else "",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("PÁLYA", fontWeight = FontWeight.Bold, color = Primary.copy(alpha = 0.7f))
        if (isHost) {
            MapPicker(
                selectedId = meta.mapId,
                enabled = true,
                onSelect = vm::hostSetMap,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(Maps.byId(meta.mapId).name, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))

        if (isHost) {
            Button(
                onClick = vm::hostStartMatch,
                enabled = players.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text("JÁTÉK INDÍTÁSA", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        } else {
            Text("Várakozás a host indítására…", color = Primary.copy(alpha = 0.8f))
        }

        OutlinedButton(
            onClick = vm::backToMenu,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Kilépés") }
    }
}
