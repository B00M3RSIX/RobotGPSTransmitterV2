package com.example.robotgpstransmitterv2.viewmodel

import android.content.SharedPreferences
import android.location.Location
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.robotgpstransmitterv2.utils.Constants
import com.example.robotgpstransmitterv2.utils.Logger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GPSViewModel : ViewModel() {
    // Service status
    val isServiceRunning = MutableLiveData<Boolean>(false)
    val connectionStatus = MutableLiveData<String>("Getrennt")
    
    // GPS data
    val latitude = MutableLiveData<Double>()
    val longitude = MutableLiveData<Double>()
    val altitude = MutableLiveData<Double>()
    val accuracy = MutableLiveData<Float>()
    val satellites = MutableLiveData<Int>(0)
    val lastUpdated = MutableLiveData<Long>(0)
    
    // Message stats
    val messagesSent = MutableLiveData<Int>(0)
    
    // Settings (temporary until saved)
    val serverUrl = MutableLiveData<String>()
    val topicName = MutableLiveData<String>()
    val frameId = MutableLiveData<String>()
    val publishIntervalMinutes = MutableLiveData<Int>(0)
    val publishIntervalSeconds = MutableLiveData<Int>(5)
    val maxRetryAttempts = MutableLiveData<Int>(5)
    val maxRetryDelayMinutes = MutableLiveData<Int>(0)
    val maxRetryDelaySeconds = MutableLiveData<Int>(10)
    val batteryWarningLevel = MutableLiveData<Int>(20)
    val batteryShutdownLevel = MutableLiveData<Int>(10)
    
    // ROS Message Components
    val rosStatus = MutableLiveData<Int>(0) // 0=no fix, 1=fix
    val rosService = MutableLiveData<Int>(1) // 1=GPS
    val rosPositionCovarianceType = MutableLiveData<Int>(1) // 1=approximated
    
    // Raw sensor data if needed
    val rawSpeed = MutableLiveData<Float>()
    val rawBearing = MutableLiveData<Float>()
    val rawProvider = MutableLiveData<String>()
    
    // Connection state management
    val isAdvertised = MutableLiveData<Boolean>(false)
    val lastTransmitTime = MutableLiveData<Long>(0)
    val batteryLevel = MutableLiveData<Int>(100)
    
    /**
     * Lädt die Einstellungen aus den SharedPreferences
     */
    fun loadSettings(prefs: SharedPreferences) {
        Logger.service("Lade Einstellungen aus SharedPreferences")
        
        serverUrl.value = prefs.getString(Constants.KEY_SERVER_URL, Constants.DEFAULT_SERVER_URL)
        topicName.value = prefs.getString(Constants.KEY_TOPIC, Constants.DEFAULT_TOPIC)
        frameId.value = prefs.getString(Constants.KEY_FRAME_ID, Constants.DEFAULT_FRAME_ID)
        
        // Konvertiere Millisekunden in Minuten und Sekunden
        val intervalMs = prefs.getInt(Constants.KEY_PUBLISH_INTERVAL, Constants.DEFAULT_PUBLISH_INTERVAL)
        publishIntervalMinutes.value = (intervalMs / 60000)
        publishIntervalSeconds.value = (intervalMs % 60000) / 1000
        
        maxRetryAttempts.value = prefs.getInt(Constants.KEY_MAX_RETRY_ATTEMPTS, Constants.DEFAULT_MAX_RETRY_ATTEMPTS)
        
        val delayMs = prefs.getInt(Constants.KEY_MAX_RETRY_DELAY, Constants.DEFAULT_MAX_RETRY_DELAY)
        maxRetryDelayMinutes.value = (delayMs / 60000)
        maxRetryDelaySeconds.value = (delayMs % 60000) / 1000
        
        batteryWarningLevel.value = prefs.getInt(Constants.KEY_BATTERY_WARNING_LEVEL, Constants.DEFAULT_BATTERY_WARNING_LEVEL)
        batteryShutdownLevel.value = prefs.getInt(Constants.KEY_BATTERY_SHUTDOWN_LEVEL, Constants.DEFAULT_BATTERY_SHUTDOWN_LEVEL)
    }
    
    /**
     * Speichert die Einstellungen in den SharedPreferences
     */
    fun saveSettings(prefs: SharedPreferences.Editor) {
        Logger.service("Speichere Einstellungen in SharedPreferences")
        
        prefs.putString(Constants.KEY_SERVER_URL, serverUrl.value)
        prefs.putString(Constants.KEY_TOPIC, topicName.value)
        prefs.putString(Constants.KEY_FRAME_ID, frameId.value)
        
        // Konvertiere Minuten und Sekunden in Millisekunden
        val minutes = publishIntervalMinutes.value ?: 0
        val seconds = publishIntervalSeconds.value ?: 5
        val intervalMs = (minutes * 60 + seconds) * 1000
        prefs.putInt(Constants.KEY_PUBLISH_INTERVAL, intervalMs)
        
        prefs.putInt(Constants.KEY_MAX_RETRY_ATTEMPTS, maxRetryAttempts.value ?: Constants.DEFAULT_MAX_RETRY_ATTEMPTS)
        
        val retryMinutes = maxRetryDelayMinutes.value ?: 0
        val retrySeconds = maxRetryDelaySeconds.value ?: 10
        val delayMs = (retryMinutes * 60 + retrySeconds) * 1000
        prefs.putInt(Constants.KEY_MAX_RETRY_DELAY, delayMs)
        
        prefs.putInt(Constants.KEY_BATTERY_WARNING_LEVEL, batteryWarningLevel.value ?: Constants.DEFAULT_BATTERY_WARNING_LEVEL)
        prefs.putInt(Constants.KEY_BATTERY_SHUTDOWN_LEVEL, batteryShutdownLevel.value ?: Constants.DEFAULT_BATTERY_SHUTDOWN_LEVEL)
        
        prefs.apply()
    }
    
    /**
     * Überprüft, ob das Veröffentlichungsintervall gültig ist (mindestens 1 Sekunde)
     */
    fun isPublishIntervalValid(): Boolean {
        val minutes = publishIntervalMinutes.value ?: 0
        val seconds = publishIntervalSeconds.value ?: 0
        return (minutes > 0 || seconds > 0) // Mindestens 1 Sekunde insgesamt
    }
    
    /**
     * Überprüft, ob die Batterieeinstellungen gültig sind (Warnung > Abschaltung oder Abschaltung = 0)
     */
    fun isBatteryLevelsValid(): Boolean {
        val warningLevel = batteryWarningLevel.value ?: Constants.DEFAULT_BATTERY_WARNING_LEVEL
        val shutdownLevel = batteryShutdownLevel.value ?: Constants.DEFAULT_BATTERY_SHUTDOWN_LEVEL
        return shutdownLevel == 0 || warningLevel > shutdownLevel
    }
    
    /**
     * Aktualisiert die Standortdaten mit einem neuen Location-Objekt
     * Verwendet postValue für Thread-Sicherheit
     */
    fun updateLocationData(location: Location) {
        latitude.postValue(location.latitude)
        longitude.postValue(location.longitude)
        altitude.postValue(location.altitude)
        accuracy.postValue(location.accuracy)
        
        // Zusätzliche Felder aktualisieren
        lastUpdated.postValue(location.time)
        rawSpeed.postValue(location.speed)
        rawBearing.postValue(location.bearing)
        rawProvider.postValue(location.provider)
        
        // ROS-Status basierend auf Provider aktualisieren
        rosStatus.postValue(if (location.provider == android.location.LocationManager.GPS_PROVIDER) 1 else 0)
    }
    
    /**
     * Erhöht den Nachrichtenzähler und aktualisiert den Zeitstempel der letzten Übertragung
     * Verwendet postValue für Thread-Sicherheit
     * @param timestamp Der Zeitstempel der Übertragung, standardmäßig die aktuelle Zeit
     */
    fun incrementMessageCount(timestamp: Long = System.currentTimeMillis()) {
        val current = messagesSent.value ?: 0
        messagesSent.postValue(current + 1)
        lastTransmitTime.postValue(timestamp)
        
        // Aktualisiere auch lastUpdated, um den Zeitpunkt der letzten Übertragung anzuzeigen
        lastUpdated.postValue(timestamp)
    }
    
    /**
     * Setzt den Nachrichtenzähler zurück
     */
    fun resetMessageCount() {
        messagesSent.postValue(0)
    }
    
    /**
     * Aktualisiert den Verbindungsstatus
     */
    fun updateConnectionStatus(status: String, isConnected: Boolean, isAdv: Boolean) {
        // Verwende postValue statt value, um Thread-sicher zu sein
        connectionStatus.postValue(status)
        isAdvertised.postValue(isAdv)
        
        // Log für Debugging
        Logger.connection("ViewModel: Verbindungsstatus aktualisiert zu '$status', isConnected=$isConnected, isAdv=$isAdv")
    }
    
    /**
     * Aktualisiert den Batteriestand
     */
    fun updateBatteryLevel(level: Int) {
        batteryLevel.value = level
    }
    
    /**
     * Formatiert den Zeitstempel der letzten Aktualisierung als lesbaren String
     */
    fun getFormattedLastUpdateTime(): String {
        val timestamp = lastUpdated.value ?: 0
        if (timestamp == 0L) {
            return "Noch keine Daten"
        }
        
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
