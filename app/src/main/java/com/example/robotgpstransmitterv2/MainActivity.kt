package com.example.robotgpstransmitterv2

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.robotgpstransmitterv2.databinding.ActivityMainBinding
import com.example.robotgpstransmitterv2.service.LocationService
import com.example.robotgpstransmitterv2.utils.Constants
import com.example.robotgpstransmitterv2.utils.Logger
import com.example.robotgpstransmitterv2.viewmodel.GPSViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: GPSViewModel
    
    // Berechtigung für Hintergrund-Standortnutzung anfordern (Android 10+)
    private val requestBackgroundLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Logger.service("Hintergrund-Standortberechtigung gewährt")
            startLocationService()
        } else {
            showPermissionExplanationDialog()
        }
    }
    
    // Berechtigung für Standortnutzung anfordern
    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Logger.service("Standortberechtigungen gewährt")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocationPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                startLocationService()
            }
        } else {
            showPermissionExplanationDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Toolbar einrichten
        setSupportActionBar(binding.toolbar)
        
        // ViewModel initialisieren
        viewModel = ViewModelProvider(this)[GPSViewModel::class.java]
        
        // SharedPreferences laden
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        viewModel.loadSettings(prefs)
        
        setupUI()
        setupObservers()
    }
    
    private fun setupUI() {
        // Connect-Button
        binding.btnConnect.setOnClickListener {
            if (viewModel.isServiceRunning.value == true) {
                stopLocationService()
            } else {
                checkPermissionsAndStartService()
            }
        }
        
        // Publish-Button
        binding.btnPublish.setOnClickListener {
            val isActive = viewModel.isAdvertised.value == true
            val serviceIntent = Intent(this, LocationService::class.java).apply {
                action = if (isActive) {
                    Constants.ACTION_STOP_PUBLISHING
                } else {
                    Constants.ACTION_START_PUBLISHING
                }
            }
            startService(serviceIntent)
        }
        
        // Koordinaten kopieren
        binding.btnCopy.setOnClickListener {
            val latitude = viewModel.latitude.value
            val longitude = viewModel.longitude.value
            
            if (latitude != null && longitude != null) {
                val clipboardText = "$latitude, $longitude"
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("GPS-Koordinaten", clipboardText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Koordinaten in Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Keine Koordinaten verfügbar", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupObservers() {
        // Service-Status beobachten
        viewModel.isServiceRunning.observe(this, Observer { isRunning ->
            binding.btnConnect.text = if (isRunning) getString(R.string.disconnect) else getString(R.string.connect)
            binding.btnPublish.isEnabled = isRunning && viewModel.connectionStatus.value?.contains("Verbunden") == true
        })
        
        // Verbindungsstatus beobachten
        viewModel.connectionStatus.observe(this, Observer { status ->
            binding.connectionStatus.text = getString(R.string.connection_status, status)
            
            // Textfarbe je nach Status anpassen
            when {
                status.contains("Verbunden") -> binding.connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected))
                status.contains("wird") -> binding.connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_waiting))
                else -> binding.connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected))
            }
            
            // Publishing-Button nur aktivieren, wenn verbunden
            binding.btnPublish.isEnabled = viewModel.isServiceRunning.value == true && status.contains("Verbunden")
        })
        
        // Veröffentlichungsstatus beobachten
        viewModel.isAdvertised.observe(this, Observer { isAdvertised ->
            binding.btnPublish.text = if (isAdvertised) {
                getString(R.string.stop_publishing)
            } else {
                getString(R.string.start_publishing)
            }
        })
        
        // Nachrichtenzähler beobachten
        viewModel.messagesSent.observe(this, Observer { count ->
            binding.messagesSent.text = getString(R.string.messages_sent, count)
        })
        
        // GPS-Daten beobachten
        viewModel.latitude.observe(this, Observer { lat ->
            viewModel.longitude.value?.let { lon ->
                binding.coordinates.text = getString(R.string.coordinates, lat, lon)
            }
        })
        
        viewModel.longitude.observe(this, Observer { lon ->
            viewModel.latitude.value?.let { lat ->
                binding.coordinates.text = getString(R.string.coordinates, lat, lon)
            }
        })
        
        viewModel.altitude.observe(this, Observer { altitude ->
            binding.altitude.text = getString(R.string.altitude, altitude)
        })
        
        viewModel.accuracy.observe(this, Observer { accuracy ->
            binding.accuracy.text = getString(R.string.accuracy, accuracy)
        })
        
        viewModel.lastUpdated.observe(this, Observer { timestamp ->
            val formattedTime = if (timestamp > 0) {
                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                sdf.format(Date(timestamp))
            } else {
                "Noch keine Daten"
            }
            binding.lastUpdate.text = getString(R.string.last_update, formattedTime)
        })
    }
    
    private fun checkPermissionsAndStartService() {
        // GPS aktiviert?
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            showGpsDisabledAlert()
            return
        }
        
        // Berechtigungen prüfen
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        
        if (hasPermissions(requiredPermissions)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    startLocationService()
                } else {
                    requestBackgroundLocationPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            } else {
                startLocationService()
            }
        } else {
            requestLocationPermission.launch(requiredPermissions)
        }
    }
    
    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun showGpsDisabledAlert() {
        val builder = AlertDialog.Builder(this)
            .setTitle("GPS deaktiviert")
            .setMessage("GPS ist erforderlich, um Standortdaten zu senden. Möchten Sie die GPS-Einstellungen öffnen?")
            .setPositiveButton("Einstellungen öffnen") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Abbrechen", null)
            .create()
        builder.show()
    }
    
    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Berechtigungen erforderlich")
            .setMessage("Diese App benötigt Zugriff auf Ihren Standort, auch wenn die App im Hintergrund läuft, um GPS-Daten zu senden. Bitte erteilen Sie die Berechtigung in den Einstellungen.")
            .setPositiveButton("Einstellungen") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Abbrechen", null)
            .create()
            .show()
    }
    
    private fun startLocationService() {
        Logger.service("Starte LocationService")
        val serviceIntent = Intent(this, LocationService::class.java).apply {
            action = Constants.ACTION_START_SERVICE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        viewModel.isServiceRunning.value = true
    }
    
    private fun stopLocationService() {
        Logger.service("Stoppe LocationService")
        val serviceIntent = Intent(this, LocationService::class.java).apply {
            action = Constants.ACTION_STOP_SERVICE
        }
        stopService(serviceIntent)
        viewModel.isServiceRunning.value = false
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
