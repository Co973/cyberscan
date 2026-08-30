# CyberScan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a hardware-only Android 16 CyberScan app that safely orchestrates rooted Bluetooth discovery, XML nmap correlation, and magnetometer anomaly telemetry in a portrait Compose HUD.

**Architecture:** A single Android app module is split into focused core, data, domain, service, and UI packages. Pure parsing, correlation, subnet, adapter-selection, and state-reducer logic stays Android-free and receives JVM tests; Android shell, sensor, service, permission, and Compose adapters sit at the edges.

**Tech Stack:** Kotlin 2.4.10, Android Gradle Plugin 9.3.2, Android API 36, Compose BOM 2026.08.00, Material 3, Hilt 2.60.1, KSP 2.3.11, coroutines/StateFlow, JUnit 5, XML pull parsing, Gradle Kotlin DSL

**Spec:** `docs/superpowers/specs/2026-08-30-cyberscan-design.md`

## Global Constraints

- Application ID is exactly `com.cyberscan.app`.
- Compile and target SDK are API 36; minimum SDK is API 31.
- Pin AGP 9.3.2, Kotlin 2.4.10, KSP 2.3.11, Hilt 2.60.1, and Compose BOM 2026.08.00.
- Keep exactly one Android application module.
- Runtime integrations are hardware-only: no mock repository, sample devices, cloud calls, analytics, or persistence.
- `bluelog` runs in a dedicated killable root process; one-shot commands use a serialized persistent root shell.
- Detect an active `hci*` adapter; never assume `hci0`.
- Run nmap as `nmap -sn -oX - <cidr>` and parse XML.
- Keep privileged command arguments internal and validated.
- A scan is fresh per app launch and the service is `START_NOT_STICKY`.
- Use a portrait-first original HUD inspired by the supplied direction without copying proprietary assets.

---

## File Map

Build configuration:

- `settings.gradle.kts` — plugin repositories and single `app` module inclusion.
- `build.gradle.kts` — root Android and Hilt plugin versions.
- `gradle.properties` — AndroidX and Kotlin build settings.
- `app/build.gradle.kts` — API levels, Compose/Hilt/coroutines/XML/test dependencies.
- `app/src/main/AndroidManifest.xml` — Android 16 permissions, activity, application, and foreground service.

Core and domain:

- `core/shell/ShellExecutor.kt` — serialized persistent `su` command execution.
- `core/shell/LoopingShellProcess.kt` — continuous privileged process lifecycle and output streaming.
- `core/shell/AppProcessRegistry.kt` — idempotent process cleanup.
- `core/sensors/EmfSensorManager.kt` — magnetometer calibration, smoothing, and throttling.
- `core/di/AppModule.kt` — Hilt providers and application-scoped runtime objects.
- `domain/model/ScanModels.kt` — device, confidence, EMF, network, and scan-state types.
- `domain/usecase/DeviceCorrelation.kt` — pure classification and correlation.
- `domain/usecase/Ipv4Subnet.kt` — validated IPv4 CIDR calculation.
- `domain/state/ScanReducer.kt` — deterministic scan events and transitions.

Data and orchestration:

- `data/bluetooth/BluelogParser.kt` — bluelog record parsing and CoD mapping.
- `data/bluetooth/BluetoothAdapterDetector.kt` — `hci*` output parsing and selection.
- `data/bluetooth/BluetoothRepository.kt` — adapter selection, bluelog loop, deduplication, and failures.
- `data/network/NmapXmlParser.kt` — nmap XML host parsing.
- `data/network/NetworkRepository.kt` — local CIDR lookup and one-shot XML sweep.
- `service/ScanSessionController.kt` — scan sequencing independent of Android Service callbacks.
- `service/ScanForegroundService.kt` — Android 16 foreground notification and lifecycle boundary.

UI:

