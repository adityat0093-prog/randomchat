package com.example.randomchat

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) { App(vm) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Release camera and mic when the app leaves the foreground.
        if (!isChangingConfigurations) vm.stop()
    }
}

@Composable
private fun App(vm: ChatViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.isNotEmpty() && result.values.all { it }) vm.start()
        else vm.setNotice("Camera and microphone permissions are required to chat.")
    }

    if (s.phase == Phase.Idle) {
        Landing(
            notice = s.notice,
            onStart = { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
        )
    } else {
        ChatScreen(vm, s)
    }
}

@Composable
private fun Landing(notice: String?, onStart: () -> Unit) {
    var ok by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Random Chat", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Meet someone new by video and text.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ok, onCheckedChange = { ok = it })
            Text(
                "I am 18 or older. I will be respectful: no nudity, harassment or illegal content.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (notice != null) {
            Spacer(Modifier.height(12.dp))
            Text(notice, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStart, enabled = ok, modifier = Modifier.fillMaxWidth()) {
            Text("Start chatting")
        }
    }
}

@Composable
private fun ChatScreen(vm: ChatViewModel, s: UiState) {
    var draft by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Remote video (full screen)
        if (s.remoteVideo != null) {
            VideoView(vm.eglBase, s.remoteVideo, mirror = false, overlay = false, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                text = when (s.phase) {
                    Phase.Connecting -> "Connecting…"
                    Phase.Waiting -> "Looking for someone…"
                    else -> "Connecting video…"
                },
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // Local preview (picture-in-picture)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .size(110.dp, 160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray)
        ) {
            if (s.camOn) {
                VideoView(vm.eglBase, s.localVideo, mirror = true, overlay = true, modifier = Modifier.fillMaxSize())
            }
        }

        // Top-left status
        Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)) {
            Text("${s.online} online", color = Color.White, style = MaterialTheme.typography.labelLarge)
            s.notice?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = Color(0xFFFFD166), style = MaterialTheme.typography.bodySmall)
            }
        }

        // Bottom controls
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (s.messages.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x99000000))
                        .padding(10.dp)
                ) {
                    s.messages.takeLast(6).forEach { m ->
                        Text(
                            text = (if (m.mine) "You: " else "Stranger: ") + m.text,
                            color = if (m.mine) Color(0xFFB8B0FF) else Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(500) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Say something…") },
                    singleLine = true,
                    enabled = s.phase == Phase.Chatting,
                )
                Button(
                    onClick = { vm.sendChat(draft); draft = "" },
                    enabled = s.phase == Phase.Chatting && draft.isNotBlank(),
                ) { Text("Send") }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = vm::toggleMic, modifier = Modifier.weight(1f)) {
                    Text(if (s.micOn) "Mic on" else "Mic off")
                }
                FilledTonalButton(onClick = vm::toggleCam, modifier = Modifier.weight(1f)) {
                    Text(if (s.camOn) "Cam on" else "Cam off")
                }
                FilledTonalButton(onClick = vm::flipCamera, modifier = Modifier.weight(1f)) { Text("Flip") }
                FilledTonalButton(
                    onClick = vm::report,
                    enabled = s.phase == Phase.Chatting,
                    modifier = Modifier.weight(1f),
                ) { Text("Report") }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.stop() }) { Text("Stop") }
                Button(onClick = vm::next, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Next stranger", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun VideoView(
    eglBase: EglBase,
    track: VideoTrack?,
    mirror: Boolean,
    overlay: Boolean,
    modifier: Modifier = Modifier,
) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBase.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                setZOrderMediaOverlay(overlay)
                renderer = this
            }
        },
    )

    DisposableEffect(track, renderer) {
        val r = renderer
        val t = track
        if (r != null && t != null) runCatching { t.addSink(r) }
        onDispose {
            if (r != null && t != null) runCatching { t.removeSink(r) }
        }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { renderer?.release() } }
    }
}
