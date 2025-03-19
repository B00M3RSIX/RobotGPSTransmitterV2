package com.example.robotgpstransmitterv2.service

import com.example.robotgpstransmitterv2.utils.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Verwaltet die WebSocket-Verbindung zum ROS Bridge Server
 */
class WebSocketManager(
    private val serverUrl: String,
    private val maxRetryAttempts: Int,
    private val maxRetryDelay: Int
) {
    private var webSocket: WebSocket? = null
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()
    
    // Listener, der über Verbindungsereignisse informiert wird
    var listener: WebSocketListener? = null
    
    // Verbindungsstatus-Callback
    var connectionCallback: ((isConnected: Boolean, errorMessage: String?) -> Unit)? = null
    
    // Nachrichtenempfangs-Callback
    var messageCallback: ((message: String) -> Unit)? = null
    
    // Exponentieller Backoff-Mechanismus
    var attemptCount: Int = 0
    var isConnecting: Boolean = false
    private var reconnectJob: java.util.Timer? = null

    /**
     * Verbindet zum WebSocket-Server
     * @return true, wenn der Verbindungsversuch gestartet wurde
     */
    fun connect(): Boolean {
        if (isConnecting || webSocket != null) {
            Logger.connection("Bereits verbunden oder Verbindung wird hergestellt")
            return false
        }
        
        isConnecting = true
        attemptConnection()
        return true
    }
    
    /**
     * Führt den eigentlichen Verbindungsversuch durch
     */
    private fun attemptConnection() {
        Logger.connection("Verbindungsversuch #${attemptCount + 1} zu $serverUrl")
        
        val request = Request.Builder()
            .url(serverUrl)
            .build()
        
        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Logger.connection("WebSocket-Verbindung hergestellt")
                isConnecting = false
                attemptCount = 0
                resetReconnectJob()
                this@WebSocketManager.webSocket = webSocket
                connectionCallback?.invoke(true, null)
                listener?.onOpen(webSocket, response)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Logger.connection("Nachricht empfangen: $text")
                messageCallback?.invoke(text)
                listener?.onMessage(webSocket, text)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Logger.connection("WebSocket wird geschlossen: $code, $reason")
                listener?.onClosing(webSocket, code, reason)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Logger.connection("WebSocket geschlossen: $code, $reason")
                this@WebSocketManager.webSocket = null
                connectionCallback?.invoke(false, null)
                listener?.onClosed(webSocket, code, reason)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Logger.error("WebSocket-Fehler", t)
                this@WebSocketManager.webSocket = null
                isConnecting = false
                
                // Erneuter Verbindungsversuch mit exponentieller Verzögerung
                if (attemptCount < maxRetryAttempts) {
                    scheduleReconnect()
                } else {
                    // Max. Versuche erreicht
                    Logger.connection("Maximale Anzahl von Verbindungsversuchen erreicht: $maxRetryAttempts")
                    connectionCallback?.invoke(false, "Maximale Verbindungsversuche erreicht: ${t.message}")
                }
                
                listener?.onFailure(webSocket, t, response)
            }
        }
        
        webSocket = httpClient.newWebSocket(request, wsListener)
    }
    
    /**
     * Plant einen erneuten Verbindungsversuch mit exponentieller Verzögerung
     */
    private fun scheduleReconnect() {
        attemptCount++
        val delay = calculateRetryDelay()
        
        Logger.connection("Plane erneuten Verbindungsversuch in $delay ms")
        
        resetReconnectJob()
        reconnectJob = java.util.Timer().apply {
            schedule(object : java.util.TimerTask() {
                override fun run() {
                    isConnecting = true
                    attemptConnection()
                }
            }, delay)
        }
    }
    
    /**
     * Berechnet die Verzögerung für den nächsten Verbindungsversuch
     * mit exponentieller Backoff-Strategie und Jitter (Zufallsschwankung)
     */
    fun calculateRetryDelay(): Long {
        // Basis: 2^attemptCount * 1000 ms mit maximalem Verzögerungswert
        val baseDelay = minOf(Math.pow(2.0, attemptCount.toDouble()).toLong() * 1000, maxRetryDelay.toLong())
        
        // Zufällige Schwankung von +/- 10%
        val jitter = (baseDelay * 0.1 * (Random.nextDouble() * 2 - 1)).toLong()
        
        return baseDelay + jitter
    }
    
    /**
     * Verwirft den Timer für erneute Verbindungsversuche
     */
    private fun resetReconnectJob() {
        reconnectJob?.cancel()
        reconnectJob = null
    }
    
    /**
     * Sendet eine Nachricht über die WebSocket-Verbindung
     * @param message Die zu sendende Nachricht
     * @return true, wenn die Nachricht gesendet werden konnte
     */
    fun send(message: String): Boolean {
        val ws = webSocket
        if (ws != null) {
            Logger.connection("Sende Nachricht: $message")
            return ws.send(message)
        }
        Logger.error("Konnte Nachricht nicht senden, keine Verbindung")
        return false
    }
    
    /**
     * Schließt die WebSocket-Verbindung
     */
    fun disconnect() {
        resetReconnectJob()
        attemptCount = 0
        isConnecting = false
        
        webSocket?.let { ws ->
            Logger.connection("Trenne WebSocket-Verbindung")
            ws.close(1000, "Benutzer hat Verbindung getrennt")
            webSocket = null
        }
    }
}