- `CyberScanApplication.kt` — Hilt application.
- `ui/MainActivity.kt` — Compose host and service intent wiring.
- `ui/ScanPermissions.kt` — lazy Android 16 permission request flow.
- `ui/viewmodel/ScanViewModel.kt` — state, scan actions, selection, and EMF collection.
- `ui/theme/Color.kt`, `Type.kt`, `Theme.kt` — original cyan/red HUD design system.
- `ui/hud/CornerBracketPanel.kt` — reusable angular panel primitive.
- `ui/hud/ScanScreen.kt` — portrait screen composition.
- `ui/hud/StatusMeter.kt`, `DeviceList.kt`, `TargetDataPanel.kt`, `ActionBar.kt` — focused HUD regions.

Tests mirror pure production packages under `app/src/test/java/com/cyberscan/app/`.

---

### Task 1: Project Scaffold and Domain Contract

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/cyberscan/app/CyberScanApplication.kt`
- Create: `app/src/main/java/com/cyberscan/app/domain/model/ScanModels.kt`
- Test: `app/src/test/java/com/cyberscan/app/domain/model/ScanModelsTest.kt`

**Interfaces:**
- Produces: `BluetoothDevice`, `NetworkDevice`, `MergedDevice`, `DeviceClass`, `Confidence`, `EmfReading`, `NetworkStatus`, `ScanPhase`, and `ScanUiState`.
- Produces: Hilt-enabled `CyberScanApplication` and an API 36 single-module Gradle build.

- [ ] **Step 1: Write the domain contract test**

```kotlin
class ScanModelsTest {
    @Test fun `normalized MAC identity is uppercase`() {
        val device = BluetoothDevice("aa:bb:cc:01:02:03", "node", DeviceClass.COMPUTER, null, 1, 2)
        assertEquals("AA:BB:CC:01:02:03", device.macAddress)
    }

    @Test fun `failed state retains an actionable reason`() {
        val state = ScanUiState(phase = ScanPhase.Failed("No active Bluetooth adapter"))
        assertEquals("No active Bluetooth adapter", (state.phase as ScanPhase.Failed).reason)
    }
}
```

- [ ] **Step 2: Create the Gradle and manifest scaffold**

Configure `compileSdk = 36`, `targetSdk = 36`, `minSdk = 31`, AGP 9.3.2, Kotlin 2.4.10, KSP 2.3.11, Hilt 2.60.1, and Compose BOM 2026.08.00. Enable Compose, Hilt/KSP, coroutines, lifecycle, XML pull support, JUnit 5, and `useJUnitPlatform()`. Declare `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `ACCESS_WIFI_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, and `POST_NOTIFICATIONS`; declare the service with `android:foregroundServiceType="connectedDevice"` and lock the activity to portrait.

- [ ] **Step 3: Implement the model types**

```kotlin
enum class Confidence { NONE, MAYBE, HIGH }
enum class DeviceClass { COMPUTER, PHONE, NETWORKING, AUDIO_VIDEO, PERIPHERAL, WEARABLE, UNKNOWN }
enum class NetworkStatus { Pending, Available, Unavailable }

sealed interface ScanPhase {
    data object Idle : ScanPhase
    data object Calibrating : ScanPhase
    data object Scanning : ScanPhase
    data object Complete : ScanPhase
    data class Failed(val reason: String) : ScanPhase
}

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.Idle,
    val devices: List<MergedDevice> = emptyList(),
    val selectedMac: String? = null,
    val networkStatus: NetworkStatus = NetworkStatus.Pending,
    val adapterName: String? = null,
    val emf: EmfReading? = null,
)
```

Normalize and validate MAC addresses at model construction so equality and repository keys are stable.

- [ ] **Step 4: Run the model test and debug compilation**

Run: `./gradlew testDebugUnitTest --tests '*ScanModelsTest'`

Expected: model tests pass and the app module compiles for API 36.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app
git commit -m "build: scaffold CyberScan Android app"
```

### Task 2: Pure Correlation, Subnet, and State Reducer

