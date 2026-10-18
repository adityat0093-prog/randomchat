package com.example.randomchat

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

enum class Kind { Mine, Stranger, System }

data class Msg(val text: String, val kind: Kind, val time: Long = System.currentTimeMillis())

enum class Phase { Idle, Connecting, Waiting, Chatting, Ended }

data class UiState(
    val phase: Phase = Phase.Idle,
    val messages: List<Msg> = emptyList(),
    val online: Int = 0,
    val strangerTyping: Boolean = false,
    val notice: String? = null,
)

class ChatViewModel : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var signaling: Signaling? = null
    private var typingSent = false

    private val stopTyping = Runnable { setTyping(false) }
    private val clearStrangerTyping = Runnable { _state.update { it.copy(strangerTyping = false) } }
    private val clearNotice = Runnable { _state.update { it.copy(notice = null) } }

    /** Connects to the server and starts looking for a stranger. */
    fun start() {
        stop()
        _state.value = UiState(phase = Phase.Connecting)
        signaling = Signaling(BuildConfig.SERVER_URL, object : Signaling.Listener {
            override fun onOpen() {
                signaling?.send(JSONObject().put("type", "join").put("age18", true))
            }

            override fun onMessage(json: JSONObject) {
                main.post { handle(json) }
            }

            override fun onClosed(reason: String) {
                main.post {
                    if (_state.value.phase != Phase.Idle) {
                        stop("Connection lost. Check your internet and try again.")
                    }
                }
            }
        }).also { it.connect() }
    }

    fun stop(notice: String? = null) {
        main.removeCallbacksAndMessages(null)
        typingSent = false
        signaling?.let {
            it.send(JSONObject().put("type", "leave"))
            it.close()
        }
        signaling = null
        _state.value = UiState(phase = Phase.Idle, notice = notice)
    }

    /** Skip the current stranger (or find a new one after they left). */
    fun next() {
        main.removeCallbacks(stopTyping)
        typingSent = false
        _state.update {
            it.copy(phase = Phase.Waiting, messages = emptyList(), strangerTyping = false)
        }
        signaling?.send(JSONObject().put("type", "next"))
    }

    fun report() {
        signaling?.send(JSONObject().put("type", "report").put("reason", "user_report"))
        next()
        showNotice("Thanks, we've recorded your report. Looking for someone new…")
    }

    fun sendChat(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _state.value.phase != Phase.Chatting) return
        main.removeCallbacks(stopTyping)
        setTyping(false)
        signaling?.send(JSONObject().put("type", "chat").put("text", clean))
        addMessage(Msg(clean, Kind.Mine))
    }

    /** Call whenever the text field changes so the stranger sees "typing…". */
    fun onDraftChanged(text: String) {
        if (_state.value.phase != Phase.Chatting) return
        main.removeCallbacks(stopTyping)
        if (text.isBlank()) {
            setTyping(false)
            return
        }
        setTyping(true)
        main.postDelayed(stopTyping, 2500)
    }

    private fun setTyping(on: Boolean) {
        if (typingSent == on) return
        typingSent = on
        signaling?.send(JSONObject().put("type", "typing").put("on", on))
    }

    private fun addMessage(msg: Msg) {
        _state.update { it.copy(messages = (it.messages + msg).takeLast(300)) }
    }

    private fun showNotice(text: String) {
        main.removeCallbacks(clearNotice)
        _state.update { it.copy(notice = text) }
        main.postDelayed(clearNotice, 3500)
    }

    private fun handle(m: JSONObject) {
        when (m.optString("type")) {
            "waiting" -> _state.update {
                it.copy(phase = Phase.Waiting, messages = emptyList(), strangerTyping = false)
            }

            "matched" -> _state.update {
                it.copy(
                    phase = Phase.Chatting,
                    strangerTyping = false,
                    messages = listOf(Msg("You're connected to a stranger. Say hi! 👋", Kind.System)),
                )
            }

            "chat" -> {
                main.removeCallbacks(clearStrangerTyping)
                _state.update { it.copy(strangerTyping = false) }
                addMessage(Msg(m.optString("text"), Kind.Stranger))
            }

            "typing" -> {
                main.removeCallbacks(clearStrangerTyping)
                val on = m.optBoolean("on")
                _state.update { it.copy(strangerTyping = on) }
                if (on) main.postDelayed(clearStrangerTyping, 5000)
            }

            "partner_left" -> {
                main.removeCallbacks(clearStrangerTyping)
                _state.update {
                    it.copy(
                        phase = Phase.Ended,
                        strangerTyping = false,
                        messages = (it.messages + Msg("The stranger left the chat.", Kind.System)).takeLast(300),
                    )
                }
            }

            "online" -> _state.update { it.copy(online = m.optInt("count")) }

            "banned" -> stop("You've been temporarily banned after multiple reports.")

            "error" -> showNotice(m.optString("message"))
        }
    }

    override fun onCleared() {
        stop()
    }
}
