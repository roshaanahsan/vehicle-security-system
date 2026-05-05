#include <ESP8266WiFi.h>
#include <FirebaseESP8266.h>
#include <SoftwareSerial.h>
#include <TinyGPSPlus.h>

// --- SETUP: 1. Wi-Fi Credentials ---
// Replace with your hotspot or testing network details.
#define WIFI_SSID       "YOUR_WIFI_SSID"
#define WIFI_PASSWORD   "YOUR_WIFI_PASSWORD"

// --- SETUP: 2. Firebase Project Details ---
// Find these in your Firebase Project Settings.
#define DATABASE_URL    "YOUR_FIREBASE_DATABASE_URL" // e.g., "https://project-name-default-rtdb.firebaseio.com"
#define DATABASE_SECRET "YOUR_FIREBASE_DATABASE_SECRET" // Legacy token from Project Settings > Service accounts

// --- Hardware Pin Definitions ---
#define GPS_RX D7
#define GPS_TX D8
#define RELAY_PIN D0
#define IGN_PIN D2

// --- System Variables ---
FirebaseData firebaseData;
FirebaseAuth auth;
FirebaseConfig config;
SoftwareSerial gpsSerial(GPS_RX, GPS_TX);
TinyGPSPlus gps;
bool lastKillState = false;
bool lastIgnState = false;

void connectWiFi() {
  Serial.print("Connecting Wi-Fi");
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWi-Fi connected: " + WiFi.localIP().toString());
}

void setup() {
  Serial.begin(115200);
  gpsSerial.begin(9600);
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, HIGH);  // Default OFF (Relay is likely Active LOW)
  pinMode(IGN_PIN, INPUT);        // Ignition sense pin

  connectWiFi();

  config.database_url = DATABASE_URL;
  config.signer.tokens.legacy_token = DATABASE_SECRET;

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
  Serial.println("Firebase initialized");

  lastIgnState = digitalRead(IGN_PIN) == HIGH;

  // Initialize killSwitch on Firebase if it doesn't exist to prevent null reads
  if (!Firebase.getInt(firebaseData, "/killSwitch")) {
    Firebase.setInt(firebaseData, "/killSwitch", 0);
  }

  Serial.printf("Ignition at boot: %s\n", lastIgnState ? "ON" : "OFF");
}

void loop() {
  // Process GPS data for one second to ensure a potential fix
  unsigned long start = millis();
  while (millis() - start < 1000) {
    if (gpsSerial.available()) {
      gps.encode(gpsSerial.read());
    }
  }

  bool gpsValid = gps.location.isValid();
  float lat = gpsValid ? gps.location.lat() : 0.0;
  float lng = gpsValid ? gps.location.lng() : 0.0;

  // 1. Send heartbeat timestamp to monitor device connectivity
  Firebase.setTimestamp(firebaseData, "/heartbeat/timestamp");

  // 2. Update GPS coordinates and timestamp
  Firebase.setFloat(firebaseData, "/gps/latitude", lat);
  Firebase.setFloat(firebaseData, "/gps/longitude", lng);
  Firebase.setTimestamp(firebaseData, "/gps/timestamp");

  if (gpsValid) {
    Serial.printf("GPS: %.6f, %.6f [VALID]\n", lat, lng);
  } else {
    Serial.println("GPS: Invalid data.");
  }

  // 3. Listen for kill-switch commands and update relay state
  if (Firebase.getInt(firebaseData, "/killSwitch")) {
    bool killCmd = (firebaseData.intData() == 1);
    digitalWrite(RELAY_PIN, killCmd ? LOW : HIGH); // Active LOW relay control

    int relayStatus = (digitalRead(RELAY_PIN) == LOW) ? 1 : 0;
    if (relayStatus != lastKillState) {
      Firebase.setInt(firebaseData, "/relay", relayStatus);
      Serial.printf("Relay switched %s\n", relayStatus ? "ON" : "OFF");
      lastKillState = relayStatus;
    }
  }

  // 4. Detect ignition state changes and log location on "Ignition ON" event
  bool ignState = digitalRead(IGN_PIN) == HIGH;
  Firebase.setInt(firebaseData, "/ignition/state", ignState ? 1 : 0);

  if (!lastIgnState && ignState && gpsValid) { // If changed from OFF to ON
    Firebase.setFloat(firebaseData, "/ignition/lat", lat);
    Firebase.setFloat(firebaseData, "/ignition/lng", lng);
    Serial.println("Ignition ON event detected. Location logged.");
  }

  if (ignState != lastIgnState) {
    Serial.printf("Ignition state changed to: %s\n", ignState ? "ON" : "OFF");
  }

  lastIgnState = ignState;

  delay(1000);
}