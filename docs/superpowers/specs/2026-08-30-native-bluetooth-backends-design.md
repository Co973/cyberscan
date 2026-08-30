# Native Bluetooth and Optional External HCI Design

## Status

Approved in conversation on 2026-08-30.

## Problem

CyberScan currently assumes every supported device exposes a BlueZ `hci*` interface and a Kali chroot at `/data/local/nhsystem/kali-armhf`. The target phone instead exposes its internal Bluetooth chipset through Android's Bluetooth HAL. Kernel HCI driver modules are present, but `/sys/class/bluetooth` contains no registered BlueZ interface. Consequently, `hciconfig` and `bluelog` cannot access the internal chipset, and the scan fails before collecting any Bluetooth results.

The existing Bluelog implementation remains useful for users who attach a NetHunter-compatible external adapter. It must remain available without being a prerequisite for the internal chipset.

## Goals

- Always use Android's internal Bluetooth chipset through native BLE and Classic discovery APIs.
- Automatically add the existing Bluelog backend when an active external `hci*` adapter and executable `bluelog` environment are detected.
- Merge native and external observations into one deduplicated device stream keyed by normalized MAC address.
- Allow native Bluetooth and EMF scanning to work without root, a Kali chroot, `hciconfig`, or `bluelog`.
- Continue using the device's installed `nmap` for optional network correlation after resolving its actual execution environment.
- Preserve the existing single app module, Compose/Hilt/MVVM shape, foreground-service lifecycle, hardened Nmap XML parser, and focused pure unit tests.

## Non-goals

- Detaching the internal chipset from Android's Bluetooth HAL or binding it to BlueZ.
- Requiring an external Bluetooth adapter.
- Port scanning. Network discovery remains `nmap -sn -oX -` only.
- Persisting scan history across app launches.
- Adding a user-facing backend selector. Backend selection is automatic.

## Architecture

### Backend composition

`ScanSessionController` will no longer ask an HCI gateway for a mandatory adapter before starting. It will start a `CompositeBluetoothScanner`, which owns two independent sources:

1. `AndroidBluetoothScanner` is mandatory and starts first. It combines BLE scan callbacks with Classic discovery broadcasts from the platform `BluetoothAdapter`.
2. `BluelogBluetoothScanner` is the existing root/BlueZ implementation. A capability probe starts it only when an active external `hci*` adapter and a working `bluelog` command are both available.

The external source is additive. Native scanning remains active even when external HCI scanning is available. A failure in the optional external source is a warning/soft degradation and never terminates native collection.

Platform APIs will be hidden behind small interfaces so the orchestrator and merge behavior can be tested on the JVM without mocking Android framework classes.

### Native BLE and Classic scanning

The native scanner will use the application-scoped `BluetoothManager` and its default `BluetoothAdapter`.

- BLE: `BluetoothLeScanner.startScan()` supplies address, advertised name, RSSI, and timestamps.
- Classic: `BluetoothAdapter.startDiscovery()` plus an application-context receiver for `BluetoothDevice.ACTION_FOUND` supplies address, device name, Bluetooth class, and RSSI.
- When one Classic discovery cycle ends during an active session, the scanner schedules the next cycle rather than overlapping discovery requests.
- Stop unregisters the receiver, cancels Classic discovery, and stops the exact BLE callback instance.
- A missing adapter, disabled Bluetooth, or lost runtime permission produces a clear native-backend failure. The UI will distinguish these from optional external/Nmap warnings.

`BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` remain runtime requirements. Location remains requested because physical proximity is an intentional part of the product.

### Observation mapping and deduplication

Each backend emits a backend-neutral `BluetoothObservation` containing source, normalized MAC, optional name, optional class, optional RSSI, and observation time. A pure `BluetoothDeviceAccumulator` merges observations by MAC using deterministic rules:

- earliest `firstSeenAtMs` and latest `lastSeenAtMs` are retained;
- the newest nonblank name replaces a missing or stale name;
- the latest non-null RSSI is retained;
- a known class replaces `UNKNOWN`;
- an observation never erases previously known non-null data.

The accumulator publishes the existing `BluetoothDevice` domain type, so correlation, reducer, ViewModel, and most UI code remain unchanged. The device row may display a compact source marker (`NATIVE`, `HCI`, or `NATIVE+HCI`) without splitting one physical address into multiple rows.

