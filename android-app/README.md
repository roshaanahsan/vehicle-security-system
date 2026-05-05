# Remote Ignition Control System
> An end-to-end system that orchestrates microcontroller firmware with a cloud backend and mobile interface to provide real-time vehicle tracking and remote ignition control.

## Executive Summary
Vehicle theft is a critical vulnerability for any asset owner. I engineered a complete hardware-software system that bridges a physical vehicle's ignition circuit to the cloud, enabling real-time GPS monitoring and a remote, un-bypassable kill-switch controlled from an Android application.

## System Architecture
*(You will embed your graphic design schematic here)*

**Data Flow:**
`GPS Module (Silicon) → ESP8266 (Firmware) → Firebase (Cloud) → Android App (Interface)`

## Technical Stack
- **Hardware:** ESP8266, Neo-6M GPS Module, 5V Relay Module.
- **Firmware:** C++ (Arduino Framework), TinyGPS++, FirebaseESP8266 Client.
- **Backend & Transport:** Firebase Realtime Database.
- **Software:** Kotlin, Android SDK, Jetpack Compose, Google Maps SDK, WorkManager API.

## Engineering Deep Dive
The initial system design specified a SIM800L cellular module for untethered connectivity. However, the 2G module's lack of native HTTPS support created an insurmountable conflict with Firebase's modern security requirements. Faced with regional component unavailability for a 4G module, I re-engineered the transport layer to use a Wi-Fi-based architecture.

This pivot, successfully tested with a portable in-car hotspot, proves the system's core logic is transport-agnostic. The critical achievement is maintaining a sub-500ms command latency from the mobile app to the physical relay, demonstrating that robust security control is achievable even when adapting to hardware constraints.

## Installation & Usage
1.  **Hardware:** Flash the code in `/firmware` to an ESP8266. Connect the GPS module and relay according to the pin definitions.
2.  **Firebase:** Create a new Realtime Database project. Replace the placeholders in `firmware.ino` and `android-app/app/google-services.json` with your credentials.
3.  **Android:** Open the `/android-app` directory in Android Studio. Let Gradle sync, then build and deploy to a device.

## Project Context
Built in 2024. Published May 2024. This system was engineered to deliver a complete, deployed solution for a real-world security problem.

## Contact
→ roshaanahsan.pro@gmail.com · github.com/roshaanahsan
→ linkedin.com/in/roshaanahsan · x.com/roshaanahsan