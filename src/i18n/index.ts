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
    qqmusic: string;
    netease: string;
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
    sourcesTitle: string;
    sourceApp: string;
  };
  settings: {
    title: string;
    permSection: string;
    notifPerm: string;
    notifPermDesc: string;
    batteryPerm: string;
    batteryPermDesc: string;
    granted: string;
    toGrant: string;
    toConfig: string;
    language: string;
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
      standby: '车机待命',
      serviceRunning: '守护运行中',
      serviceStopped: '未启动',
      permReady: '权限就绪',
      permPending: '待授权',
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
      qqmusic: 'QQ 音乐',
      netease: '网易云音乐',
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
      safetyDesc: '为保障行车安全，车载屏幕仅单向播报消息提醒，不支持回复',
      configTitle: '播报与隐私设置',
      wechatToggle: '微信消息播报',
      wechatDesc: '转译微信通知并同步至车机屏幕',
      feishuToggle: '飞书消息播报',
      feishuDesc: '转译飞书通知并同步至车机屏幕',
      dingtalkToggle: '钉钉消息播报',
      dingtalkDesc: '转译钉钉通知并同步至车机屏幕',
      filterGroup: '忽略群聊消息',
      filterGroupDesc: '仅播报单聊联系人消息，减少驾驶干扰',
      recordTitle: '近期通知记录',
      clear: '清空',
      empty: '暂无通讯消息，微信、飞书或钉钉收到通知后将自动在此记录',
    },
    mediaTab: {
      title: '媒体控制',
      bannerTitle: '车载媒体互联',
      bannerDesc: '在手机端播放音乐和播客时，车机将会自动同步媒体信息\n也支持在车机端控制媒体播放',
      autoPlayTitle: '车载连接自动播放',
      autoPlayDesc: '连接 Android Auto 后，自动继续播放上次的音乐',
      defaultPlayerTitle: '默认音乐应用',
      defaultPlayerDesc: '车载切歌与快速启动时优先使用的音乐软件',
      sourcesTitle: '活跃播放源',
      sourceApp: '来源应用',
    },
    settings: {
      title: '系统权限与设置',
      permSection: '系统权限状态',
      notifPerm: '通知读取权限',
      notifPermDesc: '捕获微信、飞书与媒体播报',
      batteryPerm: '后台运行权限',
      batteryPermDesc: '允许后台常驻，防止系统查杀',
      granted: '已开启',
      toGrant: '去开启',
      toConfig: '去配置',
      language: '语言 (Language)',
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
      qqmusic: 'QQ Music',
      netease: 'NetEase Music',
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
      safetyDesc: 'Notifications are displayed read-only on the car display to prevent distracted driving. Keyboard reply is disabled.',
      configTitle: 'Relay & Privacy Settings',
      wechatToggle: 'WeChat Notifications',
      wechatDesc: 'Relay WeChat messages to head unit display',
      feishuToggle: 'Feishu Lark Notifications',
      feishuDesc: 'Relay Feishu messages to head unit display',
      dingtalkToggle: 'DingTalk Notifications',
      dingtalkDesc: 'Relay DingTalk messages to head unit display',
      filterGroup: 'Ignore Group Chats',
      filterGroupDesc: 'Only relay direct personal messages to minimize driving distractions',
      recordTitle: 'Recent Messages',
      clear: 'Clear',
      empty: 'No notifications captured yet.',
    },
    mediaTab: {
      title: 'Media Hub',
      bannerTitle: 'Android Auto Media Bridge',
      bannerDesc: 'Connected via universal MediaSession. Head unit displays cover art, track info, and playback controls seamlessly.',
      autoPlayTitle: 'Auto-Resume on Connect',
      autoPlayDesc: 'Automatically resume playback when connected to Android Auto',
      defaultPlayerTitle: 'Default Music Player',
      defaultPlayerDesc: 'Preferred music app for in-car controls and quick launch',
      sourcesTitle: 'Active Media Sessions',
      sourceApp: 'Application',
    },
    settings: {
      title: 'Settings & Permissions',
      permSection: 'System Permissions',
      notifPerm: 'Notification Access',
      notifPermDesc: 'Required to mirror chat messages and playback sessions to car screen',
      batteryPerm: 'Background Running',
      batteryPermDesc: 'Prevent system from killing services when screen locks',
      granted: 'Enabled',
      toGrant: 'Enable',
      toConfig: 'Configure',
      language: 'Language',
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
      qqmusic: 'QQ Music',
      netease: 'NetEase Music',
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
      safetyDesc: 'Nachrichten werden nur lesbar auf dem Display angezeigt, um Ablenkung während der Fahrt zu vermeiden.',
      configTitle: 'Übertragungs- & Datenschutzeinstellungen',
      wechatToggle: 'WeChat Benachrichtigungen',
      wechatDesc: 'WeChat Nachrichten an Head Unit übertragen',
      feishuToggle: 'Feishu Benachrichtigungen',
      feishuDesc: 'Feishu Nachrichten an Head Unit übertragen',
      dingtalkToggle: 'DingTalk Benachrichtigungen',
      dingtalkDesc: 'DingTalk Nachrichten an Head Unit übertragen',
      filterGroup: 'Gruppenchats Ignorieren',
      filterGroupDesc: 'Nur Direktnachrichten anzeigen, um Fahrablenkung zu minimieren',
      recordTitle: 'Nachrichtenverlauf',
      clear: 'Leeren',
      empty: 'Noch keine Benachrichtigungen erfasst.',
    },
    mediaTab: {
      title: 'Medienzentrale',
      bannerTitle: 'Android Auto Medienbrücke',
      bannerDesc: 'Über Standard-MediaSession angebunden. Anzeige von Albumcover, Titel und Steuerung im Fahrzeug.',
      autoPlayTitle: 'Autoplay bei Verbindung',
      autoPlayDesc: 'Setzt die Musikwiedergabe bei Verbindung mit dem Fahrzeug automatisch fort',
      defaultPlayerTitle: 'Standard-Musikplayer',
      defaultPlayerDesc: 'Bevorzugter Musikplayer für Fahrzeugsteuerung und Schnellstart',
      sourcesTitle: 'Aktive Medien-Sessions',
      sourceApp: 'Quell-Anwendung',
    },
    settings: {
      title: 'Einstellungen & Berechtigungen',
      permSection: 'Systemberechtigungen',
      notifPerm: 'Benachrichtigungszugriff',
      notifPermDesc: 'Erforderlich zur Anzeige von Nachrichten und Musik auf dem Fahrzeugdisplay',
      batteryPerm: 'Hintergrundbetrieb',
      batteryPermDesc: 'Verhindert das Schließen des Dienstes im Hintergrund',
      granted: 'Aktiviert',
      toGrant: 'Aktivieren',
      toConfig: 'Konfigurieren',
      language: 'Sprache (Language)',
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
      qqmusic: 'QQ 音楽',
      netease: 'NetEase Music',
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
      safetyDesc: '運転中の安全を最優先するため、車載画面には通知のみを読み取り専用で表示します。',
      configTitle: '通知・プライバシー設定',
      wechatToggle: 'WeChat 通知連携',
      wechatDesc: 'WeChat メッセージを車載画面に転送',
      feishuToggle: 'Feishu 通知連携',
      feishuDesc: 'Feishu メッセージを車载画面に転送',
      dingtalkToggle: 'DingTalk 通知連携',
      dingtalkDesc: 'DingTalk メッセージを車载画面に転送',
      filterGroup: 'グループチャットを除外',
      filterGroupDesc: '個別メッセージのみを転送し、運転中の通知頻度を抑制',
      recordTitle: '最近の通知履歴',
      clear: '消去',
      empty: '受信通知はありません。',
    },
    mediaTab: {
      title: 'メディア操作',
      bannerTitle: '車載メディア連携',
      bannerDesc: 'システム標準の MediaSession と連携。楽曲情報、アルバムアート、再生操作が車載画面と自動同期します。',
      autoPlayTitle: '車載機接続時に自動再生',
      autoPlayDesc: '車載機に接続時、前回の音楽再生を自動で再开します',
      defaultPlayerTitle: 'デフォルト音楽アプリ',
      defaultPlayerDesc: '車載操作およびクイック起動で優先する音楽アプリ',
      sourcesTitle: 'アクティブな再生ソース',
      sourceApp: '再生元アプリ',
    },
    settings: {
      title: '権限と設定',
      permSection: 'システム権限状況',
      notifPerm: '通知アクセス権限',
      notifPermDesc: '車載画面へのメッセージ通知および楽曲同期に必要',
      batteryPerm: 'バックグラウンド実行',
      batteryPermDesc: '画面消灯時のバックグラウンド切断を防止',
      granted: '許可済み',
      toGrant: '設定する',
      toConfig: '確認する',
      language: '言語 (Language)',
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
    },
  },
};
