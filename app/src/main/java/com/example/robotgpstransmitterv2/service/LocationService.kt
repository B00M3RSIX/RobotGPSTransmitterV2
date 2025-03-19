package com.example.robotgpstransmitterv2.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.robotgpstransmitterv2.MainActivity
import com.example.robotgpstransmitterv2.R
import com.example.robotgpstransmitterv2.model.NavSatFixMessage
import com.example.robotgpstransmitterv2.utils.Constants
import com.example.robotgpstransmitterv2.utils.Logger
import com.example.robotgpstransmitterv2.viewmodel.GPSViewModel
import java.util.Timer
import java.util.TimerTask
import kotlin.properties.Delegates

/**
 * Foreground Service für die GPS-Standorterfassung und WebSocket-Kommunikation
 */
class LocationService : Service(), LocationListener {

    // Systemdienste
    private lateinit var notificationManager: NotificationManager
    private lateinit var locationManager: LocationManager
    private lateinit var preferences: SharedPreferences
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Einstellungen
    private var serverUrl: String = Constants.DEFAULT_SERVER_URL
    private var topicName: String = Constants.DEFAULT_TOPIC
    private var frameId: String = Constants.DEFAULT_FRAME_ID
    private var publishInterval: Int = Constants.DEFAULT_PUBLISH_INTERVAL
    private var maxRetryAttempts: Int = Constants.DEFAULT_MAX_RETRY_ATTEMPTS
    private var maxRetryDelay: Int = Constants.DEFAULT_MAX_RETRY_DELAY
    private var batteryWarningLevel: Int = Constants.DEFAULT_BATTERY_WARNING_LEVEL
    private var batteryShutdownLevel: Int = Constants.DEFAULT_BATTERY_SHUTDOWN_LEVEL
    
    // WebSocket-Manager
    private var webSocketManager: WebSocketManager? = null
    
    // Zustandsverwaltung
    private var currentState by Delegates.observable(ServiceState.INITIALIZED) { _, oldState, newState ->
        Logger.service("Zustandsänderung: $oldState -> $newState")
        updateNotification()
    }
    
    // Timer für Standortaktualisierungen
    private var publishTimer: Timer? = null
    
    // ViewModel für UI-Updates
    private var viewModel: GPSViewModel? = null
    
    // Zähler für Verbindungsversuche
    var attemptCount: Int = 0
    
    // Batterie-Management
    private var batteryReceiver: BroadcastReceiver? = null
    private var currentBatteryLevel: Int = 100
    private var lowBatteryNotificationShown: Boolean = false
    
    override fun onCreate() {
        super.onCreate()
        Logger.service("LocationService wird erstellt")
        
        // Systemdienste initialisieren
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        preferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        
        // Benachrichtigungskanäle erstellen
        createNotificationChannels()
        
        // Batterie-Broadcast-Receiver registrieren
        registerBatteryReceiver()
        
        // Service-Zustand setzen
        currentState = ServiceState.INITIALIZED
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.service("LocationService wird gestartet: ${intent?.action}")
        
        when (intent?.action) {
            Constants.ACTION_START_SERVICE -> {
                if (currentState == ServiceState.INITIALIZED) {
                    // Einstellungen laden
                    loadSettings()
                    
                    // Als Foreground-Service starten
                    startForeground(Constants.NOTIFICATION_ID, createNotification())
                    currentState = ServiceState.STARTING
                    
                    // Wake-Lock erwerben, um CPU-Aktivität im Hintergrund zu gewährleisten
                    acquireWakeLock()
                    
                    // WebSocket initialisieren und verbinden
                    initWebSocket()
                }
            }
            Constants.ACTION_STOP_SERVICE -> {
                stopSelf()
            }
            Constants.ACTION_START_PUBLISHING -> {
                if (currentState == ServiceState.CONNECTED) {
                    startPublishing()
                }
            }
            Constants.ACTION_STOP_PUBLISHING -> {
                if (currentState == ServiceState.PUBLISHING) {
                    stopPublishing()
                }
            }
        }
        
        // Service wird nicht automatisch neu gestartet, wenn er vom System beendet wird
        return START_NOT_STICKY
    }
    
