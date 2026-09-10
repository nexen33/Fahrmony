<div align="right">
  <a href="./README.md">简体中文</a> | <a href="./README_en.md">English</a> | <strong>Deutsch</strong> | <a href="./README_ja.md">日本語</a>
</div>

# Fahrmony

![Platform](https://img.shields.io/badge/Platform-Android%20Auto%20%7C%20Android-green)
![Capacitor](https://img.shields.io/badge/Capacitor-v8-blue)
![React](https://img.shields.io/badge/React-v19-cyan)
![Kotlin](https://img.shields.io/badge/Kotlin-Native-purple)
![License](https://img.shields.io/badge/License-CC%20BY--NC%204.0-red)

> Eine lokale Schnittstellenbrücke für Android Auto, mit der Fahrzeug-Displays bei Fahrten im Ausland gängige chinesische Audio-Streaming- und Messaging-Dienste nahtlos anzeigen und teilweise steuern können.

## Was ist Fahrmony?

Der Name **Fahrmony** verbindet das deutsche Wort **Fahren** mit dem englischen Begriff **Harmony** (Harmonie und Synchronisation).

Viele Fahrer im Ausland, die Fahrzeuge mit Android Auto nutzen, stehen vor einer alltäglichen Hürde: Das native Fahrzeug-System unterstützt gewohnte Audio- und Messaging-Dienste aus dem asiatischen Raum meist nicht direkt. Auf dem Armaturenbrett bleibt oft nur eine unkomfortable Bluetooth-Verbindung, bei der Lenkradtasten und Bordmonitor Titel weder wechseln noch vorspulen können. Zudem verleiten eingehende Kurznachrichten von Apps wie WeChat oder Feishu während der Fahrt dazu, gefährliche Blicke auf das Smartphone zu werfen.

**Fahrmony schließt diese Lücke sicher und zuverlässig.** Ohne Root-Rechte und ohne Modifikation des Betriebssystems fungiert die Software als standardkonforme Schnittstelle. Sie spiegelt die Hintergrundwiedergabe auf die Benutzeroberfläche von Android Auto (einschließlich Split-Screen und Coolwalk) und wandelt Nachrichten in fahrgerechte Sprachausgaben um, damit Auslandsfahrten entspannter ablaufen.

### Was Fahrmony nicht ist

- **Kein eigenständiger Musikplayer**: Enthält keine internen Audiodecoder und streamt keine Medieninhalte eigenständig.
- **Kein Ersatz für Original-Apps**: Benutzerkonten, Mitgliedschaften und die Audioerzeugung verbleiben vollständig bei den installierten Apps.
- **Keine Medienspeicherung oder Proxy-Übertragung**: Es werden keine Mediendateien heruntergeladen oder über externe Server weitergeleitet.
- **Keine Root-Rechte erforderlich**: Die Anwendung arbeitet vollständig innerhalb der regulären Android-Sicherheitsgrenzen.
- **Kein Reverse-Engineering proprietärer Schnittstellen**: Es werden keine privaten Protokolle manipuliert oder geknackt.

## Funktionsweise

Fahrmony nutzt ausschließlich offizielle Schnittstellen des Android-Betriebssystems:

1. **Audio-Schnittstelle**: Erkennt aktive Medien auf dem Smartphone und spiegelt deren Status direkt an das Fahrzeugsystem weiter, sodass Steuerung über Bordmonitor und Lenkradtasten möglich ist.
2. **Benachrichtigungs-Schnittstelle**: Wandelt Statusleisten-Nachrichten in fahrzeugkonforme Formate um, damit der Sprachassistent sie während der Fahrt sicher vorlesen kann.
3. **Prozessisolierung**: Alle fahrzeugrelevanten Dienste laufen in einem isolierten Hintergrundprozess (`:car`), entkoppelt von der Smartphone-Benutzeroberfläche.

## Hauptfunktionen

- **Standard-Bedienelemente im Fahrzeug**: Wiedergabe, Pause, Vor- und Zurückspringen sowie präzises Spulen direkt am Bordbildschirm.
- **Sitzungserkennung und -fokus**: Erkennt aktive Audiositzungen und stabilisiert die Steuerung beim Wechsel zwischen mehreren Audio-Apps.
- **Kaltstart & automatisches Aufwecken**: Startet ruhende Ziel-Apps bei Befehlen über den Autobildschirm automatisch im Hintergrund und setzt die Wiedergabe fort; inklusive Wiederholungsversuchen bei Verzögerungen.
- **Adaptiver Hintergrund**: Dynamische Farbabstimmung verhindert, dass dunkle Cover-Bilder oder Offline-Wiedergabe die Bedientasten auf dem Monitor schwarz und unlesbar machen.
- **Wiedergabelisten und Wiederholungsmodi**: Reicht vorhandene Titellisten durch und unterstützt gängige Schleifen- und Wiederholungsmodi.
- **Fahrgerechte Nachrichtenbenachrichtigung**: Filtert Gruppenchats und ermöglicht das Vorlesen sowie die Bestätigung als gelesen per Tastendruck.
- **Schutz vor versehentlicher Tonwiedergabe**: Pausiert die Wiedergabe sofort beim Trennen des Kabels, damit das Handy nicht plötzlich laut weiterspielt.
- **Schlankes Smartphone-Frontend**: Übersichtliche Smartphone-Oberfläche mit Hell- und Dunkelmodus sowie mehrsprachiger Unterstützung.

## Kompatibilitätsmatrix

> **Prinzip**: Die Kompatibilität basiert auf technischen Standardschnittstellen und ist nicht an bestimmte App-Hersteller gebunden. Jede Anwendung, die Standard-Mediensitzungen oder Systembenachrichtigungen nutzt, wird unterstützt.

| Funktion | Status | Technische Umsetzung | Grenzen & Bedingungen |
| :--- | :---: | :--- | :--- |
| **Wiedergabe und Pause** | **Vollständig** | Über Standard-Mediensteuerung mit Tastensperre gegen Mehrfachklicks | Quell-App muss auf Medienbefehle des Systems reagieren |
| **Vor- und Zurückspringen** | **Vollständig** | Standardbefehle mit zeitlicher Statusverriegelung | Manche Radio- oder Podcast-Streams bieten kein Zurückspringen |
| **Spulen im Titel** | **Unterstützt** | Reicht die Zeitposition durch und aktualisiert die Fortschrittsanzeige | Bei Live-Übertragungen technisch nicht möglich |
| **Titel- und Interpretenanzeige** | **Vollständig** | Liest Titel, Interpret und Album aus dem Medienspeicher | Bei verzögerter Bereitstellung greift kurzzeitig der App-Name |
| **Cover-Hintergrund** | **Adaptiv generiert** | Kontrastreiche Farbgestaltung verhindert unleserliche schwarze Tasten | Direkte Bildübertragung wird vermieden, um Ruckler auszuschließen |
| **Wiedergabeliste** | **Durchgereicht** | Zeigt die Titelliste an, sofern die Quell-App diese an das System meldet | Bleibt automatisch ausgeblendet, wenn keine Liste vorhanden ist |
| **Wiederholungsmodus** | **Adaptiv** | Unterstützt Standardeinstellungen und herstellerspezifische Aktionen | Abhängig von der Umsetzung in der jeweiligen Quell-App |
| **Zufallswiedergabe** | **Eingeschränkt** | Funktioniert nur, wenn die Quell-App entsprechende Aktionen anbietet | Wird von vielen Streaming-Apps nicht über Standards bereitgestellt |
| **Kaltstart bei Bedarf** | **Vollständig** | Startet ruhende Ziel-Apps per Systembefehl und setzt die Wiedergabe fort | Erfordert erlaubte Hintergrund- oder Autostart-Rechte im Handy |
| **Benachrichtigungsanzeige** | **Vollständig** | Wandelt Nachrichten in standardisierte Karten für das Fahrzeug um | Erfordert erteilten Benachrichtigungszugriff im Smartphone |
| **Sprachausgabe** | **Systemgesteuert** | Wiedergabe erfolgt automatisch über den Sprachassistenten des Autos | Sprachausgabe richtet sich nach den Spracheinstellungen im Konto |
| **Direktes Antworten** | **Nicht unterstützt** | Bietet Bestätigung als gelesen. Das Senden von Text zurück an die App wird nicht unterstützt | Messaging-Apps bieten keine öffentlichen Schnittstellen für externe Antworten |

## Systemanforderungen

- **Smartphone-Betriebssystem**: Android 10.0 oder höher (Android Auto ist fest im System integriert, direkt einsatzbereit; abwärtskompatibel bis Android 9.0 mit manuellem Download aus dem Play Store).
- **Fahrzeugumgebung**: Fahrzeug mit Unterstützung für **Android Auto** (per Kabel oder kabellos) oder offizieller PC Desktop Head Unit (DHU) Emulator.
- **Anwendungen**: Auf dem Smartphone installierte Streaming- oder Messaging-Anwendungen.

## Installation

1. **APK herunterladen**: Laden Sie die aktuelle `Fahrmony_v1.0.0.apk` von der [Releases-Seite](https://github.com/nexen33/Fahrmony/releases) herunter.
2. **Berechtigungen vergeben**:
   - Fahrmony öffnen und den **Benachrichtigungszugriff** aktivieren;
   - Die **Akku-Optimierung ignorieren** (auf „Nicht eingeschränkt“ stellen), damit Hintergrunddienste stabil laufen;
   - Unter Android 13 oder höher das **Senden von Benachrichtigungen** erlauben.
3. **Android Auto Entwicklereinstellungen aktivieren**:
   - Systemeinstellungen des Smartphones öffnen -> nach `Android Auto` suchen;
   - Ganz nach unten scrollen und 10 Mal auf die Versionsnummer tippen;
   - Oben rechts das Menü öffnen -> **Entwicklereinstellungen** -> **Unbekannte Quellen** aktivieren.
4. **Mit dem Fahrzeug verbinden**: Smartphone per USB-Kabel oder kabellos verbinden. Das **Fahrmony**-Symbol erscheint auf dem Autobildschirm.

## Berechtigungen

Übersicht aller im System deklarierten Berechtigungen und deren tatsächlicher Verwendungszweck:

| Berechtigungsname | Funktionsbereich | Relevanz und technischer Hintergrund |
| :--- | :--- | :--- |
| **Internetzugriff** | Basiskomponente der Benutzeroberfläche | Standardbestandteil des Web-Containers der Einstellungsseite. **Fahrmony enthält keinerlei externe Server, keinen Netzwerk-Code und keine Analyse-Dienste. Es werden keine Daten übertragen.** |
| **Benachrichtigungszugriff** | Medien- und Nachrichtenbrücke | Kernberechtigung. Ermöglicht das Erkennen aktiver Mediensitzungen und die Weitergabe von Textnachrichten an das Auto. Daten werden ausschließlich flüchtig im RAM verarbeitet. |
| **Vordergrunddienst ausführen** | Hintergrundstabilität | Stellt sicher, dass die Verbindung bei ausgeschaltetem Handy-Display nicht abbricht. |
| **Medienwiedergabe im Vordergrund** | Fahrzeug-Audiosteuerung | Deklariert einen konformen Mediendienst für das Android-System, um höchste Priorität bei der Steuerung zu erhalten. |
| **Datensynchronisation im Vordergrund** | Prozess-Synchronisation | Ermöglicht den verlässlichen Statusaustausch zwischen Handy-Oberfläche und Fahrzeughintergrunddienst. |
| **Benachrichtigungen senden** | Fahrzeug-Kartenanzeige | Erforderlich ab Android 13, um eingehende Nachrichten formatiert auf dem Autobildschirm darzustellen. |
| **Akku-Optimierung ignorieren** | Schutz vor Abschaltung | Verhindert, dass aggressive Stromsparmechanismen des Handys den Dienst bei längerer Fahrt beenden. |

## Problembehebung

### 1. Fahrmony erscheint nicht auf dem Autobildschirm
- **Ursache**: „Unbekannte Quellen“ in den Entwicklereinstellungen von Android Auto nicht aktiviert.
- **Lösung**: Smartphone-Einstellungen -> Android Auto -> 10x auf Versionsnummer tippen -> Menü oben rechts -> Entwicklereinstellungen -> „Unbekannte Quellen“ aktivieren, danach neu anstecken.

### 2. Bildschirm zeigt „Warten auf Musikwiedergabe“ oder Liste bleibt leer
- **Ursache**: Die Audio-App war im Tiefschlaf und hat dem System noch keine aktive Sitzung gemeldet.
- **Lösung**: Audio-App auf dem Smartphone einmal kurz öffnen und anspielen, oder in der Quellenliste auf dem Autobildschirm auf den App-Namen tippen.

### 3. Titelname ist leer oder zeigt nur „Wird abgespielt“
- **Ursache**: Manche Player übermitteln Titeldaten erst mit einigen Sekunden Verzögerung.
- **Lösung**: Das System liest die Daten automatisch aus der Statusleiste aus; die Anzeige aktualisiert sich nach kurzer Zeit von selbst.

### 4. Wiedergabe und Pause funktionieren, aber Vor- oder Zurückspringen reagiert nicht
- **Ursache**: Der laufende Audiostream (z. B. Webradio) unterstützt das Wechseln von Titeln technisch nicht.
- **Lösung**: Dies ist eine Eigenheit der jeweiligen Quell-App und stellt keinen Fehler dar.

### 5. Antippen auf dem Autobildschirm startet die Ziel-App nicht
- **Ursache**: Das Handy-Betriebssystem blockiert das automatische Starten im Hintergrund.
- **Lösung**: Smartphone-Einstellungen -> Apps -> Fahrmony sowie die jeweilige Audio-App aufrufen und „Autostart erlauben“ sowie Hintergrundaktivitäten gestatten.

### 6. Wiederholungs- oder Zufallstaste reagiert nicht
- **Ursache**: Die Ziel-App stellt diese Optionen nicht über die Standard-Medienschnittstelle bereit.
- **Lösung**: Um Fehlbedienungen zu vermeiden, bleibt die Anzeige in einem neutralen Zustand.

### 7. Nachrichten werden auf dem Autobildschirm nicht angezeigt oder vorgelesen
- **Ursache**: Der Benachrichtigungszugriff fehlt, oder die Option zum Filtern von Gruppenchats ist aktiv.
- **Lösung**: In den Einstellungen prüfen, ob alle Berechtigungen erteilt sind.

### 8. Verbindung bricht nach längerer Fahrzeit mit dunklem Display ab
- **Ursache**: Energiesparfunktionen des Smartphones haben den Hintergrunddienst beendet.
- **Lösung**: In den Akkueinstellungen des Handys für Fahrmony und die verwendeten Audio-Apps die Option „Nicht eingeschränkt“ wählen.

## Diagnose und Feedback

### Für Fahrer und Endanwender

Wenn im Alltag ein Problem auftritt, können Sie gerne eine Fehlermeldung auf GitHub einreichen. Bitte nutzen Sie folgende Vorlage (**Hinweis: Bitte niemals vertrauliche private Nachrichteninhalte übermitteln**):

```text
- Fahrmony-Version: v1.0.0
- Android-Version: z. B. Android 14
- Smartphone-Modell: z. B. Pixel 8 / Galaxy S24 / Xiaomi 14
- Android Auto-Version: z. B. 11.8
- Verbindungstyp: USB-Kabel / Kabellos
- Name und Version der betroffenen Audio- oder Messaging-App:
- Fehlerbeschreibung:
```

### Für Entwickler und erfahrene Anwender

Für tiefergehende Analysen steht der offizielle Desktop Head Unit (DHU) Emulator in Verbindung mit ADB bereit.

#### 1. Portweiterleitung und Starten des Simulators

**Eingabeaufforderung (CMD):**
```cmd
adb forward tcp:5277 tcp:5277
desktop-head-unit.exe
```

**PowerShell:**
```powershell
adb forward tcp:5277 tcp:5277
.\desktop-head-unit.exe
```

#### 2. Echtzeit-Erfassung von Diagnoseprotokollen

**Eingabeaufforderung (CMD):**
```cmd
adb logcat -c && adb logcat -v time -s FahrmonyProbe:I
```

**PowerShell:**
```powershell
adb logcat -c; adb logcat -v time -s FahrmonyProbe:I
```

Entwickler können relevante Protokollauszüge optional an die Fehlermeldung anhängen.

## Datenschutz

1. **Vollständig lokal**: Alle Daten werden **ausschließlich im flüchtigen Arbeitsspeicher (RAM)** verarbeitet und niemals auf Speichergeräten abgelegt.
2. **Keine externen Server**: Es existieren keine Hintergrundserver; es findet kein Datenaustausch über das Internet statt.
3. **Keine Tracking-Dienste**: Es sind keinerlei Analyse-, Werbe- oder Telemetrie-Module integriert.
4. **Jederzeit widerrufbar**: Berechtigungen können in den Smartphone-Einstellungen jederzeit deaktiviert werden.

## Architektur

```text
  ┌─────────────────────────────────────────────────────────────┐
  │  Quell-Anwendungen (Audio-Streaming- und Messaging-Apps)    │
  └───────────────┬─────────────────────────────┬───────────────┘
                  │ Mediensitzungs-Token        │ Systembenachrichtigungen
                  ▼                             ▼
  ┌─────────────────────────────────────────────────────────────┐
  │  Fahrmony Isolierter Fahrzeug-Hintergrundprozess (:car)     │
  │  - Sitzungserkennung, Schlichtung und Kaltstart             │
  │  - Bereinigung von Nachrichten und Gruppenchat-Filter       │
  │  - Standardisierter Fahrzeugdienst (MediaBrowserService)    │
  │  - Dauerhafter Vordergrund-Schutzdienst                     │
  └─────────────────────────────┬───────────────────────────────┘
                                │ Lokale Prozesskommunikation (Messenger IPC)
                                ▼
  ┌─────────────────────────────────────────────────────────────┐
  │  Fahrmony Smartphone-Verwaltungsoberfläche                  │
  │  - Statusanzeige, Berechtigungsprüfung und Einstellungen    │
  └─────────────────────────────┬───────────────────────────────┘
                                │ Fahrzeug-Schnittstelle
                                ▼
  ┌─────────────────────────────────────────────────────────────┐
  │  Fahrzeug-Display (Android Auto)                            │
  │  - Mediensteuerung auf Vollbild und geteiltem Bildschirm   │
  │  - Sichere Sprachausgabe eingehender Nachrichten            │
  └─────────────────────────────────────────────────────────────┘
```

## Technologie-Stack

- **Fahrzeug-Kern (`:car` isolierter Prozess)**: Kotlin, Android Jetpack MediaCompat, Android Auto, Messenger IPC
- **Mobil-Dashboard (Hauptprozess)**: Capacitor v8, React 19, TypeScript, Vite
- **Styling und Lokalisierung**: Vanilla CSS, Native i18n

## Häufig gestellte Fragen

#### Warum kann ich auf dem Autobildschirm nicht direkt tippen oder per Sprache antworten?
Aus Gründen der Verkehrssicherheit und der technischen Grenzen. Das Verfassen von Nachrichten am Steuer lenkt stark ab. Zudem bieten gängige Messaging-Dienste keine öffentlichen Schnittstellen für externe Antworten an. Fahrmony konzentriert sich bewusst auf sicheres Vorlesen.

#### Warum zeigt der Bildschirm farbige Flächen anstelle des Original-Covers?
Einige Original-Cover sind sehr dunkel, was dazu führen kann, dass das Bordsystem die Bedientasten in unleserlichem Schwarz darstellt. Zudem können hochauflösende Bilder das System verlangsamen. Die Farbabstimmung sorgt dafür, dass die Tasten jederzeit klar lesbar bleiben.

#### Wird der Ton über das Auto oder das Smartphone ausgegeben?
Sobald das Smartphone mit dem Auto verbunden ist, läuft die Tonausgabe über die Lautsprecher des Fahrzeugs. Beim Abziehen des Kabels stoppt Fahrmony die Wiedergabe sofort, damit das Telefon nicht versehentlich in der Öffentlichkeit weiterspielt.

#### Muss ich die App vor jeder Fahrt auf dem Telefon öffnen?
Nein. Wenn die Autostart-Rechte im Smartphone vergeben sind, genügt ein Druck auf die Wiedergabetaste auf dem Autobildschirm, um den gewünschten Player im Hintergrund zu starten.

## Mitwirken

Hinweise auf Fehler und Verbesserungsvorschläge sind jederzeit willkommen. Für Code-Beiträge bitten wir um:
1. Durchgehende Typsicherheit und saubere Fehlerbehandlung;
2. Strikte Einhaltung der Prozessisolierung für einen stabilen Fahrzeugdienst;
3. Verzicht auf private Schnittstellen-Hacks oder herstellerspezifische Fremd-Bibliotheken.

## Lizenz

Dieses Projekt ist unter der Lizenz **Creative Commons Namensnennung - Nicht-kommerziell 4.0 International (CC BY-NC 4.0)** lizenziert, Copyright (c) 2026 **Tun&PaMa AG**.
- Offizieller englischer Lizenztext: Siehe [LICENSE](./LICENSE);
- Marken- und Sicherheitsrichtlinien: Siehe [NOTICE](./NOTICE);
- Vollständige chinesische Referenzübersetzung: Siehe [LICENSE.zh-CN.md](./LICENSE.zh-CN.md).

**Wichtiger Hinweis: Dieses Projekt dient ausschließlich privaten Forschungszwecken und der nicht-kommerziellen Nutzung. Jegliche kommerzielle Verwertung, gebührenpflichtige Weitergabe oder kommerzielle Einbindung ist ausdrücklich untersagt.**

## Haftungsausschluss

1. **Forschung und Gemeinnützigkeit**: Dieses Projekt dient der Verbesserung des Bedienkomforts und der Verkehrssicherheit im privaten Rahmen. Eine kommerzielle Nutzung ist ausgeschlossen.
2. **Unabhängigkeit**: Fahrmony ist ein eigenständiges Open-Source-Projekt und steht in keiner geschäftlichen Verbindung zu Google LLC, Android Auto oder Entwicklern von Drittanbieter-Apps.
3. **Urheberrechte**: Die Software speichert, verteilt oder entschlüsselt keine geschützten Mediendateien. Sämtliche Inhalte werden durch die auf dem Gerät installierten Original-Apps verarbeitet.
4. **Schutzrechte und Schlichtung**: Erwähnte Bezeichnungen dienen der sachlichen Beschreibung technischer Kompatibilitäten. Alle Rechte verbleiben bei den jeweiligen Inhabern. Bei Beanstandungen bitten wir um Kontaktaufnahme über GitHub Issues zur zeitnahen Prüfung.
5. **Verkehrssicherheit**: Die Verantwortung für sicheres Fahren und die Beachtung der Straßenverkehrsordnung liegt ausschließlich beim Fahrzeugführer. Einstellungen dürfen keinesfalls während der Fahrt vorgenommen werden.

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
