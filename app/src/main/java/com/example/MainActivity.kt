package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MikoUiEvent
import com.example.viewmodel.MikoViewModel
import com.example.websocket.SessionStatus
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val viewModel: MikoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold")
                ) { innerPadding ->
                    MikoAppScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MikoAppScreen(
    viewModel: MikoViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    // Check BuildConfig key
    val apiKey = BuildConfig.GEMINI_API_KEY

    // Handle Mic Permission
    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // SUGGESTIONS FOR ENGAGING IN CONVERSATIONS
    val conversationSuggestions = listOf(
        "Bolo 'Hello Miko'! \uD83D\uDC4B",
        "Tease me! \uD83D\uDE1C",
        "Say something flirty! \uD83D\uA7E1",
        "Who is Ankit Sinha? \uD83D\uDCBB",
        "Open website google.com! \uD83D\uDD0D",
        "Tell me a secret! \uD83E\uDD2B",
        "Do you like me? \uD83D\uDC8B"
    )

    // Receive and act upon ViewModel events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MikoUiEvent.OpenUrl -> {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(browserIntent)
                        Toast.makeText(context, "Miko launched: ${event.url}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open URL: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
                is MikoUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Modern Sci-Fi background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF040409),
                        Color(0xFF0A0914),
                        Color(0xFF0F0E23)
                    )
                )
            )
            .testTag("app_background_container")
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(400.dp)
                .blur(90.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = when (uiState.status) {
                            is SessionStatus.Connected -> {
                                if (uiState.isSpeaking) {
                                    listOf(Color(0x35FC00FF), Color.Transparent)
                                } else {
                                    listOf(Color(0x2A00F2FE), Color.Transparent)
                                }
                            }
                            is SessionStatus.Connecting -> {
                                listOf(Color(0x2AFFC400), Color.Transparent)
                            }
                            else -> {
                                listOf(Color(0x1F7A22FF), Color.Transparent)
                            }
                        }
                    )
                )
        )

        // Main Layout Scrollable area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Branding & AI Name
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text(
                    text = "MIKO",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 6.sp,
                    color = Color.White,
                    modifier = Modifier.testTag("miko_header_title")
                )
                
                Surface(
                    color = Color(0x337A22FF),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(
                        text = "Hindi Voice Companion",
                        color = Color(0xFFD0BCFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            // Central Interactive Voice Pod & Waveform
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Fluid animated Canvas sine visualizer
                    MikoWaveformVisualizer(
                        status = uiState.status,
                        isSpeaking = uiState.isSpeaking,
                        isListening = uiState.isListening,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .testTag("waveform_visualizer")
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Pulse/Glow Avatar Core
                    MikoVoiceGlobe(
                        status = uiState.status,
                        isSpeaking = uiState.isSpeaking,
                        isListening = uiState.isListening,
                        onClick = {
                            if (micPermissionState.status.isGranted) {
                                viewModel.toggleSession(apiKey)
                            } else {
                                micPermissionState.launchPermissionRequest()
                            }
                        }
                    )
                }
            }

            // Real-time Audio Transcript Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x191D1A30)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 150.dp)
                    .border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(20.dp))
                    .testTag("transcript_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val statusText = when (uiState.status) {
                        is SessionStatus.Connected -> {
                            if (uiState.isSpeaking) "Miko is Speaking... ❤️" else "Miko is Listening... ⚡"
                        }
                        is SessionStatus.Connecting -> "Waking up Miko... ✨"
                        is SessionStatus.Error -> "Offline (Error: Connect issue)"
                        else -> "Click Orb below to connect"
                    }
                    
                    Text(
                        text = statusText,
                        color = when (uiState.status) {
                            is SessionStatus.Connected -> if (uiState.isSpeaking) Color(0xFFFC00FF) else Color(0xFF00F2FE)
                            is SessionStatus.Connecting -> Color(0xFFFFC400)
                            else -> Color(0xFF8B8B9A)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val displayTranscript = if (uiState.transcript.isNotEmpty()) {
                        uiState.transcript
                    } else if (uiState.status is SessionStatus.Connected) {
                        "Tap suggestion below or talk freely in Hindi..."
                    } else {
                        "Let's chat! Tap suggestion chips or trigger the connection."
                    }

                    Text(
                        text = displayTranscript,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.testTag("live_transcript_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Suggestions Carousel
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Suggested Prompts",
                    color = Color(0xFF8B8B9A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(conversationSuggestions) { suggestion ->
                        Surface(
                            color = Color(0x331F1F35),
                            shape = RoundedCornerShape(100.dp),
                            border = BorderStroke(1.dp, Color(0x228B8B9A)),
                            modifier = Modifier.clickable {
                                Toast.makeText(context, "Unleash Voice API: Speak this out loud!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = suggestion,
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Footer Action Panel (Power / Voice Swap)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0x331F1F35), RoundedCornerShape(100.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Voice: ",
                        color = Color(0xFF8B8B9A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val voiceList = listOf("Aoede", "Kore")
                    voiceList.forEachIndexed { idx, voice ->
                        val isSelected = uiState.selectedVoice == voice
                        Text(
                            text = voice,
                            color = if (isSelected) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clickable { viewModel.changeVoice(voice) }
                                .padding(horizontal = 6.dp)
                        )
                        if (idx < voiceList.size - 1) {
                            Text(text = "|", color = Color.White.copy(alpha = 0.2f), fontSize = 12.sp)
                        }
                    }
                }

                // Developed By Attribution
                Text(
                    text = "Ankit Sinha Studio",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun MikoWaveformVisualizer(
    status: SessionStatus,
    isSpeaking: Boolean,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    if (status !is SessionStatus.Connected) {
        // Static sleep line
        Canvas(modifier = modifier) {
            val width = size.width
            val centerY = size.height / 2
            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 2.dp.toPx()
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phaseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        // Determine parameters based on active voice state
        val amplitude = if (isSpeaking) 50.dp.toPx() else if (isListening) 22.dp.toPx() else 8.dp.toPx()
        val waveCount = if (isSpeaking) 3 else 2
        val colors = if (isSpeaking) {
            listOf(Color(0xFFFC00FF), Color(0xFF00F2FE), Color(0xFF7A22FF))
        } else {
            listOf(Color(0xFF00F2FE), Color(0x8000F2FE))
        }

        for (w in 0 until waveCount) {
            val path = Path()
            val phaseShift = w * (PI / 2).toFloat()
            val frequency = if (isSpeaking) (1.5f + w * 0.5f) else 1.2f

            path.moveTo(0f, centerY)
            for (x in 0..width.toInt() step 5) {
                // Envelope window function to taper waves at boundaries nicely
                val normalizedX = x.toFloat() / width
                val envelope = sin(normalizedX * PI.toFloat()) // bell curve envelope

                val sinValue = sin((normalizedX * frequency * 2 * PI.toFloat()) - phaseOffset + phaseShift)
                val y = centerY + sinValue * amplitude * envelope
                path.lineTo(x.toFloat(), y)
            }

            drawPath(
                path = path,
                color = colors[w % colors.size].copy(alpha = if (isSpeaking) 0.8f - w * 0.2f else 0.5f),
                style = Stroke(width = if (w == 0) 3.dp.toPx() else 1.5.dp.toPx())
            )
        }
    }
}

@Composable
fun MikoVoiceGlobe(
    status: SessionStatus,
    isSpeaking: Boolean,
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status is SessionStatus.Connected) 1.25f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isSpeaking) 600 else if (isListening) 1200 else 2000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val innerBgColor = when (status) {
        is SessionStatus.Connected -> {
            if (isSpeaking) Color(0xFFFC00FF) else Color(0xFF00F2FE)
        }
        is SessionStatus.Connecting -> {
            Color(0xFFFFC400)
        }
        else -> {
            Color(0xFF7A22FF)
        }
    }

    Box(
        modifier = modifier
            .size(170.dp)
            .testTag("miko_voice_globe_container"),
        contentAlignment = Alignment.Center
    ) {
        // Multiple layered outer glowing circles
        Box(
            modifier = Modifier
                .size(130.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(innerBgColor.copy(alpha = 0.12f))
                .border(2.dp, innerBgColor.copy(alpha = 0.35f), CircleShape)
        )

        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(innerBgColor.copy(alpha = 0.2f))
        )

        // Actual Button Orb
        Surface(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .clickable { onClick() }
                .testTag("power_button_inner"),
            shape = CircleShape,
            color = innerBgColor,
            shadowElevation = 8.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // Interactive Emoji representing state beautifully
                Text(
                    text = when (status) {
                        is SessionStatus.Connected -> {
                            if (isSpeaking) "🔊" else "🎙️"
                        }
                        else -> "⚡"
                    },
                    fontSize = 32.sp,
                    modifier = Modifier.testTag("power_button_inner_icon")
                )
            }
        }
    }
}
