# Native Bluetooth Backends Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android BLE + Classic discovery the always-on Bluetooth baseline while automatically adding and merging the existing Bluelog backend when a compatible external HCI adapter is available.

**Architecture:** Introduce backend-neutral observations and a deterministic accumulator, wrap Android Bluetooth callbacks behind a focused native scanner, and compose native plus optional Bluelog sources into the existing `BluetoothScanGateway`. Refactor session startup so native scanning does not depend on root, while an execution-environment resolver supplies optional Bluelog and Nmap capabilities.

**Tech Stack:** Kotlin 2.4.10, Android API 31-36 Bluetooth framework, coroutines/StateFlow, Hilt 2.60.1, Jetpack Compose, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-30-native-bluetooth-backends-design.md`

## Global Constraints

- Keep one Android app module and the existing Compose/Hilt/MVVM structure.
- Android BLE + Classic scanning always starts first and is the baseline.
- External Bluelog scanning auto-starts additively only when root, tools, and an active `hci*` adapter resolve.
- Merge observations by normalized MAC; never erase known non-null device data.
- Missing root, chroot, external HCI, Bluelog, Nmap, or `wlan0` is a soft capability failure.
- Keep `nmap -sn -oX -`; never parse human-readable Nmap output.
- Stop callbacks, receivers, discovery, sensors, and child processes on abort, task removal, service destruction, and retry.
- Preserve API 36 targeting and the existing permissions model.

---

### Task 1: Backend-neutral observations and deterministic accumulation

**Files:**
- Create: `app/src/main/java/com/cyberscan/app/data/bluetooth/BluetoothObservation.kt`
- Create: `app/src/main/java/com/cyberscan/app/data/bluetooth/BluetoothDeviceAccumulator.kt`
- Test: `app/src/test/java/com/cyberscan/app/data/bluetooth/BluetoothDeviceAccumulatorTest.kt`

**Interfaces:**
- Produces: `enum class BluetoothSource { NATIVE_BLE, NATIVE_CLASSIC, EXTERNAL_HCI }`
- Produces: `data class BluetoothObservation(val source: BluetoothSource, val macAddress: String, val name: String?, val deviceClass: DeviceClass, val rssi: Int?, val observedAtMs: Long)`
- Produces: `class BluetoothDeviceAccumulator { val devices: StateFlow<List<BluetoothDevice>>; fun clear(); fun accept(observation: BluetoothObservation) }`
- Consumes: existing `BluetoothDevice`, `DeviceClass`, and `normalizeMac`.

- [ ] **Step 1: Write failing accumulator tests**

```kotlin
@Test
fun `observations from native and HCI deduplicate by normalized MAC`() {
    val accumulator = BluetoothDeviceAccumulator()
    accumulator.accept(observation(source = NATIVE_BLE, mac = "aa-bb-cc-dd-ee-ff", rssi = -70, at = 10))
    accumulator.accept(observation(source = EXTERNAL_HCI, mac = "AA:BB:CC:DD:EE:FF", name = "Beacon", at = 20))
    val device = accumulator.devices.value.single()
    assertEquals("AA:BB:CC:DD:EE:FF", device.macAddress)
    assertEquals("Beacon", device.name)
    assertEquals(-70, device.rssi)
    assertEquals(10, device.firstSeenAtMs)
    assertEquals(20, device.lastSeenAtMs)
}

