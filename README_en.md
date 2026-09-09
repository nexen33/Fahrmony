<div align="right">
  <a href="./README.md">简体中文</a> | <strong>English</strong> | <a href="./README_de.md">Deutsch</a> | <a href="./README_ja.md">日本語</a>
</div>

# Fahrmony

![Platform](https://img.shields.io/badge/Platform-Android%20Auto%20%7C%20Android-green)
![Capacitor](https://img.shields.io/badge/Capacitor-v8-blue)
![React](https://img.shields.io/badge/React-v19-cyan)
![Kotlin](https://img.shields.io/badge/Kotlin-Native-purple)
![License](https://img.shields.io/badge/License-MIT-green)

A lightweight, non-intrusive, privacy-first companion application for Android Auto. Designed to enhance the daily in-car experience, it allows drivers to smoothly control background audio playback and receive instant messaging notifications safely via TTS and standard automotive quick-replies, eliminating dangerous phone glances while driving.

Built on **official Android MediaBrowser architecture** and **React 19 + Capacitor**, Fahrmony operates without root or system modifications. 100% of your data stays offline on your phone.

## Key Features

- **Standard In-Car Media Bridge**: Powered by AOSP `MediaBrowserServiceCompat` and `MediaSessionCompat` to deliver a native vehicle head-unit control interface.
- **Cold/Warm Launch Arbitration**: Automatically wakes up target media apps when invoked from the car display, avoiding manual interaction with the handset.
- **Hands-Free Notification Assistant**: Listens to system notifications and translates them into automotive-friendly voice announcements and quick replies via `RemoteInput`.
- **Multi-Process Architecture**: Native automotive services run in an isolated `:car` process for high resilience and long-lasting stability.
- **Privacy-First (100% Offline)**: No cloud servers, no account registration, zero telemetry. All message texts and media states are strictly processed in local volatile RAM.
- **Modern UI & Multi-Language**: Ergonomic design with dark/light themes, fully localized in English, German, Japanese, and Simplified Chinese.

## Compatibility & Interoperability

Fahrmony bridges apps via standard Android AOSP public APIs:
- **Audio Controls**: Compatible with streaming media players supporting Android standard `MediaSession`.
- **Message Prompts**: Compatible with instant messaging apps supporting Android standard `RemoteInput` quick-reply actions.

*Note: All third-party trademarks and brand names are mentioned solely for technical compatibility identification and belong to their respective owners.*

## Privacy Promise

- **No Network Requests**: Fahrmony does not request internet permissions and contains no analytic SDKs.
- **Zero Disk Residue**: Notifications are parsed on-the-fly and wiped immediately after TTS / display.

## Tech Stack

- **Native Automotive**: Kotlin, Android MediaBrowserCompat, MediaSessionCompat, IPC Messenger
- **Frontend Architecture**: React 19, TypeScript, Vite, Capacitor v8
- **Design System**: Fluid CSS Variables, Glassmorphism, Adaptive High Contrast
- **Localization**: Lightweight native state-driven i18n engine

## Disclaimer

1. **Research & Educational Use**: This software is released under the MIT license for study and non-commercial interoperability research only.
2. **No Affiliation**: Fahrmony is not affiliated with, sponsored by, or endorsed by Google LLC, Tencent, ByteDance, Alibaba, NetEase, or any third party.
3. **No Infringement**: This software does NOT host, stream, cache, or decode any proprietary or copyrighted content, nor does it tamper with third-party software protocols.
4. **Driving Safety First**: Safe vehicle operation is the sole responsibility of the driver. Always obey local traffic laws.

---

## Screenshots

<!-- Screenshot slot: Place vehicle display and smartphone previews here -->