    /**
     * Lädt alle Einstellungen aus den SharedPreferences
     */
    private fun loadSettings() {
        Logger.service("Lade Einstellungen")
        
        serverUrl = preferences.getString(Constants.KEY_SERVER_URL, Constants.DEFAULT_SERVER_URL) ?: Constants.DEFAULT_SERVER_URL
        topicName = preferences.getString(Constants.KEY_TOPIC, Constants.DEFAULT_TOPIC) ?: Constants.DEFAULT_TOPIC
        frameId = preferences.getString(Constants.KEY_FRAME_ID, Constants.DEFAULT_FRAME_ID) ?: Constants.DEFAULT_FRAME_ID
        publishInterval = preferences.getInt(Constants.KEY_PUBLISH_INTERVAL, Constants.DEFAULT_PUBLISH_INTERVAL)
        maxRetryAttempts = preferences.getInt(Constants.KEY_MAX_RETRY_ATTEMPTS, Constants.DEFAULT_MAX_RETRY_ATTEMPTS)
        maxRetryDelay = preferences.getInt(Constants.KEY_MAX_RETRY_DELAY, Constants.DEFAULT_MAX_RETRY_DELAY)
        batteryWarningLevel = preferences.getInt(Constants.KEY_BATTERY_WARNING_LEVEL, Constants.DEFAULT_BATTERY_WARNING_LEVEL)
        batteryShutdownLevel = preferences.getInt(Constants.KEY_BATTERY_SHUTDOWN_LEVEL, Constants.DEFAULT_BATTERY_SHUTDOWN_LEVEL)
    }
    
    /**
     * Initialisiert die WebSocket-Verbindung
     */
    private fun initWebSocket() {
        Logger.service("Initialisiere WebSocket mit URL: $serverUrl")
        
        webSocketManager = WebSocketManager(serverUrl, maxRetryAttempts, maxRetryDelay).apply {
            connectionCallback = { isConnected, errorMessage ->
                if (isConnected) {
                    Logger.connection("WebSocket verbunden")
                    currentState = ServiceState.CONNECTED
                    viewModel?.updateConnectionStatus("Verbunden", true, false)
                } else {
                    Logger.connection("WebSocket Verbindung fehlgeschlagen: $errorMessage")
                    if (errorMessage != null && attemptCount >= maxRetryAttempts) {
                        currentState = ServiceState.ERROR
                        showErrorNotification("Verbindung fehlgeschlagen nach $maxRetryAttempts Versuchen")
                        viewModel?.updateConnectionStatus("Fehler: $errorMessage", false, false)
                    } else {
                        currentState = ServiceState.CONNECTING
                        viewModel?.updateConnectionStatus("Verbindung wird hergestellt...", false, false)
                    }
                }
            }
        }
        
        // Verbindung herstellen
        currentState = ServiceState.CONNECTING
        webSocketManager?.connect()
        viewModel?.updateConnectionStatus("Verbindung wird hergestellt...", false, false)
    }
    