**Files:**
- Create: `app/src/main/java/com/cyberscan/app/domain/usecase/DeviceCorrelation.kt`
- Create: `app/src/main/java/com/cyberscan/app/domain/usecase/Ipv4Subnet.kt`
- Create: `app/src/main/java/com/cyberscan/app/domain/state/ScanReducer.kt`
- Test: `app/src/test/java/com/cyberscan/app/domain/usecase/DeviceCorrelationTest.kt`
- Test: `app/src/test/java/com/cyberscan/app/domain/usecase/Ipv4SubnetTest.kt`
- Test: `app/src/test/java/com/cyberscan/app/domain/state/ScanReducerTest.kt`

**Interfaces:**
- Consumes: model types from Task 1.
- Produces: `ClassifyDevice.baseConfidence(DeviceClass): Confidence`.
- Produces: `CorrelateWithNetwork.correlate(List<BluetoothDevice>, List<NetworkDevice>): List<MergedDevice>`.
- Produces: `Ipv4Subnet.networkCidr(ip: String, prefixLength: Int): String?`.
- Produces: `ScanReducer.reduce(state: ScanUiState, event: ScanEvent): ScanUiState`.

- [ ] **Step 1: Write failing correlation tests**

```kotlin
@Test fun `eligible OUI match promotes confidence to high`() {
    val bt = btDevice("AA:BB:CC:00:00:01", DeviceClass.COMPUTER)
    val network = networkDevice("AA:BB:CC:99:88:77", "192.168.1.10")
    assertEquals(Confidence.HIGH, CorrelateWithNetwork.correlate(listOf(bt), listOf(network)).single().confidence)
}

@Test fun `peripheral is never promoted`() {
    val bt = btDevice("AA:BB:CC:00:00:01", DeviceClass.PERIPHERAL)
    val network = networkDevice("AA:BB:CC:99:88:77", "192.168.1.10")
    assertEquals(Confidence.NONE, CorrelateWithNetwork.correlate(listOf(bt), listOf(network)).single().confidence)
}
```

- [ ] **Step 2: Write failing subnet and reducer tests**

```kotlin
@ParameterizedTest
@CsvSource("192.168.1.42,24,192.168.1.0/24", "10.2.3.4,0,0.0.0.0/0", "10.2.3.4,32,10.2.3.4/32")
fun `network CIDR handles valid boundaries`(ip: String, prefix: Int, expected: String) {
    assertEquals(expected, Ipv4Subnet.networkCidr(ip, prefix))
}

@Test fun `soft network failure keeps scanning`() {
    val scanning = ScanUiState(phase = ScanPhase.Scanning)
    val result = ScanReducer.reduce(scanning, ScanEvent.NetworkUnavailable)
    assertEquals(ScanPhase.Scanning, result.phase)
    assertEquals(NetworkStatus.Unavailable, result.networkStatus)
}
```

- [ ] **Step 3: Run tests to verify the behavior is absent**

Run: `./gradlew testDebugUnitTest --tests '*DeviceCorrelationTest' --tests '*Ipv4SubnetTest' --tests '*ScanReducerTest'`

Expected: compilation fails because use cases and reducer do not exist.

- [ ] **Step 4: Implement the pure functions and reducer**

Use unsigned 32-bit arithmetic for IPv4, reject prefixes outside `0..32`, reject non-decimal or out-of-range octets, compare normalized three-octet OUIs, and use trimmed case-insensitive containment for names. Define explicit events for `StartRequested`, `CalibrationFinished(adapterName)`, `DevicesChanged`, `NetworkAvailable`, `NetworkUnavailable`, `EmfChanged`, `TargetSelected`, `StopRequested`, `HardFailure`, and `RetryRequested`. Duplicate starts while calibrating/scanning return the unchanged state.

- [ ] **Step 5: Run the focused tests**

