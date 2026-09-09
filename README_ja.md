<div align="right">
  <a href="./README.md">简体中文</a> | <a href="./README_en.md">English</a> | <a href="./README_de.md">Deutsch</a> | <strong>日本語</strong>
</div>

# Fahrmony

![Platform](https://img.shields.io/badge/Platform-Android%20Auto%20%7C%20Android-green)
![Capacitor](https://img.shields.io/badge/Capacitor-v8-blue)
![React](https://img.shields.io/badge/React-v19-cyan)
![Kotlin](https://img.shields.io/badge/Kotlin-Native-purple)
![License](https://img.shields.io/badge/License-MIT-green)

安全でプライバシーを重視した、Android Auto 向けの車載コンパニオンアプリです。運転中の安全を最優先に設計されており、スマートフォンのバックグラウンド音楽再生を車載ディスプレイから直感的に操作できるほか、メッセージ通知を音声読み上げとクイック返信で安全に確認できます。

**Android 標準の MediaBrowser アーキテクチャ** と **React 19 + Capacitor** を採用。root 権限は一切不要で、すべてのデータは端末ローカルで安全に完結します。

## 主な機能

- **標準メディアブリッジ**: AOSP の `MediaBrowserServiceCompat` および `MediaSessionCompat` に準拠し、車載機とスムーズに連動。
- **スマート起動制御**: 車載画面で再生をリクエストした際、停止中の対象アプリを自動的にフォアグラウンド/バックグラウンドで復帰。
- **安全なメッセージ通知サポート**: Android 標準の通知リスナーを利用し、メッセージの音声読み上げとステアリング操作によるクイック返信（`RemoteInput`）に対応。
- **マルチプロセス隔離**: 車載サービスは独立した `:car` プロセスで常駐し、高い安定性を維持。
- **完全オフライン＆プライバシー保護**: 外部サーバーとの通信は一切行わず、通知内容や再生履歴は端末の一時メモリ上のみで処理。
- **モダンな UI と多言語対応**: ダーク/ライトモードに完全対応し、日本語、中国語、英語、ドイツ語をサポート。

## 互換性と相互運用性

Fahrmony は Android の公開標準 API のみを用いてアプリ間をブリッジします：
- **オーディオ操作**: Android 標準の `MediaSession` に準拠した各種ストリーミング再生アプリに対応。
- **メッセージ通知**: Android 標準の `RemoteInput` クイック返信に対応したメッセージアプリをサポート。

*注：記載されている会社名および製品名は、技術的な互換性を説明するためのものであり、各権利者に帰属します。*

## プライバシー保護の方針

- **ネットワーク通信なし**: インターネット権限を使用せず、解析 SDK も一切組み込まれていません。
- **個人情報の非保持**: メッセージ内容は読み上げ時のみメモリ上で処理され、ディスクへの永続化は行われません。

## 技術スタック

- **ネイティブ車載層**: Kotlin, Android MediaBrowserCompat, MediaSessionCompat, IPC Messenger
- **フロントエンド構造**: React 19, TypeScript, Vite, Capacitor v8
- **デザインシステム**: 流動的 CSS 変数, Glassmorphism, 適応型ハイコントラスト
- **ローカライゼーション**: 軽量な状態駆動型 i18n エンジン

## 免責事項

1. **研究・教育目的**: 本ソフトウェアは MIT ライセンスに基づき、個人的な研究および技術検証を目的として公開されています。
2. **非公式性の明記**: Google LLC、Tencent、ByteDance、Alibaba、NetEase などの各社とは一切提携・協力関係にありません。
3. **権利侵害の否定**: 著作権で保護されたメディアのホスティングや配布、サードパーティ製アプリの改変やリバースエンジニアリングは一切行っていません。
4. **安全運転の最優先**: 運転中は常に交通法規と安全運転を遵守してください。本アプリの使用に起因する事故やトラブルについて、開発者は一切の責任を負いません。

---

## スクリーンショット

<p align="center">
  <img width="7550" height="5650" alt="Image" src="https://github.com/user-attachments/assets/cb70fe9f-eb4d-4c77-979a-098f5ffd5d10" />
</p>

<p align="center">
  <em>Viel Spaß damit!</em><br />
  <em>Entwickelt mit ❤️ von Tun&PaMa Familie</em><br />
  <em>Copyright © 2026 Tun & PaMa AG</em>
</p>
