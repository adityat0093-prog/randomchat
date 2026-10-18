package com.example.randomchat

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack

data class ChatMsg(val text: String, val mine: Boolean)

enum class Phase { Idle, Connecting, Waiting, Chatting }

data class UiState(
    val phase: Phase = Phase.Idle,
    val messages: List<ChatMsg> = emptyList(),
    val online: Int = 0,
    val remoteVideo: VideoTrack? = null,
    val localVideo: VideoTrack? = null,
    val micOn: Boolean = true,
    val camOn: Boolean = true,
    val notice: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val eglBase: EglBase = EglBase.create()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var signaling: Signaling? = null
    private var rtc: RtcClient? = null

    /** Call after camera + mic permissions are granted and the 18+ checkbox is ticked. */
    fun start() {
        stop()
        val client = RtcClient(
            context = getApplication(),
            eglBase = eglBase,
            onSignal = { data -> sendRaw(JSONObject().put("type", "signal").put("data", data)) },
            onRemoteVideo = { track -> main.post { _state.update { it.copy(remoteVideo = track) } } },
        )
        client.startLocalMedia()
        rtc = client

        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.isSpeakerphoneOn = true

        _state.value = UiState(phase = Phase.Connecting, localVideo = client.localVideoTrack)

        signaling = Signaling(BuildConfig.SERVER_URL, object : Signaling.Listener {
            override fun onOpen() {
                sendRaw(JSONObject().put("type", "join").put("age18", true))
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
        signaling?.let {
            it.send(JSONObject().put("type", "leave"))
            it.close()
        }
        signaling = null
        rtc?.release()
        rtc = null
        audio.mode = AudioManager.MODE_NORMAL
        audio.isSpeakerphoneOn = false
        _state.value = UiState(phase = Phase.Idle, notice = notice)
    }

    fun setNotice(text: String?) = _state.update { it.copy(notice = text) }

    fun next() {
        rtc?.endCall()
        _state.update { it.copy(phase = Phase.Waiting, messages = emptyList(), remoteVideo = null, notice = null) }
        sendRaw(JSONObject().put("type", "next"))
    }

    fun report() {
        sendRaw(JSONObject().put("type", "report").put("reason", "user_report"))
        next()
        _state.update { it.copy(notice = "Reported. Finding someone new…") }
    }

    fun sendChat(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _state.value.phase != Phase.Chatting) return
        sendRaw(JSONObject().put("type", "chat").put("text", clean))
        _state.update { it.copy(messages = it.messages + ChatMsg(clean, true)) }
    }

    fun toggleMic() {
        val on = !_state.value.micOn
        rtc?.setMicEnabled(on)
        _state.update { it.copy(micOn = on) }
    }

    fun toggleCam() {
        val on = !_state.value.camOn
        rtc?.setCameraEnabled(on)
        _state.update { it.copy(camOn = on) }
    }

    fun flipCamera() {
        rtc?.switchCamera()
    }

    private fun handle(m: JSONObject) {
        when (m.optString("type")) {
            "waiting" -> {
                rtc?.endCall()
                _state.update { it.copy(phase = Phase.Waiting, messages = emptyList(), remoteVideo = null) }
            }

            "matched" -> {
                _state.update { it.copy(phase = Phase.Chatting, messages = emptyList(), notice = null) }
                rtc?.startCall(m.optBoolean("initiator"), parseIce(m.optJSONArray("iceServers")))
            }

            "signal" -> m.optJSONObject("data")?.let { rtc?.handleSignal(it) }

            "chat" -> _state.update {
                it.copy(messages = it.messages + ChatMsg(m.optString("text"), false))
            }

            "partner_left" -> {
                rtc?.endCall()
                _state.update {
                    it.copy(
                        phase = Phase.Waiting,
                        messages = emptyList(),
                        remoteVideo = null,
                        notice = "Stranger left. Finding someone new…",
                    )
                }
                sendRaw(JSONObject().put("type", "next"))
            }

            "online" -> _state.update { it.copy(online = m.optInt("count")) }

            "banned" -> stop("You have been temporarily banned after multiple reports.")

            "error" -> _state.update { it.copy(notice = m.optString("message")) }
        }
    }

    private fun sendRaw(obj: JSONObject) {
        signaling?.send(obj)
    }

    private fun parseIce(arr: JSONArray?): List<PeerConnection.IceServer> {
        val out = mutableListOf<PeerConnection.IceServer>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val builder = PeerConnection.IceServer.builder(o.getString("urls"))
                if (o.has("username")) builder.setUsername(o.getString("username"))
                if (o.has("credential")) builder.setPassword(o.getString("credential"))
                out += builder.createIceServer()
            }
        }
        if (out.isEmpty()) {
            out += PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        }
        return out
    }

    override fun onCleared() {
        stop()
        eglBase.release()
    }
}
