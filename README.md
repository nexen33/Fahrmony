<div align="right">
  <strong>简体中文</strong> | <a href="./README_en.md">English</a> | <a href="./README_de.md">Deutsch</a> | <a href="./README_ja.md">日本語</a>
</div>

# Fahrmony (合拍)

![Platform](https://img.shields.io/badge/Platform-Android%20Auto%20%7C%20Android-green)
![Capacitor](https://img.shields.io/badge/Capacitor-v8-blue)
![React](https://img.shields.io/badge/React-v19-cyan)
![Kotlin](https://img.shields.io/badge/Kotlin-Native-purple)
![License](https://img.shields.io/badge/License-MIT-green)

一款轻量、安全且注重隐私的 Android Auto 车载伴侣工具。专为优化日常行车体验打造，能够帮助驾驶员在车载屏幕上优雅管理手机后台音频播放，并在合规安全的前提下，通过语音朗读与车载交互快速获知即时通讯通知，减少驾驶中低头查看手机的潜在危险。

基于 **Android 官方 MediaBrowser 架构** 与 **React 19 + Capacitor** 现代化流体前端构建，无需修改系统，无侵入式接入，所有数据 100% 保存在手机本地。

## 核心特性

- **标准车载媒体桥接**：基于 AOSP 原生 `MediaBrowserServiceCompat` 与 `MediaSessionCompat` 协议，提供与车机中控无缝融合的播控体验。
- **冷热启动智能拉起**：内置差异化意图路由，在车机端点击播放时，若后台目标应用未存活，可自动拉起唤醒，无需手动掏出手机。
- **行车安全通知伴侣**：通过系统开放的通知监听服务（NotificationListener），在车机端以朗读和摘要形式呈现即时消息，支持车机标准快捷回复。
- **独立多进程隔离**：原生车机后台服务运行于独立的 `:car` 进程，即便手机端控制台退出，车机音频与通讯桥接依然平稳长效运行。
- **100% 离线与隐私至上**：无账号体系、无数据上报、零云端通信。所有通讯文本与播放状态仅驻留于手机运行时内存中，用完即焚。
- **现代化设计与多语言**：遵循 Android Auto 驾驶人机工程规范，手机控制台支持跟随系统的深浅色流体拟态，内置英语、德语、日语与简体中文。

## 兼容性说明与技术互操作

本项目仅通过 Android 系统公开标准 API（如媒体会话监听与通知监听通道）提供通用桥接功能，旨在增强已安装应用的行车操作便利性：

- **音频媒体控制**：适配支持标准系统 `MediaSession` 的常见流媒体应用（如 QQ音乐、网易云音乐、酷狗、酷我、喜马拉雅、小宇宙等）。
- **即时消息提醒**：适配支持标准系统快捷回复接口（`RemoteInput`）的即时通讯应用（如微信、飞书、钉钉等）。

*注：第三方应用名称仅用于客观标识技术兼容场景，其商标及软件知识产权均归其各自版权方所有。*

## 隐私政策与安心承诺 (Privacy Policy)

我们和你一样深知行车隐私的重要性，因此确立了以下绝对原则：
1. **零网络上传**：本应用不包含任何网络请求权限，未集成任何第三方分析、监控或崩溃日志 SDK。
2. **纯本地化处理**：通知内容（如发信人与文本）仅在本地内存中即时解析以供语音播报或车机投影，绝不落盘存储，更不会上传至任何服务器。
3. **无缝权限可控**：通知读取权限随时可在系统“设置 - 辅助功能 / 特殊应用权限”中开启或关闭。

## 技术栈

- **原生车载层**: Kotlin, Android MediaBrowserCompat, MediaSessionCompat, IPC Messenger
- **前端架构**: React 19, TypeScript, Vite, Capacitor v8
- **样式与设计**: 流体 CSS 变量系统, Glassmorphism, 动态高对比度模式
- **国际化**: 原生多语言状态总线 (i18n)

## 免责声明 (Disclaimer)

1. **学习与研究用途**：本项目为开源技术研究项目，代码遵循 MIT 协议发布，仅供技术交流与个人驾驶便利性研究使用，严禁用于任何商业目的。
2. **非官方从属声明**：本项目与 Google LLC、腾讯、字节跳动、阿里巴巴等公司无任何官方关联或商业合作。
3. **资源与接口免责**：本项目不包含、不分发任何受版权保护的音视频流文件，亦未对任何第三方应用实施逆向破解或协议篡改。所有媒体内容与通讯功能均由用户设备上官方正版应用独立运行。
4. **驾驶安全第一**：驾驶员在行车过程中应始终保持高度专注，请在车辆静止或确保安全的前提下配置本软件。因使用本应用而产生的任何行车争议或法律责任，作者及版权方概不承担。

---

## 界面与效果预览

<!-- 截图展示位：请在此处贴入车机中控与手机端截图 -->
<!-- 示例：<div align="center"><img src="./docs/preview.png" width="800" /></div> -->
