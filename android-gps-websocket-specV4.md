## 9. Testing and Debugging

### 9.1 Unit Testing Approach

#### Service Testing
```kotlin
@RunWith(AndroidJUnit4::class)
class LocationServiceTest {
    @Mock
    private lateinit var locationManager: LocationManager
    
    @Mock
    private lateinit var webSocketClient: WebSocketClient
    
    private lateinit var service: LocationService
    
    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        service = LocationService()
        // Inject mocks
        service.locationManager = locationManager
        service.webSocketClient = webSocketClient
    }
    
    @Test
    fun testLocationUpdate() {
        // Create mock location
        val mockLocation = Location(LocationManager.GPS_PROVIDER)
        mockLocation.latitude = 52.3456789
        mockLocation.longitude = 13.3456789
        mockLocation.altitude = 123.4
        mockLocation.accuracy = 5.0f
        mockLocation.time = System.currentTimeMillis()
        
        // Simulate location update
        service.onLocationChanged(mockLocation)
        
        // Verify message was sent with correct format
        verify(webSocketClient).send(argThat { message ->
            val json = JSONObject(message)
            json.getString("op") == "publish" &&
            json.getString("topic") == service.topicName &&
            json.getJSONObject("msg").getDouble("latitude") == mockLocation.latitude
        })
    }
    
    @Test
    fun testReconnectionLogic() {
        // Test exponential backoff
        service.attemptCount = 3
        val delay = service.calculateRetryDelay()
        
        // Should be approximately 2^3 = 8 seconds + jitter
        assertTrue(delay >= 8000 && delay < 9000)
    }
}
```

#### ViewModel Testing
```kotlin
@RunWith(AndroidJUnit4::class)
class GPSViewModelTest {
    private lateinit var viewModel: GPSViewModel
    
    @Before
    fun setup() {
        viewModel = GPSViewModel()
    }
    
    @Test
    fun testIntervalValidation() {
        // Zero values should be invalid
        viewModel.publishIntervalMinutes.value = 0
        viewModel.publishIntervalSeconds.value = 0
        assertFalse(viewModel.isPublishIntervalValid())
        
        // At least 1 second should be valid
        viewModel.publishIntervalSeconds.value = 1
        assertTrue(viewModel.isPublishIntervalValid())
        
        // Minutes only should also be valid
        viewModel.publishIntervalMinutes.value = 1
        viewModel.publishIntervalSeconds.value = 0
        assertTrue(viewModel.isPublishIntervalValid())
    }
    
    @Test
    fun testBatteryLevelValidation() {
        // Warning should be higher than shutdown
        viewModel.batteryWarningLevel.value = 15
        viewModel.batteryShutdownLevel.value = 20
        assertFalse(viewModel.isBatteryLevelsValid())
        
        // Correct ordering should be valid
        viewModel.batteryWarningLevel.value = 20
        viewModel.batteryShutdownLevel.value = 10
        assertTrue(viewModel.isBatteryLevelsValid())
        
        // Shutdown = 0 should be valid regardless
        viewModel.batteryWarningLevel.value = 10
        viewModel.batteryShutdownLevel.value = 0
        assertTrue(viewModel.isBatteryLevelsValid())
    }
}
```

### 9.2 Service State Transition Diagram

```
┌─────────────────┐                              
│                 │                              
│   INITIALIZED   │                              
│                 │                              
└────────┬────────┘                              
         │                                       
         │ startForeground()                     
         ▼                                       
┌─────────────────┐     connect()    ┌─────────────────┐
│                 │────────────────▶│                 │
│    STARTING     │                 │   CONNECTING    │
│                 │◀────────────────│                 │
└────────┬────────┘     failure     └────────┬────────┘
         │                                   │          
         │ connection failed                 │ onOpen() 
         │ MAX_ATTEMPTS reached              │          
         ▼                                   ▼          
┌─────────────────┐                ┌─────────────────┐ 
│                 │                │                 │ 
│      ERROR      │                │    CONNECTED    │ 
│                 │                │                 │ 
└────────┬────────┘                └────────┬────────┘ 
         │                                  │          
         │ stopSelf()                       │ advertise()
         │                                  ▼          
         │                        ┌─────────────────┐ 
         │                        │                 │ 
         │                        │   ADVERTISING   │ 
         │                        │                 │ 
         │                        └────────┬────────┘ 
         │                                 │          
         │                                 │ onSuccess()
         │                                 ▼          
         │                        ┌─────────────────┐ 
         │                        │                 │ 
         │                        │   PUBLISHING    │ 
         │                        │                 │ 
         │                        └────────┬────────┘ 
         │                                 │          
         │                                 │ stopPublishing()
         │                                 ▼          
         │                        ┌─────────────────┐ 
         │                        │                 │ 
         │                        │  UNADVERTISING  │ 
         │                        │                 │ 
         │                        └────────┬────────┘ 
         │                                 │          
         │                                 │ onClose()
         ▼                                 ▼          
┌─────────────────┐                                   
│                 │                                   
│    DESTROYED    │                                   
│                 │                                   
└─────────────────┘                                   
```

