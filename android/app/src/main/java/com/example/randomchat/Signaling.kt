package com.example.randomchat

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Thin WebSocket wrapper around the matchmaking/signaling server. Callbacks arrive on OkHttp threads. */
class Signaling(private val url: String, private val listener: Listener) {

    interface Listener {
        fun onOpen()
        fun onMessage(json: JSONObject)
        fun onClosed(reason: String)
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null

    @Volatile
    private var closedByUs = false

    fun connect() {
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!closedByUs) listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (closedByUs) return
                runCatching { listener.onMessage(JSONObject(text)) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closedByUs) listener.onClosed(reason.ifBlank { "closed" })
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closedByUs) listener.onClosed(t.message ?: "failed")
            }
        })
    }

    fun send(obj: JSONObject) {
        socket?.send(obj.toString())
    }

    fun close() {
        closedByUs = true
        socket?.close(1000, null)
        client.dispatcher.executorService.shutdown()
    }
}
