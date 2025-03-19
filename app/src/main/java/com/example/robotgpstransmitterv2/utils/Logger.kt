package com.example.robotgpstransmitterv2.utils

import android.util.Log

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
