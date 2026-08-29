package com.aegis.mobile.ui.broadcast

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastScreen(onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Broadcast Message") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = "BROADCAST",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "TTL: 7 hops",
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Emergency message") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { sent = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = text.isNotBlank() && !sent
            ) {
                Text(
                    text = if (sent) "SENT" else "SEND BROADCAST",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (sent) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Broadcast stored. Will propagate through mesh.",
                    fontSize = 14.sp
                )
            }
        }
    }
}
