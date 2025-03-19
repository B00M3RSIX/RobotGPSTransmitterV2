package com.example.robotgpstransmitterv2.utils

object Constants {
    // SharedPreferences
    const val PREFS_NAME = "GPSTransmitterPrefs"
    
    // Default values
    const val DEFAULT_SERVER_URL = "ws://192.168.0.1:9090"
    const val DEFAULT_TOPIC = "/phone/gps"
    const val DEFAULT_FRAME_ID = "phone_gps"
    const val DEFAULT_PUBLISH_INTERVAL = 5000 // ms
    const val DEFAULT_MAX_RETRY_ATTEMPTS = 5
    const val DEFAULT_MAX_RETRY_DELAY = 10000 // ms
    const val DEFAULT_BATTERY_WARNING_LEVEL = 20 // %
    const val DEFAULT_BATTERY_SHUTDOWN_LEVEL = 10 // %
    
    // SharedPreferences keys
    const val KEY_SERVER_URL = "SERVER_URL"
    const val KEY_TOPIC = "TOPIC"
    const val KEY_FRAME_ID = "FRAME_ID"
    const val KEY_PUBLISH_INTERVAL = "PUBLISH_INTERVAL"
    const val KEY_MAX_RETRY_ATTEMPTS = "MAX_RETRY_ATTEMPTS"
    const val KEY_MAX_RETRY_DELAY = "MAX_RETRY_DELAY"
    const val KEY_BATTERY_WARNING_LEVEL = "BATTERY_WARNING_LEVEL"
    const val KEY_BATTERY_SHUTDOWN_LEVEL = "BATTERY_SHUTDOWN_LEVEL"
    
    // Notification
    const val NOTIFICATION_ID = 1001
    const val NOTIFICATION_CHANNEL_NORMAL = "gps_tracking_normal"
    const val NOTIFICATION_CHANNEL_ERROR = "gps_tracking_error"
    const val NOTIFICATION_CHANNEL_BATTERY = "gps_tracking_battery"
    
    // Service state
    const val ACTION_START_SERVICE = "com.example.robotgpstransmitterv2.START_SERVICE"
    const val ACTION_STOP_SERVICE = "com.example.robotgpstransmitterv2.STOP_SERVICE"
    const val ACTION_START_PUBLISHING = "com.example.robotgpstransmitterv2.START_PUBLISHING"
    const val ACTION_STOP_PUBLISHING = "com.example.robotgpstransmitterv2.STOP_PUBLISHING"
    
    // WebSocket operations
    const val WS_OP_ADVERTISE = "advertise"
    const val WS_OP_PUBLISH = "publish"
    const val WS_OP_UNADVERTISE = "unadvertise"
    
    // ROS message type
    const val ROS_MSG_TYPE = "sensor_msgs/NavSatFix"
}
