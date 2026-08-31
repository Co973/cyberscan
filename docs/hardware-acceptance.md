# Hardware acceptance checklist

Run these checks on the intended Android 16 / LineageOS 23.2 device before accepting the build. They validate the native Bluetooth stack and optional root capabilities that unit tests cannot simulate.

## 1. Native Bluetooth baseline

From an ADB shell:

```sh
cmd bluetooth_manager enable
dumpsys bluetooth_manager
```

Expected: Android reports Bluetooth enabled and an internal adapter. No `/sys/class/bluetooth/hci*`, `hciconfig`, chroot, or external USB adapter is required for this baseline.

## 2. Optional root command environments

```sh
su -c id
su -c 'command -v ip; command -v nmap; command -v hciconfig; command -v bluelog'
su -c 'for root in /data/local/nhsystem/kalifs /data/local/nhsystem/kali-arm64 /data/local/nhsystem/kali-armhf; do test -x "$root/bin/bash" && echo "$root"; done'
```

Expected: `id` reports `uid=0`. `ip` and `nmap` may be in the Android root PATH or any printed chroot. CyberScan probes these environments automatically. Missing commands only disable the corresponding optional feature.

## 3. Optional external HCI adapter

Skip this section when using only the phone's internal chipset. If a BlueZ-compatible external adapter is connected, run `hciconfig -a` in whichever supported environment contains `hciconfig` and `bluelog`.

Expected: at least one interface named `hci` followed by a number has both `UP` and `RUNNING` in its status block. CyberScan sorts active adapters numerically and selects the first one.

With the selected name substituted below, collect a short real-output sample:

```sh
su -c 'timeout 15 bluelog -i hci0 -a -v'
```

Expected: nearby discoverable devices produce lines containing valid colon- or dash-separated MAC addresses. If your active adapter is not `hci0`, use the name reported by `hciconfig`; the app itself performs this substitution automatically.

## 4. Wi-Fi subnet and XML sweep

```sh
su -c 'ip -o -4 addr show dev wlan0 scope global'
su -c 'nmap -sn -oX - 192.168.1.0/24'
```

Expected: the first command prints one IPv4 address with a CIDR prefix. For the second command, replace the example CIDR with that interface's actual network; output must begin with an Nmap XML document and include `<host>` entries for responsive peers.

Failure here is intentionally soft: Bluetooth results continue, network status reads unavailable, and confidence cannot be promoted using network correlation.

Run the commands inside a supported chroot instead if that is where CyberScan discovers both tools.

## 5. Android permission and lifecycle pass

1. Install the debug APK and open CyberScan in portrait orientation.
2. Tap `INITIATE SCAN` and grant Nearby devices, precise location, and notifications. Grant root if prompted for optional capabilities.
3. Confirm the foreground notification appears and the HUD changes from `CALIBRATING` to `SCANNING` with `ANDROID HAL`.
4. Move a discoverable Bluetooth device into range and confirm its MAC, RSSI where available, class, and confidence appear.
5. Select the row and confirm the target panel updates; if a current Wi-Fi peer correlates, verify its IP/host/vendor fields.
6. Move the phone near a changing magnetic source and confirm the relative EMF delta responds. Do not use this reading for safety decisions.
7. Tap `ABORT`; confirm readings stop and the foreground notification disappears.
8. Start again, then swipe the app task away; confirm the service, native discovery, and any optional `bluelog` process terminate.
9. Deny root or leave the external adapter disconnected; confirm native scanning continues and the adapter label remains `ANDROID HAL`.
10. Connect an external HCI adapter when available; confirm the label becomes `ANDROID HAL + hciN` and duplicate MAC addresses remain a single row.
11. Disable Android Bluetooth and retry; confirm a clear hard-failure message is shown.
12. Disable Wi-Fi and retry; confirm Bluetooth scanning continues with `NMAP // UNAVAILABLE`.

## 6. Optional ADB evidence

```sh
adb shell dumpsys activity services com.cyberscan.app
adb shell su -c 'ps -A | grep -E "bluelog|nmap"'
```

During an active scan, the service must be present; one `bluelog` loop may be present only with a usable external HCI backend. After `ABORT` or task removal, no CyberScan-owned `bluelog` process should remain. `nmap` is a one-shot sweep and should not persist.

Record the installed `bluelog`, `nmap`, root-manager, and adapter firmware versions alongside the acceptance result so later parser or hardware regressions can be reproduced.

