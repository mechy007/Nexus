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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
        setContent { NexusTheme { NexusHome() } }
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
private enum class AvatarMode { ALLIUM, ORB }

private val Ink = Color(0xFF090A12)
private val Panel = Color(0xFF141522)
private val Violet = Color(0xFFBDA7FF)
private val Muted = Color(0xFF9292A8)
private val TextPrimary = Color(0xFFF4F0FF)

@Composable
private fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet,
            secondary = Color(0xFF8EDCEB),
            background = Ink,
            surface = Panel,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        ),
        content = content
    )
}

@Composable
private fun NexusHome() {
    var draft by rememberSaveable { mutableStateOf("") }
    var avatarMode by rememberSaveable { mutableStateOf(AvatarMode.ALLIUM) }
    val keyboard = LocalSoftwareKeyboardController.current
    val chat = remember {
        mutableStateListOf(
            ChatLine(0, "I’m here. Connect an AI provider to start a conversation.", false)
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
                "The NEXUS AI engine is not connected yet, so this message has not been processed.",
                false
            )
            draft = ""
            keyboard?.hide()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF171326), Color(0xFF0C0D18), Ink)
                )
            )
            .imePadding()
            .padding(horizontal = 18.dp)
    ) {
        Header()
        Spacer(Modifier.height(12.dp))

        AvatarStage(
            mode = avatarMode,
            onModeChange = { avatarMode = it },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("CONVERSATION", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                Text("A quiet place to think out loud.", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Text("SESSION", color = Color(0xFF8E85AA), fontSize = 9.sp, letterSpacing = 1.5.sp)
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(104.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(chat, key = { it.id }) { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(if (line.fromUser) 0.86f else 0.96f),
                        color = if (line.fromUser) Color(0xFF302744) else Color(0xFF171A29),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text(
                            line.text,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            color = if (line.fromUser) Color(0xFFF1E9FF) else Color(0xFFC6C4D4),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
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
                shape = CircleShape,
                color = Color(0xFF29223A),
                contentColor = Violet
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("⌁", fontSize = 25.sp, fontWeight = FontWeight.Light)
                }
            }
            Spacer(Modifier.width(9.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Talk to NEXUS", color = Muted, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() })
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { submit() },
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color(0xFF171222))
            ) {
                Text("↑", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_nexus_launcher_foreground),
            contentDescription = "NEXUS logo",
            modifier = Modifier.size(42.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("NEXUS", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text("YOUR AI COMPANION", color = Muted, fontSize = 8.sp, letterSpacing = 2.1.sp)
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF292238),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF51416E))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).background(Color(0xFFFFC477), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text("ENGINE SETUP", color = Color(0xFFE4C99F), fontSize = 8.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AvatarStage(
    mode: AvatarMode,
    onModeChange: (AvatarMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "avatarGlow")
    val glow by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF29213F), Color(0xFF17182A), Color(0xFF111320))
                )
            )
            .border(1.dp, Color(0xFF4A3D61).copy(alpha = 0.7f), RoundedCornerShape(30.dp))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(230.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Violet.copy(alpha = glow * 0.24f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        if (mode == AvatarMode.ALLIUM) {
            AlliumLive2D(
                modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 54.dp)
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFFD5C6FF), Color(0xFF8069B7), Color(0xFF302A4D))
                            ),
                            CircleShape
                        )
                        .border(1.dp, Color(0xFFD8C8FF).copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✦", color = Color.White, fontSize = 48.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text("NEXUS ORB", color = TextPrimary, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 18.dp)
        ) {
            Text(
                if (mode == AvatarMode.ALLIUM) "ALLIUM" else "NEXUS ORB",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
            Text(
                if (mode == AvatarMode.ALLIUM) "Live2D companion" else "Minimal companion mode",
                color = Color(0xFFB8AECF),
                fontSize = 11.sp
            )
        }

        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AvatarChoice("ALLIUM", selected = mode == AvatarMode.ALLIUM) { onModeChange(AvatarMode.ALLIUM) }
            AvatarChoice("ORB", selected = mode == AvatarMode.ORB) { onModeChange(AvatarMode.ORB) }
        }
    }
}

@Composable
private fun AvatarChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Color(0xFFBDA7FF) else Color(0xFF252536),
        contentColor = if (selected) Color(0xFF171222) else Color(0xFFD2CCDF),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4B465C))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            fontSize = 8.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AlliumLive2D(modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? MainActivity
    val glView = remember(activity) {
        requireNotNull(activity) { "Allium renderer requires an Activity context." }.let { host ->
            LAppDelegate.getInstance().onStart(host)
            host.alliumInitialized = true
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