### 9.3 Debugging and Logging

#### Log Categories
- `GPS-Connection`: WebSocket connection status and operations
- `GPS-Location`: Location updates and provider information
- `GPS-Service`: Service lifecycle events
- `GPS-Battery`: Battery level monitoring

#### Example Implementation
```kotlin
object Logger {
    private const val TAG = "GPSTransmitter"
    
    fun connection(message: String) {
        Log.d("$TAG:Connection", message)
    }
    
    fun location(message: String) {
        Log.d("$TAG:Location", message)
    }
    
    fun service(message: String) {
        Log.d("$TAG:Service", message)
    }
    
    fun battery(message: String) {
        Log.d("$TAG:Battery", message)
    }
    
    fun error(message: String, exception: Throwable? = null) {
        if (exception != null) {
            Log.e(TAG, message, exception)
        } else {
            Log.e(TAG, message)
        }
    }
}
```

#### Key Debug Points
1. **WebSocket Message Processing**:
   - Log all messages sent/received
   - Include timestamps for latency analysis
   
2. **Location Updates**:
   - Log raw location data before processing
   - Log provider changes and signal quality indicators
   
3. **Service State Transitions**:
   - Log all state changes with timestamps
   - Record reason for each transition
   
4. **Connection Failures**:
   - Log detailed error information
   - Include retry count and calculated delay

#### Troubleshooting Procedures
1. **Connection Issues**:
   - Check server URL formatting
   - Verify network connectivity type (WiFi/mobile)
   - Test server directly using WebSocket test client
   
2. **Location Issues**:
   - Check permission status
   - Verify GPS is enabled in system settings
   - Test in open area for better signal
   
3. **Battery Consumption**:
   - Review logs for excessive reconnection attempts
   - Check publish interval settings
   - Monitor wake lock acquisition/release cycles## 8. Service Implementation Details

### 8.1 Service Workflow

#### Initialization
1. Service created and started via startForegroundService()
2. Load configuration from SharedPreferences
3. Create notification and start as foreground service
4. Initialize location services with configured parameters
5. Attempt WebSocket connection with retry logic

#### Normal Operation
1. Connect to WebSocket server if not connected
2. Send "advertise" message when publishing starts
3. Receive location updates from LocationManager at configured interval
4. Convert location data to NavSatFix format
5. Send data to WebSocket server
6. Update message counter and status in ViewModel

#### Termination
1. Send "unadvertise" message to clean up ROS topic
2. Close WebSocket connection gracefully
3. Remove location update listeners
4. Stop foreground service and remove notification

#### Battery Management
1. Register BroadcastReceiver for ACTION_BATTERY_CHANGED
2. Monitor battery level in background
3. Show warning notification when battery reaches warning level
4. Trigger shutdown sequence when battery reaches shutdown level (if enabled)

### 8.2 ROS Bridge Protocol Implementation

#### Topic Advertisement
```json
{
  "op": "advertise",
  "topic": "[TOPIC_NAME]",
  "type": "sensor_msgs/NavSatFix"
}
```

#### Message Publishing
```json
{
  "op": "publish",
  "topic": "[TOPIC_NAME]",
  "msg": {
    "header": {
      "frame_id": "[FRAME_ID]",
      "stamp": {
        "secs": [TIMESTAMP_SECONDS],
        "nsecs": [TIMESTAMP_NANOSECONDS]
      }
    },
    "status": {
      "status": 0,
      "service": 1
    },
    "latitude": [LATITUDE],
    "longitude": [LONGITUDE],
    "altitude": [ALTITUDE],
    "position_covariance": [COVARIANCE_ARRAY],
    "position_covariance_type": 1
  }
}
```

#### Topic Unadvertisement
```json
{
  "op": "unadvertise",
  "topic": "[TOPIC_NAME]"
}
```

### 8.3 NavSatFix Message Field Definitions

