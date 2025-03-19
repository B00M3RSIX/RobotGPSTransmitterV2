package com.example.robotgpstransmitterv2

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.robotgpstransmitterv2.databinding.ActivitySettingsBinding
import com.example.robotgpstransmitterv2.service.WebSocketManager
import com.example.robotgpstransmitterv2.utils.Constants
import com.example.robotgpstransmitterv2.utils.Logger
import com.example.robotgpstransmitterv2.viewmodel.GPSViewModel
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.regex.Pattern

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var viewModel: GPSViewModel
    
    // Temporärer WebSocket-Manager für Verbindungstests
    private var testWebSocketManager: WebSocketManager? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Toolbar einrichten
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // ViewModel initialisieren
        viewModel = ViewModelProvider(this)[GPSViewModel::class.java]
        
        // SharedPreferences laden
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        viewModel.loadSettings(prefs)
        
        setupUI()
        initializeNumberPickers()
        loadValuesFromViewModel()
    }
    
    private fun setupUI() {
        // Verbindung testen
        binding.btnTestConnection.setOnClickListener {
            val serverUrl = binding.editServerUrl.text.toString().trim()
            
            if (!isValidWebSocketUrl(serverUrl)) {
                Toast.makeText(this, "Ungültige WebSocket-URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            testConnection(serverUrl)
        }
        
        // Einstellungen speichern
        binding.btnSaveSettings.setOnClickListener {
            if (validateSettings()) {
                saveSettings()
                Toast.makeText(this, "Einstellungen gespeichert", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        
        // Werte neu laden
        binding.btnReloadValues.setOnClickListener {
            loadValuesFromViewModel()
            Toast.makeText(this, "Werte neu geladen", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun validateSettings(): Boolean {
        var isValid = true
        
        // Server-URL prüfen
        val serverUrl = binding.editServerUrl.text.toString().trim()
        if (!isValidWebSocketUrl(serverUrl)) {
            binding.editServerUrl.error = "Ungültige WebSocket-URL (muss mit ws:// oder wss:// beginnen)"
            isValid = false
        }
        
        // Topic-Name prüfen
        val topicName = binding.editTopicName.text.toString().trim()
        if (topicName.isEmpty() || !topicName.startsWith("/")) {
            binding.editTopicName.error = "Topic-Name muss mit / beginnen"
            isValid = false
        }
        
        // Frame-ID prüfen
        val frameId = binding.editFrameId.text.toString().trim()
        if (frameId.isEmpty()) {
            binding.editFrameId.error = "Frame-ID darf nicht leer sein"
            isValid = false
        }
        
        // Publish-Intervall prüfen
        val minutes = binding.pickerMinutes.value
        val seconds = binding.pickerSeconds.value
        if (minutes == 0 && seconds == 0) {
            Toast.makeText(this, "Intervall muss mindestens 1 Sekunde betragen", Toast.LENGTH_SHORT).show()
            isValid = false
        }
        
        // Max Retry Attempts prüfen
        val maxRetryAttempts = binding.editMaxRetryAttempts.text.toString().toIntOrNull()
        if (maxRetryAttempts == null || maxRetryAttempts < 1) {
            binding.editMaxRetryAttempts.error = "Muss mindestens 1 sein"
            isValid = false
        }
        
        // Batterie-Level prüfen
        val warningLevel = binding.editBatteryWarning.text.toString().toIntOrNull()
        val shutdownLevel = binding.editBatteryShutdown.text.toString().toIntOrNull()
        
        if (warningLevel == null || warningLevel < 0 || warningLevel > 100) {
            binding.editBatteryWarning.error = "Wert muss zwischen 0 und 100 liegen"
            isValid = false
        }
        
        if (shutdownLevel == null || shutdownLevel < 0 || shutdownLevel > 100) {
            binding.editBatteryShutdown.error = "Wert muss zwischen 0 und 100 liegen"
            isValid = false
        }
        
        if (warningLevel != null && shutdownLevel != null && shutdownLevel > 0 && warningLevel <= shutdownLevel) {
            binding.editBatteryWarning.error = "Warnstufe muss höher als Abschaltstufe sein"
            isValid = false
        }
        
        return isValid
    }
    
    private fun isValidWebSocketUrl(url: String): Boolean {
        val pattern = Pattern.compile("^(ws|wss)://.*")
        return pattern.matcher(url).matches()
    }
    
    private fun initializeNumberPickers() {
        // Intervall-Picker für Minuten (0-15)
        binding.pickerMinutes.apply {
            minValue = 0
            maxValue = 15
            wrapSelectorWheel = false
        }
        
        // Intervall-Picker für Sekunden (0-59)
        binding.pickerSeconds.apply {
            minValue = 0
            maxValue = 59
            wrapSelectorWheel = false
        }
        
        // Retry-Delay-Picker für Minuten (0-15)
        binding.pickerRetryMinutes.apply {
            minValue = 0
            maxValue = 15
            wrapSelectorWheel = false
        }
        
        // Retry-Delay-Picker für Sekunden (0-59)
        binding.pickerRetrySeconds.apply {
            minValue = 0
            maxValue = 59
            wrapSelectorWheel = false
        }
    }
    
    private fun loadValuesFromViewModel() {
        // Verbindungseinstellungen
        binding.editServerUrl.setText(viewModel.serverUrl.value)
        
        // Topic-Einstellungen
        binding.editTopicName.setText(viewModel.topicName.value)
        binding.editFrameId.setText(viewModel.frameId.value)
        
        // Veröffentlichungsintervall
        binding.pickerMinutes.value = viewModel.publishIntervalMinutes.value ?: 0
        binding.pickerSeconds.value = viewModel.publishIntervalSeconds.value ?: 5
        
        // Wiederholungseinstellungen
        binding.editMaxRetryAttempts.setText(viewModel.maxRetryAttempts.value?.toString() ?: "5")
        binding.pickerRetryMinutes.value = viewModel.maxRetryDelayMinutes.value ?: 0
        binding.pickerRetrySeconds.value = viewModel.maxRetryDelaySeconds.value ?: 10
        
        // Batterie-Management
        binding.editBatteryWarning.setText(viewModel.batteryWarningLevel.value?.toString() ?: "20")
        binding.editBatteryShutdown.setText(viewModel.batteryShutdownLevel.value?.toString() ?: "10")
    }
    
    private fun saveSettings() {
        // Werte ins ViewModel übernehmen
        viewModel.serverUrl.value = binding.editServerUrl.text.toString().trim()
        viewModel.topicName.value = binding.editTopicName.text.toString().trim()
        viewModel.frameId.value = binding.editFrameId.text.toString().trim()
        
        viewModel.publishIntervalMinutes.value = binding.pickerMinutes.value
        viewModel.publishIntervalSeconds.value = binding.pickerSeconds.value
        
        viewModel.maxRetryAttempts.value = binding.editMaxRetryAttempts.text.toString().toIntOrNull() ?: 5
        viewModel.maxRetryDelayMinutes.value = binding.pickerRetryMinutes.value
        viewModel.maxRetryDelaySeconds.value = binding.pickerRetrySeconds.value
        
        viewModel.batteryWarningLevel.value = binding.editBatteryWarning.text.toString().toIntOrNull() ?: 20
        viewModel.batteryShutdownLevel.value = binding.editBatteryShutdown.text.toString().toIntOrNull() ?: 10
        
        // In SharedPreferences speichern
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
        viewModel.saveSettings(prefs)
    }
    
    private fun testConnection(serverUrl: String) {
        // "Testen..."-Meldung anzeigen
        binding.btnTestConnection.isEnabled = false
        binding.btnTestConnection.text = "Verbindung wird getestet..."
        
        // Bestehenden Test abbrechen
        testWebSocketManager?.disconnect()
        
        // Maximal 5 Sekunden für den Test
        testWebSocketManager = WebSocketManager(serverUrl, 1, 5000).apply {
            listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    runOnUiThread {
                        binding.btnTestConnection.isEnabled = true
                        binding.btnTestConnection.text = getString(R.string.test_connection)
                        Toast.makeText(this@SettingsActivity, "Verbindung erfolgreich", Toast.LENGTH_SHORT).show()
                    }
                    webSocket.close(1000, "Test abgeschlossen")
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Logger.error("Verbindungstest fehlgeschlagen", t)
                    runOnUiThread {
                        binding.btnTestConnection.isEnabled = true
                        binding.btnTestConnection.text = getString(R.string.test_connection)
                        Toast.makeText(
                            this@SettingsActivity,
                            "Verbindung fehlgeschlagen: ${t.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        
        testWebSocketManager?.connect()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        testWebSocketManager?.disconnect()
        testWebSocketManager = null
    }
}
