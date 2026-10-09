# AmpWright: Battery Monitor

An Android app for checking and controlling older EcoTree lithium batteries over Bluetooth, from
Offshoot Works. The app these batteries came with is no longer supported; AmpWright replaces it.

Inside, these batteries use a battery management system (BMS) made by LTW, so other batteries with an
LTW Bluetooth BMS should work too. The Bluetooth protocol was worked out from LTW's own apps and is
kept byte-for-byte. Everything else is new: Kotlin, Jetpack Compose and Material 3.

## About this project

This is an independent, open-source project. It is not made, endorsed or supported by LTW, EcoTree or
any other battery maker.

**It has only been tested on older EcoTree batteries**, from before EcoTree moved to a different BMS
supplier. Other batteries with an LTW Bluetooth BMS use the same protocol and should work, but haven't
been tested. Newer EcoTree batteries use a different BMS and are not supported.

The app can switch a battery's charging and output off. Use it at your own risk. If you try it on
another battery, please open an issue saying whether it worked.

## Scope

This app supports the LTW Bluetooth BMS protocol and nothing else. Requests to add other BMS brands
(JBD, Daly, JK and so on) will be closed; other open-source apps already cover those. Fixes and
reports for LTW batteries, including ones fitted with a different Bluetooth module, are welcome.

## Tested batteries

| Battery | Bluetooth module | Result |
|---|---|---|
| EcoTree, older models (before the BMS supplier change) | LTW | Works |

Tried it on another battery? Please
[send a battery report](https://github.com/Offshoot-Works/ampwright/issues/new?template=battery-report.yml),
whether it worked or not.

## Features

- **Overview:** a state-of-charge ring, charge/discharge rate, and an estimated time to full or empty.
  Tiles show voltage, current, power, temperature, capacity, cell spread, health and cycles.
- **Power switches:** charge and output MOSFET switches. Turning either one off asks for confirmation.
  A switch shows a "sending" state until the BMS confirms the change.
- **Cells:** a zoomed bar chart and per-cell tiles. The highest and lowest cells are highlighted, with
  deviation from the average and balancing indicators.
- **Temperatures:** every NTC probe plus the MOSFET and ambient sensors, colour-coded.
- **Alerts:** active protections and warnings in plain English. A full list of the 43 monitored
  conditions is also available.
- **Connection:** reconnects automatically if the link drops, and connects to the last battery when the
  app launches. An unpaired battery is paired from inside the app: Android asks for its password once.
- **Diagnostics:** a shareable report of what the app saw from the battery, for attaching to bug reports.
- **Demo mode (debug builds only):** a simulated 4S 100 Ah LiFePO4 pack, for working on the app without
  a battery.
- **Theming:** light and dark themes, with wallpaper colours on Android 12+.

## Building and installing

You need Android Studio, or the Android SDK plus JDK 17 or newer.

```
gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
gradlew testDebugUnitTest      # protocol unit tests
```

To install, copy the APK to the phone and open it ("Install unknown apps" must be allowed), or use
`adb install app-release.apk`. The release build is signed with the local debug key, which is fine
for sideloading. Requires Android 6.0 (API 23) or newer and targets Android 16 (API 36). API 23 is the
lowest version the current Compose/AndroidX libraries support.

## Reporting a problem

[Open an issue](https://github.com/Offshoot-Works/ampwright/issues/new/choose) and include the
diagnostics report. In the app, open **About & diagnostics** (from the ⋮ menu, or the link at the
bottom of the connect screen) and tap **Copy** or **Share**.

The report contains the app and Android versions, connection events, the raw data the battery sent
and the last crash, if there was one. Most of the battery's Bluetooth address is hidden, and nothing
leaves your phone unless you share it.

## Protocol reference

Worked out from the original LTW BMS V1.1 app, with additions from the current LTW app on the Play
Store.

| | |
|---|---|
| GATT service | `0000fe60-0000-1000-8000-00805f9b34fb` (LTW module) |
| Write characteristic | `0xFE61` |
| Notify characteristic | `0xFE62` |
| Advertised name | starts with `LTW`, `ltw`, `BLE`, `SP-` or `AD` |

Some batteries use a generic Bluetooth module instead. The frames are the same; only the GATT
layout differs. The supported modules are listed in
[`GattProfile.kt`](app/src/main/java/io/github/offshootworks/ampwright/bms/GattProfile.kt). Only the LTW module
is paired before use.

```
EE AA | dir | cmd | len | data[len] | chk_lo chk_hi | 0D 0A
dir: A5 = read, 5A = write
chk = 0x10000 - (cmd + len + sum(data))
```

The app polls every 500 ms in a cycle (0x10, then 0x11, then 0x12). Five unanswered requests mean
the link is lost.

| Cmd | Response payload (little-endian) |
|---|---|
| `0x10` basic | pack V (10 mV), current (signed, 10 mA), SOC %, cycles, state (0 idle / 1 dsg / 2 chg), full cap (10 mAh), remaining cap (10 mAh), protection u16, temp-protection u16, alarm u32, balance u32 (bit per cell), firmware, FET bits (b0 chg, b1 dsg, b2 current ×2, b3 current ×4, b4 heater on), SOH % |
| `0x10` newer firmware | continues: multi-pack mode, BMS type, BMS ID u32, running time u32 (s), heater current u16 (10 mA), battery status u16, fault flags u32, app version (2 bytes, big-endian major.minor), device ID (20 ASCII bytes) |
| `0x11` cells | count, then count × mV (u16) |
| `0x12` temps | count, then count × 0.1 K (u16), then MOSFET temp and ambient temp |
| `0x30` MOS write | u16: `FF01` charge on, `FF00` charge off, `01FF` output on, `00FF` output off |

The bit meanings for alarms and protections are in
[`StatusFlags.kt`](app/src/main/java/io/github/offshootworks/ampwright/protocol/StatusFlags.kt).

## Code layout

```
protocol/     Frame encoding, frame reassembly, payload parsers, alarm/protection tables (pure Kotlin, unit-tested)
bms/          BLE client, scanner, demo source, remembered device
diagnostics/  Event log, crash capture and the shareable diagnostics report
ui/           ViewModel, Compose screens, theme
```

## Changes from the original

- Frames are split using the length byte. The original searched a hex string for `0D 0A`, which
  breaks if a payload contains those bytes.
- Pairing and connecting happen in one step. The original made you pair first, then pick the battery
  again from its paired list.
- Uses Android 12+ Bluetooth permissions, so location permission is not needed on newer phones.
- Supports the other Bluetooth modules and newer `0x10` fields that the current Play Store app knows.
- English only. The original also had Simplified Chinese.

## Licence

Copyright (C) 2026 Offshoot Works and contributors.

AmpWright is free software: you can redistribute it and/or modify it under the terms of the GNU
General Public License version 3, or (at your option) any later version. It is distributed without
any warranty. See [LICENSE](LICENSE) for the full text.