Run: `./gradlew testDebugUnitTest --tests '*DeviceCorrelationTest' --tests '*Ipv4SubnetTest' --tests '*ScanReducerTest'`

Expected: all focused tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cyberscan/app/domain app/src/test/java/com/cyberscan/app/domain
git commit -m "feat: add scan domain logic and reducer"
```

### Task 3: Hardened Parsers and Adapter Auto-Detection

**Files:**
- Create: `app/src/main/java/com/cyberscan/app/data/bluetooth/BluelogParser.kt`
- Create: `app/src/main/java/com/cyberscan/app/data/bluetooth/BluetoothAdapterDetector.kt`
- Create: `app/src/main/java/com/cyberscan/app/data/network/NmapXmlParser.kt`
- Test: `app/src/test/java/com/cyberscan/app/data/bluetooth/BluelogParserTest.kt`
- Test: `app/src/test/java/com/cyberscan/app/data/bluetooth/BluetoothAdapterDetectorTest.kt`
- Test: `app/src/test/java/com/cyberscan/app/data/network/NmapXmlParserTest.kt`

**Interfaces:**
- Consumes: Task 1 device models.
- Produces: `BluelogParser.parse(line: String, nowMs: Long): BluetoothDevice?`.
- Produces: `BluetoothAdapterDetector.parse(output: String): List<HciAdapter>` and `select(adapters: List<HciAdapter>): HciAdapter?`.
- Produces: `NmapXmlParser.parse(xml: String): Result<List<NetworkDevice>>`.

- [ ] **Step 1: Add parser fixtures and failing tests**

Cover labeled and compact bluelog lines, quoted names, absent RSSI, malformed MACs, major CoD values, multiple `hci*` blocks, active/down adapter preference, nmap hosts with omitted optional fields, and malformed XML.

```kotlin
@Test fun `active adapter wins over lower numbered down adapter`() {
    val adapters = BluetoothAdapterDetector.parse(hciconfigFixture)
    assertEquals("hci1", BluetoothAdapterDetector.select(adapters)?.name)
}

@Test fun `nmap XML extracts IPv4 MAC vendor and hostname`() {
    val devices = NmapXmlParser.parse(nmapFixture).getOrThrow()
    assertEquals(NetworkDevice("AA:BB:CC:11:22:33", "192.168.1.8", "Intel", "deck"), devices.single())
}
```

- [ ] **Step 2: Verify parser tests fail**

Run: `./gradlew testDebugUnitTest --tests '*BluelogParserTest' --tests '*BluetoothAdapterDetectorTest' --tests '*NmapXmlParserTest'`

Expected: compilation fails because parser classes do not exist.

- [ ] **Step 3: Implement defensive parsers**

Keep regexes anchored to MAC and known labels rather than the entire bluelog line. Map the CoD major bits with `(classOfDevice shr 8) and 0x1F`. Parse nmap through `XmlPullParser`, finalize a host only when it has both IPv4 and MAC values, ignore IPv6-only hosts, and return `Result.failure` on structurally malformed XML. Adapter names must match `hci\d+` before selection.

- [ ] **Step 4: Run parser tests**

Run: `./gradlew testDebugUnitTest --tests '*BluelogParserTest' --tests '*BluetoothAdapterDetectorTest' --tests '*NmapXmlParserTest'`

Expected: all parser and selection tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyberscan/app/data app/src/test/java/com/cyberscan/app/data
git commit -m "feat: harden scan parsers and adapter detection"
```

### Task 4: Root Process Runtime and Repositories

**Files:**
- Create: `app/src/main/java/com/cyberscan/app/core/shell/ShellExecutor.kt`
- Create: `app/src/main/java/com/cyberscan/app/core/shell/LoopingShellProcess.kt`
- Create: `app/src/main/java/com/cyberscan/app/core/shell/AppProcessRegistry.kt`
- Create: `app/src/main/java/com/cyberscan/app/data/bluetooth/BluetoothRepository.kt`
- Create: `app/src/main/java/com/cyberscan/app/data/network/NetworkRepository.kt`
- Test: `app/src/test/java/com/cyberscan/app/core/shell/AppProcessRegistryTest.kt`
- Test: `app/src/test/java/com/cyberscan/app/data/bluetooth/BluetoothRepositoryTest.kt`
- Test: `app/src/test/java/com/cyberscan/app/data/network/NetworkRepositoryTest.kt`

