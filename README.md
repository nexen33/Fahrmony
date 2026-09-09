<div align="right">
  <strong>简体中文</strong> | <a href="./README_en.md">English</a> | <a href="./README_de.md">Deutsch</a> | <a href="./README_ja.md">日本語</a>
</div>

# Fahrmony (合拍)

![Platform](https://img.shields.io/badge/Platform-Android%20Auto%20%7C%20Android-green)
![Capacitor](https://img.shields.io/badge/Capacitor-v8-blue)
![React](https://img.shields.io/badge/React-v19-cyan)
![Kotlin](https://img.shields.io/badge/Kotlin-Native-purple)
![License](https://img.shields.io/badge/License-MIT-green)

**Fahrmony** 取名自德语驾驶 **Fahren** 与英文和鸣 **Harmony**（中文译名“合拍”）。

很多生活在海外的华人朋友在驾驶支持 Android Auto 的车辆时，常常面临一个很无奈的尴尬痛点：车机原生生态里几乎找不到大家习惯的中文应用。手机明明放着常用的中文音源或播客，到了车上却只能用基础蓝牙干瞪眼，连切歌看歌词都不方便；开车途中常用的即时通讯（IM）软件来消息，又不得不冒险拿起手机低头瞅一眼。

**Fahrmony** 就是为了化解这一份车机与日常习惯之间的断层而生。它就像一座轻巧安全的原生车机桥梁，让你在海外使用 Android Auto 时，车机中控屏幕也能顺畅操控手机里常听的中文音乐，并用安全清晰的语音朗读重要通知，让每一次异国旅途都舒心合拍。

基于 **Android 官方 MediaBrowser 架构** 与 **React 19 + Capacitor** 现代化流体前端构建，无需修改系统、无需 Root 提权，无侵入式接入，所有数据 100% 保存在手机本地。

## 核心特性

- **车载媒体无缝桥接**：基于 AOSP 原生 `MediaBrowserServiceCompat` 与 `MediaSessionCompat` 协议，提供与车机中控无缝融合的播控体验。
- **冷热启动智能唤醒**：内置差异化意图路由，在车机端点击播放时，若后台目标应用未存活，可自动拉起唤醒续播，无需手动掏出手机。
- **行车安全通知伴侣**：通过系统开放的通知监听服务（NotificationListener），在车机端以清晰语音朗读（TTS）和卡片摘要形式安全呈现即时消息，无需分心低头看手机。
- **独立多进程隔离**：原生车机后台服务运行于独立的 `:car` 进程，即便手机端控制台退出，车机音频与通讯桥接依然平稳长效运行。
- **100% 离线与隐私至上**：无账号体系、无数据上报、零云端通信。所有通讯文本与播放状态仅驻留于手机运行时内存中，用完即焚。
- **现代化设计与多语言**：遵循 Android Auto 驾驶人机工程规范，手机控制台支持跟随系统的深浅色流体拟态，内置英语、德语、日语与简体中文。

## 兼容性说明与技术互操作

本项目仅通过 Android 系统公开标准 API（如媒体会话监听与通知监听通道）提供通用桥接功能，旨在增强已安装应用的行车操作便利性：

- **音频媒体控制**：适配支持 Android 标准系统 `MediaSession` 的常见主流流媒体音乐、广播与播客软件。
- **即时消息提醒**：适配支持 Android 标准系统通知渠道的主流即时通讯（IM）软件，提供车机端即时朗读与安全提示。

*注：所有第三方名称、商标和品牌版权均归其各自所有者所有，在此提及仅用于技术兼容性客观说明。*

## 如何获取与安装

本应用为绿色无广告开源软件，可通过以下方式直接获取安装：

1. 前往本仓库右侧的 [Releases 页面](https://github.com/nexen33/Fahrmony/releases)；
2. 下载最新发布的 `Fahrmony_v1.0.0.apk` 安装包；
3. 在 Android 手机上安装后，根据应用内新手引导授予 **“通知使用权”** 与 **“电池无限制”** 权限；
4. 将手机通过数据线或无线方式连接车机 Android Auto，即可在车机应用列表内看到并使用 Fahrmony。

## 隐私政策与安心承诺

我们和你一样深知行车隐私的重要性，因此确立了以下绝对原则：
1. **零网络上传**：本应用不包含任何网络请求权限，未集成任何第三方分析、监控或崩溃日志 SDK。
2. **纯本地化处理**：通知内容（如发信人与文本）仅在本地内存中即时解析以供语音播报或车机投影，绝不落盘存储，更不会上传至任何服务器。
3. **无缝权限可控**：通知读取权限随时可在系统“设置 - 辅助功能 / 特殊应用权限”中开启或关闭。

## 技术栈

- **原生车载层**: Kotlin, Android MediaBrowserCompat, MediaSessionCompat, IPC Messenger
- **前端架构**: React 19, TypeScript, Vite, Capacitor v8
- **样式与设计**: 流体 CSS 变量系统, Glassmorphism, 动态高对比度模式
- **国际化**: 原生多语言状态总线 (i18n)

## 免责声明

1. **学习与研究用途**：本项目为开源技术研究项目，代码遵循 MIT 协议发布，仅供技术交流与个人驾驶便利性研究使用，严禁用于任何商业牟利行为。
2. **非官方从属声明**：本项目为独立第三方开源实现，与 Google LLC、Android Auto 或任何第三方应用开发商均无任何商业合作、从属或授权关系。
3. **资源与接口免责**：本项目不提供、不存储、不解码任何受版权保护的音视频流文件，亦未对任何第三方应用实施逆向反编译或私有协议篡改。所有媒体内容与通讯服务均由用户设备上自行安装的官方正版软件独立完成。
4. **版权与合理使用**：项目中提及的任何第三方系统或应用名称，仅用于技术兼容性与人机交互方案的客观描述，其知识产权与商标权利完全归属于其各自版权方所有。若相关权利人认为本项目内容存在不妥，请通过 GitHub Issue 与作者联系，我们将在核实后第一时间响应并配合下架或修改。
5. **驾驶安全第一**：行车安全始终是驾驶员的第一责任。请勿在行车过程中配置应用或进行任何分心操作。对于因使用本应用或因违反当地交通法规而产生的任何意外、罚款、人身损伤或设备损坏，作者及版权方概不承担任何直接或间接法律责任。

---

## 界面与效果预览

<p align="center">
  <img width="7550" height="5650" alt="Image" src="https://github.com/user-attachments/assets/cb70fe9f-eb4d-4c77-979a-098f5ffd5d10" />
</p>

<p align="center">
  <em>Viel Spaß damit!</em><br />
  <em>Entwickelt mit ❤️ von Tun&PaMa Familie</em><br />
  <em>Copyright © 2026 Tun & PaMa AG</em>
</p>
