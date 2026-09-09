<div align="right">
  <a href="./README.md">简体中文</a> | <strong>English</strong> | <a href="./README_de.md">Deutsch</a> | <a href="./README_ja.md">日本語</a>
</div>

# Fahrmony

![Platform](https://img.shields.io/badge/Platform-Android%20Auto%20%7C%20Android-green)
![Capacitor](https://img.shields.io/badge/Capacitor-v8-blue)
![React](https://img.shields.io/badge/React-v19-cyan)
![Kotlin](https://img.shields.io/badge/Kotlin-Native-purple)
![License](https://img.shields.io/badge/License-MIT-green)

The name **Fahrmony** is a blend of the German word for driving (**Fahren**) and the English word **Harmony**.

For many people living abroad who drive vehicles equipped with Android Auto, there has always been a frustrating disconnect: the native vehicle ecosystem rarely integrates the streaming and messaging apps they use on a daily basis. You might be playing your favorite music or podcasts on your phone, but on the car screen you are stuck with basic Bluetooth audio where changing tracks and viewing metadata is clunky, or taking risky glances at your phone whenever an important message arrives.

**Fahrmony** was born to bridge this gap. Functioning as a lightweight and safe in-car proxy, Fahrmony brings smooth media dashboard control and hands-free notification readouts to your vehicle display, making every journey abroad comfortable and harmonious.

Built on **official Android MediaBrowser architecture** and **React 19 + Capacitor**, Fahrmony operates without root or system modifications. 100% of your data stays offline on your phone.

## Key Features

- **Standard In-Car Media Bridge**: Powered by AOSP `MediaBrowserServiceCompat` and `MediaSessionCompat` to deliver a native vehicle head-unit control interface.
- **Cold/Warm Launch Arbitration**: Automatically wakes up target media apps when invoked from the car display, avoiding manual interaction with the handset.
- **Hands-Free Notification Assistant**: Listens to system notifications and translates them into automotive-friendly voice announcements (TTS) and heads-up prompts, eliminating dangerous phone glances while driving.
- **Multi-Process Architecture**: Native automotive services run in an isolated `:car` process for high resilience and long-lasting stability.
- **Privacy-First (100% Offline)**: No cloud servers, no account registration, zero telemetry. All message texts and media states are strictly processed in local volatile RAM.
- **Modern UI & Multi-Language**: Ergonomic design with dark/light themes, fully localized in English, German, Japanese, and Simplified Chinese.

## Compatibility & Interoperability

Fahrmony bridges apps via standard Android AOSP public APIs:
- **Audio Controls**: Compatible with streaming media players supporting Android standard `MediaSession`.
- **Message Prompts**: Compatible with instant messaging apps supporting standard Android system notifications for voice readout.

*Note: All third-party names, trademarks, and brand logos are the property of their respective owners, cited solely for technical compatibility and descriptive purposes.*


## Download & Installation

Fahrmony is a clean, ad-free open-source project:

1. Navigate to the [Releases page](https://github.com/nexen33/Fahrmony/releases);
2. Download the latest `Fahrmony_v1.0.0.apk`;
3. Install the APK on your Android device and grant **"Notification Access"** and **"Unrestricted Battery"** following the in-app onboarding guide;
4. Connect your phone to your car via Android Auto (wired or wireless), and launch Fahrmony from the dashboard.

## Privacy Promise

- **No Network Requests**: Fahrmony does not request internet permissions and contains no analytic SDKs.
- **Zero Disk Residue**: Notifications are parsed on-the-fly and wiped immediately after TTS / display.

## Tech Stack

- **Native Automotive**: Kotlin, Android MediaBrowserCompat, MediaSessionCompat, IPC Messenger
- **Frontend Architecture**: React 19, TypeScript, Vite, Capacitor v8
- **Design System**: Fluid CSS Variables, Glassmorphism, Adaptive High Contrast
- **Localization**: Lightweight native state-driven i18n engine

## Disclaimer

1. **Research & Educational Use**: This software is released under the MIT license for technical study and non-commercial interoperability research only. Commercial distribution or monetization is strictly prohibited.
2. **Independent Implementation**: Fahrmony is an independent open-source project and is NOT affiliated with, sponsored by, authorized, or endorsed by Google LLC, Android Auto, or any third-party app developers.
3. **No Infringement**: This software does NOT host, stream, cache, or decode any copyrighted media content, nor does it reverse-engineer or tamper with third-party software protocols. All media playback and messaging services are executed independently by legitimate apps installed on the user's device.
4. **Trademarks & Fair Use**: Any references to third-party platforms or application categories are purely for technical compatibility description under nominative fair use. All intellectual property rights and trademarks belong to their respective owners. If you are a rights holder and believe any content causes concern, please contact us via GitHub Issues for immediate review and resolution.
5. **Driving Safety First**: Operating a vehicle safely is the sole responsibility of the driver. Do not configure or interact with handset settings while driving. In no event shall the authors or copyright holders be held liable for any traffic infractions, accidents, injuries, or damages arising from the use of this software.

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
