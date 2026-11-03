# MyRide (Unofficial Kawasaki Rider App) – Rider's Handbook & User Manual

[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-API%2036-blue?logo=android)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%20100%25-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Connectivity](https://img.shields.io/badge/BLE-Bluetooth%205.0%20GATT-0082FC?logo=bluetooth&logoColor=white)](https://www.bluetooth.com)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

> **Important Notice**: MyRide is an independent, community-developed companion app for motorcycle riders. It is **not** an official product of, nor affiliated with, Kawasaki Heavy Industries, Ltd. or any of its subsidiaries.

---

## Welcome to MyRide

**MyRide** transforms your Android phone into an intelligent instrument cockpit and wireless bridge for your motorcycle. 

Once connected over Bluetooth, the app communicates directly with your bike's onboard systems to give you:
* **Live Cockpit Telemetry**: View engine vitals, gear selection, real-time lean angles, and G-forces on your phone while riding.
* **TFT Navigation Mirroring**: Get turn-by-turn directions from your phone projected straight onto your motorcycle's digital dash.
* **Incoming Call & Message Alerts**: See who is calling or messaging without taking your hands off the handlebars.
* **Automatic Ignition Detection**: The app connects automatically when you turn your key and sleeps when you turn it off.
* **Service & Maintenance Log**: Keep track of service dates, mileage, cost, upcoming service milestones, and attach photos or PDFs of invoices.
* **Parked Bike Locator**: Automatically remembers where you parked and guides you back on foot.
* **Personal Riding Milestones**: Keep track of your highest lean angles, top speeds, and riding achievements over time.

---

## Table of Contents

1. [Compatibility & Requirements](#1-compatibility--requirements)
2. [Getting Started: Setup & First Connection](#2-getting-started-setup--first-connection)
   - [Installing the App](#installing-the-app)
   - [Pairing with Your Motorcycle](#pairing-with-your-motorcycle)
   - [Enabling Automatic Ignition Standby](#enabling-automatic-ignition-standby)
   - [Phone Permissions & Battery Optimization](#phone-permissions--battery-optimization)
3. [The Cockpit: Dashboard (DASH)](#3-the-cockpit-dashboard-dash)
   - [Reading the Cockpit Display](#reading-the-cockpit-display)
   - [Tachometer & Gear Indicator](#tachometer--gear-indicator)
   - [Cornering Dynamics & Lean Angle](#cornering-dynamics--lean-angle)
   - [Engine Health & Trip Statistics](#engine-health--trip-statistics)
4. [Turn-by-Turn Navigation on Your Bike's Dash](#4-turn-by-turn-navigation-on-your-bikes-dash)
   - [Option A: Google Maps Mirroring (Recommended)](#option-a-google-maps-mirroring-recommended)
   - [Option B: Built-in Interactive Map (Ola Maps)](#option-b-built-in-interactive-map-ola-maps)
   - [How Navigation Appears on Your Dash](#how-navigation-appears-on-your-dash)
   - [Automatic Call Recovery During Navigation](#automatic-call-recovery-during-navigation)
5. [Smart Alerts: Calls, Messages & Clock Sync](#5-smart-alerts-calls-messages--clock-sync)
   - [Incoming Call & Text Alerts](#incoming-call--text-alerts)
   - [Phone Battery Level Indicator](#phone-battery-level-indicator)
   - [Automatic Dash Clock Synchronization](#automatic-dash-clock-synchronization)
6. [Maintenance & Service Log (SERVICE)](#6-maintenance--service-log-service)
   - [Next Service Milestone](#next-service-milestone)
   - [Logging a Service Record](#logging-a-service-record)
   - [Managing Past Service History & Receipts](#managing-past-service-history--receipts)
7. [Riding Milestones & Records (ACHIEVE)](#7-riding-milestones--records-achieve)
   - [What Achievements Are Tracked](#what-achievements-are-tracked)
   - [Resetting or Managing Records](#resetting-or-managing-records)
8. [Parked Bike Finder ("Take Me to the Bike")](#8-parked-bike-finder-take-me-to-the-bike)
   - [How Automatic Parking Location Works](#how-automatic-parking-location-works)
   - [Navigating Back to Your Motorcycle](#navigating-back-to-your-motorcycle)
9. [Settings, Backups & Quick Access](#9-settings-backups--quick-access)
   - [Units & Display Preferences](#units--display-preferences)
   - [Dynamic Home Screen Helmet Icon](#dynamic-home-screen-helmet-icon)
   - [Quick Settings Notification Shade Tile](#quick-settings-notification-shade-tile)
   - [Encrypted Backup & Restore](#encrypted-backup--restore)
10. [Rider's Troubleshooting & FAQ](#10-riders-troubleshooting--faq)
11. [Safety, Legal & Privacy Disclaimers](#11-safety-legal--privacy-disclaimers)

---

## 1. Compatibility & Requirements

### Supported Motorcycles
Tested and verified with Kawasaki models equipped with color TFT instrument clusters:
* **Z900 / Z900 SE** (2020 through 2026+ TFT models)
* **Ninja ZX-10R / ZX-10RR**
* **Ninja ZX-6R**
* **Versys 650 / Versys 1000** (TFT models)

### Smartphone Requirements
* **Operating System**: Android 7.0 (Nougat) or newer (Android 12+ recommended for optimal background standby).
* **Hardware**: Bluetooth 5.0 Low Energy (BLE) support.
* **GPS**: Enabled on your phone for compass heading and parked bike location.

---

## 2. Getting Started: Setup & First Connection

### Installing the App
1. Download the latest `.apk` release file (or debug build `app-debug.apk`) to your Android phone from the GitHub Releases page or build it locally using Gradle.
2. Open your phone's File Manager or Downloads folder and tap the downloaded `.apk` file.
3. If prompted to allow installations from unknown sources, tap **Settings** and toggle **Allow from this source**.
4. Tap **Install**, then tap **Open**.

### Pairing with Your Motorcycle
1. Turn your motorcycle ignition key to **ON** (make sure the handlebar kill switch is set to RUN).
2. Using your handlebar selector buttons (typically `SEL` / Up / Down), navigate on your bike's TFT display to:
   `Menu` -> `Bluetooth` -> `Pairing`.
3. Your bike display will show a flashing Bluetooth icon or pairing prompt.
4. On your phone, open **MyRide**, go to the **SETTINGS** tab (gear icon in the bottom menu), and tap **Bluetooth Scan**.
5. When your motorcycle appears in the list, tap on it to connect.
6. A 6-digit confirmation code will appear on your bike's screen. When your phone displays the pairing popup, verify the code matches and tap **Pair**.
7. Once paired, the Bluetooth indicator on your bike's dash turns solid, and the app connects.

### Enabling Automatic Ignition Standby
You never need to take your phone out of your pocket or tap "Connect" every time you go for a ride:
1. In the app's **SETTINGS** tab, tap **Ignition Auto-Connect (Hardware Standby)**.
2. Accept the Android system prompt to link the motorcycle as a companion device.
3. From now on, whenever you approach your bike and turn the ignition key ON, your phone detects the bike's Bluetooth signal and starts streaming within 1.5 seconds. When you turn the key OFF, the app cleanly disconnects, saves your parked location, and puts the background service to sleep.

### Phone Permissions & Battery Optimization
To ensure the app continues streaming when your phone is in your pocket or tank bag, configure these settings in the app's **SETTINGS** screen:
* **Nearby Devices / Bluetooth**: Must be allowed for communication with your bike.
* **Location**: Required by Android for Bluetooth scanning and saving your parking spot.
* **Notification Listener**: Required to forward Google Maps turn arrows and call alerts to your bike's dash.
* **Battery Optimization**: Tap **Ignore Battery Optimization** in Settings. Select **Unrestricted** so Android does not close the app during long rides.
  * *Xiaomi / MIUI / HyperOS riders*: Enable **Autostart** in phone App Info, set Battery Saver to *No Restrictions*, and enable **Display pop-up windows while running in the background** (to auto-open when ignition turns ON). If Notification Access is grayed out by Android, open App Info -> tap 3 dots (top right) -> tap *Allow restricted settings*.
  * *Samsung riders*: Ensure MyRide is not placed in *Sleeping Apps*.

---

## 3. The Cockpit: Dashboard (DASH)

The **DASH** tab is your real-time vehicle monitoring cockpit. It has been built with high-contrast, glare-resistant typography so it remains easy to read at a glance in bright sunlight.

```
+------------------------------------------------------------------------------+
|  [● BT Connected]    MYRIDE • Z900 / TFT     [14:32]  [Compass: 247°]        |
+------------------------------------------------------------------------------+
|   CRUISE: ACTIVE 80 KM/H   |      ( ( ( 6,450 ) ) )     |  COOLANT: 88°C     |
|   KTRC: LEVEL 1 (SPORT)    |           RPM              |  FUEL: 74% (190km) |
|   TRIP A: 142.6 KM         |        [ 6th Gear ]        |  AIR TEMP: 29°C    |
|   TRIP B: 24.1 KM          |         82 KM/H            |  BATTERY: 14.2V    |
+------------------------------------------------------------------------------+
|   LEAN: ◄ 38° | 0° | 41° ► | THROTTLE: [██████    ] 58% | G-FORCE: +0.42G    |
+------------------------------------------------------------------------------+
```

### Reading the Cockpit Display

#### Tachometer & Gear Indicator
* **RPM Arc**: The circular tachometer arc sweeps clockwise up to 240° (spanning 0 to 12,000 RPM) with fixed color zones along the dial:
  * **Green (0 to ~3,200 RPM)**: Low-end cruising and idle zone.
  * **Yellow (~3,200 to ~6,000 RPM)**: Mid-range powerband.
  * **Orange (~6,000 to ~8,600 RPM)**: Approaching high-RPM powerband.
  * **Red (~8,600 to 12,000 RPM)**: High-RPM and redline zone.
* **Gear Position**: Centered inside the dial. Displays gears **1** through **6**. When you shift into neutral, a bold **N** illuminates in emerald green.
* **Digital Speedometer**: Displays exact motorcycle ground speed calculated directly by the bike's ECU wheel speed sensors.

#### Cornering Dynamics & Lean Angle
* **Live Lean Angle Bar**: Displays your live roll angle in degrees directly from your bike's factory 6-axis internal gyro (IMU).
* **Peak Lean Markers**: Distinct markers show the steepest left and right lean angles achieved during your current ride.
* **Throttle Position (TPS)**: A progressive bar showing current throttle opening from 0% (idle) to 100% (wide open).
* **G-Force Acceleration / Deceleration**: Shows real-time forward acceleration (`+G`) and heavy braking forces (`-G`).

#### Engine Health & Trip Statistics
* **Coolant Temperature**: Live engine water temperature. Changes color if temperature approaches overheating limits.
* **Battery Voltage**: Real-time reading from your motorcycle battery and alternator charging system (e.g., 14.2V while running, 12.4V at rest).
* **Fuel Gauge & Range**: Live fuel percentage and estimated distance remaining.
* **Intake Air Temperature**: Measures ambient air temperature entering the engine airbox.
* **Cruise Control & Traction Control (KTRC)**: Shows current cruise set speed and active traction control modes.
* **Trip Meters**: Displays live Trip A and Trip B synced directly from your bike's odometer.

---

## 4. Turn-by-Turn Navigation on Your Bike's Dash

MyRide allows you to keep your phone safely tucked in your jacket or tank bag while following directions shown directly on your motorcycle's TFT meter.

```
+------------------------+          +------------------------+          +------------------------+
|  Google Maps on Phone  |          |    MyRide              |          | Motorcycle Dash Screen |
|  "In 300m, Turn Right" |  ----->  | Extracts Maneuver &    |  ----->  |       [ 300 m ]        |
|                        |          | Distance over BLE      |          |         [ ➔ ]         |
+------------------------+          +------------------------+          +------------------------+
```

### Option A: Google Maps Mirroring (Recommended)
This mode lets you use standard Google Maps navigation with full TFT integration:
1. In the app's **SETTINGS** tab, ensure **Turn-by-Turn Navigation** is turned **ON** and set **Navigation Source** to **Default Map**.
2. Open **Google Maps** on your phone, set your destination, and tap **Start**.
3. Put your phone away. Your bike's dashboard will immediately illuminate with:
   * Next turn direction arrow (Left, Right, Roundabout, U-turn, Fork, Highway exit, etc.).
   * Exact distance countdown to the turn in meters, kilometers, or feet.
   * Chequered flag icon when approaching your final destination.

### Option B: Built-in Interactive Map (Ola Maps)
If you prefer an in-app visual navigation experience on your handlebar mount:
1. Obtain an API key from the Ola Maps Developer Portal and enter it in **SETTINGS** under **Ola Maps API Key**.
2. Set **Navigation Source** to **Ola Maps**.
3. A new **MAP** tab appears on the bottom bar:
   * Features a high-contrast dark road map engineered for daytime clarity.
   * Search any place or address with instant search suggestions.
   * Remembers your last 5 destinations for instant one-tap route start.
   * Displays your live position with a pulsating radar marker and route polyline.
   * Automatically streams every turn maneuver and countdown distance to your bike's TFT cluster while you ride.

### How Navigation Appears on Your Dash
* **Departure Normalization**: Initial routing instructions (like *"Head north"*) are automatically converted to a Straight arrow so your dash displays routing immediately.
* **Destination Countdown**: As you reach your destination, the distance counts down steadily (`500m... 100m... 20m`) alongside the destination flag until you fully arrive.
* **Reliable Connection Heartbeat**: The app sends a steady refresh pulse every second so your bike never loses the turn arrow on long highway stretches.

### Automatic Call Recovery During Navigation
If someone calls you while navigating, your bike displays the incoming call alert. The moment the call ends, your navigation arrow and distance immediately return to your dash automatically—no need to touch your phone or restart your route.

---

## 5. Smart Alerts: Calls, Messages & Clock Sync

### Incoming Call & Text Alerts
Keep informed without distractions:
* **Caller ID**: When your phone rings, the caller's name or number displays on your motorcycle cluster.
* **Message Alerts**: A discreet notification informs you when a new SMS or message arrives.
* **Privacy Focused**: Notifications are processed entirely in your phone's memory and sent directly to your bike over Bluetooth. No text content is ever stored, uploaded, or tracked.

### Phone Battery Level Indicator
MyRide periodically updates your motorcycle dashboard with your phone's current battery percentage and charging status, so you always know if your phone needs charging.

### Automatic Dash Clock Synchronization
Every time your motorcycle connects, MyRide silently synchronizes your bike's instrument clock with your smartphone's atomic network time. You never have to manually adjust your bike's clock for daylight saving time or battery disconnections again.

---

## 6. Maintenance & Service Log (SERVICE)

The **SERVICE** tab keeps a clean, centralized digital log of your motorcycle maintenance history and tracks upcoming service milestones:

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
|      Notes: Regular service, oil change, chain service.           |
+-------------------------------------------------------------------+
|  [ + ADD SERVICE RECORD ]                                         |
+-------------------------------------------------------------------+
```

### Next Service Milestone
At the top of the screen, an overview card tracks your upcoming service requirements:
* **Target Mileage & Date**: Displays the target odometer reading and scheduled date for your next service.
* **Remaining Countdown**: Shows how many kilometers/miles or calendar days remain before your next service is due.
* **Due Alerts**: When your motorcycle reaches or exceeds the target odometer or date, the card alerts you that service is due.

### Logging a Service Record
When you service your motorcycle, tap **Add Service Record** to log the details:
* **Service Date**: The date the maintenance was performed.
* **Odometer Reading**: Your bike's mileage at the time of service.
* **Amount / Cost**: The total invoice cost in your selected currency.
* **Notes / Work Done**: Free-form notes describing the work carried out (e.g., oil change, brake pads, general checkup).
* **Next Service Target**: Set your next planned service target by:
  * **Next Service Odometer**: Target mileage when the bike should be serviced next.
  * **Next Service Date**: Target date when maintenance is next due.
* **Document & Receipt Attachments**:
  * Tap to take photos of paper bills and receipts with your camera, or attach documents and PDFs directly from your phone.
  * You can attach multiple invoices/receipts per service record.
  * Invoices are stored securely in app-private storage and can be viewed or reviewed at any time.

### Managing Past Service History & Receipts
* Tap on any existing record to review details, view attached invoice receipts in full screen, edit entries, or remove outdated logs.

---

## 7. Riding Milestones & Records (ACHIEVE)

The **ACHIEVE** tab acts as your personal hall of fame, tracking your milestones across all your rides:

* **0–100 KM/H / 0–60 MPH Sprint Time**: Measures your quickest standing acceleration time.
* **Maximum Lean Angles**: Records your personal best left and right cornering roll angles in degrees, measured directly by your motorcycle's factory IMU sensor.
* **Top Speed Recorded**: Logs your highest speed achieved on track or open road.
* **Maximum G-Force**: Peak acceleration/braking force recorded.
* **Front & Rear Pitch**: Records wheelie lift and stoppie pitch angles detected during rides.
* **Longest Ride Records**: Tracks your longest single ride by distance (km/mi) and duration.

*Tip: All records are updated automatically from the bike's ECU and IMU. In Developer Options, achievements can also be reset when starting a new season.*

---

## 8. Parked Bike Finder ("Take Me to the Bike")

Never forget where your motorcycle is parked in a crowded rally, multistory garage, or unfamiliar city:

### How Automatic Parking Location Works
1. When you park your bike and turn the ignition key to **OFF**, the Bluetooth connection disconnects.
2. The instant the connection drops, MyRide records your phone's exact GPS coordinates and saves them as your **Parked Location**.
3. No manual button press or check-in is required.

### Navigating Back to Your Motorcycle
1. When you are ready to return to your bike, open **MyRide**.
2. On the **DASH** screen or bottom notification card, tap **Take me to the bike**.
3. The app opens Google Maps with step-by-step walking directions directly to your motorcycle's exact parked spot.
4. In **SETTINGS**, you can set a parking marker expiration time (1h, 3h, 6h, 12h, or 24h) so old parking spots clear automatically.

---

## 9. Settings, Backups & Quick Access

The **SETTINGS** tab lets you tailor the app to your preferences:

### Units & Display Preferences
* **Speed Units**: Switch between **KM/H** and **MPH**.
* **Temperature Units**: Switch between **°C** (Celsius) and **°F** (Fahrenheit).
* **Currency**: Select your local currency symbol for maintenance records (₹, $, €, £, ¥, C$, A$, CHF, etc.).
* **Dashboard Header**: Customize the text banner on the top of the cockpit (e.g., your bike model, your name, or *TRACK*).
* **Keep Screen On**: Keeps the phone display active while mounted on your handlebars so it never dims or sleeps mid-ride.

### Dynamic Home Screen Helmet Icon
The app's launcher icon on your phone's home screen changes appearance based on connection status:
* **Blue Helmet**: Connected and communicating with your bike.
* **Gray Helmet**: Disconnected / standby.

### Quick Settings Notification Shade Tile
Add the **Smart Bike** tile to your phone's quick settings drawer (swipe down from the top of your screen) to toggle telemetry, check connection status, or trigger manual reconnects with a single tap.

### Encrypted Backup & Restore
Protect your maintenance history, receipt photos, and personal records against phone loss or upgrades:
* **Export Archive**: Generates a single `.myride` backup file containing your entire history and photo receipts.
* **Password-Protected**: Archives are encrypted using your personal password.
* **Scheduled Backups**: Choose automatic weekly or daily backups to a folder of your choice (such as Google Drive or SD card).
* **Seamless Restore**: Transfer your `.myride` file to a new phone, enter your password, and restore your complete service history instantly.

---

## 10. Rider's Troubleshooting & FAQ

#### Q: The app does not auto-connect when I switch my ignition on.
* Verify that your phone's Bluetooth is turned ON.
* Check that you have completed the one-time **Ignition Auto-Connect** setup in the **SETTINGS** tab.
* Confirm that Android battery optimization is set to **Unrestricted** for MyRide. Aggressive battery savers can shut down background Bluetooth scanning.

#### Q: Will connecting my Bluetooth helmet intercom or earbuds interfere with the app?
* **No**. The app distinguishes between audio accessories (headphones, helmet intercoms) and your motorcycle's instrument cluster. Connecting your intercom will never trigger false motorcycle connection alerts.

#### Q: Turn-by-turn arrows are not showing up on my motorcycle's screen.
1. Check that **Turn-by-Turn Navigation** is toggled **ON** in MyRide's **SETTINGS**.
2. Make sure you have granted **Notification Access** to MyRide in your phone settings.
3. Verify that Google Maps is in active turn-by-turn navigation mode (showing the green banner with upcoming directions), not just previewing a route.

#### Q: Where do the lean angle and G-force measurements come from?
* They come directly from the motorcycle's factory **6-axis IMU sensor** via the ECU data stream. You do not need to calibrate your phone or mount it in any specific angle—the bike measures its own dynamics.

#### Q: How much phone battery does the app consume during rides?
* The app uses Bluetooth Low Energy (BLE 5.0) hardware filters that consume minimal battery. For all-day rides with screen-on cockpit use, keeping your phone connected to a handlebar USB charger is recommended.

---

## 11. Safety, Legal & Privacy Disclaimers

### Rider Safety Warning
> **ALWAYS KEEP YOUR EYES ON THE ROAD.**
> Motorcycle operation is an inherently hazardous activity. Never operate phone touchscreens, browse menus, or adjust app settings while riding. All settings must be adjusted while safely stopped with the motorcycle in neutral. The rider is solely and exclusively responsible for the safe operation of the vehicle and full adherence to all traffic laws at all times.

### License Notice
This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [`LICENSE`](LICENSE) file for the full license terms.

### Liability Waiver
The authors, developers, and open-source contributors disclaim all liability for any accidents, injuries, traffic citations, mechanical failures, ECU errors, property damage, or loss of warranty arising from or related to the use of this software. This software is provided "AS IS", without warranty of any kind.

### Trademark & Non-Affiliation Notice
* This is an independent, community-authored open-source utility.
* It is **not** affiliated with, endorsed by, or sponsored by **Kawasaki Heavy Industries, Ltd.**, **Kawasaki Motors Corp.**, or any of their regional distributors.
* The names *Kawasaki*, *Ninja*, *Z900*, *ZX-10R*, *ZX-6R*, *Versys*, and related model names are registered trademarks of their respective owners, used here strictly for nominative identification and vehicle compatibility descriptions.

---

*Ride safe and enjoy the open road!*

