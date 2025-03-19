package com.example.robotgpstransmitterv2.service

/**
 * Repräsentiert die verschiedenen Zustände des LocationService
 * gemäß dem Zustandsübergangsdiagramm in der Spezifikation
 */
enum class ServiceState {
    INITIALIZED,     // Service wurde erstellt aber noch nicht gestartet
    STARTING,        // Service wird als Foreground Service gestartet
    CONNECTING,      // WebSocket-Verbindung wird hergestellt
    CONNECTED,       // WebSocket-Verbindung erfolgreich hergestellt
    ADVERTISING,     // ROS Topic wird angekündigt
    PUBLISHING,      // GPS-Daten werden veröffentlicht
    UNADVERTISING,   // ROS Topic wird abgemeldet
    ERROR,           // Fehler aufgetreten (z.B. nach zu vielen Verbindungsversuchen)
    DESTROYED        // Service wurde beendet
}
