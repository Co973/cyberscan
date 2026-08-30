# CyberScan Android App Design

## Purpose

CyberScan is a hardware-only Android companion for a rooted Android 16 device running LineageOS 23.2 and Kali NetHunter. It combines continuous `bluelog` Bluetooth discovery, a one-shot `nmap` network sweep, and magnetometer-derived EMF anomaly readings in a portrait cyberpunk HUD.

The implementation stays within one Android application module and follows the supplied Kotlin, Jetpack Compose, Hilt, coroutines, StateFlow, and MVVM structure. It does not persist scan data, use cloud services, emit analytics, or include simulated results.

## Platform and Build

- Application ID: `com.cyberscan.app`
- Target and compile SDK: Android 16 / API 36
- Minimum SDK: API 31, matching the modern Bluetooth runtime-permission model and the dedicated target hardware
- Language and UI: Kotlin and Jetpack Compose
- Dependency injection: Hilt
- Concurrency: Kotlin coroutines and StateFlow
- Project shape: one Gradle Android app module with package boundaries for core, data, domain, service, and UI code

## Architecture

### Core shell layer

`ShellExecutor` owns one persistent `su` session for bounded one-shot commands. Every command uses a unique sentinel so stdout, stderr, and exit status are associated with the correct request. Access to the shell is serialized. Startup returns a clear root-grant result, and shutdown closes streams, terminates the process, and cancels pending work.

`LoopingShellProcess` owns continuous commands such as `bluelog`. It launches a separately killable root process, streams output, reports unexpected termination, and registers itself with `AppProcessRegistry`. Continuous work is never multiplexed onto the persistent one-shot shell.

`AppProcessRegistry` tracks all looping child processes and provides an idempotent `killAll()` operation for scan stop, service destruction, and task removal.

### Hardware discovery

`BluetoothAdapterDetector` queries available `hci*` interfaces, prefers an interface that is up and running, and deterministically falls back to the first usable interface. If no usable adapter exists, the scan ends with a hard, actionable failure. The implementation does not assume `hci0`.

`EmfSensorManager` is view-session scoped and uses Android's magnetometer directly. A scan begins with a two-second stationary baseline calibration, then publishes exponentially smoothed anomaly readings at approximately 12 updates per second. It unregisters on stop and screen disposal.

### Data layer

`BluetoothRepository` launches `bluelog -a -v` against the detected adapter, parses lines into Bluetooth devices, deduplicates by normalized MAC address, updates first/last-seen metadata, exposes scan activity, and turns unexpected loop termination into a hard failure.

`NetworkRepository` runs `nmap -sn -oX - <cidr>` and parses XML rather than human-readable output. It extracts IPv4 address, MAC address, vendor, and hostname without relying on nmap's localized display format. Missing wlan0, command failure, malformed XML, or an empty usable subnet is a soft degradation.

### Domain layer

Domain models remain Android-free. `ClassifyDevice` gives IP-capable Bluetooth classes a `MAYBE` confidence and non-network classes `NONE`. `CorrelateWithNetwork` promotes eligible devices to `HIGH` when an OUI prefix or normalized device-name/hostname match exists. A `NONE` classification cannot be promoted by a coincidental match.

`GetLocalSubnet` reads the configured Wi-Fi interface address and calculates the network CIDR using validated IPv4 and prefix values. Invalid octets or prefixes return no subnet instead of producing a malformed scan target.

### Service and state ownership

`ScanForegroundService` is the single owner of a running scan session. After permission approval, it immediately promotes itself with a visible `connectedDevice` notification, starts the root gate, detects the adapter, begins Bluetooth discovery, and launches the independent network sweep. It uses `START_NOT_STICKY` and never resumes a stale scan after process death.

The service calls `AppProcessRegistry.killAll()` and closes its shell on explicit stop, `onTaskRemoved()`, and `onDestroy()`. Starting twice is idempotent and cannot create duplicate bluelog processes.

`ScanViewModel` exposes immutable `StateFlow` state to Compose and accepts start, stop, retry, and target-selection intents. Scan behavior is represented by a deterministic state machine rather than inferred from loosely related booleans.

## Scan State Machine

The UI state has these phases:

1. `Idle`: no scan is running and no current-session results exist.
2. `Calibrating`: permissions are granted, the service is starting, root and adapter checks run, and the EMF baseline is collected.
3. `Scanning`: Bluetooth results stream, EMF readings update, and network correlation may be pending or available.
4. `Complete`: the user stopped the scan; child processes are gone and the current results remain visible.
5. `Failed`: a hard failure ended the session and exposes a reason plus retry.

Network status is an independent value (`Pending`, `Available`, or `Unavailable`) carried by active and complete states. Network unavailability never erases Bluetooth results or turns the whole session into `Failed`.

Hard failures are root denial, no usable Bluetooth adapter, and unexpected bluelog process death. Soft failures include no usable Wi-Fi subnet, nmap failure, and malformed nmap XML.

## Android 16 Permissions and Foreground Service

The manifest declares `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `ACCESS_WIFI_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, and `POST_NOTIFICATIONS`. Because proximity is part of CyberScan's purpose, `BLUETOOTH_SCAN` does not claim `neverForLocation`.

Bluetooth, location, and notification permissions are requested lazily when the user starts the first scan. The service starts only after the permissions needed for its connected-device runtime prerequisite are granted. Denial returns the UI to an actionable state without starting shell work.

## Portrait HUD Design

The app uses a fixed portrait-first layout with sharp angular geometry, corner-bracket panels, cyan primary telemetry, red-orange alert accents, monospace typography, subtle scanlines, and low-opacity matrix texture. It does not copy proprietary game assets.

The screen contains:

- A compact top status meter for EMF anomaly magnitude, calibration state, root status, adapter name, and network availability.
- A primary detected-device list styled like a quickhack list. Each row shows the best name, MAC address, Bluetooth class, recency, and a textual confidence label.
- A target-data panel below the list for the selected device. It shows Bluetooth identity, correlated IP/hostname/vendor data, confidence stars plus text, and the reason for the confidence level.
- A bottom action bar for start, stop, retry, and target navigation. Touch targets remain accessible even though the chrome is visually compact.

The interface provides content descriptions, readable contrast, reduced-motion behavior, and non-color indicators for confidence and failures.

## Interaction Flow

On launch, the idle HUD contains no retained results. The first scan tap requests required permissions. After approval, the foreground service starts and the UI enters calibration. When the baseline and root/adapter gate complete, live scanning begins.

Bluetooth rows update in place by MAC address. Selecting a row updates the lower target panel without interrupting the scan. The one-shot network sweep enriches existing rows whenever it completes. Stopping terminates all loop processes, unregisters the sensor, and leaves current results in `Complete` until a new scan or app exit.

Retry starts a fresh session with cleared devices, network results, errors, and EMF baseline. Swiping the app from Recents kills every registered child process and discards the session.

## Error Handling

Hard failures display the exact failed prerequisite and a retry action. Logs and user-facing messages do not expose raw privileged command output unnecessarily. Shell commands use fixed executable names and validated adapter, interface, and CIDR arguments rather than free-form user input.

Soft network degradation displays a compact warning in the status area and explains that confidence cannot exceed `MAYBE`. Bluetooth scanning and EMF updates continue.

Parser failures are contained: an invalid bluelog line is ignored, while invalid nmap XML marks network correlation unavailable. Process cleanup is safe to call more than once.

## Testing and Verification

Local JVM tests cover:

- bluelog parsing with representative valid, partial, duplicate, and malformed lines;
- nmap XML parsing with hosts containing or omitting MAC, vendor, hostname, and IPv4 fields, plus malformed XML;
- adapter detection selection across active, down, multiple, and absent adapters;
- Bluetooth class confidence and OUI/name correlation rules;
- IPv4 subnet math for `/0`, `/24`, `/31`, `/32`, invalid octets, and invalid prefixes;
- scan reducer transitions for start, calibration completion, live devices, soft network failure, stop, hard failure, retry, and duplicate start.

Android-facing tests remain focused on permission mapping and service intent construction where practical. Final verification requires a clean debug build and passing unit tests. Hardware acceptance verifies root grant, foreground notification, adapter auto-detection, live bluelog discovery, XML nmap enrichment, EMF calibration, explicit stop cleanup, and swipe-from-Recents cleanup on the target device.

## Out of Scope

- Demo or simulator mode
- Persistent history or Room storage
- Remote services, accounts, analytics, or cloud synchronization
- Active exploitation, packet injection, or automated attacks
- Multiple Gradle feature modules
- Native replacement of the installed NetHunter command-line tools

