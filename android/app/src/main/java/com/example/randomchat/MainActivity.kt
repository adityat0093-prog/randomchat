package com.example.randomchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            RandomChatTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.Bg) { App(vm) }
            }
        }
    }
}

@Composable
private fun App(vm: ChatViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    if (s.phase == Phase.Idle) {
        Landing(notice = s.notice, onStart = vm::start)
    } else {
        ChatScreen(vm, s)
    }
}
