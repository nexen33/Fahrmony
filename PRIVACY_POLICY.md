# Fahrmony Privacy Policy (Datenschutzerklärung / 隐私政策)

**Priority Order / 言語優先順位**: English (Primary) > 中文 (Secondary) > Deutsch > 日本語  
**Last Updated**: October 2026 / 2026年10月  
**Applicable Version**: v1.3.0 and above  
**Repository**: [https://github.com/nexen33/Fahrmony](https://github.com/nexen33/Fahrmony)  
**Contact & Issue Tracker**: [https://github.com/nexen33/Fahrmony/issues](https://github.com/nexen33/Fahrmony/issues)

---

# Part I: English (Primary)

## 1. Overview & Core Philosophy
Fahrmony ("the Application", "we", "us", or "our") is an open-source, lightweight automotive middleware application designed to bridge media playback states and safe notification readouts from mobile devices to Android Auto head units.

Fahrmony is built upon the foundational principle of **Zero-Knowledge Architecture and Absolute Local Processing**. We believe your communication and listening habits belong exclusively to you. The Application operates with **no proprietary business servers, no user accounts, no commercial tracking SDKs, and zero telemetry collection**.

## 2. Regulatory Compliance (GDPR, CCPA & Android Auto)
This Privacy Policy complies with international data protection frameworks, including the **General Data Protection Regulation (GDPR - Regulation (EU) 2016/679)**, the **California Consumer Privacy Act (CCPA/CPRA)**, and the **Google Play Developer Program Policies for Automotive Applications**.

### 2.1 Legal Bases for Processing (Art. 6 GDPR)
- **Art. 6(1)(a) Consent**: Processing notification content relies entirely on the user explicitly granting the specialized Android Notification Listener permission (`BIND_NOTIFICATION_LISTENER_SERVICE`). Consent may be revoked at any time via system settings.
- **Art. 6(1)(b) Contractual / Functional**: Processing active media session states is strictly necessary to perform the user-commanded action of projecting music playback metadata and transport controls to Android Auto.

### 2.2 Data Minimization & Storage Limitation (Art. 5(1)(c), (e) GDPR)
All incoming notification payloads and media session tokens are processed **ephemerally in volatile memory (RAM)** with a **retention period of zero**. No personal messages, contacts, or notification logs are ever written to non-volatile disk storage.

## 3. Sensitive Permissions & Purpose Disclosures
- **Notification Access (`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`)**:
  - *Scope*: Volatile RAM only.
  - *Purpose*: Listens exclusively to designated messaging packages (`com.tencent.mm`, `com.tencent.mobileqq`, `com.tencent.tim`, `com.ss.android.lark`, `com.alibaba.android.rimet`) and active MediaSessions.
  - *Safeguards*: Sanitizes message text to build standardized Android Auto `MessagingStyle` audio readouts. **Never recorded to disk, never logged, never transmitted off-device, and automatically destroyed upon vehicle disconnection** (`FahrmonyCarConnectionTracker`).
- **Media Playback Foreground Service (`android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`)**:
  - Mandated by Android Auto to maintain responsive media and lyric projection while the mobile screen is locked. A persistent status notification guarantees complete transparency.
- **Post Notifications (`android.permission.POST_NOTIFICATIONS`)**:
  - Required on Android 13+ solely to display the mandatory foreground service status indicator and bridged in-car messaging cards.
- **Internet Access (`android.permission.INTERNET`)**:
  - Strictly limited to two outbound HTTPS functions detailed in Section 4 below. Zero telemetry is sent.

## 4. Outbound Network Requests
1. **On-Demand Public Lyrics Retrieval**:
   - Triggered only when the active media player does not report embedded lyrics.
   - Transmits strictly **track title and artist name**.
   - Contains NO device identifiers (IMEI, MAC, Android ID, IP tracking), NO user tokens, and NO account credentials.
2. **GitHub Releases Version Check**:
   - Automated once daily in background or manually triggered.
   - Read-only query to `https://api.github.com/repos/nexen33/Fahrmony/releases/latest` to check for updates. No telemetry is collected.

## 5. Third-Party Libraries & Sub-Processors
- **Zero Trackers**: Contains no advertising SDKs, no telemetry beacons, and no crash tracking frameworks (no Firebase, no Sentry, no Bugly).
- **Zero International Transfers**: Because all personal data is processed solely on the local device, **no cross-border transfer of personal data occurs** (Art. 44+ GDPR).

## 6. Retention, Storage & User Rights
- **Retention**: Transient notification data is destroyed upon vehicle disconnect. Local settings (theme, preferred player) are stored in local `SharedPreferences`.
- **Right to Erasure (Art. 17 GDPR)**: Users can wipe all local preferences instantly via **Android Settings -> Apps -> Fahrmony -> Storage -> Clear Data**, or by uninstalling the Application.
- **Right to Lodge a Complaint (Art. 77 GDPR)**: Users have the right to lodge a complaint with a competent data protection supervisory authority in their EU member state of residence.
- **CCPA / CPRA**: We do not sell, rent, or share personal information with third parties.

## 7. Children’s Privacy & Disclaimers
The Application is designed exclusively for licensed vehicle operators and does not knowingly collect information from children under 13 (or under 16 in the EU). Android Auto is a trademark of Google LLC. Referenced third-party apps are cited solely for descriptive compatibility purposes.

---

# Part II: 简体中文 (第二优先级)

## 1. 概述与核心准则
Fahrmony（合拍）是一款专为 Android Auto 设计的开源车载轻量级中间件，致力于将手机已安装音频软件的播放状态及即时通讯消息以符合车规安全规范的方式投射至车机屏幕并进行语音播报。本应用坚守**“零知识架构与绝对本地运行”**底线，不设任何独立业务服务器，无需注册账号，不收集设备硬件标识符，绝不进行任何用户行为画像或数据追踪。

## 2. 国际法规遵从性 (GDPR / CCPA)
本隐私政策严格遵守欧盟通用数据保护条例（GDPR）、美国加州消费者隐私法案（CCPA/CPRA）以及 Google Play 车载应用开发者安全规范。

### 2.1 合法处理依据 (Article 6 GDPR)
- **Art. 6(1)(a) 用户明示同意**：通知监听权由用户在系统“通知使用权”中主动授权，用户可随时在手机系统设置中一键撤销；
- **Art. 6(1)(b) 履行用户指令**：媒体会话状态同步是执行用户主动发起的“车机大屏播放控制”所不可或缺的纯本地技术交互。

### 2.2 数据最小化与存储期限限制 (Article 5 GDPR)
所有传入的通知载荷与媒体会话全过程**仅在手机运行内存（Volatile RAM）中即时处理**，保留期限为零。绝不将任何个人通讯内容或通知历史持久化至本地存储设备。

## 3. 系统敏感权限用途与保护措施
- **通知使用权 (`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`)**：
  - *处理范围*：仅限手机运行内存。
  - *具体用途*：严格基于白名单过滤，仅捕获指定即时通讯软件（微信、QQ、TIM、飞书、钉钉）及正在发声的媒体会话。
  - *保护措施*：消息文本解析后构建车载标准 `MessagingStyle` 供车机语音助手安全朗读。**绝不落盘存储、绝不记录日志、绝不向外传输，离车拔线断连后通过感知器自动全部销毁**。
- **媒体播放前台服务 (`android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`)**：
  - 车载 Android Auto 官方框架强制要求，确保锁屏息屏长途驾驶期间与车机中控屏维持低延迟播控通信，前台通知保障运行完全透明。
- **发送通知权限 (`android.permission.POST_NOTIFICATIONS`)**：
  - 仅用于在 Android 13 及更高版本中常驻展示前台运行状态指示及转接车载消息卡片。
- **网络访问权限 (`android.permission.INTERNET`)**：
  - 严格受限，仅用于第 4 节披露的两处只读网络请求，绝不附带任何设备硬件码或行为特征。

## 4. 外部网络请求详细披露
1. **在线歌词单向检索**：仅在当前曲目未包含歌词时，单向从公开在线渠道检索纯文本歌词（仅传输歌名与歌手，不包含任何设备信息）；
2. **GitHub Releases 版本检测**：按需向官方公开的 GitHub API 发起只读版本号比对，用于提醒用户安全更新。

## 5. 第三方库与境外传输
- **零追踪器**：无广告 SDK、无统计打点、无崩溃收集探针（无 Firebase、无 Bugly、无友盟）。
- **无境外传输**：所有数据均在个人手机本地运算，不存在个人数据出境或第三方共享。

## 6. 数据存储、删除与用户权利
- **存储机制**：通知数据用完即焚；本地设置（主题、默认播放器）存储在 Android 本地 `SharedPreferences`。
- **彻底删除权 (Art. 17 GDPR)**：用户可随时通过系统设置清除应用存储或卸载，即可瞬间完全销毁所有本地配置文件。
- **申诉权 (Art. 77 GDPR)**：若认为数据处理不合规，用户有权向所在地区的监管机构提出申诉。
- **CCPA 承诺**：绝不出售、出租或共享任何个人数据。

## 7. 未成年人保护与免责声明
本应用专为合法机动车驾驶员设计，不面向 13 周岁（或欧盟 16 周岁）以下儿童。Android Auto 为 Google LLC 的注册商标。文中提及的第三方商标仅作兼容性说明，版权归原权利人所有。

---

# Part III: Deutsch (Drittplatzierte Priorität)

## 1. Übersicht und Leitprinzipien
Fahrmony („die Anwendung“, „wir“, „uns“) ist eine quelloffene, leichtgewichtige Middleware für das Fahrzeug, die Medienwiedergabestatus und sichere Benachrichtigungsvorlesungen vom Mobilgerät auf Android Auto überträgt.

Fahrmony basiert auf dem fundamentalen Prinzip der **Zero-Knowledge-Architektur und der ausschließlichen lokalen Datenverarbeitung**. Ihre Kommunikations- und Hörgewohnheiten gehören ausschließlich Ihnen. Die Anwendung betreibt **keine eigenen Server, keine Benutzerkonten, keine kommerziellen Tracking-SDKs und erhebt keinerlei Telemetriedaten**.

## 2. Einhaltung der DSGVO (Verordnung (EU) 2016/679)
Diese Datenschutzerklärung erfüllt uneingeschränkt die Anforderungen der Datenschutz-Grundverordnung (DSGVO).

### 2.1 Rechtsgrundlagen der Verarbeitung (Art. 6 DSGVO)
- **Art. 6 Abs. 1 lit. a DSGVO (Einwilligung)**: Der Zugriff auf Benachrichtigungen erfolgt ausschließlich nach ausdrücklicher Erteilung der Android-Spezialberechtigung (`BIND_NOTIFICATION_LISTENER_SERVICE`) durch den Nutzer. Die Einwilligung kann jederzeit in den Systemeinstellungen widerrufen werden.
- **Art. 6 Abs. 1 lit. b DSGVO (Vertragserfüllung / Funktionserbringung)**: Die Spiegelung des Medienstatus ist für die vom Nutzer gewünschte Steuerung und Anzeige auf dem Fahrzeugdisplay zwingend erforderlich.

### 2.2 Datenminimierung und Speicherbegrenzung (Art. 5 DSGVO)
Alle Benachrichtigungsinhalte und Mediensitzungen werden **ausschließlich flüchtig im Arbeitsspeicher (RAM)** verarbeitet. Die **Speicherdauer beträgt 0 Sekunden**. Es werden keinerlei persönliche Textinhalte oder Protokolle auf dem Festspeicher abgelegt.

## 3. Sensible Berechtigungen und Verarbeitungszwecke
- **Benachrichtigungszugriff (`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`)**:
  - *Bereich*: Nur flüchtiger Arbeitsspeicher.
  - *Zweck*: Lauscht strikt auf festgelegte Messenger-Apps (`com.tencent.mm`, `com.tencent.mobileqq`, `com.tencent.tim`, `com.ss.android.lark`, `com.alibaba.android.rimet`) und aktive Mediensitzungen.
  - *Schutzmaßnahmen*: Bereitet Textinhalte für die fahrzeuggerechte Sprachausgabe auf. **Keine Speicherung auf Datenträgern, keine Weitergabe an Dritte. Beim Trennen der Verbindung zum Fahrzeug werden alle flüchtigen Daten sofort und vollständig vernichtet**.
- **Vordergrunddienst für Medienwiedergabe (`android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`)**:
  - Vorgeschrieben durch Android Auto, um die Steuerung bei gesperrtem Bildschirm aufrechtzuerhalten. Eine dauerhafte Statusbenachrichtigung gewährleistet vollständige Transparenz.
- **Internetzugriff (`android.permission.INTERNET`)**:
  - Streng beschränkt auf die in Abschnitt 4 genannten zwei rein lesenden HTTPS-Aufrufe ohne Übertragung von Gerätekennungen.

## 4. Ausgehende Netzwerkverbindungen
1. **Bedarfsweiser Online-Songtextabruf**: Überträgt ausschließlich **Titel und Interpret**, sofern keine Songtexte lokal vorliegen. Enthält keinerlei Geräte-IDs, IP-Tracking oder Cookies.
2. **GitHub-Versionsprüfung**: Regelmäßige schreibgeschützte Abfrage der offiziellen GitHub Releases API zur Prüfung auf Sicherheitsaktualisierungen.

## 5. Keine Drittanbieter-Tracker und keine Drittlandübermittlung
- **Keine Werbe- oder Analyse-SDKs**: Keine Einbindung von Firebase, Bugly, Google Analytics oder vergleichbaren Tracking-Diensten.
- **Keine Drittlandübermittlung (Art. 44 ff. DSGVO)**: Da alle Verarbeitungen ausschließlich lokal auf Ihrem Endgerät stattfinden, erfolgt keine Übermittlung personenbezogener Daten in Drittländer.

## 6. Speicherung, Löschung und Betroffenenrechte
- **Löschung (Art. 17 DSGVO)**: Nutzer können alle lokalen Einstellungen (Design, Standard-Player) jederzeit über die Android-Systemeinstellungen („Speicherinhalt löschen“) oder durch Deinstallation restlos entfernen.
- **Beschwerderecht (Art. 77 DSGVO)**: Sie haben das Recht, sich bei einer Datenschutz-Aufsichtsbehörde Ihres Wohnsitzes oder Arbeitsplatzes zu beschweren, wenn Sie der Ansicht sind, dass die Verarbeitung gegen die DSGVO verstößt.

## 7. Schutz von Minderjährigen und Haftungsausschluss
Die Anwendung richtet sich ausschließlich an Inhaber einer gültigen Fahrerlaubnis und nicht an Kinder unter 16 Jahren. Android Auto ist eine Marke von Google LLC. Alle genannten Drittanbieter-Warenzeichen dienen ausschließlich der technischen Kompatibilitätsbeschreibung.

---

# Part IV: 日本語 (第4優先順位)

## 1. 概要および基本原則
Fahrmony（以下「本アプリ」）は、Android Auto 車載システム向けに設計されたオープンソースの軽量ミドルウェアであり、スマートフォン内の音楽再生状態およびメッセージ通知を車載ディスプレイへ安全に中継・音声読み上げを行います。

本アプリは、**「ゼロ知識アーキテクチャおよび完全ローカル処理」**の原則を厳格に遵守します。独自の業務サーバー、ユーザーアカウント登録、広告追跡SDKは一切存在せず、テレメトリや行動履歴の収集・追跡は行いません。

## 2. 国際法規への準拠 (GDPR / 個人情報保護法)
本プライバシーポリシーは、EU一般データ保護規則（GDPR）、日本の個人情報の保護に関する法律（APPI）、およびGoogle Play 車載アプリケーション開発者ポリシーに準拠しています。

### 2.1 処理の適法根拠 (GDPR第6条)
- **第6条第1項(a) 利用者の明示的同意**：通知アクセス権限（`BIND_NOTIFICATION_LISTENER_SERVICE`）は、ユーザー自身が端末設定で許可することで有効化され、いつでも無効化（同意撤回）が可能です。
- **第6条第1項(b) 契約または指示の履行**：メディア再生情報の連携処理は、ユーザーが要求した車載画面での音楽制御・表示機能を果たすために限定して行われます。

### 2.2 データの最小化と保存制限 (GDPR第5条)
受信した通知データおよび再生セッションは、**端末の揮発性メモリ（RAM）上でのみ一時的に処理され、保存期間はゼロ**です。個人メッセージや通信履歴が不揮発性ストレージ（ディスク）に保存されることはありません。

## 3. 機密権限の利用目的と保護措置
- **通知へのアクセス権 (`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`)**：
  - *処理範囲*：端末の揮発性RAM内のみ。
  - *利用目的*：ホワイトリストで指定された通信アプリ（WeChat、QQ、TIM、Feishu、DingTalk等）および再生中メディアの通知のみを監視。
  - *保護措置*：テキストを車載標準の `MessagingStyle` 音声読み上げ形式に即座に変換。**ディスク保存・ログ出力・外部送信は一切行われず、車両切断（降車）時に検知機能（FahrmonyCarConnectionTracker）によりメモリから自動破棄**されます。
- **メディア再生フォアグラウンドサービス (`android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`)**：
  - 画面消灯時でも車載ディスプレイとの安定した低遅延通信を維持するためのAndroid Auto公式必須要件です。常駐通知により動作が常に透明化されます。
- **通知送信権限 (`android.permission.POST_NOTIFICATIONS`)**：
  - Android 13以降でフォアグラウンドサービス状態および車载メッセージカードを表示するためにのみ使用されます。
- **インターネットアクセス (`android.permission.INTERNET`)**：
  - 第4項に記載する2つの読み取り専用通信に限定され、端末識別子の送信は一切行われません。

## 4. 外部ネットワーク通信の開示
1. **オンライン歌詞の単方向取得**：再生中の楽曲に歌詞が含まれていない場合のみ、公開されたオンライン歌詞ソースから曲名とアーティスト名のみで検索を行います（端末固有情報、IP追跡、Cookieは含まれません）。
2. **GitHub Releases バージョン確認**：安全な利用のため、公式GitHub API（`https://api.github.com/repos/nexen33/Fahrmony/releases/latest`）へ定期的に読み取り専用リクエストを行い、最新リリースを確認します。

## 5. 第三者提供および国外移転の不存在
- **トラッカーの不使用**：広告SDK、マーケティング分析ツール、クラッシュ報告ツール（Firebase、Bugly等）は一切導入していません。
- **国外移転の不存在 (GDPR第44条以降)**：すべてのデータ処理はユーザー端末内で完結するため、個人データの第三国移転は発生しません。

## 6. データの保管・消去および利用者の権利
- **消去権 (GDPR第17条 / 忘れられる権利)**：端末の「設定」->「アプリ」->「Fahrmony」->「ストレージを消去」またはアプリのアンインストールにより、ローカル設定をいつでも完全に消去できます。
- **監督機関への不服申立て (GDPR第77条)**：EU居住者は、所轄のデータ保護監督機関に異議を申し立てる権利を有します。
- **情報販売・共有の禁止 (CCPA)**：個人情報の第三者への販売・共有は一切行いません。

## 7. 未成年者の保護および免責事項
本アプリは運転免許を所持するドライバー向けであり、16歳未満の児童を対象としておらず、児童の個人情報を故意に取得することはありません。Android AutoはGoogle LLCの商標です。記載されている第三者の名称・商標は技術的互換性の説明のためにのみ使用されています。
