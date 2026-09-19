// 零外部依赖类型安全 i18n 语言词典 (中/英/德/日)

export type LanguageKey = 'zh-CN' | 'en-US' | 'de-DE' | 'ja-JP';

export interface Translations {
  appName: string;
  tagline: string;
  tabs: {
    overview: string;
    notifications: string;
    media: string;
    settings: string;
  };
  status: {
    connected: string;
    standby: string;
    serviceRunning: string;
    serviceStopped: string;
    permReady: string;
    permPending: string;
    carConnectedToast: string;
  };
  nowPlaying: {
    title: string;
    notPlayingTitle: string;
    emptyTitle: string;
    emptyDesc: string;
    unknownArtist: string;
    mediaService: string;
    openPlayer: string;
    switchPlayer: string;
    selectDefault: string;
    playing: string;
    paused: string;
  };
  bridge: {
    title: string;
    imGroup: string;
    mediaGroup: string;
    enabled: string;
    disabled: string;
    wechat: string;
    feishu: string;
    dingtalk: string;
    qq: string;
    qqmusic: string;
    netease: string;
    qishui: string;
    bodian: string;
    kugou: string;
    kuwo: string;
    ximalaya: string;
    xiaoyuzhou: string;
    imDesc: string;
    mediaDesc: string;
    podcastDesc: string;
  };
  notifications: {
    title: string;
    safetyTitle: string;
    safetyDesc: string;
    configTitle: string;
    wechatToggle: string;
    wechatDesc: string;
    feishuToggle: string;
    feishuDesc: string;
    dingtalkToggle: string;
    dingtalkDesc: string;
    qqToggle: string;
    qqDesc: string;
    filterGroup: string;
    filterGroupDesc: string;
    recordTitle: string;
    clear: string;
    empty: string;
  };
  mediaTab: {
    title: string;
    bannerTitle: string;
    bannerDesc: string;
    autoPlayTitle: string;
    autoPlayDesc: string;
    defaultPlayerTitle: string;
    defaultPlayerDesc: string;
    rawCardTitle: string;
    rawCardDesc: string;
    lyricsTitle: string;
    lyricsBadge: string;
    lyricsDesc: string;
    lyricsModeOff: string;
    lyricsModeSingle: string;
    lyricsModeDual: string;
    sourcesTitle: string;
    sourceApp: string;
    selectPlayerPlaceholder: string;
    customAppSubtitle: string;
  };
  customPlayer: {
    slotTitle: string;
    emptySlot: string;
    detectTitle: string;
    detectHint: string;
    noDetected: string;
    confirmAdd: string;
    cancel: string;
    removeTitle: string;
    removeDesc: string;
    removeConfirm: string;
    discoveredBannerTitle: string;
    discoveredBannerAdd: string;
    customTag: string;
  };
  settings: {
    title: string;
    permSection: string;
    postNotifPerm: string;
    postNotifPermDesc: string;
    notifPerm: string;
    notifPermDesc: string;
    batteryPerm: string;
    batteryPermDesc: string;
    granted: string;
    toGrant: string;
    toConfig: string;
    language: string;
    themeModeTitle: string;
    themeLight: string;
    themeDark: string;
    themeSystem: string;
    themeLightToast: string;
    themeDarkToast: string;
    themeSystemToast: string;
    restrictedTitle: string;
    restrictedDesc: string;
    aaConfigTitle: string;
    aaConfigDesc: string;
    aboutTitle: string;
    aboutDesc: string;
    version: string;
    techStack: string;
    changelog: string;
    info: string;
    close: string;
    aboutTarget: string;
    copyright: string;
    checkingUpdateToast: string;
    upToDateToast: string;
    updateFoundToast: string;
  };
}