| Field | Description | Source in Android |
|-------|-------------|------------------|
| header.frame_id | Coordinate frame identifier | From SharedPreferences (frameId) |
| header.stamp | Timestamp in seconds & nanoseconds | System.currentTimeMillis() converted |
| status.status | Status code (0=no fix, 1=fix, etc.) | Based on location provider |
| status.service | Service type (1=GPS) | Hardcoded to 1 |
| latitude | Latitude in degrees | From Location.getLatitude() |
| longitude | Longitude in degrees | From Location.getLongitude() |
| altitude | Altitude in meters | From Location.getAltitude() |
| position_covariance | 3x3 covariance matrix | Derived from Location.getAccuracy() |
| position_covariance_type | Covariance type (0=unknown, 1=approximated) | Set to 1 (approximated) |

### 8.4 Covariance Calculation

The position_covariance field represents uncertainty in position as a 3x3 matrix in row-major order:
```
[
  cov_xx, cov_xy, cov_xz,
  cov_yx, cov_yy, cov_yz,
  cov_zx, cov_zy, cov_zz
]
```

For Android implementation:
1. Use Location.getAccuracy() which provides accuracy in meters
2. Convert to variance by squaring the accuracy value
3. Populate the matrix using the variance for diagonal elements:
   ```java
   float accuracy = location.getAccuracy();
   float variance = accuracy * accuracy;
   
   double[] covariance = new double[9];
   // Position variances on diagonal
   covariance[0] = variance; // cov_xx
   covariance[4] = variance; // cov_yy
   covariance[8] = variance; // cov_zz
   // Off-diagonal elements set to 0
   ```

### 8.5 Additional SharedPreferences Parameters

No additional SharedPreferences parameters are needed beyond those already specified in section 2.2.

### 8.6 ViewModel Extensions for Service Data

```kotlin
// Add to existing GPSViewModel
class GPSViewModel : ViewModel() {
    // Existing fields...
    
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
    
    // Methods for service management
    fun updateLocationData(location: Location) {
        latitude.value = location.latitude
        longitude.value = location.longitude
        altitude.value = location.altitude
        accuracy.value = location.accuracy
        
        // Update additional fields
        lastUpdated.value = location.time
        rawSpeed.value = location.speed
        rawBearing.value = location.bearing
        rawProvider.value = location.provider
        
        // Update ROS status based on provider
        rosStatus.value = if (location.provider == LocationManager.GPS_PROVIDER) 1 else 0
    }
    
    fun incrementMessageCount() {
        val current = messagesSent.value ?: 0
        messagesSent.value = current + 1
        lastTransmitTime.value = System.currentTimeMillis()
    }
    
    fun updateConnectionStatus(status: String, isConnected: Boolean, isAdv: Boolean) {
        connectionStatus.value = status
        isAdvertised.value = isAdv
    }
    
    fun updateBatteryLevel(level: Int) {
        batteryLevel.value = level
    }
}
```# Android GPS WebSocket Service Specification

## 1. Overview
An Android application that sends GPS location data to a WebSocket server at regular intervals, operating in the background even when the app is not actively in use or the device is in lock-screen mode.

## 2. Core Architecture

### 2.1 Service Design
- **Foreground Service**: Implements GPS tracking as a persistent service with notification
- **Service Communication**: 
  - SharedPreferences for configuration persistence 
  - LiveData with ViewModel for UI-Service communication
- **Location Strategy**: High accuracy mode using GPS, WiFi, and cellular networks
- **WebSocket Management**: Adaptive connection strategy based on update interval
  - For intervals < 1 minute: maintain persistent connection
  - For intervals 1-15 minutes: use connect-send-disconnect pattern
  - ROS Bridge WebSocket protocol with 15-minute timeout

### 2.2 Configuration Parameters

| Parameter | Type | Default Value | Description |
|-----------|------|---------------|-------------|
| Robot Server URL | String | ws://192.168.0.1:9090 | WebSocket server address |
| Topic Setting | String | /phone/gps | ROS topic to publish to |
| Frame ID | String | phone_gps | Frame identifier for ROS messages |
| Publish Interval | Integer | 5000 | Milliseconds between location updates |
| Max Retry Attempts | Integer | 5 | Maximum reconnection attempts before failure |
| Max Retry Delay | Integer | 10000 | Maximum milliseconds between retry attempts |
| Battery Warning Level | Integer | 20 | Battery % to show warning notification |
| Battery Shutdown Level | Integer | 10 | Battery % to auto-shutdown service (0 disables) |

### 2.3 Error Recovery Mechanism
- **Exponential Backoff Reconnection**:
  - Base Delay: 1 second initial wait time
  - Multiplier: Factor of 2 for each subsequent attempt
  - Max Delay: 60 seconds upper limit
  - Max Attempts: 10 retry attempts
  - With random jitter to prevent synchronized retries
  - Service stops and shows error notification upon max attempts

## 3. User Interface

