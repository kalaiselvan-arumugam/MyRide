# MyRide Telemetry

[![Android Platform](https://img.shields.io/badge/Platform-Android_7.0+_(API_24+)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target_SDK-API_36-blue?logo=android)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin_100%25-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Connectivity](https://img.shields.io/badge/BLE-Bluetooth_5.0_GATT-0082FC?logo=bluetooth&logoColor=white)](https://www.bluetooth.com)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

> **MyRide - Unofficial Kawasaki Z900 dashboard and turn-by-turn navigation app for motorcycle riders. Independent community project; not affiliated with Kawasaki.**

`kawasaki-z900` `kawasaki` `motorcycle` `motorcycle-dashboard` `turn-by-turn-navigation` `motorcycle-navigation` `android` `offline-maps` `gps-navigation` `diy-motorcycle` `open-source` `myride`

**MyRide Telemetry** is an independent, high-performance telemetry dashboard, smart instrument cluster companion, and turn-by-turn navigation mirroring application. Designed natively for Android using **Jetpack Compose**, it communicates directly over **Bluetooth Low Energy (BLE 5.0)** with motorcycle digital meter and ECU systems. The app delivers zero-lag vehicle dynamics, automatic turn-by-turn navigation mirroring, digital service maintenance scheduling with photo invoice attachment, and session ride tracking.

---

## 🏍️ Tested & Supported Motorcycles

The application has been extensively field-tested and verified for full telemetry streaming, turn-by-turn navigation mirroring, and auto-connection compatibility:

### Tested & Verified Models:
* **Kawasaki Z900 (2026)** and 2020–2026 Z900 TFT models
* **Kawasaki Ninja ZX-10R**
* **Kawasaki Ninja ZX-6R**
* **Kawasaki Versys** (Versys 650 & Versys 1000 TFT models)

---

## Table of Contents

1. [User Guide (60%)](#-user-guide)
   - [APK Installation & Sideloading](#1-apk-installation--sideloading)
   - [First Launch & Permission Setup](#2-first-launch--permission-setup)
   - [OEM-Specific Battery & Background Setup](#3-oem-specific-battery--background-setup)
   - [Dashboard (DASH) Walkthrough](#4-dashboard-dash-walkthrough)
   - [Turn-by-Turn Navigation & HUD Mirroring](#5-turn-by-turn-navigation--hud-mirroring)
   - [Call & SMS Notification Mirroring](#6-call--sms-notification-mirroring)
   - [Personal Bests & Achievements (ACHIEVE)](#7-personal-bests--achievements-achieve)
   - [Maintenance & Service Tracker (SERVICE)](#8-maintenance--service-tracker-service)
   - [Parked Bike Finder ("Take me to the bike")](#9-parked-bike-finder-take-me-to-the-bike)
   - [Settings & Customization](#10-settings--customization)
   - [Frequently Asked Questions & Troubleshooting](#11-frequently-asked-questions--troubleshooting)
2. [Motorcycle Connectivity & Pairing Guide (10%)](#-motorcycle-connectivity--pairing-guide)
   - [TFT Meter Preparation](#1-tft-meter-preparation)
   - [First-Time Bluetooth Pairing](#2-first-time-bluetooth-pairing)
   - [Zero-Touch Ignition Auto-Connect](#3-zero-touch-ignition-auto-connect)
   - [Connection Diagnostics & Recovery](#4-connection-diagnostics--recovery)
3. [Technical Architecture & Developer Reference (30%)](#-technical-architecture--developer-reference)
   - [System Architecture & Architecture Layers](#1-system-architecture)
   - [BLE 5.0 GATT Protocol Specification](#2-ble-50-gatt-protocol-specification)
   - [Sensor Data Sources & Telemetry Pipeline](#3-sensor-data-sources--telemetry-pipeline)
   - [Turn-by-Turn & Notification Mirroring Pipeline](#4-turn-by-turn--notification-mirroring-pipeline)
   - [Persistence, Scoped Storage & Security](#5-persistence-scoped-storage--security)
4. [License & Watertight Legal Disclaimer](#-license--legal-disclaimer)

---

# 📖 User Guide

This section is a complete step-by-step handbook for riders installing and using the application APK.

---

### 1. APK Installation & Sideloading

If you downloaded the `.apk` file directly to your Android smartphone:

1. **Locate the APK**: Open your smartphone's file manager or browser downloads folder and tap `MyRide-Telemetry.apk` (or your downloaded file).
2. **Enable Unknown Apps**:
   - If prompted with *"For your security, your phone is not allowed to install unknown apps from this source"*, tap **Settings**.
   - Toggle the switch to **Allow from this source**.
3. **Confirm Installation**: Tap **Install** and wait for the package installer to complete.
4. **Launch**: Tap **Open** or locate the **MyRide** icon on your home screen or app drawer.

> **System Requirements**: Android 7.0 (Nougat / API 24) or newer. For best background auto-connect performance, Android 12+ (API 31+) with Bluetooth Low Energy hardware support is recommended.

---

### 2. First Launch & Permission Setup

When launching MyRide Telemetry for the first time, a setup wizard and safety dialog ensure the app operates safely and reliably:

#### Step 1: Safety & Legal Disclaimer
Upon initial launch, you must review and accept the Safety Notice:
- Confirm that you will not operate or look at phone settings while riding.
- Confirm your understanding that MyRide Telemetry is an independent utility not affiliated with or endorsed by Kawasaki or any motorcycle manufacturer.
- Tap **Agree & Continue**.

#### Step 2: Granting Required Android Permissions
Go to the **SETTINGS** tab (gear icon in the bottom navigation) to verify that all necessary permissions are granted:

| Permission | Why It Is Needed | How to Enable |
|---|---|---|
| **Nearby Devices / Bluetooth** | Required to discover, pair, and stream telemetry data from your motorcycle meter | Tap **Grant** or allow when prompted by Android |
| **Location Access** | Required by Android's BLE subsystem and for saving your parked motorcycle GPS coordinates | Select **Allow while using the app** (or **Allow all the time**) |
| **Notification Listener Access** | Intercepts turn-by-turn navigation arrows from Google Maps and forwards them to your motorcycle's TFT meter | Tap row -> redirects to Android Notification Access settings -> toggle **MyRide Telemetry** ON |
| **Camera Access** | Captures photos of paper service invoices, oil receipts, and job cards for digital service logs | Tap **Grant** when adding a service receipt photo |
| **Battery Optimization Whitelist** | Prevents Android from killing the telemetry service while the phone is in your jacket or tank bag | Tap **Allow** in Android's battery prompt |

---

### 3. OEM-Specific Battery & Background Setup

Modern Android device manufacturers (Xiaomi, Samsung, OnePlus, Oppo, Vivo, Huawei) aggressively terminate background processes. Follow these brand-specific steps to ensure continuous pocket telemetry:

* **Xiaomi / Redmi / POCO (HyperOS / MIUI)**:
  1. Long-press the **MyRide** app icon -> tap **App info**.
  2. Tap **Battery saver** -> change from *Smart / Balanced* to **No restrictions**.
  3. Enable **Autostart** toggle.
  4. Under **Other permissions**, enable **Display pop-up windows while running in the background** (this allows the cockpit to open automatically upon ignition ON).
* **Samsung Galaxy (One UI)**:
  1. Open phone **Settings** -> **Apps** -> **MyRide** -> **Battery**.
  2. Select **Unrestricted**.
  3. Go to **Settings** -> **Battery** -> **Background usage limits** -> ensure MyRide is **not** in *Sleeping apps* or *Deep sleeping apps*.
* **OnePlus / OPPO / Realme (OxygenOS / ColorOS)**:
  1. Go to **Settings** -> **Apps** -> **App management** -> **MyRide** -> **Battery usage**.
  2. Enable **Allow background activity** and **Allow auto-launch**.
* **Google Pixel / Motorola / Sony (Stock Android)**:
  1. Go to **Settings** -> **Apps** -> **MyRide** -> **App battery usage**.
  2. Select **Unrestricted**.

---

### 4. Dashboard (DASH) Walkthrough

The **DASH** screen is a zero-scroll, high-density cockpit display engineered with high-contrast neumorphic aesthetics for maximum visibility in bright sunlight.

```
+-------------------------------------------------------------------+
|  [BT Icon]  MYRIDE  •  Z900 / TFT         [14:32]  [Compass: 247°] |
+-------------------------------------------------------------------+
|   CRUISE: ACTIVE 80 KM/H   |      ( ( ( 6,450 ) ) )     |  COOLANT: 88°C  |
|   KTRC: LEVEL 1 (SPORT)    |           RPM              |  FUEL: 74% (190km)|
|   TRIP A: 142.6 KM         |        [ 6th Gear ]        |  AIR TEMP: 29°C |
|   TRIP B: 24.1 KM          |         82 KM/H            |  BATTERY: 14.2V |
+-------------------------------------------------------------------+
|   LEAN: ◄ 38° | 0° | 41° ► | THROTTLE: [██████░░░░] 58% | G-FORCE: +0.42G |
+-------------------------------------------------------------------+
```

#### Central Hero Tachometer & Speedometer
* **Dynamic RPM Arc**: Smoothly sweeps from 0 to redline (11,000+ RPM) with progressive color transitions:
  - **Emerald Green**: Efficient cruising RPM.
  - **Electric Yellow**: Mid-range powerband.
  - **Bright Orange**: Approaching shift point.
  - **Vibrant Redline**: High RPM zone.
* **Gear Position Indicator**: Prominently displayed in the dial center:
  - Displays gears **1** through **6**.
  - **Neutral (N)** glows in vibrant emerald green.
  - Features an animated shift indicator transition during gear changes.
* **Digital Speedometer**: Displays high-accuracy motorcycle road speed natively streamed from the ECU wheel sensors (toggleable between **KM/H** and **MPH**).

#### Left HUD Column (Electronics & Trips)
* **Cruise Control**: Displays current state (**OFF**, **ARMED**, or **ACTIVE**) alongside the programmed target cruise speed.
* **Traction Control (KTRC / TC)**: Indicates active traction control mode (Mode 1, Mode 2, Mode 3, or OFF) and flashes an alert if traction intervention occurs.
* **Trip Meters**: Dual trip readouts (**Trip A** and **Trip B**) synced live from the motorcycle's odometer memory.

#### Right HUD Column (Engine Vitals & Environment)
* **Coolant Temperature**: Real-time engine water temperature (°C or °F) with safety color thresholds (amber/red alert when exceeding operating temperatures).
* **Fuel Level & Range**: Fuel tank percentage (0–100%) and estimated remaining range in kilometers/miles.
* **Intake Air Temperature**: Ambient inlet temperature sensor readout.
* **Battery Voltage**: Direct ECU 12V system electrical readout (e.g. 14.2V charging / 12.4V rest).

#### Bottom Telemetry Strip (Dynamics & Lean Angle)
* **Dual Lean Angle Meter**: Displays live left/right roll angle in degrees directly from the bike's 6-axis IMU, with visual peak markers showing maximum lean angle achieved during the current ride session.
* **Throttle Position (TPS)**: Live throttle opening percentage (0% idle to 100% wide-open throttle).
* **Longitudinal G-Force**: Accelerometer readout measuring braking deceleration (-G) and forward acceleration (+G) directly from the bike's IMU.

#### Top Cockpit Header
* **Bluetooth Status**: Solid green dot when actively communicating with the motorcycle; pulsing amber when searching or reconnecting.
* **Custom Brand Header**: Customizable name (e.g. *MYRIDE*, *Z900*, *TRACK*).
* **Compass Heading**: Integrated phone magnetometer bearing (0°–360° with cardinal direction like *SW 240°*).
* **Digital Clock**: Displays exact synchronized time.

---

### 5. Turn-by-Turn Navigation & HUD Mirroring

MyRide Telemetry mirrors navigation directions from your phone directly onto your motorcycle's digital TFT meter display:

```
                    ┌────────────────────────────┐
                    │      GOOGLE MAPS (Phone)   │
                    │   "In 300m, turn right"    │
                    └─────────────┬──────────────┘
                                  │ (Notification Listener)
                                  ▼
                    ┌────────────────────────────┐
                    │     MYRIDE TELEMETRY       │
                    │  Distance: 300m | Icon: →  │
                    └─────────────┬──────────────┘
                                  │ (BLE GATT Control Char)
                                  ▼
                    ┌────────────────────────────┐
                    │   MOTORCYCLE TFT DISPLAY   │
                    │      [ 300 m ]   [ ➔ ]     │
                    └────────────────────────────┘
```

#### A. Default Map (Google Maps Notification Mirroring)
1. Ensure **Turn-by-Turn Navigation** is toggled ON in **SETTINGS** and set **Navigation Source** to **Default Map**.
2. Ensure **Notification Access** is granted for `MyRide`.
3. Open **Google Maps** on your smartphone, search for your destination, and tap **Start**.
4. The motorcycle instrument cluster display will automatically show:
   - **Real-Time Turn Maneuvers**: Full direction arrows including Straight (`0`), Left (`1`), Right (`2`), Sharp Left (`3`), Sharp Right (`4`), Slight Left (`5`), Slight Right (`6`), U-Turns (`7..8`), Roundabout exits (`18..41`), and Destination Arrival Chequered Flag (`43`).
   - **Departure Compatibility**: Initial routing departure instructions (*"Head northeast"*, *"Depart"*) automatically map to Straight (`0`) so the TFT display illuminates immediately upon starting a route.
   - **True Destination Distance Countdown**: As you approach your destination, the actual remaining distance (`900m ... 100m ... 10m`) is continuously streamed to the TFT alongside the destination flag (`43`), with `0m` strictly gated to actual arrival (*"You have arrived"*).
   - **In-Call Navigation Auto-Recovery**: If a phone call interrupts your ride, navigation guidance is automatically re-broadcast to the TFT the moment the call ends—no motorcycle restart required.
   - **Distance to Maneuver**: Formatted dynamically in meters (`m`), kilometers (`km`), or feet (`ft`) with full unit negotiation (Metric `1` / Imperial `3`).
   - **1 Hz Refresh Heartbeat**: Outbound Frame `0x14` keep-alive packets are streamed at 1 Hz, ensuring the cluster meter never drops the turn arrow during long stretches.

#### B. Ola Maps (In-App Turn-by-Turn & Visual Map Cockpit)
When **Ola Maps** is selected as the Navigation Source, a dedicated **MAP** tab appears on the bottom bar:
1. **API Key Setup**: Enter and store your Ola Maps API key in **SETTINGS**.
2. **Interactive Autocomplete**: Search for any destination with debounced location-biased suggestions.
3. **5-Day Local Destination Cache**: Last 5 searched places are remembered for 5 days so you can start repeated routes with zero redundant network usage.
4. **Visual Dark Map & High-Contrast Polyline**:
   - High-contrast dark vector raster tiles streamed from Ola Maps (`default-dark-standard`) with automatic OpenStreetMap fallback.
   - Neon green route glow and direction line (`#00FF88`) with red destination pin marker.
   - Live pulsating rider marker with radar wave animation.
5. **"Find Me" Circle (`map.flyTo`)**:
   - Tapping the recenter button smoothly flies the camera to your live GPS position at street/turn-level (**Zoom 18**), allowing you to clearly inspect intersections, turning lanes, and highway exits.
6. **Safety & Connectivity Gate**:
   - "START NAVIGATION" requires the bike's TFT meter to be connected via Bluetooth, preventing accidental orphan navigation sessions.
   - As you ride, turn maneuvers and countdown distances are streamed straight to the Kawasaki instrument cluster display via Frame `0x14`.

---

### 6. Call & SMS Notification Mirroring

When paired with your motorcycle, MyRide Telemetry relays critical phone communications directly to the motorcycle's TFT meter without requiring you to take your hands off the handlebars:

* **Incoming Phone Calls**: Displays the caller's contact name or phone number on the motorcycle cluster.
* **Text & Messaging Alerts**: Forward brief SMS and messaging notifications so you know when someone is trying to reach you.
* **Phone Battery Status Keep-Alive**: Transmits a periodic 15-second heartbeat maintaining your smartphone's remaining battery level on the meter's battery indicator.
* **Clock Sync**: Automatically aligns your motorcycle's onboard clock with atomic network time from your phone every time you connect.

> *Privacy Note*: All notifications are parsed locally in phone RAM and transmitted directly to your paired bike over an encrypted BLE connection. No notification content is ever recorded, stored to disk, or sent to external servers.

---

### 7. Personal Bests & Achievements (ACHIEVE)

The **ACHIEVE** tab tracks your performance records and milestones across your riding sessions:

* **Maximum Lean Angle (Left & Right)**: Records the steepest cornering roll angle achieved in your riding history from the bike's 6-axis IMU.
* **Top Speed Recorded**: Logs your highest track/road speed.
* **Peak Acceleration & Braking G-Force**: Tracks peak forward acceleration and braking forces from the bike's IMU.
* **Pitch Angles**: Measures maximum wheelie and stoppie pitch angles detected by vehicle IMU sensors.
* **Lifetime Mileage & Session Counter**: Summarizes cumulative seat time and distance traveled.

---

### 8. Maintenance & Service Tracker (SERVICE)

Keep your motorcycle in peak operating condition with dual-trigger service tracking based on **Mileage (Odometer)** and **Time (Date)** intervals:

```
+-------------------------------------------------------------------+
|  MAINTENANCE OVERVIEW                        Current ODO: 12,450 km|
+-------------------------------------------------------------------+
|  [✓] Engine Oil & Filter      Due in: 3,550 km  or  84 days       |
|  [!] Chain Clean & Lube       DUE NOW (Exceeded by 120 km)        |
|  [✓] Brake Fluid Flush        Due in: 7,550 km  or  140 days      |
|  [✓] Coolant Inspection       Due in: 11,550 km or  320 days      |
+-------------------------------------------------------------------+
|  [ + ADD SERVICE RECORD ]              [ 📷 SCAN INVOICE RECEIPT ] |
+-------------------------------------------------------------------+
```

#### Key Capabilities:
* **Preset Maintenance Schedules**: Pre-configured with manufacturer-grade maintenance intervals (Engine Oil & Filter, Chain Lube & Tension, Brake Fluid Flush, Coolant Flush, Spark Plugs, Valve Clearance Inspection).
* **Dual-Trigger Smart Tracking**: Maintenance countdown dynamically evaluates whichever comes first—mileage or calendar days.
* **Add Custom Service Logs**: Record maintenance work performed, service center name, technician notes, parts replaced, and total cost in your local currency.
* **Receipt Photo Scanner**: Attach photos of garage repair bills, parts purchase slips, and warranty stamps using your phone's camera. Images are stored securely on your phone.
* **Daily 9:00 AM Reminder**: An unobtrusive morning reminder notifies you when scheduled maintenance is due or overdue.

---

### 9. Parked Bike Finder ("Take me to the bike")

Never lose your motorcycle in large parking lots, rallies, or unfamiliar cities:

1. When your motorcycle ignition is turned OFF, the BLE link drops.
2. MyRide Telemetry instantly captures your phone's current high-precision GPS coordinates and saves them as the **Last Parked Location**.
3. When returning to your bike:
   - Open MyRide Telemetry.
   - On the Dash screen or notification card, tap **Take me to the bike**.
   - The app launches Google Maps with walking directions directly to your motorcycle's exact parked spot.
4. **Configurable Expiry**: In **SETTINGS**, configure the parked location to expire after 1h, 3h, 6h, 12h, or 24h to avoid outdated parking markers.

---

### 10. Settings & Customization

Customize the application in the **SETTINGS** tab:

* **Speed Units**: Switch between **KM/H** (native ECU metric) and **MPH** (imperial calculation).
* **Navigation Source**: Choose between **Default Map (Google Maps Mirroring)** and **Ola Maps (In-App Routing)**. Selecting Ola Maps reveals the MAP tab and routing engine.
* **Ola Maps API Key**: Configure and store your personal Ola Maps developer API key securely on your device.
* **Dashboard Brand Name**: Customize the cluster title (up to 15 alphanumeric characters).
* **Preferred Currency**: Choose your local currency symbol for maintenance invoices (₹, $, €, £, ¥, C$, A$, CHF, R$, AED, ₩, ฿, RM, ₱, Rp, NZ$, zł, kr, ZAR).
* **Keep Dashboard Screen Active**: Prevents the screen from dimming or sleeping while mounted on your handlebar phone mount.
* **Dynamic Connection Launcher Icon**: The app launcher icon dynamically reflects your motorcycle's live connection status on your home screen — showing a **Blue Helmet** when actively connected to the bike and a **Gray Helmet** when disconnected/standby.
* **Quick Settings (QS) Tile**: Add the **MyRide Telemetry** toggle tile to your Android notification shade for one-tap launch.

---

### 11. Frequently Asked Questions & Troubleshooting

#### Q: The app does not auto-connect when I turn my bike ignition on.
* **Ensure phone Bluetooth is ON** and the bike is paired in your Android Bluetooth settings.
* **Check Battery Optimization**: Confirm MyRide is set to **Unrestricted** in Android settings so Android does not suspend background BLE scanning.
* **Link Companion Device**: In **SETTINGS**, tap **Ignition Auto-Connect (Hardware Standby)** to link your bike with Android's Companion Device Manager.

#### Q: Does connecting my Bluetooth earphones or TWS trigger the bike ignition notification?
* **No**: The background receiver strictly distinguishes between audio accessories (TWS earphones, headphones, car Bluetooth) and your motorcycle. Connecting an audio accessory silently ensures the background BLE scanner is armed in hardware without launching the app, starting the foreground service, or displaying standby notifications. The notification and telemetry service activate only when the motorcycle ignition is switched ON and the bike broadcasts its BLE advertisement.

#### Q: Turn-by-Turn directions are not appearing on my bike's dash.
* Verify that **Notification Listener Access** is granted for MyRide Telemetry in your phone settings.
* Make sure navigation is actively running in **Google Maps** (not just previewing a route).
* Confirm **Turn-by-Turn Navigation** is toggled ON in MyRide settings.

#### Q: Where do the lean angle and G-force readings come from?
* They are received directly from your motorcycle's factory-installed **6-axis IMU** (Inertial Measurement Unit) via the ECU Bluetooth telemetry data stream. No phone sensor calibration is required for vehicle lean angle.

---

# 🔗 Motorcycle Connectivity & Pairing Guide

This section covers the physical and Bluetooth Low Energy (BLE) pairing workflow between your Android smartphone and your motorcycle's digital instrument cluster.

---

### 1. TFT Meter Preparation

Before pairing with your smartphone, configure your motorcycle's instrument cluster for discovery:

1. Turn your motorcycle ignition key to the **ON** position (ensure the engine kill switch is in the RUN position).
2. Use the handlebar toggle buttons (usually **SEL / Up / Down**) to access the meter's configuration menu.
3. Navigate to **Bluetooth** or **Smartphone Settings** -> **Pairing**.
4. The TFT meter display will enter pairing mode and broadcast a Bluetooth pairing beacon (often displaying an icon or flashing Bluetooth indicator).

---

### 2. First-Time Bluetooth Pairing

1. Open Android **Settings** -> **Connected devices** -> **Pair new device** (or open **MyRide Telemetry**).
2. Wait for your motorcycle's meter to appear in the list of available devices (typically named after your bike model or a Bluetooth identifier).
3. Tap on the device name to initiate pairing.
4. **Passkey Confirmation**:
   - A 6-digit PIN or passkey will appear on your motorcycle's TFT screen.
   - Android will display a Bluetooth pairing request prompt.
   - Verify that the numbers match, then tap **Pair** on your phone.
5. Once paired, the motorcycle cluster will display a connected smartphone icon.

---

### 3. Zero-Touch Ignition Auto-Connect

MyRide Telemetry features an automated, zero-battery hardware standby mechanism:

```
[ Rider approaches bike & turns Ignition ON ]
                     │
                     ▼
[ Motorcycle TFT Meter broadcasts BLE Advertisements ]
                     │
                     ▼
[ Android Hardware BLE Scan Filter triggers Match ]
                     │
                     ▼
[ BikeForegroundService awakens in background ]
                     │
                     ▼
[ GATT Connection Established & MTU Negotiated in <1.5s ]
                     │
                     ▼
[ Telemetry Streams & TBT Navigation Mirroring Live! ]
```

* **No Need to Open the App**: You do not need to take your phone out of your pocket or manually tap "Connect".
* **Low Battery Impact**: Uses Android OS hardware BLE scan filters with zero active CPU wake locks while waiting.
* **Audio Peripheral Isolation**: Connecting TWS earphones, Bluetooth headsets, or helmet intercoms silently primes the background BLE hardware scanner without starting the foreground service or posting false "Waiting for bike ignition" notifications.
* **Automatic Disconnect**: When you park and switch off the ignition, the connection drops cleanly, the foreground service enters low-power standby, and your parking GPS coordinates are saved.

---

### 4. Connection Diagnostics & Recovery

If the connection is interrupted:
1. **In-App Reconnect**: Tap the Bluetooth indicator in the top header to manually trigger a GATT reconnection sweep.
2. **Cycle Bluetooth**: Toggle your phone's Bluetooth off for 3 seconds and back on.
3. **Reset Meter Bluetooth**: If the motorcycle meter previously paired with multiple phones, clear the meter's paired device list and re-pair.
4. **GATT Cache Clear**: If Android caches outdated BLE services, restart the smartphone to flush the Android Bluetooth stack cache.

---

# 🛠 Technical Architecture & Developer Reference

This section provides technical details on the architecture, protocol stack, and engineering design of MyRide Telemetry.

---

### 1. System Architecture

The application is engineered in 100% Kotlin following modern Android architectural patterns:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION LAYER                              │
│  Jetpack Compose Declarative UI  •  Single Activity Architecture       │
│  DashScreen  |  MaintenanceScreen  |  AchievementsScreen  | DevScreen  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Observes StateFlow via UDF)
┌───────────────────────────────────▼────────────────────────────────────┐
│                          DOMAIN & STATE LAYER                          │
│  TelemetryState  •  TelemetryEngine  •  AppPreferences (DataStore)     │
└──────────────┬────────────────────┬────────────────────┬───────────────┘
               │                    │                    │
┌──────────────▼─────┐ ┌────────────▼────────┐ ┌─────────▼──────────────┐
│  BLUETOOTH GATT    │ │ NOTIFICATION PARSER │ │ SENSOR FUSION ENGINE   │
│  BikeGattManager   │ │ BikeNotifListener   │ │ CompassSensorManager   │
│  Two-Lane Queue    │ │ NavNotifParser      │ │ Tilt Compensation      │
│  GATT Subscriptions│ │ Clean-Room Parser   │ │ Phone Magnetometer     │
└────────────────────┘ └─────────────────────┘ └────────────────────────┘
```

* **Single Activity Architecture**: `MainActivity.kt` acts as the root container hosting the Compose navigation graph (`MainAppScreen.kt`).
* **Unidirectional Data Flow (UDF)**: The UI is a pure function of state. Telemetry updates from the ECU flow unidirectionally through `TelemetryEngine` into immutable `TelemetryState` exposed via Kotlin `StateFlow`.
* **Zero XML Layouts**: 100% Jetpack Compose for consistent rendering performance and zero layout inflation overhead.

---

### 2. BLE 5.0 GATT Protocol Specification

#### Protocol Architecture & Characteristic Roles
The application interfaces with the motorcycle's central telemetry and meter control system using custom Bluetooth GATT profiles:

| GATT Component | Protocol Properties | Purpose & Data Payload |
|---|---|---|
| **Primary Telemetry Service** | Service Container | Root GATT service defining the motorcycle telemetry profile |
| **Control & Command Channel** | Write, Write Without Response | Transmits TBT navigation icons, distance countdowns, clock sync, and keepalive packets |
| **Vehicle Dynamics Channel** | Notify | Streams real-time RPM, wheel speed, gear position, throttle %, 6-axis lean angle, and G-force |
| **Engine Status Channel** | Notify | Streams coolant temperature, inlet air temp, battery voltage, and fuel level |
| **Trip & Odometer Channel** | Notify | Streams total odometer, Trip A, Trip B, and fuel efficiency metrics |

#### High-Throughput MTU Negotiation
Upon successful connection (`BluetoothProfile.STATE_CONNECTED`), `BikeGattManager` requests an expanded Maximum Transmission Unit (MTU) of **517 bytes** via `gatt.requestMtu(517)`. This allows multi-metric telemetry frames and navigation bitmap payloads to be transferred in single transmission events without packet fragmentation.

#### Serialized Two-Lane Priority Queue
To prevent Bluetooth buffer overflows and GATT status `133` / `WRITE_BUSY` errors, outbound GATT operations are routed through a serialized two-lane queue system in `BikeGattManager.kt`:

1. **Urgent Priority Queue**:
   - Transmits heartbeat keepalives, watchdogs, and safety interrupts.
   - Bypasses normal operational latency.
2. **Normal Operational Queue**:
   - Transmits navigation distance countdowns, turn icon updates, incoming call notifications, and periodic clock sync packets.
3. **Operation Timeout Guard**:
   - Every write operation is monitored by an asynchronous 3,000ms watchdog job. If the remote GATT server fails to return an `onCharacteristicWrite` acknowledgment, the queue clears the lock and resumes transmission.

---

### 3. Sensor Data Sources & Telemetry Pipeline

#### Motorcycle Onboard 6-Axis IMU & ECU (Primary Data Source)
All vehicle dynamics displayed on the dashboard are read directly from the motorcycle's factory-installed **onboard 6-axis Inertial Measurement Unit (IMU)** and Engine Control Unit (ECU):
* **Bilateral Lean Angles**: Live left and right roll angles measured in degrees by the motorcycle's internal gyro/accelerometer unit.
* **Pitch & Dynamics**: Wheelie and stoppie detection angles calculated by the motorcycle's IMU.
* **Longitudinal G-Force**: Accelerometer data processed into braking deceleration (-G) and forward acceleration (+G).
* **Powertrain Vitals**: Engine RPM, wheel speed, transmission gear, throttle position %, coolant temp, battery voltage, and fuel percentage.

#### Smartphone Onboard Sensors (Supplementary Utilities)
The smartphone's internal sensors are strictly reserved for supplementary cockpit utilities:
* **Magnetometer Compass (`CompassSensorManager.kt`)**: Uses `Sensor.TYPE_ROTATION_VECTOR` (or fallback Accelerometer + Magnetometer) with coordinate remapping to calculate a smooth 0°–360° cardinal heading, compensating for handlebar phone mount tilt angles.
* **GPS (`LocationManager`)**: Records route logs and latches the exact parked geographic coordinates when the Bluetooth link disconnects upon ignition switch-off.

---

### 4. Turn-by-Turn & Notification Mirroring Pipeline

1. **Notification Interception (`BikeNotificationListenerService.kt`)**:
   - Hooks into Android's `NotificationListenerService` API to intercept active notifications from navigation (Google Maps) and messaging apps.
   - Extracts custom layout elements from `bigContentView` and `contentView` (`RemoteViews`) to capture distance and maneuver text even when standard extras are truncated.
   - Filters out trip summary/ETA text (e.g. *"25 min · 12 km"*) so remaining travel time never overrides immediate maneuver turn distances.
2. **Clean-Room Text & Regex Parsing (`NavNotificationParser.kt`)**:
   - High-performance regex engine extracting numeric distance values and units (`m`, `km`, `ft`, `mi`, `yd`).
   - Normalizes non-breaking spaces (`\u00A0`, `\u202F`) and parses direction keywords, roundabouts, ramps, forks, and U-turns.
3. **Maneuver Code Translation**:
   - Deterministically maps parsed instructions to motorcycle TFT cluster symbol protocol tables (IDs `0..55`).
   - Automatically remaps Departure (`42`) to Straight (`0`) for full compatibility with physical Kawasaki TFT LCD segments.
4. **Binary Protocol Serialization & BLE Dispatch (`BikeGattManager.kt`)**:
   - **Frame 0x14 (Turn-by-Turn Navigation, 35 bytes)**: Dispatched in Mode `0` (Navigating) or Mode `3` (Clear), specifying maneuver icon (`0..55`), 24-bit distance, and unit flag (`1` for meters, `3` for feet).
   - **Frame 0x13 (Phone Status, 15 bytes)**: Periodic 5-second pulse transmitting phone battery level and charging indicator states.
   - **Frame 0x12 (Call / SMS Mirroring, 15 bytes)**: Instant transmission of incoming call and text alerts.
   - **Frame 0x1B (Atomic Clock Sync, 15 bytes)**: Aligns bike cluster clock with smartphone local time on connection.
   - **1 Hz Refresh Heartbeat**: Maintains continuous transmission of active navigation states so the motorcycle meter never times out during navigation.
5. **Interactive Developer Diagnostics (`DevScreen.kt`)**:
   - Built-in test suite allowing manual injection of Frame 0x14 turn arrows (`Right 500m`, `Left 200m`, `Straight 1km`, `Sharp Left 100m`, `Destination Arrived`, `Clear Navigation`).
   - Phone battery level simulator (`100%`, `50%`, `15% Low`, `⚡ Charging`).
   - Manual Frame 0x1B Dash Clock Sync trigger.
   - Live BLE connection status badge with queued state inspection card and instant user toasts.

---

### 5. Persistence, Scoped Storage & Security

* **Configuration Storage**: `AppPreferences.kt` uses Android preferences to store user configs (speed units, currency, display settings).
* **Service & Log Storage**: `MaintenanceStore.kt` and `LogStore.kt` serialize service histories and diagnostic logs as JSON in application-private internal storage.
* **Secure Receipt Photos**: Invoice camera captures use Android `FileProvider` (`app/src/main/res/xml/file_paths.xml`) to keep service receipts private and secure on-device.
* **Foreground Service Architecture**: `BikeForegroundService.kt` runs with the `FOREGROUND_SERVICE_CONNECTED_DEVICE` type under Android 14+ guidelines.

---

# ⚖ License & Legal Disclaimer

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)** with comprehensive automotive safety and non-affiliation terms. See the [`LICENSE`](LICENSE) file for the full text.

### Summary of Terms:
* **Permission**: Free for personal, educational, and interoperability use.
* **"AS IS" Warranty Disclaimer**: The software is provided without warranty of any kind, express or implied.
* **Motorcycle Safety & Liability Waiver**: Motorcycle operation is an inherently hazardous activity. The authors, developers, and contributors disclaim **all** liability for any accidents, traffic violations, crashes, injuries, loss of life, property damage, ECU errors, or loss of vehicle warranty arising from or related to the use of this software. The vehicle operator is solely and exclusively responsible for safe vehicle operation and full adherence to traffic laws at all times. **Never operate or view smartphone screens while riding.**
* **Non-Affiliation Notice**: This application is an unofficial, independent tool. It is **not** affiliated with, endorsed by, sponsored by, or associated with **Kawasaki Heavy Industries, Ltd.**, **Kawasaki Motors Corp.**, or any of their subsidiaries. All trademarks (including *Kawasaki*, *Ninja*, *Z900*, *ZX-10R*, *ZX-6R*, *Versys*, and TFT configurations) are the property of their respective owners, used solely for descriptive compatibility purposes under nominative fair use.
