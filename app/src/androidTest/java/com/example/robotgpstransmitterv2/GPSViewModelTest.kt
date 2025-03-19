package com.example.robotgpstransmitterv2

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.robotgpstransmitterv2.viewmodel.GPSViewModel
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