### 3.1 Theme & Colors
- **Dark Theme** with blue accents
- Background: Dark/black (#121212)
- Top navigation: Dark blue (#3F51B5)
- Content sections: Slightly lighter gray (#272727) with rounded corners
- Buttons: Light blue (#8C9EFF) with rounded corners
- Status colors: Connected (#81C784), Disconnected (#E57373), Waiting (#FFD54F)

### 3.2 UI Component Specifications
- **Text Fields**
  - Background: #333333
  - Text color: White
  - Corner radius: 4dp
  - Padding: 12dp vertical, 16dp horizontal

- **Buttons**
  - Primary action buttons: #8C9EFF
  - Secondary action buttons: #616161
  - Text color: White
  - Corner radius: 20dp
  - Height: 48dp

- **Section Cards**
  - Background: #272727
  - Corner radius: 8dp
  - Padding: 16dp
  - Margin: 16dp
  - Title text: 18sp, bold

- **Layout Details**
  - Section cards with 16dp padding and 8dp rounded corners
  - 16dp spacing between sections
  - Content sections contained in distinct cards with headings
  - Text fields use full width with dark backgrounds
  - Labels left-aligned, values right-aligned in information displays

### 3.3 Main Screen
- **Navigation Header**: App title "GPS Transmitter" with Settings button
- **Connection Section**:
  - Full-width Connect/Disconnect button
  - WebSocket connection status display
  
- **Publishing Control Section**:
  - Counter for messages sent
  - Start/Stop Publishing button
  - Status message
  
- **GPS Data Section**:
  - Coordinate display (latitude, longitude, altitude)
  - Accuracy and satellite information
  - Last update timestamp
  - Copy-to-clipboard functionality
  - Satellite count visualization
  - Signal quality indicator

### 3.4 Settings Screen
- **Connection Settings Section**:
  - Robot Server URL input field
  - Test Connection button with loading indicator
  
- **Topic Settings Section**:
  - Topic Name input field
  - Frame ID information display
  
- **Publish Interval Section**:
  - Two NumberPickers for minutes (0-15) and seconds (0-59)
  - Minimum value validation (at least 1 second total)
  
- **Retry Settings Section**:
  - Max Retry Attempts input
  - Max Retry Delay using NumberPickers
  
- **Battery Management Section**:
  - Battery Warning Level input
  - Battery Shutdown Level input
  - Validation: Warning level > Shutdown level
  
- **Control Buttons**:
  - Save Settings button
  - Reload Values button

## 4. Input Validations

### 4.1 Robot Server URL Validations
- Format check: Must start with "ws://" or "wss://"
- Syntax validation: Valid IP/hostname and port format
- Empty check: URL cannot be empty
- Character validation: No spaces or invalid URL characters

### 4.2 Topic Settings Validations
- Format check: Must start with "/"
- Empty check: Topic cannot be empty
- Character validation: No spaces, only valid ROS topic characters
- Length check: Maximum 100 characters

### 4.3 Publish Interval Validations
- Two NumberPicker controls used instead of text input
- Minutes range: 0-15
- Seconds range: 0-59
- Total interval validation: Must be at least 1 second (not 00:00)
- No format validation needed since NumberPickers enforce valid input

### 4.4 Retry Settings Validations
- Range validation: Max attempts must be between 1 and 100
- Format check: Max delay must be at least 1 second
- Numerical validation: Only positive integers allowed

### 4.5 Battery Level Validations
- Range validation: 0-100 percent for both levels
- Consistency check: Warning level > Shutdown level (when shutdown > 0)
- Shutdown level of 0 disables automatic shutdown

### 4.6 Connection Validations
- Timeout handling: 5-second connection timeout
- Response validation: Verify server responds with correct ROS Bridge protocol
- Error classification: Provide specific error messages based on failure type
- Network check: Verify network connectivity before attempting connection

## 5. Notifications

### 5.1 Normal Operation Notification
- Title: "GPS Tracking Active"
- Content: Dynamic status updates
- PendingIntent: Opens main UI activity
- No sound/vibration
- Medium importance channel

### 5.2 Error Notification
- Title: "GPS Tracking Error"
- Content: Specific error details
- PendingIntent: Opens main UI activity
- Includes sound and vibration
- High importance channel
- Shown for connection failures, max retries, or GPS provider failures

### 5.3 Battery Warning Notification
- Title: "GPS Tracking - Battery Low"
- Content: Battery level warning
- PendingIntent: Opens main UI activity
- Includes sound
- High importance channel

## 6. Workflow Specifications

### 6.1 Settings Screen Workflows

#### Test Connection Button
1. Perform network check
2. Attempt WebSocket connection with timeout
3. Show success/failure message
4. Close connection and return to settings screen

#### Save Settings Button
1. Validate all fields
2. Test connection
3. Save to SharedPreferences only if connection successful
4. Return to main screen on success

### 6.2 Main Screen Workflows

#### Connect Button
1. Verify settings availability
2. Attempt WebSocket connection
3. Update UI with connection status
4. Enable/disable publishing controls based on result

#### Start Publishing Button
1. Verify WebSocket connection is established
2. Test connection if needed
3. Start foreground service with configuration
4. Update UI to reflect running state

## 7. Implementation Details

### 7.1 WebSocket Implementation
```java
private void initWebSocket() {
    webSocket = new WebSocketClient(...) {
        @Override
        public void onOpen() {
            setupRosSubscription();
            connectionStatus.postValue("Connected");
        }
        
        @Override
        public void onClose() {
            scheduleReconnect();
        }
    };
    connect();
}

private void setupRosSubscription() {
    String subscribeMsg = "{\"op\":\"subscribe\",\"topic\":\"/gps\"}";
    webSocket.send(subscribeMsg);
}
```

### 7.2 Required Permissions
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.BATTERY_STATS" />
```

### 7.3 Target Device Specification
- Samsung Galaxy S22 Ultra
- Android 12+
- No backward compatibility needed

### 7.4 ViewModel Implementation
```kotlin
class GPSViewModel : ViewModel() {
    // Service status
    val isServiceRunning = MutableLiveData<Boolean>(false)
    val connectionStatus = MutableLiveData<String>("Disconnected")
    
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
    
    // Load settings from SharedPreferences
    fun loadSettings(prefs: SharedPreferences) {
        serverUrl.value = prefs.getString("SERVER_URL", "ws://192.168.0.1:9090")
        topicName.value = prefs.getString("TOPIC", "/phone/gps")
        frameId.value = prefs.getString("FRAME_ID", "phone_gps")
        
        // Convert milliseconds to minutes and seconds
        val intervalMs = prefs.getInt("PUBLISH_INTERVAL", 5000)
        publishIntervalMinutes.value = (intervalMs / 60000)
        publishIntervalSeconds.value = (intervalMs % 60000) / 1000
        
        maxRetryAttempts.value = prefs.getInt("MAX_RETRY_ATTEMPTS", 5)
        
        val delayMs = prefs.getInt("MAX_RETRY_DELAY", 10000)
        maxRetryDelayMinutes.value = (delayMs / 60000)
        maxRetryDelaySeconds.value = (delayMs % 60000) / 1000
        
        batteryWarningLevel.value = prefs.getInt("BATTERY_WARNING_LEVEL", 20)
        batteryShutdownLevel.value = prefs.getInt("BATTERY_SHUTDOWN_LEVEL", 10)
    }
    
    // Save settings to SharedPreferences
    fun saveSettings(prefs: SharedPreferences.Editor) {
        prefs.putString("SERVER_URL", serverUrl.value)
        prefs.putString("TOPIC", topicName.value)
        prefs.putString("FRAME_ID", frameId.value)
        
        // Convert minutes and seconds to milliseconds
        val minutes = publishIntervalMinutes.value ?: 0
        val seconds = publishIntervalSeconds.value ?: 5
        val intervalMs = (minutes * 60 + seconds) * 1000
        prefs.putInt("PUBLISH_INTERVAL", intervalMs)
        
        prefs.putInt("MAX_RETRY_ATTEMPTS", maxRetryAttempts.value ?: 5)
        
        val retryMinutes = maxRetryDelayMinutes.value ?: 0
        val retrySeconds = maxRetryDelaySeconds.value ?: 10
        val delayMs = (retryMinutes * 60 + retrySeconds) * 1000
        prefs.putInt("MAX_RETRY_DELAY", delayMs)
        
        prefs.putInt("BATTERY_WARNING_LEVEL", batteryWarningLevel.value ?: 20)
        prefs.putInt("BATTERY_SHUTDOWN_LEVEL", batteryShutdownLevel.value ?: 10)
        
        prefs.apply()
    }
    
    // Validation functions
    fun isPublishIntervalValid(): Boolean {
        val minutes = publishIntervalMinutes.value ?: 0
        val seconds = publishIntervalSeconds.value ?: 0
        return (minutes > 0 || seconds > 0) // At least 1 second total
    }
    
    fun isBatteryLevelsValid(): Boolean {
        val warningLevel = batteryWarningLevel.value ?: 20
        val shutdownLevel = batteryShutdownLevel.value ?: 10
        return shutdownLevel == 0 || warningLevel > shutdownLevel
    }
}
```