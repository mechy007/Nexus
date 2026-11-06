package com.mechy007.nexus.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.live2d.demo.full.GLRenderer
import com.live2d.demo.full.LAppDelegate

class MainActivity : ComponentActivity() {
    internal var alliumGlView: GLSurfaceView? = null
    internal var alliumInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var darkTheme by rememberSaveable { mutableStateOf(true) }
            NexusTheme(darkTheme = darkTheme) {
                NexusApp(darkTheme = darkTheme, onThemeChange = { darkTheme = it })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        alliumGlView?.onResume()
    }

    override fun onPause() {
        alliumGlView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        alliumGlView?.onPause()
        if (alliumInitialized) LAppDelegate.getInstance().onDestroy()
        alliumGlView = null
        super.onDestroy()
    }
}

private data class ChatLine(val id: Int, val text: String, val fromUser: Boolean)

private enum class NexusPage(val label: String, val symbol: String) {
    HOME("Home", "⌂"),
    CHAT("Chat", "▤"),
    BROWSER("Browser", "◎"),
    MIND_SPACE("Mind Space", "✧"),
    SETTINGS("Settings", "⚙")
}

private val Sky = Color(0xFF4AADE3)
private val SkyBright = Color(0xFF83D0F5)
private val LightBackground = Color(0xFFF3FAFE)
private val LightSurface = Color(0xFFFFFFFF)
private val LightText = Color(0xFF142B3D)
private val LightMuted = Color(0xFF647D90)
private val Midnight = Color(0xFF07182D)
private val MidnightSurface = Color(0xFF102943)
private val MidnightRaised = Color(0xFF163550)
private val DarkText = Color(0xFFEAF5FC)
private val DarkMuted = Color(0xFFA4BACB)

@Composable
private fun NexusTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = SkyBright,
            onPrimary = Color(0xFF062039),
            secondary = Color(0xFF9ACFEB),
            background = Midnight,
            onBackground = DarkText,
            surface = MidnightSurface,
            onSurface = DarkText,
            surfaceVariant = MidnightRaised,
            onSurfaceVariant = DarkMuted,
            outline = Color(0xFF36546D)
        )
    } else {
        lightColorScheme(
            primary = Sky,
            onPrimary = Color.White,
            secondary = Color(0xFF287EAD),
            background = LightBackground,
            onBackground = LightText,
            surface = LightSurface,
            onSurface = LightText,
            surfaceVariant = Color(0xFFE6F3FA),
            onSurfaceVariant = LightMuted,
            outline = Color(0xFFD0E2ED)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun NexusApp(darkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    var page by rememberSaveable { mutableStateOf(NexusPage.HOME) }
    var draft by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val chat = remember {
        mutableStateListOf(
            ChatLine(0, "NEXUS is ready to explore. Connect an AI provider in Settings before expecting generated replies.", false)
        )
    }
    val voiceInput = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        draft = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (draft.isNotBlank()) page = NexusPage.CHAT
    }

    fun submitMessage() {
        val message = draft.trim()
        if (message.isNotEmpty()) {
            chat += ChatLine(chat.size + 1, message, true)
            chat += ChatLine(
                chat.size + 1,
                "The NEXUS AI engine is not connected yet, so I can't generate a reply. Your message has not been sent to an AI provider.",
                false
            )
            draft = ""
            keyboard?.hide()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { NexusTopBar(onStatusClick = { page = NexusPage.SETTINGS }) },
        bottomBar = { NexusNavigationBar(selected = page, onSelect = { page = it }) }
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            when (page) {
                NexusPage.HOME -> HomeScreen(
                    onOpenChat = { page = NexusPage.CHAT },
                    onOpenBrowser = { page = NexusPage.BROWSER },
                    onOpenMindSpace = { page = NexusPage.MIND_SPACE },
                    onOpenSettings = { page = NexusPage.SETTINGS }
                )
                NexusPage.CHAT -> ChatScreen(
                    chat = chat,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSend = { submitMessage() },
                    onVoice = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to NEXUS")
                        }
                        try {
                            voiceInput.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                            chat += ChatLine(chat.size + 1, "No speech recognition app is available on this device.", false)
                        }
                    }
                )
                NexusPage.BROWSER -> BrowserScreen()
                NexusPage.MIND_SPACE -> MindSpaceScreen()
                NexusPage.SETTINGS -> SettingsScreen(darkTheme = darkTheme, onThemeChange = onThemeChange)
            }
        }
    }
}

@Composable
private fun NexusTopBar(onStatusClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_nexus_launcher_foreground),
                contentDescription = "NEXUS logo",
                modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("NEXUS", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
                Text("YOUR PERSONAL AI SPACE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp, letterSpacing = 1.35.sp)
            }
            Surface(
                onClick = onStatusClick,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Color(0xFFE0A64B), CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text("AI OFFLINE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
                }
            }
        }
    }
}

@Composable
private fun NexusNavigationBar(selected: NexusPage, onSelect: (NexusPage) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        NexusPage.entries.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = {
                    Text(item.symbol, fontSize = 19.sp, fontWeight = if (selected == item) FontWeight.SemiBold else FontWeight.Normal)
                },
                label = { Text(item.label, fontSize = 9.sp, maxLines = 1) },
                alwaysShowLabel = true
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onOpenChat: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenMindSpace: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your space, at a glance", color = MaterialTheme.colorScheme.onBackground, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Text("A home for your AI tools and companions.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        AlliumStage()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("QUICK ACCESS", "Open a workspace")
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                QuickAccessCard(Modifier.weight(1f), "▤", "Chat", "Talk to NEXUS", onOpenChat)
                QuickAccessCard(Modifier.weight(1f), "◎", "Browser", "Web tools", onOpenBrowser)
                QuickAccessCard(Modifier.weight(1f), "✧", "Mind Space", "AI residents", onOpenMindSpace)
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenSettings).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text("◉", color = MaterialTheme.colorScheme.primary, fontSize = 19.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI engine", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("No provider connected yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text("SET UP  ›", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
            }
        }
    }
}