**Interfaces:**
- Produces: `CommandResult(exitCode: Int, stdout: String, stderr: String)`.
- Produces: `ShellExecutor.start(): Boolean`, `run(command: List<String>): CommandResult`, and `shutdown()`.
- Produces: repository flows `devices`, `active`, and `fatalError`, plus `startScan(adapter, scope)` and `stopScan()`.
- Produces: `NetworkRepository.scan(interfaceName: String = "wlan0"): Result<List<NetworkDevice>>`.

- [ ] **Step 1: Write lifecycle and repository tests with fakes**

```kotlin
@Test fun `registry killAll is idempotent`() {
    val process = FakeManagedProcess()
    registry.register(process)
    registry.killAll()
    registry.killAll()
    assertEquals(1, process.killCount)
}

@Test fun `duplicate MAC updates last seen without adding a row`() = runTest {
    repository.acceptLine(validLine, nowMs = 100)
    repository.acceptLine(validLine, nowMs = 200)
    assertEquals(1, repository.devices.value.size)
    assertEquals(200, repository.devices.value.single().lastSeenAtMs)
}
```

- [ ] **Step 2: Verify tests fail**

Run: `./gradlew testDebugUnitTest --tests '*AppProcessRegistryTest' --tests '*BluetoothRepositoryTest' --tests '*NetworkRepositoryTest'`

Expected: compilation fails because runtime/repository interfaces do not exist.

- [ ] **Step 3: Implement process ownership**

Use a `Mutex` to serialize persistent-shell commands. Quote only validated fixed arguments with a private shell-word encoder; never concatenate arbitrary UI text. A one-shot command writes the encoded command followed by unique stdout/stderr sentinel markers. `LoopingShellProcess` launches `su -c` with an internal fixed command and reports non-requested exit through a callback. All kill/close operations are idempotent.

- [ ] **Step 4: Implement repositories**

`BluetoothRepository` executes `bluelog -i <validatedAdapter> -a -v`, feeds stdout to `BluelogParser`, and stores devices in a MAC-keyed insertion-ordered map. `NetworkRepository` runs `ip -o -4 addr show dev wlan0`, derives CIDR through `Ipv4Subnet`, then runs `nmap -sn -oX - <validatedCidr>` and calls `NmapXmlParser`.

- [ ] **Step 5: Run repository tests**

Run: `./gradlew testDebugUnitTest --tests '*AppProcessRegistryTest' --tests '*BluetoothRepositoryTest' --tests '*NetworkRepositoryTest'`