### External HCI capability probe

The existing hardened adapter parser and Bluelog line parser remain in production code. A capability resolver will:

1. obtain root if available;
2. find an execution environment containing `hciconfig` and `bluelog`;
3. run `hciconfig -a` there;
4. select the first numeric `hci*` interface whose status is both `UP` and `RUNNING`;
5. start the existing directly killable Bluelog process for that adapter.

No active interface, no root, no chroot, or no Bluelog executable simply disables this optional backend for the session. Process registration and cleanup guarantees remain unchanged.

### Command environment and Nmap

The hard-coded Kali path will be replaced with a `CommandEnvironmentResolver`. It will probe execution environments rather than assuming one layout:

- the rooted Android shell and its current `PATH`;
- existing common NetHunter roots under `/data/local/nhsystem`, including architecture-specific and `kalifs` layouts;
- explicit executable paths returned by `command -v` inside a candidate environment.

The resolver returns an immutable command prefix/environment for each capability. Bluelog and Nmap may resolve to different environments. Command arguments remain shell-escaped by the existing hardened command builder.

The network repository continues to compute the actual `wlan0` IPv4 network and run `nmap -sn -oX - <cidr>`. If Nmap cannot be resolved or the network sweep fails, Bluetooth and EMF remain active, `NMAP // UNAVAILABLE` is shown, and confidence is not promoted by network correlation.

### Session state and error handling

The scan startup sequence becomes:

1. validate Android Bluetooth permission and adapter state;
2. start EMF calibration and native BLE/Classic collection;
3. enter `SCANNING` as soon as the native backend starts;
4. probe root capabilities in parallel;
5. attach external HCI scanning when available;
6. run the optional Nmap sweep when its environment and `wlan0` are available.

Hard failures are limited to conditions that prevent the baseline session: no Android Bluetooth adapter, Bluetooth disabled, revoked required permission, or native scanner startup failure. Root denial, missing chroot, missing HCI, Bluelog termination, missing Nmap, and network sweep failure are soft capability failures.

The UI adapter label will show `ANDROID HAL` for native-only sessions and `ANDROID HAL + hciN` when the optional external backend joins.

### Lifecycle and concurrency

The foreground service continues to own the session. Native callbacks, broadcast registration, root child processes, and EMF listeners must all be idempotently stopped by `ABORT`, task removal, service destruction, or application termination.

One session-scoped supervisor owns backend collectors. A failure in the external or network child must not cancel native Bluetooth. Starting while already active is a no-op; retry first stops all current sources and creates a fresh accumulator/session.

## Dependency injection changes

Hilt will provide:

- an Android Bluetooth platform facade backed by `BluetoothManager`;
- `AndroidBluetoothScanner`;
- the preserved Bluelog scanner;
- `BluetoothDeviceAccumulator`/composite scanner;
- `CommandEnvironmentResolver` shared by optional root capabilities.

The controller will depend on the composite gateway rather than directly requiring `BluetoothAdapterGateway` before scan startup.

## Testing

Focused JVM tests will cover:

- mapping BLE and Classic observations into normalized domain observations;
- deterministic cross-source merge and deduplication;
- native-only startup when root/HCI is unavailable;
- additive external startup when both HCI and Bluelog capabilities resolve;
- external-process failure leaving native scanning active;
- disabled/missing Android Bluetooth producing a hard failure;
- retry and stop idempotently cleaning both native and external sources;
- command-environment selection for Android-shell and common NetHunter layouts;
- Nmap remaining a soft dependency and retaining XML parsing;
- reducer/state transitions and adapter-label updates.

Existing parser, subnet, correlation, reducer, shell, service, ViewModel, and permission tests remain. The full unit suite, lint, and debug assembly must pass before publishing an APK.

## Hardware acceptance

On the target phone, acceptance requires:

- native scanning finds at least one BLE advertisement and one discoverable Classic device when available;
- the session starts while `/sys/class/bluetooth` is empty and no Kali chroot is present at the old hard-coded path;
- installed Nmap is resolved and its XML sweep populates or cleanly degrades network correlation;
- attaching a compatible external adapter later adds an `hci*` backend automatically without disabling native results;
- stopping or removing the task leaves no registered receiver, BLE callback, discovery cycle, Bluelog process, or Nmap process owned by CyberScan.

