<div align="right">
  <a href="./README.md">简体中文</a> | <a href="./README_en.md">English</a> | <strong>Deutsch</strong> | <a href="./README_ja.md">日本語</a>
</div>

# Fahrmony

![Platform](https://img.shields.io/badge/Platform-Android%20Auto%20%7C%20Android-green)
![Capacitor](https://img.shields.io/badge/Capacitor-v8-blue)
![React](https://img.shields.io/badge/React-v19-cyan)
![Kotlin](https://img.shields.io/badge/Kotlin-Native-purple)
![License](https://img.shields.io/badge/License-MIT-green)

Ein leichtgewichtiger, sicherer und datenschutzorientierter Android Auto-Begleiter. Entwickelt, um das Fahrerlebnis zu optimieren: Steuern Sie Hintergrund-Medienwiedergaben direkt über das Fahrzeugdisplay und empfangen Sie wichtige Benachrichtigungen sicher per Sprachausgabe (TTS), ohne vom Straßenverkehr abgelenkt zu werden.

Basiert auf der **offiziellen Android MediaBrowser-Architektur** und modernstem **React 19 + Capacitor**. Keine Root-Rechte erforderlich, 100 % lokal auf Ihrem Gerät.

## Hauptfunktionen

- **Standardisierte Fahrzeug-Medienbrücke**: Nutzt AOSP `MediaBrowserServiceCompat` und `MediaSessionCompat` für eine reibungslose Head-Unit-Integration.
- **Intelligenter Kalt-/Warmstart**: Aktiviert im Hintergrund ruhende Medien-Apps automatisch bei Betätigung auf dem Autobildschirm.
- **Sicherheits-Benachrichtigungsassistent**: Erkennt Systembenachrichtigungen und ermöglicht das sichere Vorlesen sowie standardkonforme Kurzantworten via `RemoteInput`.
- **Robuste Multi-Prozess-Isolierung**: Native Fahrzeugdienste laufen in einem separaten `:car`-Prozess für maximale Ausfallsicherheit.
- **Datenschutz an erster Stelle (100 % Offline)**: Keine Serveranbindung, kein Benutzerkonto, keinerlei Telemetrie. Alle Daten verbleiben flüchtig im Arbeitsspeicher des Smartphones.
- **Modernes Design & Mehrsprachigkeit**: Unterstützt Hell-/Dunkelmodus sowie Deutsch, Englisch, Japanisch und vereinfachtes Chinesisch.

## Kompatibilität & Interoperabilität

Fahrmony verbindet Anwendungen ausschließlich über offizielle, standardisierte Android-Schnittstellen:
- **Mediensteuerung**: Kompatibel mit gängigen Streaming-Playern, die den Android-Standard `MediaSession` unterstützen.
- **Benachrichtigungen**: Kompatibel mit Instant-Messaging-Diensten, die über Standard-Benachrichtigungen und Schnellantworten (`RemoteInput`) verfügen.

*Hinweis: Alle erwähnten Markennamen dienen ausschließlich der Identifikation technischer Kompatibilität und sind Eigentum ihrer jeweiligen Inhaber.*

## Datenschutzversprechen

- **Keine Internetverbindung**: Fahrmony fordert keine Berechtigung für Netzwerkzugriff an.
- **Keine Datenspeicherung**: Benachrichtigungsinhalte werden ausschließlich temporär zur Sprachausgabe verarbeitet und sofort verworfen.

## Technologie-Stack

- **Native Fahrzeugschicht**: Kotlin, Android MediaBrowserCompat, MediaSessionCompat, IPC Messenger
- **Frontend-Architektur**: React 19, TypeScript, Vite, Capacitor v8
- **Design-System**: Fluide CSS-Variablen, Glassmorphism, adaptiver Kontrastmodus
- **Lokalisierung**: Natives statusgesteuertes i18n-System

## Haftungsausschluss

1. **Forschung und Bildung**: Diese Software wird unter der MIT-Lizenz ausschließlich für Studien- und nicht-kommerzielle Kompatibilitätszwecke bereitgestellt.
2. **Keine offizielle Verbindung**: Fahrmony steht in keiner Verbindung zu Google LLC, Tencent, ByteDance, Alibaba, NetEase oder Dritten.
3. **Keine Urheberrechtsverletzung**: Die Software speichert, verteilt oder entschlüsselt keine urheberrechtlich geschützten Medieninhalte.
4. **Fahrsicherheit an erster Stelle**: Der Fahrer trägt die alleinige Verantwortung für die Einhaltung aller geltenden Verkehrs- und Sicherheitsvorschriften.

---

## Screenshots

<!-- Platzhalter für Screenshots des Fahrzeugdisplays und der Smartphone-Oberfläche -->
