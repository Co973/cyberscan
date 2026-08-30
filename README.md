# CyberScan

CyberScan is a single-module, portrait Android application for rooted NetHunter/LineageOS hardware. It combines a live `bluelog` Bluetooth scan, a magnetometer-derived EMF anomaly meter, and an XML-mode `nmap` discovery sweep in a cyberpunk Compose HUD.

The current target is Android 16 (API 36) on LineageOS `23.2-20260805-NIGHTLY-gunnar`. The app uses Kotlin, Jetpack Compose, Hilt, coroutines, and an MVVM state flow while keeping root processes in a foreground service.

## What is implemented

- Auto-detection of the first active `hci*` adapter from `hciconfig -a`; no hard-coded `hci0`.
- A dedicated, directly killable `bluelog` process with tolerant line parsing and normalized MAC addresses.
- Subnet derivation from the active `wlan0` IPv4 address and prefix.
- `nmap -sn -oX -` parsing with hardened XML settings; human-readable output is never scraped.
- Conservative Bluetooth-to-network correlation. Class-of-device yields `MAYBE`; a current-scan OUI or normalized name match can yield `HIGH`.
- A calibrated, smoothed magnetometer anomaly reading that stops with the scan.
- Explicit hard/soft failure states, permission handling, foreground notification, and process cleanup.
- Focused unit tests for parsers, correlation, subnet math, process behavior, ViewModel events, and reducer transitions.

Use CyberScan only with devices and networks you own or are authorized to assess.

## Hardware contract

The runtime intentionally expects the supplied NetHunter layout:

- a working `su` implementation;
- a Kali chroot at `/data/local/nhsystem/kali-armhf`;
- `/bin/bash`, `bluelog`, `hciconfig`, `ip`, and `nmap` available inside that chroot;
- at least one Bluetooth HCI adapter reporting `UP RUNNING`;
- `wlan0` with an IPv4 address for network correlation;
- a magnetometer sensor.

Bluetooth-only scanning remains useful if the network sweep is unavailable. Missing root access, no active HCI adapter, or an unexpected `bluelog` exit is a hard failure.

See [docs/hardware-acceptance.md](docs/hardware-acceptance.md) for on-device checks.

## Build and test

Open the repository in an API 36-capable Android Studio installation, select JDK 17 or newer, and run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Install to a connected device with:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

On first scan, grant Nearby devices, precise location, notifications, and the superuser prompt. Location is deliberately required because proximity is central to the feature; `BLUETOOTH_SCAN` does not claim `neverForLocation`.

## Runtime behavior

`INITIATE SCAN` launches a connected-device foreground service. The service validates root, selects the active HCI adapter, starts EMF calibration and Bluetooth collection, then attempts a one-shot Wi-Fi sweep. `ABORT`, task removal, service destruction, or app-process termination stop the loop and registered child processes. `RETRY LINK` creates a fresh session after a hard failure.

The EMF view is a relative magnetometer anomaly indicator, not a calibrated electromagnetic-field instrument or safety detector.

