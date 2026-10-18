package com.example.randomchat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Landing
// ---------------------------------------------------------------------------------------------

@Composable
fun Landing(notice: String?, onStart: () -> Unit) {
    var ok by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1C1745), Palette.Bg)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(Palette.AccentGradient),
                contentAlignment = Alignment.Center,
            ) { Text("💬", fontSize = 42.sp) }

            Spacer(Modifier.height(24.dp))
            Text(
                "Random Chat",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Talk to someone new. Anonymous, no sign-up.",
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
            )

            Spacer(Modifier.height(28.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Palette.Surface)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Rule("Anonymous: no names, no accounts")
                Rule("Be kind: harassment gets you banned")
                Rule("Report anyone who crosses the line")
                Rule("Messages aren't stored")
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ok,
                    onCheckedChange = { ok = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Palette.Accent,
                        uncheckedColor = Palette.TextSecondary,
                    ),
                )
                Text(
                    "I am 18 or older and agree to follow the rules.",
                    modifier = Modifier.weight(1f),
                    color = Palette.TextPrimary,
                    fontSize = 14.sp,
                )
            }

            if (notice != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    notice,
                    color = Palette.Danger,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStart,
                enabled = ok,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Accent,
                    contentColor = Color.White,
                    disabledContainerColor = Palette.SurfaceHi,
                    disabledContentColor = Palette.TextSecondary,
                ),
            ) {
                Text("Start chatting", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Rule(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Palette.Online,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, color = Palette.TextPrimary, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------------------------------------
// Chat
// ---------------------------------------------------------------------------------------------

@Composable
fun ChatScreen(vm: ChatViewModel, s: UiState) {
    var draft by remember { mutableStateOf("") }
    var confirmReport by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    BackHandler { confirmLeave = true }

    val inConversation = s.phase == Phase.Chatting || s.phase == Phase.Ended

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .systemBarsPadding()
            .imePadding()
    ) {
        TopBar(
            s = s,
            onNext = { draft = ""; vm.next() },
            onReport = { confirmReport = true },
            onClose = { confirmLeave = true },
        )

        s.notice?.let {
            Text(
                it,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.SurfaceHi)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color(0xFFFFD166),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                s.phase == Phase.Connecting -> SearchingView("Connecting…", "Reaching the server")
                s.phase == Phase.Waiting -> SearchingView(
                    "Looking for someone…",
                    "Hang tight, this usually takes a few seconds",
                )
                else -> MessageList(s)
            }
        }

        if (inConversation) {
            if (s.phase == Phase.Ended) {
                Button(
                    onClick = { draft = ""; vm.next() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent),
                ) { Text("Find a new stranger", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            } else {
                InputBar(
                    draft = draft,
                    onDraft = { draft = it.take(500); vm.onDraftChanged(draft) },
                    onSend = {
                        vm.sendChat(draft)
                        draft = ""
                    },
                )
            }
        }
    }

    if (confirmReport) {
        AlertDialog(
            onDismissRequest = { confirmReport = false },
            title = { Text("Report this person?") },
            text = {
                Text("Use this for harassment, nudity or illegal content. Repeated reports lead to a ban. You'll be matched with someone new.")
            },
            confirmButton = {
                TextButton(onClick = { confirmReport = false; draft = ""; vm.report() }) {
                    Text("Report", color = Palette.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReport = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this chat?") },
            text = { Text("The conversation will be gone and you'll return to the home screen.") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; vm.stop() }) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Stay") }
            },
        )
    }
}

@Composable
private fun TopBar(s: UiState, onNext: () -> Unit, onReport: () -> Unit, onClose: () -> Unit) {
    val inConversation = s.phase == Phase.Chatting || s.phase == Phase.Ended
    val (status, statusColor) = when {
        s.phase == Phase.Chatting && s.strangerTyping -> "typing…" to Palette.Online
        s.phase == Phase.Chatting -> "Connected" to Palette.Online
        s.phase == Phase.Ended -> "Disconnected" to Palette.TextSecondary
        s.phase == Phase.Waiting -> "Searching…" to Palette.TextSecondary
        else -> "Connecting…" to Palette.TextSecondary
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Palette.AccentGradient),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (inConversation) "S" else "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }

        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                if (inConversation) "Stranger" else "Random Chat",
                color = Palette.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Text(
                status + if (s.online > 0) "  ·  ${s.online} online" else "",
                color = statusColor,
                fontSize = 12.sp,
            )
        }

        TextButton(
            onClick = onNext,
            enabled = inConversation,
            colors = ButtonDefaults.textButtonColors(
                contentColor = Palette.Accent,
                disabledContentColor = Palette.TextSecondary.copy(alpha = 0.5f),
            ),
        ) {
            Text("Next", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }

        IconButton(onClick = onReport, enabled = inConversation) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "Report",
                tint = if (inConversation) Palette.Danger else Palette.TextSecondary.copy(alpha = 0.5f),
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Leave", tint = Palette.TextSecondary)
        }
    }
}

@Composable
private fun MessageList(s: UiState) {
    val listState = rememberLazyListState()
    val total = s.messages.size + if (s.strangerTyping) 1 else 0

    LaunchedEffect(total) {
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(s.messages) { m -> MessageRow(m) }
        if (s.strangerTyping) {
            item { TypingBubble() }
        }
    }
}

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

@Composable
private fun MessageRow(m: Msg) {
    when (m.kind) {
        Kind.System -> Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                m.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Surface)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                color = Palette.TextSecondary,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
            )
        }

        Kind.Mine -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Bubble(
                text = m.text,
                time = m.time,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp),
                background = Modifier.background(
                    Palette.AccentGradient,
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp),
                ),
                textColor = Color.White,
                timeColor = Color.White.copy(alpha = 0.7f),
            )
        }

        Kind.Stranger -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Bubble(
                text = m.text,
                time = m.time,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp),
                background = Modifier.background(
                    Palette.SurfaceHi,
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp),
                ),
                textColor = Palette.TextPrimary,
                timeColor = Palette.TextSecondary,
            )
        }
    }
}

