package com.mechy007.nexus.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexusTheme { NexusHome() } }
    }
}

private data class ChatLine(val id: Int, val text: String, val fromUser: Boolean)

private enum class AvatarMode { ALLIUM, ORB }

@Composable
private fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB69CFF),
            secondary = Color(0xFF72D9F5),
            background = Color(0xFF080A13),
            surface = Color(0xFF171B2B)
        ),
        content = content
    )
}

@Composable
private fun NexusHome() {
    var draft by rememberSaveable { mutableStateOf("") }
    var avatarMode by rememberSaveable { mutableStateOf(AvatarMode.ALLIUM) }
    val chat = remember {
        mutableStateListOf(
            ChatLine(
                0,
                "Welcome. The NEXUS Android interface is ready for development. The AI engine and Live2D renderer are not connected in this build yet.",
                false
            )
        )
    }
    val voiceInput = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        draft = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
    }

    fun submit() {
        val message = draft.trim()
        if (message.isNotEmpty()) {
            chat += ChatLine(chat.size + 1, message, true)
            chat += ChatLine(
                chat.size + 1,
                "Message received by the interface. The NEXUS AI runtime connection is still pending.",
                false
            )
            draft = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF17152C), Color(0xFF090B16), Color(0xFF080A13))
                )
            )
            .imePadding()
            .padding(horizontal = 18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("N E X U S", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("PERSONAL AI COMPANION", color = Color(0xFF999BB8), fontSize = 9.sp, letterSpacing = 2.sp)
            }
            Text("CORE OFFLINE", color = Color(0xFFFFC982), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(238.dp),
            color = Color(0xFF171A30),
            shape = RoundedCornerShape(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF44346B), Color(0xFF20213E), Color(0xFF111626))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF9276D6), Color(0xFF3C386D), Color(0xFF1C2038))
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (avatarMode == AvatarMode.ALLIUM) "A" else "✦",
                            fontSize = 48.sp,
                            color = Color(0xFFF1E9FF),
                            fontWeight = FontWeight.Light
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (avatarMode == AvatarMode.ALLIUM) "ALLIUM" else "NEXUS ORB",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                    Text(
                        if (avatarMode == AvatarMode.ALLIUM) "Live2D integration pending" else "Alternative avatar mode",
                        color = Color(0xFFB9B1D2),
                        fontSize = 10.sp
                    )
                    Row {
                        TextButton(onClick = { avatarMode = AvatarMode.ALLIUM }) { Text("ALLIUM") }
                        TextButton(onClick = { avatarMode = AvatarMode.ORB }) { Text("ORB") }
                    }
                }
            }
        }

        Text(
            "YOUR SPACE",
            color = Color(0xFF999BB8),
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items(chat, key = { it.id }) { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(if (line.fromUser) 0.88f else 0.94f),
                        color = if (line.fromUser) Color(0xFF393052) else Color(0xFF171D2D),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                if (line.fromUser) "YOU" else "NEXUS",
                                color = if (line.fromUser) Color(0xFFCBBEFF) else Color(0xFF8FD8E8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(line.text, color = Color(0xFFE9E7F4), fontSize = 13.sp, lineHeight = 19.sp)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to NEXUS")
                    }
                    try {
                        voiceInput.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        chat += ChatLine(chat.size + 1, "No speech recognition app is available.", false)
                    }
                },
                modifier = Modifier.size(50.dp),
                shape = CircleShape
            ) { Text("MIC", fontSize = 10.sp) }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message NEXUS…") },
                singleLine = true,
                shape = RoundedCornerShape(17.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() })
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { submit() }, modifier = Modifier.height(52.dp)) {
                Text("SEND", fontSize = 10.sp)
            }
        }
    }
}