@Test
fun `null observations never erase known values`() {
    val accumulator = BluetoothDeviceAccumulator()
    accumulator.accept(observation(name = "Laptop", deviceClass = DeviceClass.COMPUTER, rssi = -41, at = 10))
    accumulator.accept(observation(name = null, deviceClass = DeviceClass.UNKNOWN, rssi = null, at = 20))
    assertEquals("Laptop", accumulator.devices.value.single().name)
    assertEquals(DeviceClass.COMPUTER, accumulator.devices.value.single().deviceClass)
    assertEquals(-41, accumulator.devices.value.single().rssi)
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*BluetoothDeviceAccumulatorTest'`

Expected: compilation fails because the observation and accumulator types do not exist.

- [ ] **Step 3: Implement the observation and accumulator**

```kotlin
enum class BluetoothSource { NATIVE_BLE, NATIVE_CLASSIC, EXTERNAL_HCI }

data class BluetoothObservation(
    val source: BluetoothSource,
    val macAddress: String,
    val name: String?,
    val deviceClass: DeviceClass,
    val rssi: Int?,
    val observedAtMs: Long,
)

class BluetoothDeviceAccumulator {
    private val entries = linkedMapOf<String, BluetoothDevice>()
    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    @Synchronized fun accept(observation: BluetoothObservation) {
        val mac = normalizeMac(observation.macAddress)
        val current = entries[mac]
        entries[mac] = BluetoothDevice(
            macAddress = mac,
            name = observation.name ?: current?.name,
            deviceClass = observation.deviceClass.takeUnless { it == DeviceClass.UNKNOWN }
                ?: current?.deviceClass ?: DeviceClass.UNKNOWN,
            rssi = observation.rssi ?: current?.rssi,
            firstSeenAtMs = minOf(current?.firstSeenAtMs ?: observation.observedAtMs, observation.observedAtMs),
            lastSeenAtMs = maxOf(current?.lastSeenAtMs ?: observation.observedAtMs, observation.observedAtMs),
        )
        _devices.value = entries.values.toList()
    }

    @Synchronized fun clear() { entries.clear(); _devices.value = emptyList() }
}
```

- [ ] **Step 4: Run focused tests and the existing Bluelog parser/repository tests**

Run: `./gradlew testDebugUnitTest --tests '*BluetoothDeviceAccumulatorTest' --tests '*BluelogParserTest' --tests '*BluetoothRepositoryTest'`

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyberscan/app/data/bluetooth app/src/test/java/com/cyberscan/app/data/bluetooth
git commit -m "feat: merge Bluetooth observations across backends"
```

---

### Task 2: Native Android BLE and Classic scanner

**Files:**
- Create: `app/src/main/java/com/cyberscan/app/data/bluetooth/NativeBluetoothPlatform.kt`
- Create: `app/src/main/java/com/cyberscan/app/data/bluetooth/AndroidNativeBluetoothPlatform.kt`
- Create: `app/src/main/java/com/cyberscan/app/data/bluetooth/NativeBluetoothScanner.kt`
- Test: `app/src/test/java/com/cyberscan/app/data/bluetooth/NativeBluetoothScannerTest.kt`
- Test: `app/src/test/java/com/cyberscan/app/data/bluetooth/NativeBluetoothMappingTest.kt`

**Interfaces:**
- Produces: `sealed interface NativeBluetoothEvent { data class DeviceFound(...); data object ClassicCycleFinished; data class Failure(val reason: String) }`
- Produces: `interface NativeBluetoothPlatform { val available: Boolean; val enabled: Boolean; fun start(onEvent: (NativeBluetoothEvent) -> Unit): Result<Unit>; fun stop() }`
- Produces: `class NativeBluetoothScanner(platform, accumulator, clock) { val failure: StateFlow<String?>; fun start(): Result<Unit>; fun stop() }`
- Consumes: `BluetoothObservation`, `BluetoothDeviceAccumulator` from Task 1.

- [ ] **Step 1: Write failing mapping and lifecycle tests**

```kotlin
@Test
fun `BLE event maps to a normalized native observation`() {
    val observation = NativeBluetoothMapper.map(
        NativeBluetoothEvent.DeviceFound("aa-bb-cc-dd-ee-ff", "Tag", null, -62, NativeTransport.BLE),
        observedAtMs = 100,
    )
    assertEquals(BluetoothSource.NATIVE_BLE, observation.source)
    assertEquals("AA:BB:CC:DD:EE:FF", observation.macAddress)
}

@Test
fun `scanner reports disabled adapter without registering callbacks`() {
    val platform = FakeNativePlatform(available = true, enabled = false)
    val result = NativeBluetoothScanner(platform, BluetoothDeviceAccumulator()).start()
    assertTrue(result.isFailure)
    assertEquals(0, platform.startCount)
}

@Test
fun `stop delegates exactly once after start`() {
    val platform = FakeNativePlatform()
    val scanner = NativeBluetoothScanner(platform, BluetoothDeviceAccumulator())
    scanner.start(); scanner.stop(); scanner.stop()
    assertEquals(1, platform.stopCount)
}
```

- [ ] **Step 2: Run focused tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*NativeBluetooth*Test'`

Expected: compilation fails because native platform/scanner types do not exist.

- [ ] **Step 3: Implement the pure mapper and scanner state machine**

Implement `NativeBluetoothMapper.map(event, observedAtMs)` so BLE maps to `NATIVE_BLE`, Classic maps to `NATIVE_CLASSIC`, MAC addresses normalize, and Android major device classes map into the existing domain enum. Implement idempotent `start`/`stop`, adapter availability checks, event accumulation, and failure publication.

- [ ] **Step 4: Implement the Android platform adapter**

`AndroidNativeBluetoothPlatform` must:

```kotlin
override fun start(onEvent: (NativeBluetoothEvent) -> Unit): Result<Unit> = runCatching {
    receiver = discoveryReceiver(onEvent)
    context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
    bleCallback = scanCallback(onEvent)
    adapter.bluetoothLeScanner.startScan(bleCallback)
    check(adapter.startDiscovery()) { "Classic Bluetooth discovery could not start" }
}

override fun stop() {
    bleCallback?.let { adapter.bluetoothLeScanner?.stopScan(it) }
    if (adapter.isDiscovering) adapter.cancelDiscovery()
    receiver?.let { runCatching { context.unregisterReceiver(it) } }
    bleCallback = null
    receiver = null
}
```

Use `ACTION_FOUND` for Classic devices, `ACTION_DISCOVERY_FINISHED` to begin the next cycle only while active, and both BLE scan failure callbacks. Guard framework calls with the existing runtime permissions and return descriptive failures.

- [ ] **Step 5: Run native scanner tests and compile the app**

Run: `./gradlew testDebugUnitTest --tests '*NativeBluetooth*Test' assembleDebug`

Expected: focused tests and Android compilation pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cyberscan/app/data/bluetooth app/src/test/java/com/cyberscan/app/data/bluetooth
git commit -m "feat: scan internal Bluetooth through Android APIs"
```

---

### Task 3: Preserve Bluelog and compose it additively

**Files:**
- Modify: `app/src/main/java/com/cyberscan/app/data/bluetooth/BluetoothRepository.kt`
- Create: `app/src/main/java/com/cyberscan/app/data/bluetooth/CompositeBluetoothScanner.kt`
- Modify: `app/src/main/java/com/cyberscan/app/service/ScanGateways.kt`
- Test: `app/src/test/java/com/cyberscan/app/data/bluetooth/CompositeBluetoothScannerTest.kt`
- Modify: `app/src/test/java/com/cyberscan/app/data/bluetooth/BluetoothRepositoryTest.kt`

**Interfaces:**
- Changes `BluetoothScanGateway` to `fun startScan(scope: CoroutineScope): Result<Unit>` with `adapterLabel: StateFlow<String>` and `warning: StateFlow<String?>`.
- Produces: `interface OptionalHciBackend { suspend fun detectAndStart(scope: CoroutineScope, onObservation: (BluetoothObservation) -> Unit): HciAdapter?; fun stop() }`
- Produces: `CompositeBluetoothScanner(native: NativeBluetoothScanner, external: OptionalHciBackend, accumulator: BluetoothDeviceAccumulator)` implementing `BluetoothScanGateway`.

- [ ] **Step 1: Write failing composition tests**

```kotlin
@Test
fun `native starts when external adapter is unavailable`() = runTest {
    val scanner = fixture(externalAdapter = null)
    assertTrue(scanner.startScan(backgroundScope).isSuccess)
    runCurrent()
    assertEquals(1, scanner.native.startCount)
    assertEquals("ANDROID HAL", scanner.subject.adapterLabel.value)
}

@Test
fun `external adapter joins native and merged MAC remains one row`() = runTest {
    val scanner = fixture(externalAdapter = HciAdapter("hci2", true, true))
    scanner.subject.startScan(backgroundScope); runCurrent()
    scanner.native.emit(observation(source = NATIVE_BLE, mac = TEST_MAC, rssi = -60))
    scanner.external.emit(observation(source = EXTERNAL_HCI, mac = TEST_MAC, name = "Probe"))
    assertEquals(1, scanner.subject.devices.value.size)
    assertEquals("ANDROID HAL + hci2", scanner.subject.adapterLabel.value)
}

@Test
fun `external failure is a warning and native remains active`() = runTest {
    val scanner = fixture(externalFailure = "bluelog exited")
    scanner.subject.startScan(backgroundScope); runCurrent()
    assertTrue(scanner.native.active)
    assertEquals("bluelog exited", scanner.subject.warning.value)
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*CompositeBluetoothScannerTest'`

Expected: compilation fails because the composite interface does not exist.

- [ ] **Step 3: Adapt the existing Bluelog repository without deleting it**

Rename the concrete role to `BluelogBluetoothScanner` while preserving `BluelogParser`, active HCI validation, directly killable process behavior, and its unit tests. Convert parsed lines into `BluetoothObservation(source = EXTERNAL_HCI, ...)` and send them to the shared accumulator callback. Unexpected process death publishes an optional-backend warning instead of a session-fatal error.

- [ ] **Step 4: Implement the composite scanner**

Start native synchronously and return its result. On success, launch external capability detection in the provided supervisor scope. Publish `ANDROID HAL` immediately and append ` + hciN` only after external startup succeeds. `stopScan` must stop native and external backends and be idempotent.

- [ ] **Step 5: Run composition, repository, and accumulator tests**

Run: `./gradlew testDebugUnitTest --tests '*CompositeBluetoothScannerTest' --tests '*BluetoothRepositoryTest' --tests '*BluetoothDeviceAccumulatorTest'`

Expected: all selected tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cyberscan/app/data/bluetooth app/src/main/java/com/cyberscan/app/service/ScanGateways.kt app/src/test/java/com/cyberscan/app/data/bluetooth
git commit -m "feat: compose native and external Bluetooth scans"
```

---

### Task 4: Resolve root command environments for optional HCI and Nmap

**Files:**
- Create: `app/src/main/java/com/cyberscan/app/core/shell/CommandEnvironment.kt`
- Create: `app/src/main/java/com/cyberscan/app/core/shell/CommandEnvironmentResolver.kt`
- Modify: `app/src/main/java/com/cyberscan/app/core/shell/ShellExecutor.kt`
- Modify: `app/src/main/java/com/cyberscan/app/core/shell/LoopingShellProcess.kt`
- Modify: `app/src/main/java/com/cyberscan/app/service/RootBluetoothAdapterGateway.kt`
- Modify: `app/src/main/java/com/cyberscan/app/data/network/NetworkRepository.kt`
- Test: `app/src/test/java/com/cyberscan/app/core/shell/CommandEnvironmentResolverTest.kt`
- Modify: `app/src/test/java/com/cyberscan/app/data/network/NetworkRepositoryTest.kt`

**Interfaces:**
- Produces: `sealed interface CommandEnvironment { data object AndroidRoot; data class Chroot(val root: String) }`
- Produces: `suspend fun resolve(requiredCommands: Set<String>): CommandEnvironment?`
- Changes command execution to accept an explicit environment: `suspend fun run(command: List<String>, environment: CommandEnvironment = AndroidRoot): CommandResult`.

- [ ] **Step 1: Write failing resolver tests**

```kotlin
@Test
fun `Android root wins when all commands exist there`() = runTest {
    val executor = FakeProbeExecutor(androidCommands = setOf("ip", "nmap"))
    assertEquals(AndroidRoot, CommandEnvironmentResolver(executor).resolve(setOf("ip", "nmap")))
}

@Test
fun `resolver selects existing NetHunter kalifs`() = runTest {
    val executor = FakeProbeExecutor(
        roots = setOf("/data/local/nhsystem/kalifs"),
        chrootCommands = mapOf("/data/local/nhsystem/kalifs" to setOf("hciconfig", "bluelog")),
    )
    assertEquals(
        Chroot("/data/local/nhsystem/kalifs"),
        CommandEnvironmentResolver(executor).resolve(setOf("hciconfig", "bluelog")),
    )
}

@Test
fun `missing capability resolves to null`() = runTest {
    assertNull(CommandEnvironmentResolver(FakeProbeExecutor()).resolve(setOf("nmap")))
}
```

- [ ] **Step 2: Run resolver tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*CommandEnvironmentResolverTest'`

Expected: compilation fails because command environments do not exist.

- [ ] **Step 3: Implement explicit environments and safe probing**

Probe Android root with `command -v -- <name>` for validated command names. Check only fixed candidate roots:

```kotlin
private val candidates = listOf(
    "/data/local/nhsystem/kalifs",
    "/data/local/nhsystem/kali-arm64",
    "/data/local/nhsystem/kali-armhf",
)
```

Confirm a candidate's `/bin/bash` exists before probing commands inside it. Do not use unrestricted filesystem searches from the app. Cache successful resolutions per required-command set for the current session and clear the cache on retry.

- [ ] **Step 4: Make shell and loop execution environment-explicit**

Remove the constructor-level hard-coded `useChroot` behavior. Render `AndroidRoot` as the escaped payload itself and `Chroot(root)` as `chroot <root> /bin/bash -lc <payload>`. Keep sentinel delimiting, argument escaping, process registration, and direct loop termination unchanged.

- [ ] **Step 5: Resolve Nmap and HCI independently**

The network repository resolves `{ip, nmap}` before subnet discovery and returns `Result.failure` if unavailable. The external HCI gateway resolves `{hciconfig, bluelog}` and returns no adapter if unavailable. Both use their resolved environment for every related command.

- [ ] **Step 6: Run shell, resolver, adapter, and network tests**

Run: `./gradlew testDebugUnitTest --tests '*CommandEnvironment*Test' --tests '*NetworkRepositoryTest' --tests '*BluetoothAdapterDetectorTest' --tests '*AppProcessRegistryTest'`

Expected: all selected tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/cyberscan/app/core/shell app/src/main/java/com/cyberscan/app/data/network app/src/main/java/com/cyberscan/app/service/RootBluetoothAdapterGateway.kt app/src/test
git commit -m "feat: resolve optional NetHunter command environments"
```

---

### Task 5: Refactor session orchestration, DI, and HUD state

**Files:**
- Modify: `app/src/main/java/com/cyberscan/app/service/ScanSessionController.kt`
- Modify: `app/src/main/java/com/cyberscan/app/core/di/AppModule.kt`
- Modify: `app/src/main/java/com/cyberscan/app/domain/model/ScanModels.kt`
- Modify: `app/src/main/java/com/cyberscan/app/domain/state/ScanReducer.kt`
- Modify: `app/src/main/java/com/cyberscan/app/ui/hud/StatusMeter.kt`
- Modify: `app/src/main/java/com/cyberscan/app/ui/hud/ScanScreen.kt`
- Modify: `app/src/test/java/com/cyberscan/app/service/ScanSessionControllerTest.kt`
- Modify: `app/src/test/java/com/cyberscan/app/domain/state/ScanReducerTest.kt`

**Interfaces:**
- Controller starts `BluetoothScanGateway.startScan(scope)` before optional root/network work.
- Adapter label comes from the composite gateway.
- Optional backend warnings remain visible without replacing `ScanPhase.Scanning`.

- [ ] **Step 1: Replace old hard-root tests with failing native-baseline tests**

```kotlin
@Test
fun `root denial does not block native Bluetooth or EMF`() = runTest {
    val fixture = fixture(rootGranted = false, scope = backgroundScope)
    fixture.controller.start(); runCurrent()
    assertEquals(ScanPhase.Scanning, fixture.controller.state.value.phase)
    assertEquals(1, fixture.bluetooth.startCount)
    assertTrue(fixture.emf.started)
    assertEquals(NetworkStatus.Unavailable, fixture.controller.state.value.networkStatus)
}

@Test
fun `native scanner startup failure is a hard failure`() = runTest {
    val fixture = fixture(nativeStart = Result.failure(IllegalStateException("Bluetooth is disabled")), scope = backgroundScope)
    fixture.controller.start(); runCurrent()
    assertEquals(ScanPhase.Failed("Bluetooth is disabled"), fixture.controller.state.value.phase)
}

@Test
fun `external warning leaves phase scanning`() = runTest {
    val fixture = fixture(scope = backgroundScope)
    fixture.controller.start(); fixture.bluetooth.warning.value = "No external HCI backend"; runCurrent()
    assertEquals(ScanPhase.Scanning, fixture.controller.state.value.phase)
}
```

- [ ] **Step 2: Run controller/reducer tests and verify failures**

Run: `./gradlew testDebugUnitTest --tests '*ScanSessionControllerTest' --tests '*ScanReducerTest'`

Expected: old root-first behavior fails the new assertions.

- [ ] **Step 3: Refactor controller startup and failure boundaries**

Start EMF and native Bluetooth first. If native start fails, stop EMF and reduce a hard failure. Otherwise reduce calibration/scanning with `ANDROID HAL`, collect devices/labels/warnings, and launch root/Nmap work in a supervisor child. Root denial or network failure reduces `NetworkUnavailable` only. Retry stops all gateways, clears resolver/session state, and restarts.

- [ ] **Step 4: Rewire Hilt**

Provide `AndroidNativeBluetoothPlatform`, `NativeBluetoothScanner`, preserved `BluelogBluetoothScanner`, shared accumulator, composite scanner, and command resolver as singletons. Bind only the composite as `BluetoothScanGateway`. Remove the mandatory adapter gateway parameter from the controller provider.

- [ ] **Step 5: Update HUD labels and warnings**

Show `ANDROID HAL` immediately, `ANDROID HAL + hciN` when external joins, and a compact soft warning without changing the phase to `FAULT`. Keep target/device panels and action controls unchanged.

- [ ] **Step 6: Run controller, reducer, ViewModel, permission, and service tests**

Run: `./gradlew testDebugUnitTest --tests '*ScanSessionControllerTest' --tests '*ScanReducerTest' --tests '*ScanViewModelTest' --tests '*ScanPermissionsTest'`

Expected: all selected tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: make native Bluetooth the baseline scan path"
```

---

### Task 6: Documentation, full verification, and publishable artifact

**Files:**
- Modify: `README.md`
- Modify: `docs/hardware-acceptance.md`

**Interfaces:**
- Documents native baseline, automatic external augmentation, command capability probing, and exact hardware acceptance steps.

- [ ] **Step 1: Update user and hardware documentation**

Replace statements that root, chroot, or HCI are mandatory for Bluetooth. Document that native BLE + Classic uses the internal chipset, external HCI is auto-added, and Nmap is optional but auto-resolved from Android root or supported NetHunter roots.

- [ ] **Step 2: Run the complete unit suite**

Run: `./gradlew testDebugUnitTest`

Expected: zero failed or errored tests.

- [ ] **Step 3: Run lint and assemble the debug APK**

Run: `./gradlew lintDebug assembleDebug`

Expected: lint exits with zero errors and `app/build/outputs/apk/debug/app-debug.apk` is produced.

- [ ] **Step 4: Inspect the artifact**

Run API 36 `aapt2 dump badging` and `aapt2 dump permissions` against the APK. Confirm package `com.cyberscan.app`, target SDK 36, portrait launcher, Bluetooth/location/notification permissions, and the non-exported connected-device foreground service.

- [ ] **Step 5: Hardware smoke test when the device is connected**

Install with `adb install -r`, grant runtime/root prompts, and verify: native scan starts with empty `/sys/class/bluetooth`; BLE and Classic observations appear; Nmap either correlates or shows unavailable; abort removes callbacks and processes. If an external adapter is later attached, verify the label becomes `ANDROID HAL + hciN` and duplicate MACs stay merged.

- [ ] **Step 6: Commit**

```bash
git add README.md docs/hardware-acceptance.md
git commit -m "docs: document native and external Bluetooth scanning"
```
