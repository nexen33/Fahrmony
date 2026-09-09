<div align="right">
  <a href="./README.md">简体中文</a> | <a href="./README_en.md">English</a> | <strong>Deutsch</strong> | <a href="./README_ja.md">日本語</a>
</div>

# Fahrmony

![Platform](https://img.shields.io/badge/Platform-Android%20Auto%20%7C%20Android-green)
![Capacitor](https://img.shields.io/badge/Capacitor-v8-blue)
![React](https://img.shields.io/badge/React-v19-cyan)
![Kotlin](https://img.shields.io/badge/Kotlin-Native-purple)
![License](https://img.shields.io/badge/License-MIT-green)

Der Name **Fahrmony** setzt sich aus dem deutschen Wort **Fahren** und dem englischen Begriff **Harmony** zusammen.

Für viele Menschen, die im Ausland leben und Fahrzeuge mit Android Auto fahren, besteht oft ein frustrierender Widerspruch: Das native Fahrzeug-Ökosystem unterstützt die gewohnten Streaming- und Messaging-Dienste aus der Heimat meist überhaupt nicht. Auf dem Smartphone laufen die Lieblingsalben oder Podcasts, aber im Auto bleibt oft nur die einfache Bluetooth-Verbindung, bei der Titelwechsel und Textanzeigen unkomfortabel sind. Kommt während der Fahrt eine wichtige Nachricht an, muss man immer noch riskant auf das Handydisplay blicken.

**Fahrmony** schließt diese Lücke. Als leichtgewichtige und sichere Brücke ermöglicht Fahrmony eine reibungslose Mediensteuerung über das Fahrzeugdisplay sowie das sichere Vorlesen wichtiger Benachrichtigungen – für ein entspanntes und harmonisches Fahrerlebnis in der Ferne.

Basiert auf der **offiziellen Android MediaBrowser-Architektur** und modernstem **React 19 + Capacitor**. Keine Root-Rechte erforderlich, 100 % lokal auf Ihrem Gerät.

## Hauptfunktionen

- **Standardisierte Fahrzeug-Medienbrücke**: Nutzt AOSP `MediaBrowserServiceCompat` und `MediaSessionCompat` für eine reibungslose Head-Unit-Integration.
- **Intelligenter Kalt-/Warmstart**: Aktiviert im Hintergrund ruhende Medien-Apps automatisch bei Betätigung auf dem Autobildschirm.
- **Sicherheits-Benachrichtigungsassistent**: Erkennt Systembenachrichtigungen und ermöglicht das sichere Vorlesen per Sprachausgabe (TTS) sowie visuelle Hinweise, ohne vom Straßenverkehr abgelenkt zu werden.
- **Robuste Multi-Prozess-Isolierung**: Native Fahrzeugdienste laufen in einem separaten `:car`-Prozess für maximale Ausfallsicherheit.
- **Datenschutz an erster Stelle (100 % Offline)**: Keine Serveranbindung, kein Benutzerkonto, keinerlei Telemetrie. Alle Daten verbleiben flüchtig im Arbeitsspeicher des Smartphones.
- **Modernes Design & Mehrsprachigkeit**: Unterstützt Hell-/Dunkelmodus sowie Deutsch, Englisch, Japanisch und vereinfachtes Chinesisch.

## Kompatibilität & Interoperabilität

Fahrmony verbindet Anwendungen ausschließlich über offizielle, standardisierte Android-Schnittstellen:
- **Mediensteuerung**: Kompatibel mit gängigen Streaming-Playern, die den Android-Standard `MediaSession` unterstützen.
- **Benachrichtigungen**: Kompatibel mit Instant-Messaging-Diensten, die über Standard-Benachrichtigungen zur Sprachausgabe verfügen.

*Hinweis: Alle erwähnten Marken- und Produktnamen dienen ausschließlich der Beschreibung technischer Kompatibilität und sind Eigentum ihrer jeweiligen Inhaber.*


## Download & Installation

Fahrmony ist eine saubere, werbefreie Open-Source-Software:

1. Besuchen Sie die [Releases-Seite](https://github.com/nexen33/Fahrmony/releases);
2. Laden Sie die neueste Installationsdatei `Fahrmony_v1.0.0.apk` herunter;
3. Installieren Sie die APK und erteilen Sie in der App die Berechtigungen für **„Benachrichtigungszugriff“** und **„Keine Akku-Einschränkungen“**;
4. Verbinden Sie Ihr Smartphone per Kabel oder kabellos mit Android Auto und starten Sie Fahrmony direkt auf dem Armaturenbrett.

## Datenschutzversprechen

- **Keine Internetverbindung**: Fahrmony fordert keine Berechtigung für Netzwerkzugriff an.
- **Keine Datenspeicherung**: Benachrichtigungsinhalte werden ausschließlich temporär zur Sprachausgabe verarbeitet und sofort verworfen.

## Technologie-Stack

- **Native Fahrzeugschicht**: Kotlin, Android MediaBrowserCompat, MediaSessionCompat, IPC Messenger
- **Frontend-Architektur**: React 19, TypeScript, Vite, Capacitor v8
- **Design-System**: Fluide CSS-Variablen, Glassmorphism, adaptiver Kontrastmodus
- **Lokalisierung**: Natives statusgesteuertes i18n-System

## Haftungsausschluss

1. **Forschung und Bildung**: Diese Software wird unter der MIT-Lizenz ausschließlich für technische Studien- und nicht-kommerzielle Kompatibilitätszwecke bereitgestellt. Eine kommerzielle Nutzung oder Monetarisierung ist untersagt.
2. **Unabhängige Entwicklung**: Fahrmony ist ein unabhängiges Open-Source-Projekt und steht in keiner geschäftlichen Beziehung zu Google LLC, Android Auto oder Entwicklern von Drittanbieter-Apps.
3. **Keine Urheberrechtsverletzung**: Die Software speichert, verteilt, entschlüsselt oder manipuliert keine urheberrechtlich geschützten Medieninhalte oder proprietäre Kommunikationsprotokolle. Alle Dienste werden durch die auf dem Gerät installierten offiziellen Apps ausgeführt.
4. **Markenrechte & Fair Use**: Jegliche Erwähnung von Drittanbieter-Systemen dient ausschließlich der objektiven technischen Beschreibung. Alle Markenrechte verbleiben bei ihren jeweiligen Inhabern. Rechteinhaber können sich bei Anliegen direkt über GitHub Issues an uns wenden.
5. **Fahrsicherheit an erster Stelle**: Die Verantwortung für sicheres Fahren und die Einhaltung aller Verkehrsregeln liegt uneingeschränkt beim Fahrer. Konfigurationen am Gerät dürfen keinesfalls während der Fahrt vorgenommen werden. Die Autoren übernehmen keine Haftung für Schäden oder Verkehrsverstöße.

---

## Vorschau

<p align="center">
  <img width="7550" height="5650" alt="Image" src="https://github.com/user-attachments/assets/cb70fe9f-eb4d-4c77-979a-098f5ffd5d10" />
</p>

<p align="center">
  <em>Viel Spaß damit!</em><br />
  <em>Entwickelt mit ❤️ von Tun&PaMa Familie</em><br />
  <em>Copyright © 2026 Tun & PaMa AG</em>
</p>
