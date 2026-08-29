package com.aegis.mobile.ui.sos

import android.view.MotionEvent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegis.mobile.speech.OfflineSpeechManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val speechManager = remember { OfflineSpeechManager(context) }

    var text by remember { mutableStateOf("") }
    var gpsStatus by remember { mutableStateOf("Acquiring...") }
    var sent by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { speechManager.destroy() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Emergency SOS") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "EMERGENCY SOS",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Mic button — HOLD TO SPEAK
            if (speechManager.isAvailable) {
                Text(
                    text = if (isListening) "Listening..." else "Hold to speak",
                    fontSize = 14.sp,
                    color = if (isListening) Color(0xFFD32F2F) else MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = if (isListening) Color(0xFFFFCDD2) else Color(0xFFE0E0E0),
                            shape = CircleShape
                        )
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val motionEvent = event.changes.firstOrNull()?.also { it.consume() }
                                    when (motionEvent?.pressed) {
                                        true -> {
                                            if (!isListening) {
                                                isListening = true
                                                partialText = ""
                                                speechManager.startListening(
                                                    onResult = { result ->
                                                        text = if (text.isBlank()) result else "$text $result"
                                                        isListening = false
                                                        partialText = ""
                                                    },
                                                    onPartial = { partial ->
                                                        partialText = partial
                                                    }
                                                )
                                            }
                                        }
                                        false -> {
                                            if (isListening) {
                                                speechManager.stopListening()
                                                isListening = false
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = "Hold to speak",
                        modifier = Modifier.size(40.dp),
                        tint = if (isListening) Color(0xFFD32F2F) else Color(0xFF757575)
                    )
                }

                if (partialText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"$partialText\"",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "— or type below —", fontSize = 12.sp, color = Color(0xFF9E9E9E))
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Describe your situation") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(text = "Location: $gpsStatus", fontSize = 14.sp)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { sent = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F)
                ),
                enabled = text.isNotBlank() && !sent
            ) {
                Text(
                    text = if (sent) "SOS SENT" else "SEND SOS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (sent) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "SOS stored locally. Will propagate when peers are nearby.",
                    color = Color(0xFF388E3C),
                    fontSize = 14.sp
                )
            }
        }
    }
}