    /**
     * Startet die Veröffentlichung von GPS-Daten
     */
    private fun startPublishing() {
        Logger.service("Starte Veröffentlichung mit Intervall: $publishInterval ms")
        
        // ROS Topic ankündigen
        currentState = ServiceState.ADVERTISING
        advertiseRosTopic()
        
        // GPS-Updates anfordern
        requestLocationUpdates()
        
        // Timer für regelmäßige Veröffentlichung starten
        publishTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    // Aktuelle Position abfragen und senden
                    val lastLocation = getLastKnownLocation()
                    if (lastLocation != null) {
                        onLocationChanged(lastLocation)
                    }
                }
            }, 0, publishInterval.toLong())
        }
        
        currentState = ServiceState.PUBLISHING
        viewModel?.updateConnectionStatus("Veröffentliche Daten", true, true)
    }
    
    /**
     * Beendet die Veröffentlichung von GPS-Daten
     */
    private fun stopPublishing() {
        Logger.service("Stoppe Veröffentlichung")
        
        // Timer beenden
        publishTimer?.cancel()
        publishTimer = null
        
        // Standort-Updates beenden
        try {
            locationManager.removeUpdates(this)
        } catch (e: SecurityException) {
            Logger.error("Keine Berechtigung für LocationManager", e)
        } catch (e: Exception) {
            Logger.error("Fehler beim Entfernen von Location-Updates", e)
        }
        
        // Topic abmelden
        currentState = ServiceState.UNADVERTISING
        unadvertiseRosTopic()
        
        currentState = ServiceState.CONNECTED
        viewModel?.updateConnectionStatus("Verbunden, keine Veröffentlichung", true, false)
    }
    
    /**
     * ROS Topic ankündigen
     */
    private fun advertiseRosTopic() {
        val navSatFixMessage = NavSatFixMessage(
            topicName,
            frameId,
            0.0, 0.0, 0.0, 0.0f
        )
        val advertiseMessage = navSatFixMessage.createAdvertiseMessage()
        webSocketManager?.send(advertiseMessage)
        Logger.connection("Topic angekündigt: $topicName")
    }
    
    /**
     * ROS Topic abmelden
     */
    private fun unadvertiseRosTopic() {
        val navSatFixMessage = NavSatFixMessage(
            topicName,
            frameId,
            0.0, 0.0, 0.0, 0.0f
        )
        val unadvertiseMessage = navSatFixMessage.createUnadvertiseMessage()
        webSocketManager?.send(unadvertiseMessage)
        Logger.connection("Topic abgemeldet: $topicName")
    }
    
    /**
     * Fordert Standort-Updates an
     */
    private fun requestLocationUpdates() {
        try {
            // Höchste Genauigkeit mit GPS, WLAN und Mobilfunk
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                publishInterval.toLong(),
                0f,
                this
            )
            Logger.location("GPS-Updates angefordert mit Intervall: $publishInterval ms")
        } catch (e: SecurityException) {
            Logger.error("Keine Berechtigung für LocationManager", e)
            currentState = ServiceState.ERROR
            showErrorNotification("Keine Standortberechtigung")
        } catch (e: Exception) {
            Logger.error("Fehler beim Anfordern von Location-Updates", e)
            currentState = ServiceState.ERROR
            showErrorNotification("Fehler: ${e.message}")
        }
    }
    
    /**
     * Holt den letzten bekannten Standort
     */
    private fun getLastKnownLocation(): Location? {
        try {
            return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            Logger.error("Keine Berechtigung für getLastKnownLocation", e)
        } catch (e: Exception) {
            Logger.error("Fehler bei getLastKnownLocation", e)
        }
        return null
    }
    
    /**
     * LocationListener Callback für Standortänderungen
     */
    override fun onLocationChanged(location: Location) {
        Logger.location("Standortänderung: ${location.latitude}, ${location.longitude}, Genauigkeit: ${location.accuracy}m")
        
        // ViewModel aktualisieren
        viewModel?.updateLocationData(location)
        
        // Nur senden, wenn wir im PUBLISHING Zustand sind
        if (currentState == ServiceState.PUBLISHING) {
            val navSatFixMessage = NavSatFixMessage(
                topicName,
                frameId,
                location.latitude,
                location.longitude,
                location.altitude,
                location.accuracy,
                location.time
            )
            
            // Nachricht senden
            val messageJson = navSatFixMessage.toJsonString()
            val success = webSocketManager?.send(messageJson) ?: false
            
            if (success) {
                viewModel?.incrementMessageCount()
                Logger.location("GPS-Daten gesendet")
            } else {
                Logger.error("Konnte GPS-Daten nicht senden")
            }
        }
    }
    
    /**
     * Registriert einen BroadcastReceiver für Batterieupdates
     */
    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    
                    if (level != -1 && scale != -1) {
                        currentBatteryLevel = (level * 100 / scale.toFloat()).toInt()
                        Logger.battery("Batteriestand: $currentBatteryLevel%")
                        
                        viewModel?.updateBatteryLevel(currentBatteryLevel)
                        
                        // Batterie-Management
                        handleBatteryLevel(currentBatteryLevel)
                    }
                }
            }
        }
        
        registerReceiver(batteryReceiver, filter)
    }
    
    /**
     * Reagiert auf niedrigen Batteriestand
     */
    private fun handleBatteryLevel(level: Int) {
        // Warnung bei niedrigem Batteriestand
        if (level <= batteryWarningLevel && !lowBatteryNotificationShown) {
            showLowBatteryNotification(level)
            lowBatteryNotificationShown = true
        } else if (level > batteryWarningLevel) {
            lowBatteryNotificationShown = false
        }
        
        // Automatisches Abschalten bei kritischem Batteriestand
        if (batteryShutdownLevel > 0 && level <= batteryShutdownLevel && currentState == ServiceState.PUBLISHING) {
            Logger.battery("Kritischer Batteriestand erreicht ($level%). Stoppe Service.")
            stopPublishing()
            stopSelf()
        }
    }
    
    /**
     * Erstellt Benachrichtigungskanäle für Android 8+
     */
    private fun createNotificationChannels() {
        createNotificationChannel(
            Constants.NOTIFICATION_CHANNEL_NORMAL,
            "GPS Tracking",
            "Zeigt Informationen zum GPS Tracking Service",
            NotificationManager.IMPORTANCE_LOW
        )
        
        createNotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ERROR,
            "GPS Tracking Fehler",
            "Zeigt Fehler im GPS Tracking Service",
            NotificationManager.IMPORTANCE_HIGH
        )
        
        createNotificationChannel(
            Constants.NOTIFICATION_CHANNEL_BATTERY,
            "GPS Tracking Batterie",
            "Zeigt Warnungen bei niedrigem Batteriestand",
            NotificationManager.IMPORTANCE_HIGH
        )
    }
    
    /**
     * Erstellt einen Benachrichtigungskanal
     */
    private fun createNotificationChannel(id: String, name: String, description: String, importance: Int) {
        val channel = NotificationChannel(id, name, importance).apply {
            this.description = description
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * Erstellt die Benachrichtigung für den Foreground Service
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val status = when (currentState) {
            ServiceState.INITIALIZED -> "Initialisiert"
            ServiceState.STARTING -> "Wird gestartet"
            ServiceState.CONNECTING -> "Verbindung wird hergestellt"
            ServiceState.CONNECTED -> "Verbunden"
            ServiceState.ADVERTISING -> "Kündige Topic an"
            ServiceState.PUBLISHING -> "Veröffentliche GPS-Daten"
            ServiceState.UNADVERTISING -> "Melde Topic ab"
            ServiceState.ERROR -> "Fehler"
            ServiceState.DESTROYED -> "Beendet"
        }
        
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_NORMAL)
            .setContentTitle(getString(R.string.notification_title_normal))
            .setContentText("Status: $status")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Aktualisiert die Benachrichtigung, z.B. bei Zustandsänderungen
     */
    private fun updateNotification() {
        notificationManager.notify(Constants.NOTIFICATION_ID, createNotification())
    }
    
    /**
     * Zeigt eine Fehlerbenachrichtigung an
     */
    private fun showErrorNotification(errorMessage: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ERROR)
            .setContentTitle(getString(R.string.notification_title_error))
            .setContentText(errorMessage)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()
        
        notificationManager.notify(Constants.NOTIFICATION_ID + 1, notification)
    }
    
    /**
     * Zeigt eine Benachrichtigung für niedrigen Batteriestand an
     */
    private fun showLowBatteryNotification(level: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_BATTERY)
            .setContentTitle(getString(R.string.notification_title_battery))
            .setContentText("Batteriestand: $level%")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()
        
        notificationManager.notify(Constants.NOTIFICATION_ID + 2, notification)
    }
    
    /**
     * Erwirbt einen Wake-Lock, um den CPU-Betrieb im Hintergrund zu gewährleisten
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GPSTransmitter:LocationServiceWakeLock"
        ).apply {
            acquire(30 * 60 * 1000L) // 30 Minuten Wake-Lock
        }
        Logger.service("Wake-Lock erworben")
    }
    
    /**
     * Gibt den Wake-Lock frei
     */
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Logger.service("Wake-Lock freigegeben")
            }
        }
        wakeLock = null
    }
    
    /**
     * Setzt das ViewModel für UI-Updates
     */
    fun setViewModel(model: GPSViewModel) {
        this.viewModel = model
        viewModel?.isServiceRunning?.value = true
    }
    
    /**
     * Berechnet die Verzögerung für den nächsten Verbindungsversuch
     * analog zu WebSocketManager für Testzwecke (siehe Testspezifikation)
     */
    fun calculateRetryDelay(): Long {
        return webSocketManager?.calculateRetryDelay() ?: 0L
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        Logger.service("LocationService wird beendet")
        
        // Veröffentlichung stoppen, falls aktiv
        if (currentState == ServiceState.PUBLISHING) {
            stopPublishing()
        }
        
        // WebSocket-Verbindung schließen
        webSocketManager?.disconnect()
        webSocketManager = null
        
        // Battery-Receiver abmelden
        batteryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Logger.error("Fehler beim Abmelden des Batterie-Receivers", e)
            }
        }
        batteryReceiver = null
        
        // Wake-Lock freigeben
        releaseWakeLock()
        
        // Status aktualisieren
        currentState = ServiceState.DESTROYED
        viewModel?.isServiceRunning?.value = false
        viewModel?.updateConnectionStatus("Getrennt", false, false)
        viewModel = null
        
        super.onDestroy()
    }
}
