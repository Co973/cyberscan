# Hardware acceptance checklist

Run these checks on the intended rooted Android 16 / LineageOS 23.2 device before accepting the build. They validate the external runtime contract; unit tests cannot simulate the USB/Bluetooth firmware, root manager, or NetHunter chroot.

## 1. Root and chroot

From an ADB shell:

```sh
su -c id
su -c 'test -x /data/local/nhsystem/kali-armhf/bin/bash'
su -c 'chroot /data/local/nhsystem/kali-armhf /bin/bash -lc "command -v bluelog; command -v hciconfig; command -v ip; command -v nmap"'
```

Expected: `id` reports `uid=0`, the chroot test exits successfully, and all four commands print absolute paths.

## 2. Bluetooth adapter auto-detection

```sh
su -c 'chroot /data/local/nhsystem/kali-armhf /bin/bash -lc "hciconfig -a"'
```

Expected: at least one interface named `hci` followed by a number has both `UP` and `RUNNING` in its status block. CyberScan sorts active adapters numerically and selects the first one.

With the selected name substituted below, collect a short real-output sample:

```sh
su -c 'chroot /data/local/nhsystem/kali-armhf /bin/bash -lc "timeout 15 bluelog -i hci0 -a -v"'
```

Expected: nearby discoverable devices produce lines containing valid colon- or dash-separated MAC addresses. If your active adapter is not `hci0`, use the name reported by `hciconfig`; the app itself performs this substitution automatically.

## 3. Wi-Fi subnet and XML sweep

```sh
su -c 'chroot /data/local/nhsystem/kali-armhf /bin/bash -lc "ip -o -4 addr show dev wlan0 scope global"'
su -c 'chroot /data/local/nhsystem/kali-armhf /bin/bash -lc "nmap -sn -oX - 192.168.1.0/24"'
```

Expected: the first command prints one IPv4 address with a CIDR prefix. For the second command, replace the example CIDR with that interface's actual network; output must begin with an Nmap XML document and include `<host>` entries for responsive peers.

Failure here is intentionally soft: Bluetooth results continue, network status reads unavailable, and confidence cannot be promoted using network correlation.

## 4. Android permission and lifecycle pass

1. Install the debug APK and open CyberScan in portrait orientation.
2. Tap `INITIATE SCAN` and grant Nearby devices, precise location, notifications, and root.
3. Confirm the foreground notification appears and the HUD changes from `CALIBRATING` to `SCANNING` with the selected `hci*` name.
4. Move a discoverable Bluetooth device into range and confirm its MAC, RSSI where available, class, and confidence appear.
5. Select the row and confirm the target panel updates; if a current Wi-Fi peer correlates, verify its IP/host/vendor fields.
6. Move the phone near a changing magnetic source and confirm the relative EMF delta responds. Do not use this reading for safety decisions.
7. Tap `ABORT`; confirm readings stop and the foreground notification disappears.
8. Start again, then swipe the app task away; confirm the service and `bluelog` process terminate.
9. Disable or remove the active HCI adapter and retry; confirm a clear hard-failure message is shown.
10. Disable Wi-Fi and retry; confirm Bluetooth scanning continues with `NMAP // UNAVAILABLE`.

## 5. Optional ADB evidence

```sh
adb shell dumpsys activity services com.cyberscan.app
adb shell su -c 'ps -A | grep -E "bluelog|nmap"'
```

During an active scan, the service and one `bluelog` loop may be present. After `ABORT` or task removal, no CyberScan-owned `bluelog` process should remain. `nmap` is a one-shot sweep and should not persist.

Record the installed `bluelog`, `nmap`, root-manager, and adapter firmware versions alongside the acceptance result so later parser or hardware regressions can be reproduced.

