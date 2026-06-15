package com.gameofwhat.arena.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gameofwhat.arena.AppViewModel
import com.gameofwhat.arena.ui.theme.Danger
import com.gameofwhat.arena.ui.theme.Primary

@Composable
fun MenuScreen(vm: AppViewModel) {
    val name by vm.playerName
    val busy by vm.busy
    val error by vm.error
    val selectedMap by vm.selectedMapId
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("GAME OF WHAT", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Primary)
        Text(
            "Középkori coop aréna",
            fontSize = 15.sp,
            color = Primary.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = vm::setPlayerName,
            label = { Text("Neved") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "PÁLYA",
            fontWeight = FontWeight.Bold,
            color = Primary.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.Start),
        )
        MapPicker(
            selectedId = selectedMap,
            enabled = !busy,
            onSelect = vm::setSelectedMap,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = vm::startPractice,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("Gyakorlás (offline)", fontSize = 17.sp, fontWeight = FontWeight.Bold) }

        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Text("ONLINE", fontWeight = FontWeight.Bold, color = Primary.copy(alpha = 0.7f))

        if (!vm.firebaseAvailable) {
            Text(
                "Online módhoz add hozzá a google-services.json fájlt (lásd README).",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = Danger.copy(alpha = 0.85f),
            )
        }

        OutlinedButton(
            onClick = vm::createRoom,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Szoba létrehozása", fontSize = 16.sp) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(4) },
                label = { Text("Szobakód") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                ),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { vm.joinRoom(code) },
                enabled = !busy,
                modifier = Modifier.height(54.dp).width(120.dp),
            ) { Text("Csatlakozás") }
        }

        if (busy) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(color = Primary)
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Danger, textAlign = TextAlign.Center)
            OutlinedButton(onClick = vm::clearError) { Text("OK") }
        }
    }
}