@Composable
private fun Bubble(
    text: String,
    time: Long,
    shape: RoundedCornerShape,
    background: Modifier,
    textColor: Color,
    timeColor: Color,
) {
    Column(
        Modifier
            .widthIn(max = 300.dp)
            .clip(shape)
            .then(background)
            .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 6.dp),
    ) {
        Text(text, color = textColor, fontSize = 15.5.sp, lineHeight = 21.sp)
        Text(
            timeFormat.format(Date(time)),
            modifier = Modifier.align(Alignment.End),
            color = timeColor,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun TypingBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        Modifier
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp))
            .background(Palette.SurfaceHi)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val a by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 550, delayMillis = i * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Palette.TextSecondary.copy(alpha = a))
            )
        }
    }
}

@Composable
private fun SearchingView(title: String, subtitle: String) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha",
    )

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(130.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Palette.Accent.copy(alpha = alpha))
            )
            Box(
                Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Palette.AccentGradient),
                contentAlignment = Alignment.Center,
            ) { Text("💬", fontSize = 30.sp) }
        }
        Spacer(Modifier.height(28.dp))
        Text(title, color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Palette.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun InputBar(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraft,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message…", color = Palette.TextSecondary) },
            maxLines = 4,
            shape = RoundedCornerShape(26.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank()) onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Palette.SurfaceHi,
                unfocusedContainerColor = Palette.SurfaceHi,
                focusedBorderColor = Palette.Accent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Palette.TextPrimary,
                unfocusedTextColor = Palette.TextPrimary,
                cursorColor = Palette.Accent,
            ),
        )
        IconButton(
            onClick = onSend,
            enabled = draft.isNotBlank(),
            modifier = Modifier.size(50.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Palette.Accent,
                contentColor = Color.White,
                disabledContainerColor = Palette.SurfaceHi,
                disabledContentColor = Palette.TextSecondary,
            ),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}
