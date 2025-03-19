package com.example.robotgpstransmitterv2

import android.location.Location
import android.location.LocationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.robotgpstransmitterv2.service.LocationService
import com.example.robotgpstransmitterv2.service.WebSocketManager
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class LocationServiceTest {
    @Mock
    private lateinit var locationManager: LocationManager
    
    @Mock
    private lateinit var webSocketClient: WebSocketManager
    
    private lateinit var service: LocationService
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        service = LocationService()
        // Inject mocks
        service.locationManager = locationManager
        service.webSocketManager = webSocketClient
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
