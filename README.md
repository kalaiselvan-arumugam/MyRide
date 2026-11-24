# MyRide – Motorcycle Telemetry, Navigation & Companion App

[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-API%2036-blue?logo=android)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%20100%25-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Connectivity](https://img.shields.io/badge/BLE-Bluetooth%205.0%20GATT-0082FC?logo=bluetooth&logoColor=white)](https://www.bluetooth.com)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen?logo=gradle)](https://github.com/kalaiselvan-a/MyRide)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Release-v1.0.0-green.svg)](https://github.com/kalaiselvan-a/MyRide/releases)

> **Notice**: MyRide is an independent, community-developed companion application for motorcycle riders. It is **not** an official product of, nor affiliated with, Kawasaki Heavy Industries, Ltd., Kawasaki Motors Corp., or any of their regional subsidiaries.

---

## Overview

**MyRide** transforms your Android smartphone into an intelligent, high-contrast motorcycle telemetry cockpit and real-time Bluetooth bridge for compatible motorcycle instrument clusters.

Connecting directly over Bluetooth Low Energy (BLE), MyRide communicates with onboard vehicle electronics to deliver:
* **Near Zero-Lag Cockpit Telemetry**: Live tachometer, gear position, factory 6-axis IMU roll lean angles, G-force acceleration/braking, and engine vitals.
* **Dual-Engine Turn-by-Turn Navigation**: 
  * **Built-in Ola Maps**: In-app routing, live vector map, and direct TFT maneuver streaming via GPS.
  * **Universal Location Sharing**: Share locations directly from WhatsApp, Google Maps, Apple Maps, or web links to begin routing instantly.
  * **External Map Mirroring**: Forward navigation arrows and countdown distances straight to your motorcycle's digital dash from **Google Maps**, **Waze**, or **HERE WeGo**.
  * **Multilingual Navigation Engine**: Seamlessly parses turn maneuvers, distances, and roundabouts across major **Indian languages** (Hindi, Tamil, Telugu, Kannada, Malayalam, Marathi, Bengali, Gujarati, Punjabi) and **International languages** (Spanish, French, German, Italian, Portuguese, Japanese, Chinese, Arabic, Russian) with automatic Indic/Arabic numeral normalization.
* **Universal Notification Alerts (Call, SMS, Email, Messaging)**: Mirror incoming caller names and notification icons on your motorcycle screen from phone dialers, SMS, WhatsApp, Gmail, Outlook, Telegram, Signal, Instagram, Messenger, LINE, and Truecaller.
* **Intelligent Battery & Heartbeat Management**: Continuous keep-alive packets keep the motorcycle TFT battery indicator illuminated even during extended phone calls.
* **Automatic Ignition Detection**: Hands-free background reconnection via hardware BLE filters when turning your key ON; automatic low-power sleep when turning OFF.
* **Maintenance & Service Tracker**: Full digital service records, odometer milestones, invoice photo/PDF attachments, and automated date/distance reminders.
* **Parked Bike Locator**: Automatically records GPS coordinates when your motorcycle disconnects and provides one-tap walking directions back to your bike.
* **Personal Riding Milestones**: Track maximum lean angles, 0-100 km/h sprint times, peak G-forces, and longest ride records.

---

## Table of Contents

1. [Compatibility & Hardware Requirements](#1-compatibility--hardware-requirements)
2. [Getting Started & Installation](#2-getting-started--installation)
   - [Building from Source](#building-from-source)
   - [Direct APK Installation](#direct-apk-installation)
   - [Troubleshooting APK Installation: "App is Blocked" / Google Play Protect](#troubleshooting-apk-installation-app-is-blocked--google-play-protect)
   - [Enabling Restricted Settings for Notification Listener (Android 13+)](#enabling-restricted-settings-for-notification-listener-android-13)
   - [Bluetooth Pairing with Your Motorcycle](#bluetooth-pairing-with-your-motorcycle)
   - [Background Standby & Auto-Ignition Setup](#background-standby--auto-ignition-setup)
   - [OS Battery Optimization & Permissions](#os-battery-optimization--permissions)
3. [The Cockpit: Dashboard (DASH)](#3-the-cockpit-dashboard-dash)
   - [Tachometer & Gear Indicator](#tachometer--gear-indicator)
   - [Cornering Dynamics & IMU Lean Angle](#cornering-dynamics--imu-lean-angle)
   - [Cockpit Audio Alerts (Zero-Asset Helmet Sound Cues)](#cockpit-audio-alerts-zero-asset-helmet-sound-cues)
   - [Engine Health & Trip Statistics](#engine-health--trip-statistics)
4. [Turn-by-Turn Navigation](#4-turn-by-turn-navigation)
   - [Option A: Built-in Interactive Map (Ola Maps)](#option-a-built-in-interactive-map-ola-maps)
   - [Option B: External Map Mirroring (Google Maps, Waze, HERE WeGo)](#option-b-external-map-mirroring-google-maps-waze-here-wego)
   - [Multilingual Turn-by-Turn Engine](#multilingual-turn-by-turn-engine)
   - [Universal Location Sharing (WhatsApp / Maps Intents)](#universal-location-sharing)
   - [TFT Navigation Packet Protocol](#tft-navigation-packet-protocol)
5. [Notification Mirroring: Call, SMS, Email & Messaging](#5-notification-mirroring-call-sms-email--messaging)
   - [Supported Notification Categories & Apps](#supported-notification-categories--apps)
   - [Cluster Indicator Types](#cluster-indicator-types)
6. [Bluetooth Architecture & Call Resilience](#6-bluetooth-architecture--call-resilience)
   - [High Connection Priority](#high-connection-priority)
   - [Continuous Heartbeat During Phone Calls](#continuous-heartbeat-during-phone-calls)
   - [Direct In-Process WakeLock & Foreground Service](#direct-in-process-wakelock--foreground-service)
7. [Telemetry Processing & Performance Analytics](#7-telemetry-processing--performance-analytics)
   - [How Top Speed is Calculated](#how-top-speed-is-calculated)
   - [0–100 km/h (0–60 mph) Sprint Timing](#0100-kmh-060-mph-sprint-timing)
   - [How Ride Sessions & Breadcrumb History Work](#how-ride-sessions--breadcrumb-history-work)
   - [Stationary Idle Discard Logic](#stationary-idle-discard-logic)
8. [Maintenance & Service Log (SERVICE)](#8-maintenance--service-log-service)
   - [How Service Tracking & Dual-Trigger Alerts Work](#how-service-tracking--dual-trigger-alerts-work)
   - [Upcoming Service Milestones](#upcoming-service-milestones)
   - [Adding Records & Attaching Invoices](#adding-records--attaching-invoices)
   - [Daily & Distance Reminder Notifiers](#daily--distance-reminder-notifiers)
9. [Riding Milestones & Records (ACHIEVE)](#9-riding-milestones--records-achieve)
10. [Parked Bike Finder ("Take Me to the Bike")](#10-parked-bike-finder-take-me-to-the-bike)
11. [Experimental Features & Safety Systems](#11-experimental-features--safety-systems)
    - [Smart Crash Detection & Emergency SOS](#smart-crash-detection--emergency-sos)
    - [Sensor Fusion & Impact Verification](#sensor-fusion--impact-verification)
    - [Emergency Countdown & False Alarm Cancellation](#emergency-countdown--false-alarm-cancellation)
    - [Automated Emergency SMS & Loud Alarm Buzzer](#automated-emergency-sms--loud-alarm-buzzer)
12. [Settings, Security & Encrypted Backups](#12-settings-security--encrypted-backups)
    - [Encrypted Backups (AES-GCM / PBKDF2)](#encrypted-backups)
    - [Dynamic Home Screen Launcher Icon](#dynamic-home-screen-launcher-icon)
13. [Developer & Diagnostics Screen (DEV)](#13-developer--diagnostics-screen-dev)
14. [Troubleshooting & FAQ](#14-troubleshooting--faq)
15. [Safety, Legal & Privacy Disclaimers](#15-safety-legal--privacy-disclaimers)

---

## 1. Compatibility & Hardware Requirements

### Supported Motorcycles
Engineered and tested with motorcycle models equipped with Bluetooth-enabled color TFT instrument clusters:
* **Z900 / Z900 SE**: Model Years **MY2025, MY2026** (Tested & Verified; should work in MY2027 as well)
* **Ninja ZX-6R**: Model Years **MY2017 – MY2027** (*Not tested* / Protocol compatible)
* **Ninja ZX-10R / ZX-10RR**: *Not tested* (Protocol compatible, awaiting field testing)
* **Other TFT Models** (Versys 650/1000, Ninja 1000SX): *Untested / Experimental*

### Smartphone Requirements
* **Operating System**: Android 7.0 (Nougat, API 24) or newer. Android 12+ (API 31–36) recommended for optimal background standby.
* **Connectivity**: Bluetooth 5.0 Low Energy (BLE) chipset.
* **Location & Sensors**: GPS receiver for in-app navigation and parked bike logging; magnetic compass sensor for dashboard heading.

---

## 2. Getting Started & Installation

### Building from Source
Ensure you have the Android SDK (API 36) and Java 17 installed:
```bash
git clone https://github.com/kalaiselvan-arumugam/MyRide.git
cd MyRide
./gradlew assembleDebug
```
The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### Direct APK Installation
1. Transfer the `.apk` file to your Android phone or install via ADB:
   ```bash
   adb install -r app-debug.apk
   ```
2. If installing directly via phone browser, WhatsApp, or file manager, tap the downloaded APK and select **Install**.

### Troubleshooting APK Installation: "App is Blocked" / Google Play Protect
When sideloading an APK outside the Google Play Store (especially an app built from source or downloaded directly via GitHub/browser), Android's Google Play Protect may display a warning:
> **"Unsafe app blocked"** or **"App blocked by Play Protect"**

#### Why does this happen?
* Google Play Protect scans apps that are not installed from Google Play. Because this APK is signed with a personal/developer keystore and has not yet established global install telemetry on Google Play servers, Play Protect flags it as an "Unknown Developer / Unverified App".
* **MyRide contains zero spyware, zero trackers, zero ads, and zero remote telemetry uploaders.** All telemetry processing is strictly local to your phone and motorcycle.

#### How to Install:
1. When the Play Protect dialog appears, tap **"More details"** (or down arrow).
2. Tap **"Install anyway"** to proceed with installation.
3. If the system completely prevents installation:
   - Open **Google Play Store** -> Tap your profile icon (top right) -> **Play Protect**.
   - Tap the **Settings gear** (top right).
   - Temporarily toggle off **"Scan apps with Play Protect"**, install the APK, and then re-enable it.

---

### Enabling Restricted Settings for Notification Listener (Android 13+)
Starting in Android 13 (API 33) and heightened in Android 14–16, Google introduced a security measure called **Restricted Settings** to block sideloaded apps from accessing powerful permissions (like Accessibility and Notification Access) without explicit user approval.

When you attempt to grant **Notification Access** to MyRide, Android may show:
> **"Restricted setting – For your security, this setting is currently unavailable."**

#### Why is Notification Access needed?
* It is required **only** if you want to mirror caller names, SMS, WhatsApp, and turn-by-turn maneuvers from external apps (Google Maps, Waze, HERE) to your motorcycle's TFT cluster.
* All core motorcycle telemetry (tachometer, lean angles, gear, temps, Ola Maps navigation) works 100% without this permission.

#### How to unlock Restricted Settings:
1. Open your phone's system **Settings** -> **Apps** -> **See all apps** (or **Manage apps**).
2. Tap **MyRide** to open its **App Info** page.
3. Tap the **three vertical dots (⋮)** in the top-right corner.
4. Tap **"Allow restricted settings"**.
5. Authenticate with your phone fingerprint, PIN, or pattern.
6. Return to **MyRide**, go to **SETTINGS**, and tap **Notification Access** (or grant it via phone **Settings** -> **Security & privacy** -> **Special app access** -> **Notification access** -> enable **MyRide**).
   * *Xiaomi HyperOS / MIUI*: Also enable **Autostart**, set Battery Saver to *No Restrictions*, and enable **Display pop-up windows while running in background*.

### Bluetooth Pairing with Your Motorcycle
1. Turn your motorcycle ignition key to **ON** (handlebar kill switch in RUN position).
2. Using the handlebar selector switches (`SEL` / Up / Down), navigate on your motorcycle TFT display to:
   `Menu` -> `Bluetooth` -> `Pairing`.
3. The bike display will flash its Bluetooth pairing indicator.
4. Open **MyRide**, navigate to **SETTINGS**, and tap **Bluetooth Scan**.
5. Select your motorcycle from the discovered devices list.
6. A 6-digit confirmation PIN appears on your bike's screen. Confirm the matching prompt on your phone and tap **Pair**.
7. Once paired, the Bluetooth indicator on your motorcycle cluster illuminates solid.

### Background Standby & Auto-Ignition Setup
You do not need to launch the app manually every time you ride:
1. In the app's **SETTINGS** tab, tap **Ignition Auto-Connect (Hardware Standby)**.
2. Accept the Android system dialog to register the motorcycle as a companion device.
3. Whenever you approach your bike and turn the ignition ON, the background hardware scanner wakes the application and connects within 1.5 seconds. When you turn the key OFF, the app cleanly saves your parking spot and returns to ultra-low-power standby.

### OS Battery Optimization & Permissions
To prevent aggressive Android battery savers from killing the telemetry service in your pocket:
* **Nearby Devices / Bluetooth**: Set to *Allow*.
* **Location**: Set to *Allow all the time* (needed for GPS navigation and parking location).
* **Battery Optimization**: Tap **Ignore Battery Optimization** in Settings and select *Unrestricted*.
* **Notification Access (Optional)**: Needed only if you wish to mirror incoming caller IDs and external Google Maps notifications to your motorcycle cluster.
  * *Xiaomi HyperOS / MIUI riders*: In phone **App Info**, enable **Autostart**, set Battery Saver to *No Restrictions*, and enable **Display pop-up windows while running in background**. If Notification Access is blocked, open phone App Info -> 3 dots (top right) -> tap *Allow restricted settings*.

---

## 3. The Cockpit: Dashboard (DASH)

The **DASH** screen is a dedicated zero-scroll vehicle dynamics cockpit engineered with glare-resistant neumorphic elements and high-contrast typography.

```
+------------------------------------------------------------------------------+
|  [● BT Connected]    MYRIDE • Z900 / TFT     [14:32]  [Compass: 247° W]      |
+------------------------------------------------------------------------------+
|   CRUISE: ACTIVE 80 KM/H   |      ( ( ( 6,450 ) ) )     |  COOLANT: 88°C     |
|   KTRC: LEVEL 1 (SPORT)    |           RPM              |  FUEL: 74% (190km) |
|   TRIP A: 142.6 KM         |        [ 6th Gear ]        |  AIR TEMP: 29°C    |
|   TRIP B: 24.1 KM          |         82 KM/H            |  BATTERY: 14.2V    |
+------------------------------------------------------------------------------+
|   LEAN: ◄ 38° | 0° | 41° ► | THROTTLE: [██████    ] 58% | G-FORCE: +0.42G    |
+------------------------------------------------------------------------------+
```

### Tachometer & Gear Indicator
* **Circular RPM Arc**: Clockwise 240° sweep spanning 0 to 12,000 RPM with distinct color stages:
  * **Green (0 to ~3,200 RPM)**: Idle and low-speed cruising.
  * **Yellow (~3,200 to ~6,000 RPM)**: Mid-range power delivery.
  * **Orange (~6,000 to ~8,600 RPM)**: Upper powerband.
  * **Sport Crimson (~8,600 to 12,000 RPM)**: Peak power and redline.
* **Gear Position Indicator**: Displays current engaged gear (**1** through **6**). When shifted into neutral, a bold **N** illuminates in emerald green.
* **Ground Speed**: Digital velocity calculated directly from the ECU wheel speed sensors.

### Cornering Dynamics & IMU Lean Angle
* **Live Lean Angle Gauge**: Displays real-time chassis roll angle in degrees directly from the motorcycle's internal 6-axis Inertial Measurement Unit (IMU).
* **Peak Lean Memory**: Markers track the maximum left and right roll angles reached during your current ride session.
* **Throttle Position (TPS)**: Bar indicator showing exact throttle valve opening from 0% (closed) to 100% (wide open).
* **Linear G-Force**: Measures longitudinal acceleration force (`+G`) under throttle and braking deceleration (`-G`).

### Cockpit Audio Alerts (Zero-Asset Helmet Sound Cues)
MyRide features an ultra-low-latency, zero-asset audio synthesizer engine (`AudioAlertManager`) designed for helmet Bluetooth intercoms (Sena, Cardo, FreedConn) and TWS earbuds. Instead of playing heavy MP3/WAV audio files that introduce playback lag and APK bloat, MyRide synthesizes mathematical 16-bit PCM sine waves directly in code via Android `AudioTrack`:

* **Rev Limit / Shift Lamp Alert**:
  * **When it plays**: Triggers instantly when engine RPM crosses the shift lamp threshold (or redline, e.g., 11,000 RPM).
  * **Tone characteristics**: 3 sharp, ultra-distinct high-frequency beeps at **2,200 Hz** (60ms ON, 35ms OFF).
  * **Hysteresis & Cooldown**: Protected by a 3.5-second anti-repetition cooldown to prevent alert fatigue during sustained high-RPM riding.
* **Graduated Speed Threshold Pulses**:
  * **When they play**: Audible speed cues trigger when accelerating past critical highway milestones (or equivalent MPH):
    * **80 km/h (50 mph)**: **2-pulse chime** at **880 Hz** (100ms ON, 80ms OFF). Resets when vehicle speed drops below 72 km/h.
    * **150 km/h (93 mph)**: **3-pulse alert** at **1,175 Hz** (90ms ON, 70ms OFF). Resets when vehicle speed drops below 140 km/h.
    * **200 km/h (124 mph)**: **4-pulse rapid warning** at **1,480 Hz** (80ms ON, 60ms OFF). Resets when vehicle speed drops below 188 km/h.
  * **Clean Hysteresis**: Tones only sound once when ascending through each speed band and will not re-trigger until the rider dips below the reset threshold and accelerates back up.
* **Directional Lean Angle Apex Audio**:
  * **When it plays**: Triggers when cornering lean angle reaches your custom target threshold (configurable between 30° and 55°; default 42°).
  * **Tone characteristics**: A gentle acoustic blip at **680 Hz** (85ms duration) with 6ms smooth envelope fade to eliminate speaker pop clicks.
  * **Binaural 3D Spatial Audio**: Plays strictly in the **Left ear** for left-hand corners and **Right ear** for right-hand corners.
  * **Cornering Hysteresis**: Features an 8° release hysteresis (must straighten the bike by >= 8° from the threshold before re-arming for the next apex) and a 2.0-second safety cooldown.
* **Audio Focus & Safety**: Uses `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` and `CONTENT_TYPE_SONIFICATION` so alerts cleanly duck background music without interrupting helmet intercom conversations. Can be individually configured or toggled entirely in **SETTINGS -> COCKPIT AUDIO ALERTS**.

### Engine Health & Trip Statistics
* **Coolant Temperature**: Live engine water temperature with thermal threshold alerts.
* **Charging Voltage**: Real-time motorcycle battery and stator charging output (e.g. 14.2V running, 12.4V at rest).
* **Fuel Level & Range**: Live fuel percentage and calculated distance to empty.
* **Intake Air Temperature**: Ambient intake manifold reading.
* **Electronic Rider Aids**: Displays Electronic Cruise Control status and Kawasaki Traction Control (KTRC) mode.
* **Trip Meters**: Synchronized live readings for Trip A and Trip B.

---

## 4. Turn-by-Turn Navigation

MyRide delivers turn directions directly to your motorcycle's TFT instrument cluster using official Frame 0x14 control packets.

```
+--------------------+        +--------------------+        +--------------------+
|  Ola Maps In-App   |  OR    |  Google Maps Nav   |        |  Motorcycle Dash   |
|  GPS Route Engine  |        |  Status Bar Notif  |        |     Cluster        |
+---------+----------+        +---------+----------+        +---------+----------+
          |                             |                             ^
          +-------------------+---------+                             |
                              |                                       |
                              v                                       |
                     +-----------------+                              |
                     |  MyRide Engine  +------------------------------+
                     |  Frame 0x14 BLE |    Next Maneuver: [ ➔ ]
                     |  Control Point  |    Distance:      [ 350 m ]
                     +-----------------+
```

### Option A: Built-in Interactive Map (Ola Maps)
1. In **SETTINGS**, enter your Ola Maps API Key and select **Ola Maps (In-App Routing)**.
2. The **MAP** tab appears on the bottom navigation bar:
   * Interactive vector road map with smooth camera tracking.
   * Auto-suggest place and address search.
   * Recent destination quick-start list.
   * Multi-route calculation displaying primary and alternate routes with ETA and distance.
   * Automatic TFT streaming: maneuver arrows and distance countdowns are generated directly from in-app GPS without third-party dependencies.

### Option B: External Map Mirroring (Google Maps, Waze, HERE WeGo)
1. In **SETTINGS**, set Navigation Source to **External Map (Google Maps, Waze, HERE)**.
2. Ensure **Notification Access** is granted.
3. Start navigation in Google Maps, Waze, or HERE WeGo; MyRide captures ongoing turn maneuvers, countdown distances, and arrivals from their persistent notifications and pushes them directly to the motorcycle cluster.

### Multilingual Turn-by-Turn Engine
External navigation notifications (Google Maps, Waze, HERE WeGo) are processed through an intelligent multilingual linguistic parser with automatic Indic and Arabic numeral normalization:
* **Indian Languages**: Hindi (`दाएं`/`बाएं`), Tamil (`வலது`/`இடது`), Telugu (`కుడి`/`ఎడమ`), Kannada (`ಬಲ`/`ಎಡ`), Malayalam (`വലത്`/`ഇടത്`), Marathi (`उजवीकडे`/`डावीकडे`), Bengali (`ডান`/`বাম`), Gujarati (`જમણે`/`ડાબે`), and Punjabi (`ਸੱਜੇ`/`ਖੱਬੇ`).
* **International Languages**: Spanish (`derecha`/`izquierda`), French (`droite`/`gauche`), German (`rechts`/`links`), Italian (`destra`/`sinistra`), Portuguese (`direita`/`esquerda`), Japanese (`右折`/`左折`), Chinese (`右转`/`左转`), Russian (`направо`/`налево`), and Arabic (`يمين`/`يسار`).
* **Indic & Non-Latin Numerals**: Digits in Devanagari (`५०० मी.`), Bengali, Gurmukhi, Gujarati, Odia, Tamil, Telugu, Kannada, Malayalam, and Arabic-Indic (`٤٠٠ م`) are automatically converted on-the-fly to ASCII digits for continuous countdown accuracy.
* **Accessibility Icon Descriptions**: If a third-party app displays turn arrows solely as visual graphics without text, the engine inspects the notification's `RemoteViews` hierarchy to extract the direction from the icon's `contentDescription`.
* **State Continuity & Anti-Flicker**: Non-directional intermediate updates (such as road names or traffic delays) retain the last known maneuver icon, preventing cluster LCD flickering between turns.

### Universal Location Sharing
You can start navigation without typing:
* Share any location, address, or dropped pin directly from **WhatsApp**, **Google Maps**, **Apple Maps**, or web links.
* Select **MyRide** in the Android Share sheet; the app automatically resolves the coordinates and prepares the route.

### TFT Navigation Packet Protocol
Navigation packets conform to motorcycle cluster specifications:
* **Frame ID**: `0x14` (`NAVI_CONTROL_POINT`, 35 bytes).
* **Supported Maneuvers**: Straight, Gentle Turn, Regular Turn, Sharp Turn, U-Turn, Roundabouts (Exits 1–8), Fork Left/Right, Highway Exit, and Destination Arrival Flag.
* **Distance Countdown**: Auto-scaled between meters (m), kilometers (km), and feet (ft).

---

## 5. Notification Mirroring: Call, SMS, Email & Messaging

MyRide integrates directly with the Android notification system via its `BikeNotificationListenerService` to stream real-time incoming alerts directly to your motorcycle's instrument cluster without needing to look at your phone.

```
+-----------------------------------------------------------------------------------+
|                           INCOMING NOTIFICATION CHANNELS                          |
|                                                                                   |
|  [📞 PHONE / CALLS]       [💬 SMS & CHAT]             [✉️ EMAIL & ENTERPRISE]    |
|  • Google Phone           • Google Messages           • Gmail                     |
|  • Samsung Dialer         • Samsung Messages          • Microsoft Outlook         |
|  • OnePlus / Oppo Dialer  • WhatsApp & WA Business    • Yahoo! Mail               |
|  • Xiaomi / Vivo Dialer   • Telegram Messenger                                    |
|  • Sony Mobile Dialer     • Signal Private Messenger                              |
|  • InCallUI               • Facebook Messenger                                    |
|  • Truecaller Call Alerts • Instagram Direct Messages                             |
|                           • LINE                                                  |
|                           • Android Native MMS                                    |
+-----------------------------------------------------------------------------------+
```

### Supported Notification Categories & Apps
* **Incoming Voice Calls (`TYPE_CALL = 1`)**:
  * Captures caller contact names and phone numbers from all major stock phone dialers (Google Phone, Samsung Dialer, OnePlus, Oppo, Vivo, Xiaomi, Sony, and Truecaller).
  * Immediately pushes caller identity to the bike screen and lights the cluster Call icon.
* **Text, Chat & Instant Messaging (`TYPE_MAIL = 6`)**:
  * **SMS/MMS**: Google Messages, Samsung Messages, stock AOSP MMS.
  * **Social & Instant Messengers**: WhatsApp, Telegram Messenger, Signal, Instagram Direct, Facebook Messenger, and LINE.
* **Emails (`TYPE_MAIL = 6`)**:
  * Google Gmail, Microsoft Outlook, and Yahoo! Mail.

### Cluster Indicator Types
The motorcycle TFT supports two hardware indicator channels:
1. **Call Alert Icon**: Blinks/illuminates during active incoming voice calls.
2. **Mail / Message Icon**: Lights up when an SMS, chat message, or email notification arrives. When the notification is dismissed on your phone, the cluster icon automatically turns off.

---

## 6. Bluetooth Architecture & Call Resilience

### High Connection Priority
Upon connection, MyRide requests `BluetoothGatt.CONNECTION_PRIORITY_HIGH`. This forces Android's BLE controller to allocate an aggressive 15–20 ms connection interval, eliminating packet loss and telemetry stutter caused by 2.4 GHz radio congestion.

### Continuous Heartbeat During Phone Calls
Under default Android behavior, active phone calls allocate radio bandwidth to Hands-Free Profile (HFP) audio, which can starve BLE GATT packets. MyRide resolves this:
* **Dedicated Status Pulse**: Phone Status (battery % and charging state) continues pulsing every 5 seconds throughout active calls.
* **Persistent Cluster Display**: The motorcycle TFT battery indicator remains permanently illuminated, and the ECU communication session never times out.
* **Automatic Call Recovery**: When a call ends, the navigation display immediately resumes.

### Direct In-Process WakeLock & Foreground Service
To prevent background auto-connect disconnects on Android 12 through Android 15:
* When auto-connecting in the background, `BikeForegroundService` updates in-memory directly without triggering restricted `startForegroundService` intents.
* A `PARTIAL_WAKE_LOCK` is acquired directly in-process while the motorcycle is connected, ensuring the phone can remain in the rider's pocket with the screen turned off without being stopped by Android Doze mode.

---

## 7. Telemetry Processing & Performance Analytics

### How Top Speed is Calculated
MyRide uses a high-reliability hardware telemetry model to capture and record top speed:
* **Direct ECU Wheel Speed**: Speed is sampled continuously from the motorcycle's factory wheel speed sensors over the BLE GATT characteristic. Because it reads from the physical sensor ring (reluctance/Hall sensors) rather than GPS Doppler alone, it is immune to GPS drift, tunnel signal loss, and tall-building reflections.
* **Peak Tracking (`peakSpeed`)**: During an active ride, every positive speed sample is checked (`if (speedKmh > peakSpeed) peakSpeed = speedKmh`).
* **Achievement Persistence**: If the ride's peak speed exceeds the all-time personal best in `AppPreferences`, the new record is atomically written along with the exact date.
* **GPS Fallback for Map Breadcrumbs**: When plotting ride map paths, if a momentary Bluetooth telemetry frame drops, the breadcrumb recorder seamlessly falls back to high-accuracy GPS velocity (`loc.speed * 3.6f`).

### 0–100 km/h (0–60 mph) Sprint Timing
* **Automatic Launch Detection**: When the vehicle slows to a complete stop (`speed == 0.0`), the sprint timer arms automatically.
* **Hardware Timestamp Interpolation**: The instant wheel speed crosses `0.5 km/h`, high-precision monotonic timestamps (`SystemClock.elapsedRealtime()`) start counting.
* **Linear Time Interpolation**: When the motorcycle crosses the target speed threshold (100.0 km/h or 60.0 mph), MyRide calculates the exact sub-millisecond crossing point using linear interpolation between the two boundary speed samples (`(target - vPrev) / (vCurr - vPrev)`), eliminating discrete sampling jitter.

### How Ride Sessions & Breadcrumb History Work
1. **Session Lifecycle**:
   - **Start**: Initiated the moment the motorcycle connects over BLE (`startSessionTracking()`), capturing initial odometer, starting coordinates, and timestamp.
   - **Movement Trigger**: To prevent cluttering your log with short key-on checks in the driveway, actual recording starts once vehicle movement is detected.
2. **Breadcrumb Sampling**:
   - GPS coordinates, real-time vehicle speed, and instantaneous chassis lean angle (positive for right, negative for left) are recorded at a balanced interval (every 1.5 seconds / 2 meters).
   - **Memory & Storage Optimization**: When long cross-country rides exceed 1,000 breadcrumbs, an intelligent stride downsampling algorithm preserves corner apexes and route fidelity while preventing unbounded storage growth.
3. **Session Summary (`RideSession`)**:
   - Each completed ride persists: Start/End time, total elapsed duration, total distance (delta from ECU odometer), peak speed, average speed (`sumSpeed / totalSamples`), maximum left/right lean angles, maximum G-forces, and estimated fuel consumption.
   - Stored safely via `AtomicFile` JSON serialization (`ride_sessions.json`) in app-private storage, viewable in the **RIDES** tab with interactive map playback.

### Stationary Idle Discard Logic
If the motorcycle ignition key is turned ON in a garage or driveway to check fuel or settings and the bike disconnects without exceeding 50 meters of movement or 20 seconds of riding, MyRide automatically detects and discards the stationary idle session. This ensures your previous genuine ride statistics and achievements are never overwritten by an idle stop.

---

## 8. Maintenance & Service Log (SERVICE)

The **SERVICE** tab provides a centralized digital logbook and preventative maintenance reminder engine.

```
+-------------------------------------------------------------------+
|  NEXT SERVICE STATUS                                              |
|  Due in: 1,850 km  or  42 days       Target ODO: 15,000 km        |
+-------------------------------------------------------------------+
|  SERVICE HISTORY                                                  |
|                                                                   |
|  [●] Service Date: 12 Jan, 2026                 ODO: 10,000 km    |
|      Cost: ₹4,500                               [ Invoices (2) ]  |
|      Next Due: 15,000 km or 12 Jul, 2026                          |
|      Notes: Engine oil, oil filter, chain tension & lube.         |
+-------------------------------------------------------------------+
|  [ + ADD SERVICE RECORD ]                                         |
+-------------------------------------------------------------------+
```

### How Service Tracking & Dual-Trigger Alerts Work
MyRide prevents missed motorcycle maintenance using a dual-axis tracking architecture based on both **distance** and **calendar time**:
* **Odometer-Driven Distance Milestones**: Every service record can specify a target distance (e.g. every 6,000 km or 10,000 km).
* **Calendar-Driven Time Milestones**: Target dates (e.g. 6 months or 1 year) are tracked concurrently, honoring whichever threshold occurs first (industry standard "every 6,000 km or 6 months").

### Upcoming Service Milestones
* **Real-Time Countdown Cards**: Dynamically computes `remainingKm = targetOdoKm - currentOdo` and `remainingDays` until the due date.
* **Visual Status Hierarchy**:
  - **Normal**: Service interval healthy (> 500 km and > 14 days).
  - **Warning (Orange)**: Service due within 500 km or within 14 days.
  - **Overdue (Red)**: Odometer exceeded target or due date has passed.

### Adding Records & Attaching Invoices
* Log service date, exact odometer at time of service, total labor/parts cost, and detailed maintenance notes.
* **Document & Receipt Scanning**: Attach camera photos of workshop paper bills or import invoice PDFs directly from your device. Invoices are copied into secure app-private storage (`receipts/`) and linked to the service record.
* Records are stored using atomic file persistence (`maintenance_records.json`).

### Daily & Distance Reminder Notifiers
* **Distance Trigger on Connection**: Every time your phone connects to the bike's Bluetooth, `MaintenanceReminderNotifier.checkDistanceTriggerOnConnection()` checks the live odometer. If within 500 km of target, a persistent reminder notification is triggered.
* **Daily Inexact Alarm**: `DailyMaintenanceAlarmReceiver` wakes at 9:00 AM once daily using low-power Android `AlarmManager.setAndAllowWhileIdle()` to verify calendar targets without battery drain.

---

## 9. Riding Milestones & Records (ACHIEVE)

The **ACHIEVE** tab tracks riding records across all sessions:
* **0–100 KM/H (0–60 MPH) Sprint**: Precision acceleration timing calculated from ECU wheel speed sensors.
* **Maximum Lean Angles**: Peak left and right chassis roll angles recorded by the factory IMU.
* **Top Speed Recorded**: Highest vehicle ground speed logged.
* **Maximum G-Force**: Peak longitudinal acceleration and braking forces.
* **Chassis Pitch**: Front and rear pitch angle detection.
* **Endurance Records**: Longest single ride by distance and elapsed riding time.

---

## 10. Parked Bike Finder ("Take Me to the Bike")

1. **Automatic Coordinate Capture**: The instant your motorcycle ignition key is turned OFF and the Bluetooth link drops, MyRide records your phone's GPS location.
2. **One-Tap Walking Directions**: Tap **Take me to the bike** on the dashboard to open Google Maps walking directions directly back to your motorcycle.
3. **Configurable Expiry**: Set parking marker expiration in Settings (1h, 3h, 6h, 12h, or 24h) to automatically clear old spots.

---

## 11. Experimental Features & Safety Systems

### Smart Crash Detection & Emergency SOS
MyRide includes an experimental, multi-stage Crash Detection & Emergency Response system designed to detect motorcycle accidents and alert designated emergency contacts with GPS coordinates.

```
+-------------------------------------------------------------------------------------------------+
|                               CRASH DETECTION & EMERGENCY PIPELINE                              |
|                                                                                                 |
|   [STAGE 1: IMPACT SENSING]          [STAGE 2: SPEED VERIFICATION]    [STAGE 3: SOS COUNTDOWN]  |
|   • Accelerometer (G-force >= 4G-7G) • Was riding speed > 10 km/h?    • 30-sec visible dialog   |
|   • Gyroscope (Rotational >= 4-7 rad)• Did speed drop to 0 km/h?      • High-volume alert tone  |
|   • OR Bike IMU chassis impact frame • Evaluated 3 sec post-impact    • Continuous vibration    |
|                                                                       • Lock-button cancel      |
+-------------------------------------------------------------------------------------------------+
                                               |
                                               v (If countdown expires without cancellation)
+-------------------------------------------------------------------------------------------------+
|                               [STAGE 4: EMERGENCY BROADCAST]                                    |
|   • Automated SMS dispatched to Emergency Contacts via SMSManager                               |
|   • Message contains live Google Maps pin: "EMERGENCY: I may have had a motorcycle crash..."    |
|   • Device siren sounds at maximum volume to attract nearby bystanders                          |
+-------------------------------------------------------------------------------------------------+
```

### Sensor Fusion & Impact Verification
To avoid false alarms caused by dropping a phone or riding over potholes, MyRide uses dual-sensor verification:
1. **Kinetic Impact Thresholds**:
   - **Accelerometer**: Monitors resultant linear acceleration `sqrt(ax^2 + ay^2 + az^2) / 9.81`.
   - **Gyroscope**: Monitors angular velocity magnitude `sqrt(gx^2 + gy^2 + gz^2)`.
   - **Configurable Sensitivity**:
     - *Low*: Impact >= 7.0G and Rotation >= 7.0 rad/s (~400°/s).
     - *Medium (Default)*: Impact >= 5.5G and Rotation >= 5.5 rad/s (~315°/s).
     - *High*: Impact >= 4.0G and Rotation >= 4.0 rad/s (~230°/s).
2. **Speed-Drop Post-Impact Check**:
   - The detector records whether the bike was traveling faster than 10 km/h prior to impact.
   - After a 3-second delay, it checks if the vehicle has come to a complete standstill (`speed < 2 km/h`).
   - If the vehicle is still moving normally, the impact event is discarded as a road bump.

### Emergency Countdown & False Alarm Cancellation
When a verified crash is detected:
* A full-screen emergency dialog appears with an automated countdown (configurable from 10 to 60 seconds; default 30s).
* **Audio-Haptic Alert**: Plays an urgent alarm ringtone and vibration pattern.
* **Two Ways to Cancel**:
  1. Tap the big green **"I'M OK — CANCEL ALERT"** button on screen.
  2. Double-click the phone's physical **Power / Lock button** (detected via screen off/on state changes, allowing cancellation even with thick motorcycle gloves or if the phone screen is in a pocket/mount).

### Automated Emergency SMS & Loud Alarm Buzzer
If the countdown reaches zero without being cancelled:
* An emergency SMS is immediately dispatched to your configured emergency phone numbers.
* The SMS contains your rider name, vehicle model, and direct GPS coordinates formatted as a clickable Google Maps link.
* An emergency audio siren sounds through the phone speaker to help first responders locate you.

---

## 12. Settings, Security & Encrypted Backups

### Encrypted Backups
Export your complete riding records, service history, and attached invoice images into a single portable `.myride` archive:
* **Security Baseline**: Encrypted with your custom password using AES-256-GCM authenticated encryption and PBKDF2 key derivation.
* **Automated Scheduling**: Optional weekly or daily backup exports to your device storage or cloud-synced folders.
* **Zero-Loss Restore**: Move your archive to any new smartphone and restore your entire history in seconds.

### Dynamic Home Screen Launcher Icon
The app's launcher icon adapts dynamically:
* **Blue Helmet**: Connected and communicating with your motorcycle.
* **Gray Helmet**: Disconnected / standby mode.

---

## 13. Developer & Diagnostics Screen (DEV)

For advanced verification, the **DEV** tab provides hardware diagnostics:
* **Frame 0x14 Navigation Test Tool**: Dispatch test maneuvers (1 through 43) and test distances (10m to 5,000m) directly to your bike's TFT meter to verify cluster display compatibility.
* **Cluster Clock Sync Test**: Trigger on-demand atomic clock synchronization packets.
* **Live Hex Stream Inspector**: Real-time monitor of incoming and outgoing Bluetooth telemetry packets.

---

## 14. Troubleshooting & FAQ

#### Q: The app does not auto-connect when I switch my ignition ON.
* Ensure Bluetooth is turned ON on your phone.
* Confirm that you completed the one-time **Ignition Auto-Connect (Hardware Standby)** setup in **SETTINGS**.
* Verify that battery optimization is set to **Unrestricted** for MyRide.

#### Q: Why are "Call Notifications" and "SMS Notifications" disabled in Settings?
* These toggles require Android **Notification Access** to read incoming call banners. If access is not granted, the toggles are disabled to prevent confusion. You can enable Notification Access in phone Settings -> Apps -> Special App Access -> Notification Access.

#### Q: Does MyRide work without Notification Access?
* **Yes!** All core motorcycle telemetry, speedometer, tachometer, lean angles, fuel, maintenance logs, and built-in **Ola Maps navigation** work 100% without Notification Access. Notification Access is strictly optional for mirroring caller names and external Google Maps notifications.

#### Q: Where do lean angle and G-force values come from?
* Directly from your motorcycle's factory 6-axis internal IMU sensor via the ECU telemetry stream. Phone gyro calibration is not required.

---

## 15. Safety, Legal & Privacy Disclaimers

### Rider Safety Notice
> **ALWAYS KEEP YOUR EYES ON THE ROAD.**  
> Operating a motorcycle is an inherently hazardous activity. Never operate your phone touchscreen, change settings, or read complex data while riding. All adjustments must be performed while safely stopped with the vehicle in neutral. The rider bears sole and absolute responsibility for vehicle operation and adherence to traffic laws at all times.

### Privacy Policy
MyRide processes telemetry and navigation data **strictly locally on your device in real-time**:
* Zero personal data, telemetry points, location coordinates, or notification contents are ever uploaded to remote servers or third-party ad networks.
* Encrypted backup files remain strictly under your personal control.

### Trademark Notice
* This software is an independent, community-developed open-source project.
* It is not sponsored, endorsed, or affiliated with **Kawasaki Heavy Industries, Ltd.** or its affiliates.
* Product names, model designations (*Z900*, *Ninja*, *Versys*, *ZX-10R*, *ZX-6R*), and trademarks are referenced solely for nominative compatibility identification.

### License
This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for complete details.