export const translations: Record<LanguageKey, Translations> = {
  'zh-CN': {
    appName: 'Fahrmony',
    tagline: '合拍',
    tabs: {
      overview: '概览',
      notifications: '通知',
      media: '媒体',
      settings: '设置',
    },
    status: {
      connected: '车机已连接',
      standby: '车机待连接',
      serviceRunning: '守护运行中',
      serviceStopped: '未启动',
      permReady: '权限就绪',
      permPending: '待授权',
      carConnectedToast: '车机连接成功',
    },
    nowPlaying: {
      title: '正在播放',
      notPlayingTitle: '未播放',
      emptyTitle: '未播放',
      emptyDesc: '请打开对应App播放，即可同步至车机',
      unknownArtist: '未知艺术家',
      mediaService: '车载媒体',
      openPlayer: '启动播放器',
      switchPlayer: '切换播放源',
      selectDefault: '设为默认',
      playing: '播放中',
      paused: '暂停',
    },
    bridge: {
      title: '支持的应用',
      imGroup: '通讯通知播报',
      mediaGroup: '车载媒体音频',
      enabled: '已启用',
      disabled: '已停用',
      wechat: '微信',
      feishu: '飞书',
      dingtalk: '钉钉',
      qq: 'QQ',
      qqmusic: 'QQ 音乐',
      netease: '网易云音乐',
      qishui: '汽水音乐',
      bodian: '波点音乐',
      kugou: '酷狗音乐',
      kuwo: '酷我音乐',
      ximalaya: '喜马拉雅',
      xiaoyuzhou: '小宇宙',
      imDesc: '消息播报',
      mediaDesc: '媒体控制',
      podcastDesc: '音频播客',
    },
    notifications: {
      title: '通知管理',
      safetyTitle: '行车安全提示',
      safetyDesc: '为保障行车安全，车机仅单向播报消息提醒，不支持回复',
      configTitle: '播报与隐私设置',
      wechatToggle: '微信消息播报',
      wechatDesc: '转译微信通知并同步至车机屏幕',
      feishuToggle: '飞书消息播报',
      feishuDesc: '转译飞书通知并同步至车机屏幕',
      dingtalkToggle: '钉钉消息播报',
      dingtalkDesc: '转译钉钉通知并同步至车机屏幕',
      qqToggle: 'QQ消息播报',
      qqDesc: '转译QQ通知并同步至车机屏幕',
      filterGroup: '忽略群聊消息',
      filterGroupDesc: '仅播报单聊联系人消息，减少驾驶干扰',
      recordTitle: '近期通知记录',
      clear: '清空',
      empty: '暂无通讯消息，开启播报的通讯App收到通知后将自动记录在此',
    },
    mediaTab: {
      title: '媒体控制',
      bannerTitle: '车载媒体互联',
      bannerDesc: '在手机端播放音乐和播客时，车机将会自动同步媒体信息\n也支持在车机端控制媒体播放',
      autoPlayTitle: '车载连接自动播放',
      autoPlayDesc: '连接 Android Auto 后，自动继续播放上次的音频',
      defaultPlayerTitle: '默认音频 App',
      defaultPlayerDesc: '车载切歌与快速启动时优先使用的音频App',
      rawCardTitle: '音频 App 原始播放卡片',
      rawCardDesc: '开启后将显示原始播放卡片，按键颜色会受封面影响',
      lyricsTitle: '车载实时歌词',
      lyricsBadge: 'BETA',
      lyricsDesc: 'QQ/网易云高度适配，其他音源视歌词资源而定',
      lyricsModeOff: '关闭',
      lyricsModeSingle: '单行',
      lyricsModeDual: '双行',
      sourcesTitle: '活跃播放源',
      sourceApp: '来源应用',
      selectPlayerPlaceholder: '请选择播放器',
      customAppSubtitle: '自定义添加的音频 App 可能仅支持基础播控',
    },
    customPlayer: {
      slotTitle: '自定义音源',
      emptySlot: '添加自定义音源',
      detectTitle: '选择已检测到的音频 App',
      detectHint: '如果这里没有显示你想要的App，\n请开启并播放你需要的音频App，便于检测',
      noDetected: '未检测到正在播放的其他音频 App',
      confirmAdd: '确认添加',
      cancel: '取消',
      removeTitle: '移除自定义音源',
      removeDesc: '确定要移除已添加的自定义音频 App\n「{name}」吗？',
      removeConfirm: '确认移除',
      discoveredBannerTitle: '检测到后台有未列出音源: ',
      discoveredBannerAdd: '添加',
      customTag: '自定义',
    },
    settings: {
      title: '系统权限与设置',
      permSection: '系统权限状态',
      postNotifPerm: '通知提醒权限',
      postNotifPermDesc: '展示前台服务与车载状态通知',
      notifPerm: '通知读取权限',
      notifPermDesc: '捕获IM通知与媒体状态',
      batteryPerm: '后台运行权限',
      batteryPermDesc: '允许后台常驻，防止系统查杀',
      granted: '已开启',
      toGrant: '去开启',
      toConfig: '去配置',
      language: '语言 (Language)',
      themeModeTitle: '外观偏好',
      themeLight: '浅色模式',
      themeDark: '深色模式',
      themeSystem: '跟随系统',
      themeLightToast: '已切换为浅色模式',
      themeDarkToast: '已切换为深色模式',
      themeSystemToast: '已设为跟随系统偏好',
      restrictedTitle: '若遇“受限设置”无法开启',
      restrictedDesc: 'Android 13+ 出于系统安全策略可能会限制侧载应用的通知权限：\n1. 打开手机 系统设置 → 应用管理\n2. 找到 Fahrmony 应用信息页\n3. 点击右上角 更多菜单 (⋮)\n4. 选择 “允许受限设置”，完成指纹/密码验证后即可开启',
      aaConfigTitle: 'Android Auto 车机端配置',
      aaConfigDesc: '1. 打开手机 Android Auto 设置页面\n2. 滑动至底部连续点击 “版本号” 10 次 开启开发者选项\n3. 点击右上角菜单进入 “开发者设置”\n4. 勾选 “未知来源 (Unknown sources)” 即可连接真实车辆',
      aboutTitle: '关于 Fahrmony',
      aboutDesc: '版本与技术架构说明',
      version: '版本',
      techStack: '核心架构',
      changelog: '更新日志',
      info: '关于信息',
      close: '关闭',
      aboutTarget: '为部分中国 App 的 Android Auto 适配打造',
      copyright: '© 2026 Tun&PaMa AG',
      checkingUpdateToast: '正在检查更新...',
      upToDateToast: '当前版本为最新',
      updateFoundToast: '发现更新的版本，点击跳转下载',
    },
  },
  'en-US': {
    appName: 'Fahrmony',
    tagline: 'AA-Plugin',
    tabs: {
      overview: 'Overview',
      notifications: 'Messages',
      media: 'Media',
      settings: 'Settings',
    },
    status: {
      connected: 'Connected',
      standby: 'Standby',
      serviceRunning: 'Active',
      serviceStopped: 'Stopped',
      permReady: 'Ready',
      permPending: 'Required',
      carConnectedToast: 'Vehicle Connected Successfully',
    },
    nowPlaying: {
      title: 'Now Playing',
      notPlayingTitle: 'Not Playing',
      emptyTitle: 'No Media Playing',
      emptyDesc: 'Play audio on your phone to mirror playback to Android Auto',
      unknownArtist: 'Unknown Artist',
      mediaService: 'In-Car Media',
      openPlayer: 'Launch Player',
      switchPlayer: 'Switch Source',
      selectDefault: 'Set Default',
      playing: 'Playing',
      paused: 'Paused',
    },
    bridge: {
      title: 'Supported Apps',
      imGroup: 'Messaging Notification',
      mediaGroup: 'In-Car Audio',
      enabled: 'Enabled',
      disabled: 'Disabled',
      wechat: 'WeChat',
      feishu: 'Feishu Lark',
      dingtalk: 'DingTalk',
      qq: 'QQ',
      qqmusic: 'QQ Music',
      netease: 'NetEase Music',
      qishui: 'Soda Music',
      bodian: 'Bodian Music',
      kugou: 'Kugou Music',
      kuwo: 'Kuwo Music',
      ximalaya: 'Ximalaya',
      xiaoyuzhou: 'Xiaoyuzhou',
      imDesc: 'Message Relay',
      mediaDesc: 'Media Controls',
      podcastDesc: 'Podcast Stream',
    },
    notifications: {
      title: 'Notifications',
      safetyTitle: 'Driver Safety Notice',
      safetyDesc: 'Notifications are displayed read-only on the head unit to prevent distracted driving. Keyboard reply is disabled.',
      configTitle: 'Relay & Privacy Settings',
      wechatToggle: 'WeChat Notifications',
      wechatDesc: 'Relay WeChat messages to head unit display',
      feishuToggle: 'Feishu Lark Notifications',
      feishuDesc: 'Relay Feishu messages to head unit display',
      dingtalkToggle: 'DingTalk Notifications',
      dingtalkDesc: 'Relay DingTalk messages to head unit display',
      qqToggle: 'QQ Notifications',
      qqDesc: 'Relay QQ messages to head unit display',
      filterGroup: 'Ignore Group Chats',
      filterGroupDesc: 'Only relay direct personal messages to minimize driving distractions',
      recordTitle: 'Recent Messages',
      clear: 'Clear',
      empty: 'No communication messages yet. Incoming notifications from active communication apps will be recorded here automatically',
    },
    mediaTab: {
      title: 'Media Hub',
      bannerTitle: 'Android Auto Media Bridge',
      bannerDesc: 'Connected via universal MediaSession. Head unit displays cover art, track info, and playback controls seamlessly.',
      autoPlayTitle: 'Auto-Resume on Connect',
      autoPlayDesc: 'Automatically resume playback of previous audio when connected to Android Auto',
      defaultPlayerTitle: 'Default Audio App',
      defaultPlayerDesc: 'Preferred audio app for in-car controls and quick launch',
      rawCardTitle: 'Audio App Original Playing Card',
      rawCardDesc: 'Enabling shows original playing card; button colors may be affected by cover art',
      lyricsTitle: 'In-Car Live Lyrics',
      lyricsBadge: 'BETA',
      lyricsDesc: 'QQ & NetEase adapted, others depend on resources',
      lyricsModeOff: 'Off',
      lyricsModeSingle: 'Single',
      lyricsModeDual: 'Dual',
      sourcesTitle: 'Active Media Sessions',
      sourceApp: 'Application',
      selectPlayerPlaceholder: 'Select a player',
      customAppSubtitle: 'Custom audio apps may only support basic playback controls',
    },
    customPlayer: {
      slotTitle: 'Custom Audio',
      emptySlot: 'Add Custom Player',
      detectTitle: 'Select Detected Audio App',
      detectHint: 'If your app is not listed here,\nplease start playing audio in it for detection',
      noDetected: 'No active third-party audio apps detected',
      confirmAdd: 'Add Player',
      cancel: 'Cancel',
      removeTitle: 'Remove Custom Audio',
      removeDesc: 'Are you sure you want to remove custom audio app\n"{name}"?',
      removeConfirm: 'Remove',
      discoveredBannerTitle: 'Detected unlisted audio playing: ',
      discoveredBannerAdd: 'Add',
      customTag: 'Custom',
    },
    settings: {
      title: 'Settings & Permissions',
      permSection: 'System Permissions',
      postNotifPerm: 'Notification Permission',
      postNotifPermDesc: 'Show foreground service & car status alerts',
      notifPerm: 'Notification Access',
      notifPermDesc: 'Capture IM notifications & media state',
      batteryPerm: 'Background Running',
      batteryPermDesc: 'Prevent system from killing services when screen locks',
      granted: 'Enabled',
      toGrant: 'Enable',
      toConfig: 'Configure',
      language: 'Language',
      themeModeTitle: 'Appearance',
      themeLight: 'Light Mode',
      themeDark: 'Dark Mode',
      themeSystem: 'System Preference',
      themeLightToast: 'Switched to Light Mode',
      themeDarkToast: 'Switched to Dark Mode',
      themeSystemToast: 'Following System Preference',
      restrictedTitle: 'Restricted Settings Guide (Android 13+)',
      restrictedDesc: 'If Android blocks notification access as "Restricted setting":\n1. Go to Settings → Apps → Fahrmony\n2. Tap the top-right menu (⋮)\n3. Tap "Allow restricted settings"\n4. Authenticate with fingerprint/PIN.',
      aaConfigTitle: 'Android Auto Developer Setup',
      aaConfigDesc: '1. Open Android Auto settings on phone\n2. Tap "Version" 10 times at the bottom to unlock Developer Settings\n3. Tap top-right menu → Developer settings\n4. Check "Unknown sources" to connect with vehicle',
      aboutTitle: 'About Fahrmony',
      aboutDesc: 'Version & System Architecture',
      version: 'Version',
      techStack: 'Tech Stack',
      changelog: 'Changelog',
      info: 'About Info',
      close: 'Close',
      aboutTarget: 'Tailored for Android Auto & Chinese Apps',
      copyright: '© 2026 Tun&PaMa AG',
      checkingUpdateToast: 'Checking for updates...',
      upToDateToast: 'Current version is up to date',
      updateFoundToast: 'Newer version found, tap to download',
    },
  },
  'de-DE': {
    appName: 'Fahrmony',
    tagline: 'AA-Plugin',
    tabs: {
      overview: 'Übersicht',
      notifications: 'Nachrichten',
      media: 'Medien',
      settings: 'Optionen',
    },
    status: {
      connected: 'Verbunden',
      standby: 'Bereit',
      serviceRunning: 'Aktiv',
      serviceStopped: 'Inaktiv',
      permReady: 'Bereit',
      permPending: 'Erforderlich',
      carConnectedToast: 'Fahrzeug erfolgreich verbunden',
    },
    nowPlaying: {
      title: 'Aktuelle Wiedergabe',
      notPlayingTitle: 'Keine Wiedergabe',
      emptyTitle: 'Keine Medienwiedergabe',
      emptyDesc: 'Musik auf dem Telefon starten, um Steuerung auf Android Auto zu spiegeln',
      unknownArtist: 'Unbekannter Künstler',
      mediaService: 'Fahrzeug-Medien',
      openPlayer: 'Player Starten',
      switchPlayer: 'Quelle Wechseln',
      selectDefault: 'Als Standard',
      playing: 'Wiedergabe',
      paused: 'Pausiert',
    },
    bridge: {
      title: 'Unterstützte Apps',
      imGroup: 'Nachrichtenbenachrichtigung',
      mediaGroup: 'Fahrzeug-Audiosystem',
      enabled: 'Aktiv',
      disabled: 'Inaktiv',
      wechat: 'WeChat',
      feishu: 'Feishu Lark',
      dingtalk: 'DingTalk',
      qq: 'QQ',
      qqmusic: 'QQ Music',
      netease: 'NetEase Music',
      qishui: 'Soda Music',
      bodian: 'Bodian Music',
      kugou: 'Kugou Music',
      kuwo: 'Kuwo Music',
      ximalaya: 'Ximalaya',
      xiaoyuzhou: 'Xiaoyuzhou',
      imDesc: 'Nachrichtenübertragung',
      mediaDesc: 'Mediensteuerung',
      podcastDesc: 'Podcast-Stream',
    },
    notifications: {
      title: 'Benachrichtigungen',
      safetyTitle: 'Fahrsicherheitshinweis',
      safetyDesc: 'Nachrichten werden nur lesbar auf der Head Unit angezeigt, um Ablenkung während der Fahrt zu vermeiden.',
      configTitle: 'Übertragungs- & Datenschutzeinstellungen',
      wechatToggle: 'WeChat Benachrichtigungen',
      wechatDesc: 'WeChat Nachrichten an Head Unit übertragen',
      feishuToggle: 'Feishu Benachrichtigungen',
      feishuDesc: 'Feishu Nachrichten an Head Unit übertragen',
      dingtalkToggle: 'DingTalk Benachrichtigungen',
      dingtalkDesc: 'DingTalk Nachrichten an Head Unit übertragen',
      qqToggle: 'QQ Benachrichtigungen',
      qqDesc: 'QQ Nachrichten an Head Unit übertragen',
      filterGroup: 'Gruppenchats Ignorieren',
      filterGroupDesc: 'Nur Direktnachrichten anzeigen, um Fahrablenkung zu minimieren',
      recordTitle: 'Nachrichtenverlauf',
      clear: 'Leeren',
      empty: 'Noch keine Nachrichten. Benachrichtigungen von aktiven Messenger-Apps werden hier automatisch aufgezeichnet',
    },
    mediaTab: {
      title: 'Medienzentrale',
      bannerTitle: 'Android Auto Medienbrücke',
      bannerDesc: 'Über Standard-MediaSession angebunden. Anzeige von Albumcover, Titel und Steuerung im Fahrzeug.',
      autoPlayTitle: 'Autoplay bei Verbindung',
      autoPlayDesc: 'Setzt die Audiowiedergabe bei Verbindung mit dem Fahrzeug automatisch fort',
      defaultPlayerTitle: 'Standard-Audio-App',
      defaultPlayerDesc: 'Bevorzugte Audio-App für Fahrzeugsteuerung und Schnellstart',
      rawCardTitle: 'Original-Wiedergabekarte der Audio-App',
      rawCardDesc: 'Bei Aktivierung wird die Originalkarte angezeigt; Tastenfarben können vom Cover beeinflusst werden',
      lyricsTitle: 'Live-Songtexte im Auto',
      lyricsBadge: 'BETA',
      lyricsDesc: 'QQ & NetEase angepasst, andere nach Ressourcen',
      lyricsModeOff: 'Aus',
      lyricsModeSingle: 'Einzeln',
      lyricsModeDual: 'Doppelt',
      sourcesTitle: 'Aktive Medien-Sessions',
      sourceApp: 'Quell-Anwendung',
      selectPlayerPlaceholder: 'Player auswählen',
      customAppSubtitle: 'Benutzerdefinierte Apps unterstützen evtl. nur Basis-Steuerung',
    },
    customPlayer: {
      slotTitle: 'Eigener Player',
      emptySlot: 'Player hinzufügen',
      detectTitle: 'Erkannte Audio-App wählen',
      detectHint: 'Falls Ihre App nicht angezeigt wird,\nbitte Wiedergabe in der App starten',
      noDetected: 'Keine aktiven Drittanbieter-Audio-Apps erkannt',
      confirmAdd: 'Hinzufügen',
      cancel: 'Abbrechen',
      removeTitle: 'Eigenen Player entfernen',
      removeDesc: 'Möchten Sie die Audio-App\n"{name}" wirklich entfernen?',
      removeConfirm: 'Entfernen',
      discoveredBannerTitle: 'Nicht gelistete Audio-App erkannt: ',
      discoveredBannerAdd: 'Hinzufügen',
      customTag: 'Eigen',
    },
    settings: {
      title: 'Einstellungen & Berechtigungen',
      permSection: 'Systemberechtigungen',
      postNotifPerm: 'Benachrichtigungsberechtigung',
      postNotifPermDesc: 'Vordergrunddienst & Fahrzeugstatus anzeigen',
      notifPerm: 'Benachrichtigungszugriff',
      notifPermDesc: 'IM-Benachrichtigungen & Medienstatus erfassen',
      batteryPerm: 'Hintergrundbetrieb',
      batteryPermDesc: 'Verhindert das Schließen des Dienstes im Hintergrund',
      granted: 'Aktiviert',
      toGrant: 'Aktivieren',
      toConfig: 'Konfigurieren',
      language: 'Sprache (Language)',
      themeModeTitle: 'Erscheinungsbild',
      themeLight: 'Heller Modus',
      themeDark: 'Dunkler Modus',
      themeSystem: 'Systemstandard',
      themeLightToast: 'Zu hellem Modus gewechselt',
      themeDarkToast: 'Zu dunklem Modus gewechselt',
      themeSystemToast: 'Folgt dem Systemstandard',
      restrictedTitle: 'Eingeschränkte Einstellungen (Android 13+)',
      restrictedDesc: 'Wenn Android den Benachrichtigungszugriff einschränkt:\n1. Einstellungen → Apps → Fahrmony öffnen\n2. Menü oben rechts (⋮) antippen\n3. "Eingeschränkte Einstellungen zulassen" wählen.',
      aaConfigTitle: 'Android Auto Entwickleroptionen',
      aaConfigDesc: '1. Android Auto Einstellungen öffnen\n2. Unten 10-mal auf "Version" tippen\n3. Entwicklereinstellungen → "Unbekannte Quellen" aktivieren für Fahrzeugverbindung',
      aboutTitle: 'Über Fahrmony',
      aboutDesc: 'Version & Systemarchitektur',
      version: 'Version',
      techStack: 'Kerntechnologie',
      changelog: 'Changelog',
      info: 'Info',
      close: 'Schließen',
      aboutTarget: 'Optimiert für Android Auto & ausgewählte Apps',
      copyright: '© 2026 Tun&PaMa AG',
      checkingUpdateToast: 'Suche nach Updates...',
      upToDateToast: 'Aktuelle Version ist auf dem neuesten Stand',
      updateFoundToast: 'Neuere Version gefunden, tippen zum Herunterladen',
    },
  },
  'ja-JP': {
    appName: 'Fahrmony',
    tagline: 'AA-Plugin',
    tabs: {
      overview: '概要',
      notifications: '通知',
      media: 'メディア',
      settings: '設定',
    },
    status: {
      connected: '接続済み',
      standby: '待機中',
      serviceRunning: '常駐中',
      serviceStopped: '停止中',
      permReady: '設定済み',
      permPending: '要設定',
      carConnectedToast: '車載機器に接続しました',
    },
    nowPlaying: {
      title: '再生中',
      notPlayingTitle: '再生停止中',
      emptyTitle: '再生中のメディアはありません',
      emptyDesc: 'スマートフォンの音楽アプリで再生を開始すると、車載ディスプレイに自動同期されます',
      unknownArtist: '不明なアーティスト',
      mediaService: '車載メディア',
      openPlayer: 'アプリ起動',
      switchPlayer: 'ソース切替',
      selectDefault: 'デフォルト設定',
      playing: '再生中',
      paused: '一時停止',
    },
    bridge: {
      title: '対応アプリ',
      imGroup: 'メッセージ通知連携',
      mediaGroup: '車載オーディオメディア',
      enabled: '有効',
      disabled: '無効',
      wechat: 'WeChat',
      feishu: 'Feishu Lark',
      dingtalk: 'DingTalk',
      qq: 'QQ',
      qqmusic: 'QQ 音楽',
      netease: 'NetEase Music',
      qishui: 'ソーダ音楽',
      bodian: '波点音楽',
      kugou: 'KuGou 音楽',
      kuwo: 'KuWo 音楽',
      ximalaya: 'シマラヤ',
      xiaoyuzhou: '小宇宙',
      imDesc: 'メッセージ通知',
      mediaDesc: 'メディア操作',
      podcastDesc: 'ポッドキャスト',
    },
    notifications: {
      title: '通知管理',
      safetyTitle: '安全運転に関するご注意',
      safetyDesc: '運転中の安全を最優先するため、車載機には通知のみを読み取り専用で表示します。',
      configTitle: '通知・プライバシー設定',
      wechatToggle: 'WeChat 通知連携',
      wechatDesc: 'WeChat メッセージを車載画面に転送',
      feishuToggle: 'Feishu 通知連携',
      feishuDesc: 'Feishu メッセージを車载画面に転送',
      dingtalkToggle: 'DingTalk 通知連携',
      dingtalkDesc: 'DingTalk メッセージを車载画面に転送',
      qqToggle: 'QQ 通知連携',
      qqDesc: 'QQ メッセージを車載画面に転送',
      filterGroup: 'グループチャットを除外',
      filterGroupDesc: '個別メッセージのみを転送し、運転中の通知頻度を抑制',
      recordTitle: '最近の通知履歴',
      clear: '消去',
      empty: '通信メッセージはありません。通知連携が有効なアプリから受信するとここに自動記録されます',
    },
    mediaTab: {
      title: 'メディア操作',
      bannerTitle: '車載メディア連携',
      bannerDesc: 'システム標準の MediaSession と連携。楽曲情報、アルバムアート、再生操作が車載画面と自動同期します。',
      autoPlayTitle: '車載機接続時に自動再生',
      autoPlayDesc: '車載機に接続時、前回のオーディオ再生を自動で再開します',
      defaultPlayerTitle: 'デフォルトオーディオApp',
      defaultPlayerDesc: '車載操作およびクイック起動で優先するオーディオApp',
      rawCardTitle: 'オーディオAppのオリジナル再生カード',
      rawCardDesc: '有効にするとオリジナルの再生カードを表示します（ボタンの色がカバーの影響を受ける場合があります）',
      lyricsTitle: '車載リアルタイム歌詞',
      lyricsBadge: 'BETA',
      lyricsDesc: 'QQ/NetEase高度対応、他は音源次第',
      lyricsModeOff: 'オフ',
      lyricsModeSingle: '1行',
      lyricsModeDual: '2行',
      sourcesTitle: 'アクティブな再生ソース',
      sourceApp: '再生元アプリ',
      selectPlayerPlaceholder: '音楽アプリを選択',
      customAppSubtitle: 'カスタム追加のアプリは基本再生制御のみ対応する場合があります',
    },
    customPlayer: {
      slotTitle: 'カスタム音源',
      emptySlot: '音源を追加',
      detectTitle: '検出された音楽アプリを選択',
      detectHint: '希望のアプリが表示されない場合は、\n該当アプリで音声を再生してください',
      noDetected: '再生中の外部音楽アプリが検出されませんでした',
      confirmAdd: '追加する',
      cancel: 'キャンセル',
      removeTitle: 'カスタム音源の解除',
      removeDesc: 'カスタム音楽アプリ\n「{name}」を解除しますか？',
      removeConfirm: '解除する',
      discoveredBannerTitle: 'バックグラウンドで未登録音源を検出: ',
      discoveredBannerAdd: '追加',
      customTag: 'カスタム',
    },
    settings: {
      title: '権限と設定',
      permSection: 'システム権限状況',
      postNotifPerm: '通知リマインダー権限',
      postNotifPermDesc: 'フォアグラウンドサービスと車載状態の通知を表示',
      notifPerm: '通知アクセス権限',
      notifPermDesc: 'IM通知とメディアステータスをキャプチャ',
      batteryPerm: 'バックグラウンド実行',
      batteryPermDesc: '画面消灯時のバックグラウンド切断を防止',
      granted: '許可済み',
      toGrant: '設定する',
      toConfig: '確認する',
      language: '言語 (Language)',
      themeModeTitle: '外観設定',
      themeLight: 'ライトモード',
      themeDark: 'ダークモード',
      themeSystem: 'システムに従う',
      themeLightToast: 'ライトモードに切り替えました',
      themeDarkToast: 'ダークモードに切り替えました',
      themeSystemToast: 'システム設定に従います',
      restrictedTitle: '制限付き設定が表示された場合 (Android 13+)',
      restrictedDesc: 'Android のセキュリティ保護によりアクセスが制限される場合：\n1. 設定 → アプリ → Fahrmony を開く\n2. 右上のメニュー (⋮) をタップ\n3. 「制限付き設定を許可」を選択し認証してください',
      aaConfigTitle: 'Android Auto 開発者設定',
      aaConfigDesc: '1. Android Auto の設定画面を開く\n2. 最下部の「バージョン」を10回連続タップ\n3. 右上メニューの「デベロッパー向け設定」を開く\n4. 「提供元不明のアプリ」にチェックを入れて車両に接続',
      aboutTitle: 'Fahrmony について',
      aboutDesc: 'バージョンとシステム仕様',
      version: 'バージョン',
      techStack: 'コア技術',
      changelog: '更新履歴',
      info: '基本情報',
      close: '閉じる',
      aboutTarget: 'Android Auto 向け中国主要アプリ最適化',
      copyright: '© 2026 Tun&PaMa AG',
      checkingUpdateToast: 'アップデートを確認中...',
      upToDateToast: '現在のバージョンは最新です',
      updateFoundToast: '新しいバージョンが見つかりました、タップしてダウンロード',
    },
  },
};
