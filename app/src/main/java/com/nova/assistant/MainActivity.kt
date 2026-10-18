package com.nova.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val viewModel: AssistantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NovaTheme {
                AssistantScreen(viewModel)
            }
        }
    }
}

private val suggestions = listOf(
    "Open Chrome",
    "What time is it",
    "Set a timer for 5 minutes",
    "Help"
)

@Composable
fun AssistantScreen(vm: AssistantViewModel) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startListening() else vm.onMicPermissionDenied()
    }

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }

    fun send() {
        if (input.isNotBlank()) {
            vm.submit(input)
            input = ""
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        contentColor = NovaColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NovaColors.screenBrush)
        ) {
            Header(vm)
            ServiceControls(Modifier.padding(horizontal = 16.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.messages) { message -> MessageBubble(message) }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions) { text ->
                    QuickChip(text) { vm.submit(text) }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(NovaColors.Card)
                        .border(1.dp, NovaColors.Outline, RoundedCornerShape(28.dp))
                        .padding(start = 18.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = NovaColors.TextPrimary, fontSize = 16.sp),
                        cursorBrush = SolidColor(NovaColors.Indigo),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (input.isEmpty()) {
                                    Text(
                                        if (vm.isListening) "Listening…" else "Message Nova",
                                        color = NovaColors.TextSecondary,
                                        fontSize = 16.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    val hasText = input.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .then(
                                if (hasText) Modifier.background(NovaColors.accentBrush)
                                else Modifier.background(NovaColors.CardHigh)
                            )
                            .clickable(enabled = hasText) { send() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_send),
                            contentDescription = "Send",
                            tint = if (hasText) Color.White else NovaColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                MicOrb(listening = vm.isListening) {
                    if (vm.isListening) {
                        vm.stopListening()
                    } else {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) vm.startListening()
                        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }
    }
}

@Composable
fun Header(vm: AssistantViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(NovaColors.accentBrush),
            contentAlignment = Alignment.Center
        ) {
            Text("N", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Nova",
                color = NovaColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    Modifier
                        .size(7.dp)
                        .background(NovaColors.Warn, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "AI chat not configured · local commands",
                    color = NovaColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(NovaColors.Card)
                .border(1.dp, NovaColors.Outline, RoundedCornerShape(50))
                .clickable { vm.speakReplies = !vm.speakReplies }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                if (vm.speakReplies) "🔊 Voice on" else "🔇 Voice off",
                color = NovaColors.TextPrimary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun QuickChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(NovaColors.CardHigh)
            .border(1.dp, NovaColors.Outline, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = NovaColors.TextPrimary, fontSize = 13.sp)
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val shape = if (message.fromUser) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }
    val bubbleModifier = when {
        message.isError -> Modifier.background(MaterialTheme.colorScheme.errorContainer, shape)
        message.fromUser -> Modifier.background(NovaColors.accentBrush, shape)
        else -> Modifier.background(NovaColors.CardHigh, shape)
    }
    val textColor = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        message.fromUser -> Color.White
        else -> NovaColors.TextPrimary
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .then(bubbleModifier)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(message.text, color = textColor, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun PulseRing() {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Box(
        Modifier
            .size(56.dp)
            .scale(pulse)
            .background(NovaColors.Teal.copy(alpha = 0.28f), CircleShape)
    )
}

@Composable
fun MicOrb(listening: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(68.dp)) {
        if (listening) PulseRing()
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .then(
                    if (listening) Modifier.background(NovaColors.listeningBrush)
                    else Modifier.background(NovaColors.accentBrush)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = if (listening) "Stop listening" else "Speak",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