@Composable
private fun AlliumStage() {
    val stageBrush = if (MaterialTheme.colorScheme.background == Midnight) {
        Brush.verticalGradient(listOf(Color(0xFF153B5B), Color(0xFF0B223A), Color(0xFF091A2E)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFE1F4FC), Color(0xFFD5EDF8), Color(0xFFEAF6FB)))
    }
    Box(
        Modifier.fillMaxWidth().height(390.dp).clip(RoundedCornerShape(30.dp)).background(stageBrush)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(30.dp))
    ) {
        Box(
            Modifier.align(Alignment.Center).size(250.dp).background(
                Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), Color.Transparent)),
                CircleShape
            )
        )
        AlliumLive2D(Modifier.fillMaxSize().padding(top = 22.dp, bottom = 58.dp))
        Surface(
            Modifier.align(Alignment.TopStart).padding(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            shape = RoundedCornerShape(50)
        ) {
            Text("ALLIUM  /  LIVE2D", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.82f))))
                .padding(horizontal = 18.dp, vertical = 15.dp)
        ) {
            Text("ALLIUM", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Your NEXUS companion", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QuickAccessCard(
    modifier: Modifier = Modifier,
    symbol: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(symbol, color = MaterialTheme.colorScheme.primary, fontSize = 21.sp)
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun ChatScreen(
    chat: List<ChatLine>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit
) {
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Conversation", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("Your messages stay on this screen until an AI provider is connected.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        OfflineNotice()
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(chat, key = { it.id }) { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start) {
                    Surface(
                        Modifier.fillMaxWidth(if (line.fromUser) 0.84f else 0.96f),
                        color = if (line.fromUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(17.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(line.text, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onVoice,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("⌁", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
                }
            }
            Spacer(Modifier.width(9.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Write a message", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSend, modifier = Modifier.size(48.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp)) {
                Text("↑", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OfflineNotice() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(Color(0xFFE0A64B), CircleShape))
            Spacer(Modifier.width(9.dp))
            Text("AI provider not connected", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BrowserScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Browser", color = MaterialTheme.colorScheme.onBackground, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text("Search and explore the web from NEXUS.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        EmptyFeatureCard(
            symbol = "◎",
            title = "Browser is not connected",
            description = "The browser and web-search tools are planned, but they are not available in this build yet."
        )
    }
}

@Composable
private fun MindSpaceScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Mind Space", color = MaterialTheme.colorScheme.onBackground, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text("The shared environment for NEXUS agents and companions.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NEXUS CORE", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("The foundation of your Mind Space", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Own agent runtime, hybrid AI, shared memory and permission-controlled tools.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                Text("FOUNDATION IN DEVELOPMENT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text("✧", color = MaterialTheme.colorScheme.primary, fontSize = 23.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Allium", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Primary companion · Live2D", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Text("CHARACTER", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        EmptyFeatureCard(
            symbol = "＋",
            title = "More residents",
            description = "NEXUS is designed to support additional AI residents. No other residents are configured yet."
        )
    }
}

@Composable
private fun SettingsScreen(darkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", color = MaterialTheme.colorScheme.onBackground, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text("Personalize your NEXUS experience.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("APPEARANCE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text("Choose your theme", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = !darkTheme, onClick = { onThemeChange(false) }, label = { Text("Light") })
                    FilterChip(selected = darkTheme, onClick = { onThemeChange(true) }, label = { Text("Midnight") })
                }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI CONNECTION", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text("No AI provider connected", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Provider setup and model routing will appear here when the connection flow is implemented.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ABOUT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text("NEXUS  ·  0.1.0", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("An independent AI ecosystem, built Android-first.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptyFeatureCard(symbol: String, title: String, description: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(symbol, color = MaterialTheme.colorScheme.primary, fontSize = 30.sp)
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun AlliumLive2D(modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? MainActivity
    val glView = remember(activity) {
        requireNotNull(activity) { "Allium renderer requires an Activity context." }.let { host ->
            if (!host.alliumInitialized) {
                LAppDelegate.getInstance().onStart(host)
                host.alliumInitialized = true
            }
            GLSurfaceView(host).apply {
                host.alliumGlView = this
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                setRenderer(GLRenderer())
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                preserveEGLContextOnPause = true
                setOnTouchListener { _, event ->
                    val x = event.x
                    val y = event.y
                    queueEvent {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> LAppDelegate.getInstance().onTouchBegan(x, y)
                            MotionEvent.ACTION_UP -> LAppDelegate.getInstance().onTouchEnd(x, y)
                            MotionEvent.ACTION_MOVE -> LAppDelegate.getInstance().onTouchMoved(x, y)
                        }
                    }
                    true
                }
            }
        }
    }

    DisposableEffect(glView) {
        glView.onResume()
        onDispose {
            glView.onPause()
            if (activity?.alliumGlView === glView) activity.alliumGlView = null
        }
    }

    AndroidView(factory = { glView }, modifier = modifier)
}
