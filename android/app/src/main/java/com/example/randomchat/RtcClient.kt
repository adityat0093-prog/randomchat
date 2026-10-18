package com.example.randomchat

import android.content.Context
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.DataChannel

/**
 * Owns the camera/mic and one PeerConnection at a time.
 * Signaling messages are emitted via [onSignal] and consumed via [handleSignal].
 */
class RtcClient(
    private val context: Context,
    private val eglBase: EglBase,
    private val onSignal: (JSONObject) -> Unit,
    private val onRemoteVideo: (VideoTrack?) -> Unit,
) {
    private val factory: PeerConnectionFactory
    private var capturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var audioSource: org.webrtc.AudioSource? = null
    private var pc: PeerConnection? = null
    private val pendingCandidates = mutableListOf<IceCandidate>()

    var localVideoTrack: VideoTrack? = null
        private set
    private var localAudioTrack: AudioTrack? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun startLocalMedia() {
        audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audio0", audioSource)

        val source = factory.createVideoSource(false)
        videoSource = source
        surfaceHelper = SurfaceTextureHelper.create("capture-thread", eglBase.eglBaseContext)

        val enumerator = Camera2Enumerator(context)
        val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
        capturer = cameraName?.let { enumerator.createCapturer(it, null) }
        capturer?.initialize(surfaceHelper, context, source.capturerObserver)
        capturer?.startCapture(1280, 720, 30)

        localVideoTrack = factory.createVideoTrack("video0", source)
    }

    fun startCall(initiator: Boolean, iceServers: List<PeerConnection.IceServer>) {
        closePeer()
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val peer = factory.createPeerConnection(config, observer) ?: return
        pc = peer
        localAudioTrack?.let { peer.addTrack(it, listOf("stream0")) }
        localVideoTrack?.let { peer.addTrack(it, listOf("stream0")) }

        if (initiator) {
            peer.createOffer(object : SimpleSdp() {
                override fun onCreateSuccess(desc: SessionDescription) {
                    peer.setLocalDescription(SimpleSdp(), desc)
                    onSignal(JSONObject().put("kind", "offer").put("sdp", desc.description))
                }
            }, MediaConstraints())
        }
    }

    fun handleSignal(data: JSONObject) {
        val peer = pc ?: return
        when (data.optString("kind")) {
            "offer" -> {
                val offer = SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp"))
                peer.setRemoteDescription(object : SimpleSdp() {
                    override fun onSetSuccess() {
                        drainCandidates(peer)
                        peer.createAnswer(object : SimpleSdp() {
                            override fun onCreateSuccess(desc: SessionDescription) {
                                peer.setLocalDescription(SimpleSdp(), desc)
                                onSignal(JSONObject().put("kind", "answer").put("sdp", desc.description))
                            }
                        }, MediaConstraints())
                    }
                }, offer)
            }

            "answer" -> {
                val answer = SessionDescription(SessionDescription.Type.ANSWER, data.getString("sdp"))
                peer.setRemoteDescription(object : SimpleSdp() {
                    override fun onSetSuccess() = drainCandidates(peer)
                }, answer)
            }

            "candidate" -> {
                val candidate = IceCandidate(
                    data.optString("sdpMid"),
                    data.optInt("sdpMLineIndex"),
                    data.getString("candidate"),
                )
                if (peer.remoteDescription != null) peer.addIceCandidate(candidate)
                else pendingCandidates += candidate
            }
        }
    }

    private fun drainCandidates(peer: PeerConnection) {
        pendingCandidates.forEach { peer.addIceCandidate(it) }
        pendingCandidates.clear()
    }

    fun endCall() = closePeer()

    private fun closePeer() {
        val peer = pc ?: return
        pc = null
        pendingCandidates.clear()
        onRemoteVideo(null)
        runCatching { peer.close() }
        runCatching { peer.dispose() }
    }

    fun setMicEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        capturer?.switchCamera(null)
    }

    fun release() {
        closePeer()
        try {
            capturer?.stopCapture()
        } catch (_: InterruptedException) {
        }
        capturer?.dispose()
        capturer = null
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        surfaceHelper?.dispose()
        factory.dispose()
    }

    private val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            onSignal(
                JSONObject()
                    .put("kind", "candidate")
                    .put("sdpMid", candidate.sdpMid)
                    .put("sdpMLineIndex", candidate.sdpMLineIndex)
                    .put("candidate", candidate.sdp)
            )
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) onRemoteVideo(track)
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
    }

    private open class SimpleSdp : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
