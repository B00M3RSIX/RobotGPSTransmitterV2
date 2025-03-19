package com.example.robotgpstransmitterv2.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Repräsentiert eine ROS NavSatFix Nachricht gemäß dem ROS Bridge Protokoll
 */
class NavSatFixMessage(
    val topic: String,
    val frameId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        // Status-Codes gemäß ROS NavSatStatus
        const val STATUS_NO_FIX = 0
        const val STATUS_FIX = 1
        const val STATUS_SBAS_FIX = 2
        const val STATUS_GBAS_FIX = 3
        
        // Service-Typen gemäß ROS NavSatStatus
        const val SERVICE_GPS = 1
        const val SERVICE_GLONASS = 2
        const val SERVICE_COMPASS = 4
        const val SERVICE_GALILEO = 8
    }
    
    /**
     * Erstellt die JSON-Nachricht für ROS Bridge WebSocket
     */
    fun toJsonString(): String {
        val covarianceArray = createCovarianceArray(accuracy)
        
        // Aufteilen des Timestamps in Sekunden und Nanosekunden
        val secs = TimeUnit.MILLISECONDS.toSeconds(timestamp)
        val nsecs = (timestamp % 1000) * 1000000 // Restliche Millisekunden in Nanosekunden
        
        val msgObj = JSONObject().apply {
            put("header", JSONObject().apply {
                put("frame_id", frameId)
                put("stamp", JSONObject().apply {
                    put("secs", secs)
                    put("nsecs", nsecs)
                })
            })
            put("status", JSONObject().apply {
                put("status", STATUS_FIX)  // Standardmäßig einen Fix annehmen
                put("service", SERVICE_GPS)
            })
            put("latitude", latitude)
            put("longitude", longitude)
            put("altitude", altitude)
            put("position_covariance", JSONArray(covarianceArray))
            put("position_covariance_type", 1)  // 1 = approximated
        }
        
        val publishObj = JSONObject().apply {
            put("op", "publish")
            put("topic", topic)
            put("msg", msgObj)
        }
        
        return publishObj.toString()
    }
    
    /**
     * Erstellt das Advertise-Nachrichtenformat für ROS Bridge
     */
    fun createAdvertiseMessage(): String {
        val advertiseObj = JSONObject().apply {
            put("op", "advertise")
            put("topic", topic)
            put("type", "sensor_msgs/NavSatFix")
        }
        
        return advertiseObj.toString()
    }
    
    /**
     * Erstellt das Unadvertise-Nachrichtenformat für ROS Bridge
     */
    fun createUnadvertiseMessage(): String {
        val unadvertiseObj = JSONObject().apply {
            put("op", "unadvertise")
            put("topic", topic)
        }
        
        return unadvertiseObj.toString()
    }
    
    /**
     * Berechnet die Kovarianzmatrix basierend auf der GPS-Genauigkeit
     */
    private fun createCovarianceArray(accuracy: Float): DoubleArray {
        val variance = accuracy * accuracy
        
        // 3x3 Kovarianzmatrix als eindimensionales Array in row-major order
        return doubleArrayOf(
            variance.toDouble(), 0.0, 0.0,   // Erste Zeile
            0.0, variance.toDouble(), 0.0,   // Zweite Zeile
            0.0, 0.0, variance.toDouble()    // Dritte Zeile
        )
    }
}