Expected: all focused tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cyberscan/app/core/shell app/src/main/java/com/cyberscan/app/data app/src/test
git commit -m "feat: add rooted scan runtime and repositories"
```

### Task 5: EMF Sensor, Scan Controller, and Foreground Service

**Files:**
- Create: `app/src/main/java/com/cyberscan/app/core/sensors/EmfSensorManager.kt`
- Create: `app/src/main/java/com/cyberscan/app/core/di/AppModule.kt`
- Create: `app/src/main/java/com/cyberscan/app/service/ScanSessionController.kt`
- Create: `app/src/main/java/com/cyberscan/app/service/ScanForegroundService.kt`
- Test: `app/src/test/java/com/cyberscan/app/core/sensors/EmfFilterTest.kt`
- Test: `app/src/test/java/com/cyberscan/app/service/ScanSessionControllerTest.kt`

**Interfaces:**
- Produces: `EmfSensorManager.readings: StateFlow<EmfReading?>`, `start()`, and `stop()`.
- Produces: `ScanSessionController.state: StateFlow<ScanUiState>`, `start()`, `stop()`, `retry()`, and `selectTarget(mac)`.
- Service actions: `ACTION_START`, `ACTION_STOP`, and notification ID/channel constants.

- [ ] **Step 1: Extract and test pure EMF filtering**

```kotlin
@Test fun `baseline average and EMA yield stable anomaly`() {
    val filter = EmfFilter(calibrationDurationMs = 2_000, alpha = 0.2f)
    repeat(20) { filter.accept(magnitude = 50f, timestampMs = it * 100L) }
    val reading = filter.accept(magnitude = 60f, timestampMs = 2_100)!!
    assertEquals(50f, reading.baselineMicroTesla, 0.01f)
    assertTrue(reading.anomalyMicroTesla in 1.9f..2.1f)
}
```

- [ ] **Step 2: Test orchestration failure boundaries**

Use fake shell, adapter detector, Bluetooth repository, network repository, and EMF source. Assert root denial and missing adapter become `Failed`; nmap failure becomes `NetworkStatus.Unavailable` while phase remains `Scanning`; duplicate start launches bluelog once; stop kills processes and yields `Complete`.

- [ ] **Step 3: Verify tests fail**

Run: `./gradlew testDebugUnitTest --tests '*EmfFilterTest' --tests '*ScanSessionControllerTest'`

Expected: compilation fails because filter and controller do not exist.

- [ ] **Step 4: Implement sensor and controller**

Keep `EmfFilter` pure. The Android manager registers `TYPE_MAGNETIC_FIELD`, computes vector magnitude, calibrates for two seconds, applies EMA, and publishes no faster than every 83 ms. The controller runs the root gate before adapter detection, starts Bluetooth and EMF together, launches the network sweep independently, and reduces every event through `ScanReducer`.

- [ ] **Step 5: Implement the Android 16 service boundary**

Create the notification channel in `onCreate()`. On `ACTION_START`, call `startForeground()` immediately with type `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`, then invoke the idempotent controller start. On stop/task removal/destruction, stop the controller, kill registered processes, stop foreground state, and shut down the shell.

- [ ] **Step 6: Run focused tests and compile**

Run: `./gradlew testDebugUnitTest --tests '*EmfFilterTest' --tests '*ScanSessionControllerTest' assembleDebug`

Expected: tests pass and service/sensor Android code compiles.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/cyberscan/app/core app/src/main/java/com/cyberscan/app/service app/src/test
git commit -m "feat: orchestrate foreground scan sessions"
```

### Task 6: ViewModel, Permissions, and Portrait HUD

**Files:**
- Create: `app/src/main/java/com/cyberscan/app/ui/viewmodel/ScanViewModel.kt`
- Create: `app/src/main/java/com/cyberscan/app/ui/ScanPermissions.kt`
- Create: `app/src/main/java/com/cyberscan/app/ui/MainActivity.kt`
- Create: `app/src/main/java/com/cyberscan/app/ui/theme/Color.kt`
- Create: `app/src/main/java/com/cyberscan/app/ui/theme/Type.kt`
- Create: `app/src/main/java/com/cyberscan/app/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/cyberscan/app/ui/hud/CornerBracketPanel.kt`
- Create: `app/src/main/java/com/cyberscan/app/ui/hud/StatusMeter.kt`
- Create: `app/src/main/java/com/cyberscan/app/ui/hud/DeviceList.kt`
- Create: `app/src/main/java/com/cyberscan/app/ui/hud/TargetDataPanel.kt`
- Create: `app/src/main/java/com/cyberscan/app/ui/hud/ActionBar.kt`
- Create: `app/src/main/java/com/cyberscan/app/ui/hud/ScanScreen.kt`
- Test: `app/src/test/java/com/cyberscan/app/ui/viewmodel/ScanViewModelTest.kt`

