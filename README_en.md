<div align="right">
  <a href="./README.md">简体中文</a> | <strong>English</strong> | <a href="./README_de.md">Deutsch</a> | <a href="./README_ja.md">日本語</a>
</div>

# Fahrmony

![Platform](https://img.shields.io/badge/Platform-Android%20Auto%20%7C%20Android-green)
![Capacitor](https://img.shields.io/badge/Capacitor-v8-blue)
![React](https://img.shields.io/badge/React-v19-cyan)
![Kotlin](https://img.shields.io/badge/Kotlin-Native-purple)
![License](https://img.shields.io/badge/License-CC%20BY--NC%204.0-red)

> A local-first interoperability bridge tailored for Android Auto, enabling car displays to naturally display and partially control your favorite Chinese streaming audio and messaging notifications while driving abroad.

## What is Fahrmony?

The name **Fahrmony** is a blend of the German word for driving (**Fahren**) and the English word **Harmony**.

For many drivers living abroad and driving vehicles equipped with Android Auto, a persistent frustration exists: the native in-car ecosystem rarely supports the Chinese music, podcast, and messaging apps they are accustomed to using. While phone background playback works, stepping into the car often reduces the experience to rudimentary Bluetooth audio—steering wheel and dashboard controls cannot change tracks or adjust progress, and when messages arrive from apps like WeChat or Feishu, drivers are tempted to risk glancing down at their phone screens.

**Fahrmony was built to bridge this disconnect safely.** Without requiring root privileges and without modifying the operating system, it functions as standard-compliant lightweight in-car middleware. It mirrors phone background playback onto Android Auto full-screen and split-screen controls (including Coolwalk) and converts incoming chat notifications into driver-safe voice readouts (TTS), making trips abroad calmer and safer.

### What Fahrmony is NOT

To avoid misconceptions, please note:
- **Not a standalone music player**: Contains no internal audio decoders and does not stream media independently.
- **Not a replacement for original apps**: Accounts, VIP privileges, playlists, and audio decoding remain 100% managed by your installed apps.
- **No media caching or relaying**: Does not download, proxy, or relay media files over remote servers.
- **No root required**: Operates strictly within Android's standard application sandbox security boundaries.
- **No private protocol reverse-engineering**: Does not decompile private commercial APIs or modify network packets.

## How It Works

Fahrmony establishes a bidirectional bridge using official Android standard interfaces:

1. **Audio Media Pipeline**: Discovers active media on the phone via notification listeners and media session managers, mapping playback state bidirectionally into an automotive media browser service for direct dashboard and steering wheel control.
2. **Messaging Pipeline**: Captures incoming status bar notifications, cleans and normalizes their payload, and translates them into automotive-standard notification cards for safe voice readouts via the vehicle's assistant.
3. **Process Isolation**: All automotive background services run in an isolated `:car` system process, physically decoupled from the mobile frontend and synchronized via non-blocking Messenger IPC.

## Features

- **Standard Head-Unit Media Controls**: Seamless Play, Pause, Next, Previous, and Seek operations directly on your dashboard.
- **Session Discovery & Smart Arbitration**: Automatically detects active playback across the system and anchors focus when switching apps to prevent card jumping.
- **Cold Start & Warm Reconnect Resumption**: Finely polished for ignition startup and projection reconnects. Upon connecting to Android Auto, the system automatically detects and resumes playback state without requiring manual clicks on the phone or car screen in most driving scenarios; features built-in self-healing retry logic for sluggish background starts.
- **Adaptive Ambient Artwork**: Generates high-contrast gradient backdrops, completely resolving the common issue where dark album art or offline playback causes car playback capsule buttons to turn invisible black.
- **Queue Passthrough & Playback Modes**: Automatically passes through playback queues when exposed by the underlying player; probes and supports standard repeat modes as well as vendor custom actions.
- **Driving Safety Notification Assistant**: Deconstructs direct and group messages, filters out group chat spam, and generates automotive-standard cards with voice readout and "Mark as Read" actions.
- **Anti-Jitter & Audio Leak Prevention**: Instantly claims transient audio focus and pauses all players upon car disconnection to eliminate speaker audio leakage; enforces an anti-jitter state lock during track switching to prevent UI flashing.
- **Minimalist Handset Frontend**: A lightweight fluid console on the handset displaying real-time media source detection status and notification relay records, with light/dark adaptive theming, 4-language support, and permission setup guides.

## Compatibility

> **Core Principle**: Compatibility is based strictly on **standard Android capabilities** rather than vendor lock-in. Any third-party application publishing standard media sessions or system notifications is supported.

| Capability | Support Status | Implementation & Mechanism | Limitations & Boundaries |
| :--- | :---: | :--- | :--- |
| **Playback Controls** | **Full** | Issues play/pause commands with a debounce lock | Requires original app to handle standard media intents |
| **Track Skip** | **Full** | Issues next/previous commands with state lock | Certain radio or podcast streams do not implement previous track |
| **Seek** | **Supported** | Relays seek timestamps in milliseconds and syncs UI progress | Disabled for live streams or non-seekable streams |
| **Track Metadata** | **Full** | Extracts title, artist, and album with local caching | Displays app name temporarily if metadata delivery is delayed |
| **Cover Artwork** | **Adaptive** | Generates high-contrast gradient art to preserve button visibility | Avoids loading remote image bitmaps directly to prevent black buttons |
| **Playback Queue** | **Passthrough** | Displays track lists on car display when provided by source app | Automatically hidden if the source app does not expose a queue |
| **Repeat Modes** | **Adaptive** | Prioritizes standard flags; probes vendor actions as fallback | Depends on source app exposing standard or custom controls |
| **Shuffle** | **Limited** | Functional only when source app exposes cycle actions | Most streaming apps do not expose standalone shuffle commands |
| **Cold Start** | **Full** | Wakes dormant target app in background and resumes playback | Requires handset permission for background autostart |
| **Notification Display** | **Full** | Parses messages and generates standardized car cards | Requires granted Notification Access permission |
| **Voice Readout** | **System-driven** | Handled by vehicle voice assistant following automotive specs | Voice synthesis quality depends on system speech settings |
| **Direct Reply** | **Unsupported** | Offers "Mark as Read" dismissal. **Replying back to source app is unsupported** | Messaging apps do not provide public third-party message-sending APIs |

## Requirements

- **Handset OS**: Android 10.0 or higher (Android Auto is natively built into the system, plug and play; backwards compatible with Android 9.0 with manual app download from Google Play).
- **In-Vehicle Environment**: Android Auto compatible vehicle display (wired USB or wireless), or official PC Desktop Head Unit (DHU) emulator.
- **App Environment**: Standard audio streaming or messaging applications installed on the handset.

## Installation

1. **Download APK**: Visit the [Releases page](https://github.com/nexen33/Fahrmony/releases) and download the latest release package.
2. **Grant Permissions**:
   - Open Fahrmony and enable **Notification Access**;
   - Enable **Ignore Battery Optimizations** to prevent the OS from terminating background services during long drives;
   - On Android 13 or higher, allow the **Post Notifications** permission.
3. **Enable Android Auto Developer Settings**:
   - Open phone Settings -> search for `Android Auto`;
   - Scroll to the bottom and tap Version 10 times to unlock developer mode;
   - Tap the top-right three dots -> **Developer settings** -> check **Unknown sources**.
4. **Connect to Vehicle**: Connect via USB cable or wireless projection. The **Fahrmony** icon will appear on your vehicle's dashboard.

## Permissions

All permissions declared in the system manifest and their actual technical purposes:

| Permission | Component | Technical Purpose & Disclosure |
| :--- | :--- | :--- |
| **Network Access** | UI Container Environment | Standard configuration for hybrid WebView container. **Fahrmony contains zero backend servers, zero networking code, zero analytics, and never transmits data externally.** |
| **Notification Access** | Core Media & Message Bridge | Essential permission. Used to discover media tokens from status bar notifications and capture messages for voice readouts. Processed purely in volatile RAM. |
| **Foreground Service** | Background Daemon Stability | Maintains service persistence when the phone screen is locked or another app is in use. |
| **Foreground Service Media Playback** | Automotive Audio Control | Declares compliant media service type to Android OS to ensure high-priority audio responsiveness. |
| **Foreground Service Data Sync** | Inter-Process Sync | Facilitates safe and reliable state synchronization between handset UI and `:car` background service. |
| **Post Notifications** | Vehicle Message Cards | Required on Android 13+ to post formatted notification cards for vehicle display and voice readouts. |
| **Ignore Battery Optimizations** | Long-drive Protection | Excludes app from aggressive system power-saving killers during long navigation drives. |

## FAQ

#### Do I need to manually press play on my phone or car screen every time I get in the car?
In the vast majority of everyday scenarios, no. Fahrmony is specifically engineered for automotive "cold start" and "warm reconnect" situations. When connecting via USB cable or wireless projection to Android Auto, the system automatically establishes session awareness and resumes your previous stream without needing to take out your phone or tap the dashboard; even if the target media app was killed in the background, it wakes up and continues playback automatically upon connection.

#### Why can't I type or dictate replies to chat messages on the car screen?
For driver safety and technical compliance. Typing on a dashboard while driving is extremely hazardous; furthermore, major messaging apps do not expose third-party external message-sending APIs. Fahrmony strictly maintains a read-only policy.

#### Why does the screen show gradient artwork instead of the original cover?
Certain source player covers are extremely dark, causing the in-car system to automatically tint playback control buttons into invisible black. Transmitting high-resolution bitmaps can also introduce latency. Adaptive gradients ensure buttons remain readable and track changes smooth.

#### Does audio output from the car or the phone speaker?
Audio automatically routes through the vehicle speakers when connected. Upon disconnection, Fahrmony's built-in leakage prevention pauses playback instantly, preventing sudden loud speaker output in public.

## Troubleshooting & Potential Exceptions

### 1. Fahrmony icon does not appear on car display
- **Cause**: "Unknown sources" is not enabled in Android Auto developer settings.
- **Fix**: Phone Settings -> Android Auto -> tap version 10 times -> top-right menu -> Developer settings -> check "Unknown sources", then reconnect.

### 2. Car display shows "Waiting for audio" or sources list is empty
- **Cause**: Target audio app was in deep sleep and has not yet registered a system media session.
- **Fix**: Open the audio app on your phone once to start playback, or tap the app name in the head unit sources list to wake it up.

### 3. Track title is blank or stuck on "Playing"
- **Cause**: Certain audio apps delay posting metadata until decoding begins.
- **Fix**: Fahrmony automatically extracts metadata from status bar notifications within milliseconds; the display will self-correct shortly.

### 4. Play/Pause works, but Previous/Next does not respond
- **Cause**: The active audio stream (such as a live radio broadcast) does not implement skip callbacks.
- **Fix**: This is an implementation boundary of the underlying app.

### 5. Tapping play on car screen fails to wake dormant player
- **Cause**: Handset operating system restricts background autostart.
- **Fix**: Phone Settings -> Apps -> Fahrmony and target media apps -> allow "Autostart" and background execution.

### 6. Repeat or shuffle buttons do not respond
- **Cause**: Target player does not expose repeat mode control via standard interfaces.
- **Fix**: To avoid invalid state corruption, non-compliant apps remain in neutral state on the dashboard.

### 7. Chat messages arrive but no alert or voice readout on car screen
- **Cause**: Notification Access permission is missing, or "Filter group chats" is active in settings.
- **Fix**: Verify in phone settings that both Notification Access and Post Notifications permissions are granted.

### 8. Connection drops after driving for a while with screen locked
- **Cause**: Phone power-saving policies killed background services.
- **Fix**: In phone battery settings, set battery usage for Fahrmony and target audio apps to "Unrestricted".

## Diagnostics & Feedback

### For Most Drivers & Car Owners

If you encounter an issue during daily driving, please file a report on GitHub Issues using the template below (**Note: Never submit personal private chat contents**):

```text
- Fahrmony Version: v1.0.0
- Android OS Version: e.g., Android 14
- Phone Model: e.g., Pixel 8 / Galaxy S24 / Xiaomi 14
- Android Auto Version: e.g., 11.8
- Connection Type: Wired USB / Wireless Android Auto
- Affected Audio or Messaging App Name & Version:
- Issue Description:
```

### For Developers & Tech Enthusiasts

If you have a debugging setup, you can use the official Desktop Head Unit (DHU) emulator and ADB tool to capture native probe logs.

#### 1. Port Forwarding & Starting DHU

**Command Prompt (CMD):**
```cmd
adb forward tcp:5277 tcp:5277
desktop-head-unit.exe
```

**PowerShell:**
```powershell
adb forward tcp:5277 tcp:5277
.\desktop-head-unit.exe
```

#### 2. Capturing Probe Logs in Real Time

**Command Prompt (CMD):**
```cmd
adb logcat -c && adb logcat -v time -s FahrmonyProbe:I
```

**PowerShell:**
```powershell
adb logcat -c; adb logcat -v time -s FahrmonyProbe:I
```

Developers may optionally attach relevant log excerpts to their issue submission.

## Privacy Policy

1. **100% Local-First**: All notifications, text parsing, and media states are **processed purely in volatile memory (RAM)** and discarded immediately; nothing is written to disk storage.
2. **No Remote Servers**: No backend servers exist; zero network transmission takes place.
3. **No Analytics or Trackers**: Completely free of advertising, behavior tracking, or crash analytics SDKs.
4. **User Revocable**: Permissions can be revoked at any time through Android system settings.

## Architecture

```text
┌─ Source Applications (Audio Streaming & Messaging Apps)
│  └─ MediaSession Token / System Notifications
▼
┌─ Fahrmony Isolated Automotive Process (:car)
│  ├─ Session Discovery, Smart Arbitration & Auto Wakeup
│  ├─ Message Sanitization, Group Filtering & Translation
│  ├─ Automotive Media Service (MediaBrowserServiceCompat)
│  └─ Persistent Foreground Daemon Service
▼
┌─ Fahrmony Mobile Handset Frontend (Main process)
│  └─ Status Monitoring, App Configuration & Preferences
▼
┌─ Vehicle Head Unit (Android Auto Display)
│  ├─ Split-Screen & Full-Screen Media Controls
│  └─ Google Assistant Voice Announcement
```

## Tech Stack

- **Automotive Core (`:car` isolated process)**: Kotlin, Android Jetpack MediaCompat, Android Auto, Messenger IPC
- **Handset Frontend (Main process)**: Capacitor v8, React 19, TypeScript, Vite
- **Styling & Localization**: Vanilla CSS, Native i18n

## Contributing

Bug reports and suggestions for AOSP-compliant standards are welcome! When submitting a Pull Request, please ensure:
1. Strict type safety and proper exception handling;
2. Strict adherence to process isolation to preserve `:car` daemon stability;
3. No inclusion of proprietary vendor SDKs or undocumented private API hacks.

## License

Released under the **Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)** license, Copyright (c) 2026 **Tun&PaMa AG**.
- Canonical Legal Code: See [LICENSE](./LICENSE);
- Trademark & Driving Safety Notices: See [NOTICE](./NOTICE);
- Chinese Reference Translation: See [LICENSE.zh-CN.md](./LICENSE.zh-CN.md).

**Notice: This project is strictly for personal research and non-commercial interoperability verification. Commercial exploitation, monetization, or paid redistribution is prohibited.**

## Disclaimer

1. **Research & Non-Commercial**: Created to improve personal driving ergonomics; commercial use is strictly prohibited.
2. **Independent Implementation**: Fahrmony is an independent open-source project and is not affiliated with, endorsed by, or partnered with Google LLC, Android Auto, or any third-party app developers.
3. **No Copyright Infringement**: Does not host, distribute, or decrypt copyright-protected media files. All media playback is handled independently by legitimate apps installed on the device.
4. **Fair Use & Notice-and-Takedown**: References to standards or app classifications are solely for objective technical compatibility descriptions. All trademarks belong to their respective owners. Rights holders with concerns may contact via GitHub Issues for prompt review.
5. **Driver Safety**: Compliance with traffic laws and safe vehicle operation remains the sole responsibility of the motor vehicle operator. Never configure or interact with handset settings while driving.

---

## Preview

<p align="center">
  <img width="7550" height="5650" alt="Image" src="https://github.com/user-attachments/assets/cb70fe9f-eb4d-4c77-979a-098f5ffd5d10" />
</p>

<p align="center">
  <em>Viel Spaß damit!</em><br />
  <em>Entwickelt mit ❤️ von Tun&PaMa Familie</em><br />
  <em>Copyright © 2026 Tun & PaMa AG</em>
</p>
