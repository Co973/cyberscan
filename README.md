# CyberScan

CyberScan is a single-module, portrait Android application for LineageOS/NetHunter hardware. It combines native Android BLE and Classic discovery, a magnetometer-derived EMF anomaly meter, and an optional root-backed XML-mode `nmap` discovery sweep in a cyberpunk Compose HUD. An external BlueZ HCI adapter can enrich the native results when one is present.

The current target is Android 16 (API 36) on LineageOS `23.2-20260805-NIGHTLY-gunnar`. The app uses Kotlin, Jetpack Compose, Hilt, coroutines, and an MVVM state flow while keeping root processes in a foreground service.

## What is implemented

- Native BLE and Classic discovery through Android's Bluetooth HAL and the phone's internal chipset.
- Optional auto-detection of the first active external `hci*` adapter from `hciconfig -a`; no hard-coded `hci0`.
- A dedicated, directly killable optional `bluelog` process, tolerant line parsing, and MAC-based deduplication with native results.
- Subnet derivation from the active `wlan0` IPv4 address and prefix.
- `nmap -sn -oX -` parsing with hardened XML settings; human-readable output is never scraped.
- Conservative Bluetooth-to-network correlation. Class-of-device yields `MAYBE`; a current-scan OUI or normalized name match can yield `HIGH`.
- A calibrated, smoothed magnetometer anomaly reading that stops with the scan.
- Explicit hard/soft failure states, permission handling, foreground notification, and process cleanup.
- Focused unit tests for parsers, correlation, subnet math, process behavior, ViewModel events, and reducer transitions.

Use CyberScan only with devices and networks you own or are authorized to assess.

## Hardware contract

The baseline scan requires an enabled Android Bluetooth adapter, Nearby devices/location permissions, and the internal chipset exposed through Android's Bluetooth HAL. It does not require `hciconfig`, BlueZ, a Kali chroot, or an external Bluetooth adapter.

The optional network sweep requires working `su`, `wlan0` with an IPv4 address, and both `ip` and `nmap`. CyberScan searches Android's root environment and the supported NetHunter roots (`kalifs`, `kali-arm64`, and `kali-armhf`) instead of assuming one fixed layout. Optional external-HCI enrichment similarly requires `hciconfig`, `bluelog`, and an active `hci*` adapter in one supported environment.

Missing root, Nmap, a chroot, or an external adapter is a soft capability loss: native BLE/Classic and EMF continue. Only failure of the Android native Bluetooth path is a hard Bluetooth failure.

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

On first scan, grant Nearby devices, precise location, and notifications. Grant the superuser prompt to enable Nmap and optional external-HCI enrichment. Location is deliberately required because proximity is central to the feature; `BLUETOOTH_SCAN` does not claim `neverForLocation`.

## Runtime behavior

`INITIATE SCAN` launches a connected-device foreground service. The service immediately starts EMF calibration and native BLE/Classic collection. In parallel, it probes for Nmap and an optional external HCI backend; those results enrich the session without gating native discovery. `ABORT`, task removal, service destruction, or app-process termination stop native discovery and registered child processes. `RETRY LINK` creates a fresh session after a hard failure.

The EMF view is a relative magnetometer anomaly indicator, not a calibrated electromagnetic-field instrument or safety detector.