**Interfaces:**
- Consumes: `ScanSessionController` from Task 5.
- Produces: `ScanViewModel.uiState`, `onStartGranted()`, `onStop()`, `onRetryGranted()`, and `onTargetSelected(mac)`.
- Produces: `ScanScreen(state, onStart, onStop, onRetry, onTargetSelected)`.

- [ ] **Step 1: Write ViewModel action tests**

```kotlin
@Test fun `start delegates only after permission grant`() = runTest {
    viewModel.onStartGranted()
    assertEquals(1, controller.startCount)
}

@Test fun `selection delegates normalized MAC`() {
    viewModel.onTargetSelected("aa:bb:cc:11:22:33")
    assertEquals("AA:BB:CC:11:22:33", controller.selectedMac)
}
```

- [ ] **Step 2: Verify ViewModel tests fail**

Run: `./gradlew testDebugUnitTest --tests '*ScanViewModelTest'`

Expected: compilation fails because the ViewModel does not exist.

- [ ] **Step 3: Implement permission and Activity flow**

Request `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, and, on API 33+, `POST_NOTIFICATIONS` only when start/retry is tapped. Permission success dispatches `ACTION_START`; denial keeps the session idle and shows concise UI feedback. Hilt provides the controller to the ViewModel and the Activity hosts the theme and screen.

- [ ] **Step 4: Implement the portrait HUD**

Use a near-black background, cyan telemetry, red-orange hard-failure accents, sharp corner brackets drawn with Compose Canvas, a segmented EMF meter, live device list, selected target panel, and bottom action hints. Confidence always includes `NONE`, `MAYBE`, or `HIGH` text in addition to color/stars. Respect `LocalAccessibilityManager` and system animator scale by avoiding required looping motion.

- [ ] **Step 5: Run ViewModel tests and assemble**

Run: `./gradlew testDebugUnitTest --tests '*ScanViewModelTest' assembleDebug`

Expected: ViewModel tests pass and the complete HUD builds.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cyberscan/app/ui app/src/test/java/com/cyberscan/app/ui
git commit -m "feat: build portrait CyberScan HUD"
```

### Task 7: Full Verification and Hardware Runbook

**Files:**
- Create: `README.md`
- Create: `docs/hardware-acceptance.md`
- Modify: any production or test file implicated by verification failures.

**Interfaces:**
- Consumes: complete app from Tasks 1–6.
- Produces: reproducible local build instructions, target prerequisites, install command, and hardware acceptance checklist.

- [ ] **Step 1: Run the complete unit suite**

Run: `./gradlew testDebugUnitTest`

Expected: all parser, adapter, correlation, subnet, reducer, repository, filter, controller, and ViewModel tests pass.

- [ ] **Step 2: Run static checks and build the APK**

Run: `./gradlew lintDebug assembleDebug`

Expected: lint succeeds and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Inspect the merged manifest**

Run: `./gradlew processDebugMainManifest`

Verify the merged manifest contains API 36 targeting, the required Bluetooth/location/notification/foreground permissions, `connectedDevice`, exported launcher Activity, non-exported Service, and portrait orientation.

- [ ] **Step 4: Write the runbook**

Document Android 16/LineageOS 23.2, root grant, installed `su`, `bluelog`, `nmap`, an available magnetometer, installation with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, permission prompts, notification behavior, adapter diagnostic commands, and the exact stop/task-removal cleanup checks.

- [ ] **Step 5: Perform connected-device smoke checks when ADB is available**

Run: `adb devices`, install the APK, launch `com.cyberscan.app/.ui.MainActivity`, grant permissions interactively, confirm the foreground notification, verify live devices/EMF/network states, stop scanning, and swipe the task away. Then run `adb shell su -c 'pgrep -a bluelog'` and expect no CyberScan-owned bluelog process.

- [ ] **Step 6: Commit verification documentation and fixes**

```bash
git add README.md docs app
git commit -m "docs: add CyberScan build and hardware verification"
```
