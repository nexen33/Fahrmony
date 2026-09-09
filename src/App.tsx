import { useState, useEffect, useLayoutEffect, useRef, useMemo } from 'react';
import { Preferences } from '@capacitor/preferences';
import { Capacitor } from '@capacitor/core';
import FahrmonyPlugin, {
  type PermissionStatusResult,
  type BridgeStatusResult,
  type MediaSessionItem,
  type BridgeLogEntry,
  type AppBridgeConfig,
} from './plugins/FahrmonyPlugin.ts';
import { translations, type LanguageKey } from './i18n/index.ts';
import { Switch } from './components/Switch.tsx';
import { CustomSelect } from './components/CustomSelect.tsx';

// 392dp 基准全局屏幕动态自适应 (遵循老项目规范 line 419)
const calculateZoomRatio = () => {
  if (typeof window === 'undefined') return 1;
  const minDimension = Math.min(window.screen.width, window.screen.height);
  const ratio = minDimension / 392;
  return Math.max(0.85, Math.min(1.25, Number(ratio.toFixed(3))));
};

const APP_ZOOM_RATIO = calculateZoomRatio();

// SVG 图标原语 (遵循 design_taste_v1 严禁 Emoji 规则)
const Icons = {
  Car: ({ size = 22 }: { size?: number }) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2" />
      <circle cx="7" cy="17" r="2" />
      <path d="M9 17h6" />
      <circle cx="17" cy="17" r="2" />
    </svg>
  ),
  Bell: ({ size = 20 }: { size?: number }) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
      <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
    </svg>
  ),
  Music: ({ size = 20 }: { size?: number }) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 18V5l12-2v13" />
      <circle cx="6" cy="18" r="3" />
      <circle cx="18" cy="16" r="3" />
    </svg>
  ),
  Settings: ({ size = 20 }: { size?: number }) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  ),
  Shield: () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10" />
    </svg>
  ),
  Sun: () => (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
    </svg>
  ),
  Moon: () => (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z" />
    </svg>
  ),
  Play: () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
      <polygon points="5 3 19 12 5 21 5 3" />
    </svg>
  ),
  Pause: () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
      <rect x="6" y="4" width="4" height="16" />
      <rect x="14" y="4" width="4" height="16" />
    </svg>
  ),
  SkipForward: () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polygon points="5 4 15 12 5 20 5 4" />
      <line x1="19" y1="5" x2="19" y2="19" />
    </svg>
  ),
  SkipBack: () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polygon points="19 20 9 12 19 4 19 20" />
      <line x1="5" y1="19" x2="5" y2="5" />
    </svg>
  ),
  AlertTriangle: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
      <line x1="12" y1="9" x2="12" y2="13" />
      <line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  ),
  Trash: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 6h18M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
    </svg>
  ),
  ExternalLink: () => (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
      <polyline points="15 3 21 3 21 9" />
      <line x1="10" y1="14" x2="21" y2="3" />
    </svg>
  ),
  Info: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="16" x2="12" y2="12" />
      <line x1="12" y1="8" x2="12.01" y2="8" />
    </svg>
  ),
};

// 播放器品牌图标 (高保真 16x16 矢量徽标)
const PlayerIcons = {
  QQMusic: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" style={{ borderRadius: '4.5px', flexShrink: 0, display: 'block' }}>
      <rect width="24" height="24" rx="5" fill="#1ECD99" />
      {/* 官方 QQ 音乐极简白描线条：黑胶唱片同心圆盘 + 经典音符 */}
      <circle cx="12" cy="12" r="8" stroke="#ffffff" strokeWidth="1.2" strokeOpacity="0.4" fill="none" />
      <circle cx="9.5" cy="14" r="2.4" fill="#ffffff" />
      <path d="M11.9 14V6.5c1.8 0 4 1 4.4 3.2" stroke="#ffffff" strokeWidth="2" strokeLinecap="round" fill="none" />
    </svg>
  ),
  NetEase: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" style={{ borderRadius: '4.5px', flexShrink: 0, display: 'block' }}>
      <rect width="24" height="24" rx="5" fill="#E60026" />
      {/* 官方网易云音乐白描单线：双环云音黑胶旋钮 */}
      <path
        d="M6.8 13.8c-.6-3.4 1.5-6.5 5.2-6.5 3.5 0 5.8 2.5 5.2 5.8-.5 2.8-2.5 4.5-5.2 4.2-2-.2-3.5-1.6-3.2-3.5.3-1.6 1.8-2.6 3.5-2.3 1.4.2 2.1 1.2 1.7 2.4-.3.8-1.2 1.2-2 .9"
        stroke="#ffffff"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
    </svg>
  ),
  Ximalaya: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" style={{ borderRadius: '4.5px', flexShrink: 0, display: 'block' }}>
      <rect width="24" height="24" rx="5" fill="#F86442" />
      <rect x="4.5" y="10" width="2" height="4" rx="1" fill="#ffffff" />
      <rect x="8" y="7" width="2" height="10" rx="1" fill="#ffffff" />
      <rect x="11.5" y="4.5" width="2" height="15" rx="1" fill="#ffffff" />
      <rect x="15" y="7" width="2" height="10" rx="1" fill="#ffffff" />
      <rect x="18.5" y="10" width="2" height="4" rx="1" fill="#ffffff" />
    </svg>
  ),
  Xiaoyuzhou: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" style={{ borderRadius: '4.5px', flexShrink: 0, display: 'block' }}>
      <rect width="24" height="24" rx="5" fill="#2563EB" />
      <circle cx="12" cy="12" r="4.2" fill="#ffffff" />
      <ellipse cx="12" cy="12" rx="8" ry="3" stroke="#ffffff" strokeWidth="1.3" transform="rotate(-25 12 12)" />
      <circle cx="12" cy="12" r="2.2" fill="#2563EB" />
    </svg>
  ),
  Kugou: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" style={{ borderRadius: '4.5px', flexShrink: 0, display: 'block' }}>
      <rect width="24" height="24" rx="5" fill="#0096FA" />
      {/* 官方酷狗音乐白描极简科技标志：律动双弧与经典白描 'K' */}
      <path d="M7 6v12M17 6.5l-6.8 5.8 7.3 5.7" stroke="#ffffff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  Kuwo: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" style={{ borderRadius: '4.5px', flexShrink: 0, display: 'block' }}>
      <rect width="24" height="24" rx="5" fill="#FF8F00" />
      {/* 官方酷我音乐白描极简音符：双跳音符连杠与声波旋律 */}
      <circle cx="8" cy="16" r="2.5" fill="#ffffff" />
      <circle cx="16" cy="13.5" r="2.5" fill="#ffffff" />
      <path d="M10.5 16V7.5l8-2.5V13.5" stroke="#ffffff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
};

const getPlayerOptions = (t: (typeof translations)[LanguageKey]) => [
  { value: 'com.tencent.qqmusic', label: t.bridge.qqmusic, icon: <PlayerIcons.QQMusic /> },
  { value: 'com.netease.cloudmusic', label: t.bridge.netease, icon: <PlayerIcons.NetEase /> },
  { value: 'kugou.service', label: t.bridge.kugou, icon: <PlayerIcons.Kugou /> },
  { value: 'cn.kuwo.player', label: t.bridge.kuwo, icon: <PlayerIcons.Kuwo /> },
  { value: 'com.ximalaya.ting.android', label: t.bridge.ximalaya, icon: <PlayerIcons.Ximalaya /> },
  { value: 'app.podcast.cosmos', label: t.bridge.xiaoyuzhou, icon: <PlayerIcons.Xiaoyuzhou /> },
];

const LANGUAGE_OPTIONS = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English' },
  { value: 'de-DE', label: 'Deutsch' },
  { value: 'ja-JP', label: '日本語' },
];

export default function App() {
  const [activeTab, setActiveTab] = useState<'overview' | 'notifications' | 'media' | 'guide'>('overview');
  const [theme, setTheme] = useState<'dark' | 'light'>('light');
  const [lang, setLang] = useState<LanguageKey>('zh-CN');
  const [isScrollable, setIsScrollable] = useState<boolean>(false);

  // 关于弹窗状态 (遵循 MyOmnis_design.md 提权原则)
  const [showAboutModal, setShowAboutModal] = useState<boolean>(false);
  const [aboutViewMode, setAboutViewMode] = useState<'info' | 'changelog'>('info');

  // 受限设置指引折叠状态 (默认折叠)
  const [isRestrictedExpanded, setIsRestrictedExpanded] = useState<boolean>(false);

  const [permissions, setPermissions] = useState<PermissionStatusResult>({
    notificationListener: false,
    batteryOptimized: true,
    postNotifications: false,
  });
  const [bridgeStatus, setBridgeStatus] = useState<BridgeStatusResult>({
    isForegroundRunning: false,
    isCarConnected: false,
    activeSessionCount: 0,
    capturedLogCount: 0,
  });
  const [mediaSessions, setMediaSessions] = useState<MediaSessionItem[]>([]);
  const [currentProgressMs, setCurrentProgressMs] = useState<number>(0);

  const [logs, setLogs] = useState<BridgeLogEntry[]>([]);
  const [appConfig, setAppConfig] = useState<AppBridgeConfig>({
    wechat: true,
    feishu: true,
    dingtalk: true,
    qqmusic: true,
    netease: true,
    kugou: true,
    kuwo: true,
    ximalaya: true,
    xiaoyuzhou: true,
    autoPlayOnConnect: true,
    defaultPlayerPackage: 'com.tencent.qqmusic',
    filterGroupChats: false,
    hidePreviewContent: false,
  });

  const selectedPlayerPkg = appConfig.defaultPlayerPackage || 'com.tencent.qqmusic';
  // 概览页大卡片精准映射当前选择播放器的实际后台实况：有对应后台会话则如实展示，无则进入干净的未播放等待态
  const displayedSession = useMemo(() => {
    return mediaSessions.find((s) => s.packageName === selectedPlayerPkg) || null;
  }, [mediaSessions, selectedPlayerPkg]);

  // 本地进度平滑走针机制：当处于播放态时，前端每秒匀速自增 1000ms，在原生事件到达时自动校准
  useEffect(() => {
    if (displayedSession) {
      setCurrentProgressMs(displayedSession.position || 0);
    } else {
      setCurrentProgressMs(0);
    }
  }, [displayedSession]);

  useEffect(() => {
    if (!displayedSession?.isPlaying) return;

    const timer = setInterval(() => {
      setCurrentProgressMs((prev) => {
        const dur = displayedSession.duration || 0;
        if (dur > 0 && prev >= dur) return prev;
        return prev + 1000;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [displayedSession]);

  const t = translations[lang];
  const playerOptions = useMemo(() => getPlayerOptions(t), [t]);

  // 时间格式化辅助 (ms -> mm:ss)
  const formatTime = (ms?: number) => {
    if (!ms || ms <= 0) return '00:00';
    const totalSec = Math.floor(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  // 1. 初始化屏幕自适应缩放与持久化主题、语言
  useEffect(() => {
    document.body.style.zoom = String(APP_ZOOM_RATIO);

    // 动态注入手机端安全区保底值 (避免刘海与挖孔摄像头遮挡)
    if (Capacitor.isNativePlatform()) {
      document.documentElement.style.setProperty('--safe-fallback', '38px');
    } else {
      document.documentElement.style.setProperty('--safe-fallback', '0px');
    }

    // 读取持久化主题 (默认浅色: light)
    Preferences.get({ key: 'fahrmony_theme' }).then(({ value }) => {
      const initialTheme = (value as 'dark' | 'light') || 'light';
      setTheme(initialTheme);
      document.documentElement.setAttribute('data-theme', initialTheme);
      document.body.setAttribute('data-theme', initialTheme);
    });

    // 读取持久化语言
    Preferences.get({ key: 'fahrmony_lang' }).then(({ value }) => {
      if (value && (value in translations)) {
        setLang(value as LanguageKey);
      }
    });

    // 读取持久化配置
    Preferences.get({ key: 'fahrmony_app_config' }).then(({ value }) => {
      if (value) {
        try {
          const parsed = JSON.parse(value);
          setAppConfig((prev) => ({ ...prev, ...parsed }));
        } catch {
          // ignore
        }
      }
    });
  }, []);

  // 2. 主题切换与持久化 (Capacitor Preferences + Web localStorage 双写保障)
  const toggleTheme = async () => {
    const nextTheme = theme === 'dark' ? 'light' : 'dark';
    setTheme(nextTheme);
    document.documentElement.setAttribute('data-theme', nextTheme);
    document.body.setAttribute('data-theme', nextTheme);
    try {
      localStorage.setItem('fahrmony_theme', nextTheme);
    } catch {
      // ignore
    }
    await Preferences.set({ key: 'fahrmony_theme', value: nextTheme });
  };

  // 3. 语言切换与持久化
  const handleLangChange = async (newLang: string) => {
    const target = newLang as LanguageKey;
    setLang(target);
    await Preferences.set({ key: 'fahrmony_lang', value: target });
  };

  // 4. 配置修改与多端同步 (Preferences + Kotlin Native)
  const updateConfig = async (newPartial: Partial<AppBridgeConfig>) => {
    const nextConfig = { ...appConfig, ...newPartial };
    setAppConfig(nextConfig);
    await Preferences.set({ key: 'fahrmony_app_config', value: JSON.stringify(nextConfig) });
    await FahrmonyPlugin.setAppBridgeConfig({ config: newPartial });
  };

  // 5. 轮询同步原生底层状态 (带互斥与错误保护，秒级响应切歌与状态变化)
  const isRefreshingRef = useRef(false);
  const activeTabRef = useRef(activeTab);
  activeTabRef.current = activeTab;

  const refreshNativeState = async () => {
    // 互斥保护：杜绝并发 IPC 堆叠与 Last-Write-Wins 竞态
    if (isRefreshingRef.current) return;
    isRefreshingRef.current = true;
    try {
      const isNotificationTab = activeTabRef.current === 'notifications';
      const [perm, status, media, logData, cfg] = await Promise.all([
        FahrmonyPlugin.checkPermissions(),
        FahrmonyPlugin.getBridgeStatus(),
        FahrmonyPlugin.getActiveMediaSessions(),
        // 消融实验优化：仅在通知页拉取大尺寸日志列表，降低非必要跨进程序列化负载
        isNotificationTab ? FahrmonyPlugin.getCapturedLogs() : Promise.resolve({ logs: null as any }),
        FahrmonyPlugin.getAppBridgeConfig(),
      ]);
      setPermissions(perm);
      setBridgeStatus(status);
      // 同源会话去重：按 packageName 归一，优先保留 isPlaying 活跃会话
      const rawSessions = media.sessions || [];
      const deduplicated = rawSessions.reduce((acc, curr) => {
        const idx = acc.findIndex((s) => s.packageName === curr.packageName);
        if (idx === -1) {
          acc.push(curr);
        } else if (!acc[idx].isPlaying && curr.isPlaying) {
          acc[idx] = curr;
        }
        return acc;
      }, [] as MediaSessionItem[]);
      setMediaSessions(deduplicated);
      if (logData.logs) {
        setLogs(logData.logs);
      }
      if (cfg.config) {
        setAppConfig((prev) => ({ ...prev, ...cfg.config }));
      }
    } catch (e) {
      console.warn('Fahrmony state pull error:', e);
    } finally {
      isRefreshingRef.current = false;
    }
  };

  useEffect(() => {
    refreshNativeState();

    // 反应式事件监听：当原生媒体控制器切歌/启停时实时更新，彻底摒弃 1500ms 暴力高频轮询
    let listenerHandle: any = null;
    FahrmonyPlugin.addListener('mediaSessionChanged', (event) => {
      if (event && event.packageName) {
        setMediaSessions((prev) => {
          const updated: MediaSessionItem = {
            packageName: event.packageName!,
            appName: event.appName || event.packageName!,
            title: event.title || '正在播放',
            artist: event.artist || '',
            album: event.album || '',
            isPlaying: event.isPlaying ?? false,
            duration: event.duration || 0,
            position: event.position || 0,
          };
          const idx = prev.findIndex((s) => s.packageName === updated.packageName);
          if (idx === -1) {
            return [updated, ...prev];
          }
          const next = [...prev];
          next[idx] = { ...next[idx], ...updated };
          return next;
        });
      } else if (event && !event.hasActiveSession) {
        // 无活跃会话时，不清除列表，仅将所有会话置为暂停
        setMediaSessions((prev) => prev.map((s) => ({ ...s, isPlaying: false })));
      }
    }).then((handle) => {
      listenerHandle = handle;
    });

    // 仅保留 20 秒极低频轻量心跳（确保后台通知授权/连接状态校准），杜绝主线程与 Binder 泛洪
    const heartbeatTimer = setInterval(() => {
      FahrmonyPlugin.checkPermissions().then(setPermissions).catch(() => { });
      FahrmonyPlugin.getBridgeStatus().then(setBridgeStatus).catch(() => { });
    }, 20000);

    const handleVisibility = () => {
      if (document.visibilityState === 'visible') {
        refreshNativeState();
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      if (listenerHandle?.remove) {
        listenerHandle.remove();
      }
      clearInterval(heartbeatTimer);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, []);

  // 切换到通知页时主动拉取最新记录
  useEffect(() => {
    if (activeTab === 'notifications') {
      FahrmonyPlugin.getCapturedLogs().then((res) => {
        if (res.logs) setLogs(res.logs);
      }).catch(() => { });
    }
  }, [activeTab]);

  // 媒体指令发送 (带同步重入与 350ms 防抖锁保护)
  const isMediaCommandInFlightRef = useRef(false);
  const handleMediaControl = async (action: 'play' | 'pause' | 'skip_next' | 'skip_previous') => {
    if (isMediaCommandInFlightRef.current) return;
    isMediaCommandInFlightRef.current = true;
    try {
      await FahrmonyPlugin.sendMediaCommand({ action });
      setTimeout(refreshNativeState, 200);
    } catch (e) {
      console.warn('Media command error:', e);
    } finally {
      setTimeout(() => {
        isMediaCommandInFlightRef.current = false;
      }, 350);
    }
  };

  // 顶部提示胶囊 Toast 状态
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimerRef = useRef<any>(null);

  const showToast = (msg: string) => {
    if (toastTimerRef.current) {
      clearTimeout(toastTimerRef.current);
    }
    setToastMessage(msg);
    toastTimerRef.current = setTimeout(() => {
      setToastMessage(null);
    }, 2500);
  };

  // 清除日志
  const handleClearLogs = async () => {
    await FahrmonyPlugin.clearLogs();
    setLogs([]);
  };

  // 启动播放器 (带未查找到该App的胶囊弹窗提示)
  const handleLaunchPlayer = async (pkg?: string) => {
    const target = pkg || appConfig.defaultPlayerPackage || 'com.tencent.qqmusic';
    try {
      const res = await FahrmonyPlugin.launchApp({ packageName: target });
      if (!res || !res.success) {
        showToast('未查找到该App');
      }
    } catch {
      showToast('未查找到该App');
    }
  };

  // 120fps 零重绘 GPU 直驱物理拉伸引擎 (遵循 MyOmnis_design.md 规范)
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const transformContentRef = useRef<HTMLDivElement>(null);
  const isPullingRef = useRef(false);
  const startYRef = useRef(0);
  const currentOffsetRef = useRef(0);
  const hasTriggeredBoundaryHapticRef = useRef(false);
  const rafIdRef = useRef<number | null>(null);

  // 真实溢出门禁判定：若内容总高度未超出视口，物理锁定 overflowY 为 hidden，杜绝无意义上下滑动
  useLayoutEffect(() => {
    const el = scrollContainerRef.current;
    if (!el) return;
    const checkOverflow = () => {
      const canScroll = el.scrollHeight > el.clientHeight + 4;
      setIsScrollable(canScroll);
    };
    checkOverflow();
    window.addEventListener('resize', checkOverflow);
    return () => window.removeEventListener('resize', checkOverflow);
  }, [activeTab, logs.length, mediaSessions.length]);

  useEffect(() => {
    const container = scrollContainerRef.current;
    const content = transformContentRef.current;
    if (!container || !content) return;

    const triggerHaptic = () => {
      if (typeof window !== 'undefined' && window.navigator?.vibrate) {
        try {
          window.navigator.vibrate(10);
        } catch {
          // ignore
        }
      }
    };

    const handleTouchStart = (e: TouchEvent) => {
      // 若内容总高度未超出视口，彻底锁死两端拉伸动效 (满足消融实验门禁原则)
      const canScroll = isScrollable && (container.scrollHeight > container.clientHeight + 4);
      if (!canScroll) {
        isPullingRef.current = false;
        return;
      }

      const isAtBottom = container.scrollTop + container.clientHeight >= container.scrollHeight - 1.5;
      if (isAtBottom) {
        isPullingRef.current = true;
        startYRef.current = e.touches[0].clientY;
        hasTriggeredBoundaryHapticRef.current = false;
      } else {
        isPullingRef.current = false;
      }
    };

    const handleTouchMove = (e: TouchEvent) => {
      if (!isPullingRef.current || !isScrollable || container.scrollHeight <= container.clientHeight + 4) return;
      const currentY = e.touches[0].clientY;
      const pullDistance = startYRef.current - currentY;

      if (pullDistance > 0) {
        const dampedOffset = -(pullDistance * 0.42) / (1 + pullDistance * 0.0035);

        if (rafIdRef.current) cancelAnimationFrame(rafIdRef.current);
        rafIdRef.current = requestAnimationFrame(() => {
          content.style.willChange = 'transform';
          content.style.transition = 'none';
          content.style.transform = `translate3d(0, ${dampedOffset.toFixed(2)}px, 0)`;
          currentOffsetRef.current = dampedOffset;

          if (dampedOffset <= -10 && !hasTriggeredBoundaryHapticRef.current) {
            hasTriggeredBoundaryHapticRef.current = true;
            triggerHaptic();
          }
        });
      } else {
        if (currentOffsetRef.current !== 0) {
          content.style.transform = 'none';
          currentOffsetRef.current = 0;
        }
      }
    };

    const handleTouchEnd = () => {
      if (!isPullingRef.current) return;
      isPullingRef.current = false;

      if (currentOffsetRef.current !== 0) {
        content.style.transition = 'transform 0.35s cubic-bezier(0.2, 0.8, 0.25, 1)';
        content.style.transform = 'translate3d(0, 0, 0)';

        setTimeout(() => {
          content.style.willChange = 'auto';
          content.style.transform = 'none';
          content.style.transition = 'none';
          currentOffsetRef.current = 0;
        }, 360);
      }
    };

    container.addEventListener('touchstart', handleTouchStart, { passive: true });
    container.addEventListener('touchmove', handleTouchMove, { passive: true });
    container.addEventListener('touchend', handleTouchEnd, { passive: true });
    container.addEventListener('touchcancel', handleTouchEnd, { passive: true });

    return () => {
      container.removeEventListener('touchstart', handleTouchStart);
      container.removeEventListener('touchmove', handleTouchMove);
      container.removeEventListener('touchend', handleTouchEnd);
      container.removeEventListener('touchcancel', handleTouchEnd);
      if (rafIdRef.current) cancelAnimationFrame(rafIdRef.current);
    };
  }, [activeTab, logs.length, mediaSessions.length]);

  return (
    <div style={{ height: '100dvh', width: '100%', maxWidth: '480px', margin: '0 auto', display: 'flex', flexDirection: 'column', overflow: 'hidden', position: 'relative', background: 'var(--bg-main)', transition: 'background-color 300ms cubic-bezier(0.4, 0, 0.2, 1)' }}>

      {/* 顶部胶囊弹窗 (Top Capsule Toast: 靠上的toast胶囊弹窗，“未查找到该App”) */}
      {toastMessage && (
        <div
          style={{
            position: 'fixed',
            top: 'calc(16px + var(--sat, 0px))',
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 99999,
            background: 'rgba(24, 27, 34, 0.92)',
            backdropFilter: 'blur(20px)',
            WebkitBackdropFilter: 'blur(20px)',
            color: '#f8fafc',
            border: '1px solid rgba(255, 255, 255, 0.14)',
            boxShadow: '0 8px 30px rgba(0, 0, 0, 0.35)',
            padding: '8px 18px',
            borderRadius: '9999px',
            fontSize: '14px',
            fontWeight: 500,
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            animation: 'toastDropIn 240ms cubic-bezier(0.16, 1, 0.3, 1)',
            pointerEvents: 'none',
            whiteSpace: 'nowrap',
          }}
        >
          <div
            style={{
              width: '6px',
              height: '6px',
              borderRadius: '50%',
              background: 'var(--accent-primary)',
            }}
          />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* 顶部固定直通左右封顶的毛玻璃导航栏 (自适应手机刘海/挖孔与安全区) */}
      <header
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          width: '100%',
          height: 'calc(var(--header-base-height, 58px) + var(--sat, 0px))',
          paddingTop: 'var(--sat, 0px)',
          backdropFilter: 'blur(20px)',
          WebkitBackdropFilter: 'blur(20px)',
          background: 'var(--bg-surface-glass)',
          borderBottom: '1px solid var(--border-subtle)',
          borderRadius: 0,
          zIndex: 100,
          boxSizing: 'border-box',
        }}
      >
        <div
          style={{
            maxWidth: '480px',
            height: '100%',
            margin: '0 auto',
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxSizing: 'border-box',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center' }}>
            {/* 顶部栏状态指示灯 (从右侧移至左侧，占位 16px 槽位，使标题本身起始点精确对齐下方卡片内容 32px 物理垂线) */}
            <div
              style={{
                width: '16px',
                height: '24px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-start',
                flexShrink: 0,
              }}
            >
              <div
                title={bridgeStatus.isCarConnected ? t.status.connected : t.status.standby}
                style={{
                  width: '8px',
                  height: '8px',
                  borderRadius: '50%',
                  background: bridgeStatus.isCarConnected ? 'var(--status-success)' : 'var(--accent-primary)',
                  boxShadow: bridgeStatus.isCarConnected ? '0 0 8px var(--status-success)' : 'none',
                }}
              />
            </div>

            <h1 style={{ fontSize: '23px', fontWeight: 700, letterSpacing: '-0.02em', color: 'var(--text-primary)', margin: 0, padding: 0 }}>
              {activeTab === 'overview' ? t.appName : (activeTab === 'guide' ? t.tabs.settings : t.tabs[activeTab])}
            </h1>

            {activeTab === 'overview' && (
              <span style={{ fontSize: '14px', padding: '3px 10px', borderRadius: '10px', background: 'var(--accent-tint)', color: 'var(--accent-primary)', fontWeight: 600, marginLeft: '8px' }}>
                {t.tagline}
              </span>
            )}
          </div>

          <button
            onClick={toggleTheme}
            className="btn-jelly surface-card"
            style={{
              width: '40px',
              height: '40px',
              borderRadius: '50%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              border: '1px solid var(--border-subtle)',
              background: 'var(--bg-surface-elevated)',
              color: 'var(--text-primary)',
            }}
            aria-label="切换主题"
          >
            {theme === 'dark' ? <Icons.Sun /> : <Icons.Moon />}
          </button>
        </div>
      </header>

      {/* 滚动容器：精准避让动态刘海顶栏与底部 Dock 栏 (首张卡片与顶栏下边缘间距精准拉齐至 16px) */}
      <div
        ref={scrollContainerRef}
        style={{
          flex: 1,
          minHeight: 0,
          width: '100%',
          overflowY: isScrollable ? 'auto' : 'hidden',
          overflowX: 'hidden',
          WebkitOverflowScrolling: 'touch',
          boxSizing: 'border-box',
          paddingTop: 'calc(74px + var(--sat, 0px))',
          paddingBottom: 'calc(104px + var(--sab, 0px))',
        }}
      >
        {/* GPU 直驱物理拉伸承接层 */}
        <div
          ref={transformContentRef}
          style={{
            width: '100%',
            display: 'flex',
            flexDirection: 'column',
            flexShrink: 0,
            padding: '0 16px',
            boxSizing: 'border-box',
          }}
        >
          {/* Tab 非线性平滑淡入淡出动画承接容器 (key 驱动原生微动效) */}
          <div key={activeTab} style={{ animation: 'tabFadeIn 220ms cubic-bezier(0.16, 1, 0.3, 1)' }}>

            {/* ========== 1. 概览 Tab (Overview) Apple Music / Spotify 沉浸风格 ========== */}
            {activeTab === 'overview' && (
              <div>
                {/* 车机连接与通讯徽章看板 (紧凑无冗余) */}
                <div className="glass-card" style={{ padding: '12px 16px', marginBottom: '16px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <div
                        style={{
                          width: '8px',
                          height: '8px',
                          borderRadius: '50%',
                          background: bridgeStatus.isCarConnected ? 'var(--status-success)' : 'var(--accent-primary)',
                          boxShadow: bridgeStatus.isCarConnected ? '0 0 8px var(--status-success)' : 'none',
                        }}
                      />
                      <div>
                        <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>
                          {bridgeStatus.isCarConnected ? t.status.connected : t.status.standby}
                        </div>
                        <div style={{ fontSize: '13px', color: 'var(--text-tertiary)', marginTop: '2px' }}>
                          {bridgeStatus.isForegroundRunning ? t.status.serviceRunning : t.status.serviceStopped}
                        </div>
                      </div>
                    </div>

                    {/* IM 状态指示徽章：开启绿色，关闭灰色并加斜杠 */}
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <span
                        style={{
                          fontSize: '13px',
                          padding: '3px 8px',
                          borderRadius: '6px',
                          background: appConfig.wechat ? 'var(--status-success-bg)' : 'var(--bg-surface-elevated)',
                          color: appConfig.wechat ? 'var(--status-success)' : 'var(--text-tertiary)',
                          fontWeight: 600,
                          textDecoration: appConfig.wechat ? 'none' : 'line-through',
                        }}
                      >
                        微信
                      </span>
                      <span
                        style={{
                          fontSize: '13px',
                          padding: '3px 8px',
                          borderRadius: '6px',
                          background: appConfig.feishu ? 'var(--status-success-bg)' : 'var(--bg-surface-elevated)',
                          color: appConfig.feishu ? 'var(--status-success)' : 'var(--text-tertiary)',
                          fontWeight: 600,
                          textDecoration: appConfig.feishu ? 'none' : 'line-through',
                        }}
                      >
                        飞书
                      </span>
                      <span
                        style={{
                          fontSize: '13px',
                          padding: '3px 8px',
                          borderRadius: '6px',
                          background: appConfig.dingtalk ? 'var(--status-success-bg)' : 'var(--bg-surface-elevated)',
                          color: appConfig.dingtalk ? 'var(--status-success)' : 'var(--text-tertiary)',
                          fontWeight: 600,
                          textDecoration: appConfig.dingtalk ? 'none' : 'line-through',
                        }}
                      >
                        钉钉
                      </span>
                    </div>
                  </div>
                </div>

                {/* Apple Music / Spotify 风格沉浸式大封面播放器 (紧凑适配任意手机视口) */}
                <div className="surface-card" style={{ padding: '16px 16px', textAlign: 'center', marginBottom: 0 }}>
                  {/* 顶栏：标题 + 自定义下拉切换播放器 */}
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '18px', gap: '8px' }}>
                    <span style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-secondary)', flexShrink: 0, whiteSpace: 'nowrap' }}>
                      {(() => {
                        const hasTrack = Boolean(displayedSession && displayedSession.title && displayedSession.title.trim().length > 0);
                        if (displayedSession?.isPlaying) return t.nowPlaying.title;
                        if (hasTrack) return t.nowPlaying.paused;
                        return t.nowPlaying.notPlayingTitle;
                      })()}
                    </span>
                    <div style={{ flexShrink: 0 }}>
                      <CustomSelect
                        compact
                        value={appConfig.defaultPlayerPackage || 'com.tencent.qqmusic'}
                        onChange={(val) => updateConfig({ defaultPlayerPackage: val })}
                        options={playerOptions}
                      />
                    </div>
                  </div>

                  {/* 居中超大专辑封面 (适度增大上部呼吸间距) */}
                  <div style={{ display: 'flex', justifyContent: 'center', marginTop: '4px', marginBottom: '12px' }}>
                    <div
                      style={{
                        width: '144px',
                        height: '144px',
                        borderRadius: '20px',
                        background: 'radial-gradient(circle at top left, var(--bg-surface-elevated), var(--bg-surface))',
                        border: '1px solid var(--border-subtle)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: 'var(--accent-primary)',
                        boxShadow: '0 12px 28px rgba(0, 0, 0, 0.18)',
                        position: 'relative',
                        overflow: 'hidden',
                      }}
                    >
                      <div
                        style={{
                          position: 'absolute',
                          width: '90px',
                          height: '90px',
                          borderRadius: '50%',
                          background: 'var(--accent-primary)',
                          filter: 'blur(35px)',
                          opacity: 0.25,
                        }}
                      />
                      <div style={{ transform: 'scale(1.8)', opacity: 0.9, position: 'relative', zIndex: 1, top: '1px', left: '-1px' }}>
                        <Icons.Music />
                      </div>
                    </div>
                  </div>

                  {/* 歌曲与艺术家信息 (与外部音乐 App 实时保持一致) */}
                  <div style={{ marginBottom: '16px' }}>
                    {(() => {
                      const hasTrack = Boolean(displayedSession && displayedSession.title && displayedSession.title.trim().length > 0);
                      const targetPlayerName = playerOptions.find((p) => p.value === selectedPlayerPkg)?.label || '媒体应用';
                      return (
                        <>
                          <div style={{ fontSize: '18px', fontWeight: 700, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {hasTrack ? displayedSession!.title : `${targetPlayerName} · ${t.nowPlaying.emptyTitle}`}
                          </div>
                          <div style={{ fontSize: '14px', color: 'var(--text-secondary)', marginTop: '3px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {hasTrack
                              ? `${displayedSession!.artist || '未知艺术家'}${displayedSession!.album ? ' · ' + displayedSession!.album : ''}${displayedSession!.appName ? ' · ' + displayedSession!.appName : ''}`
                              : t.nowPlaying.emptyDesc}
                          </div>
                        </>
                      );
                    })()}
                  </div>

                  {/* 真实媒体进度条与时间 (处于上下元素完全对称居中位置) */}
                  {(() => {
                    const pos = currentProgressMs;
                    const dur = displayedSession?.duration || 0;
                    const percent = dur > 0 ? Math.min(100, Math.max(0, (pos / dur) * 100)) : (displayedSession?.isPlaying ? 35 : 0);
                    return (
                      <div style={{ width: '100%', marginBottom: '16px', padding: '0 4px', boxSizing: 'border-box' }}>
                        <div style={{ height: '4px', borderRadius: '2px', background: 'var(--bg-surface-elevated)', width: '100%', overflow: 'hidden' }}>
                          <div
                            style={{
                              height: '100%',
                              width: `${percent}%`,
                              background: 'var(--accent-primary)',
                              borderRadius: '2px',
                              transition: 'width 200ms ease',
                            }}
                          />
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                          <span>{formatTime(pos)}</span>
                          <span>{dur > 0 ? formatTime(dur) : '--:--'}</span>
                        </div>
                      </div>
                    );
                  })()}

                  {/* 播放控制按钮组 */}
                  {(() => {
                    const isPlaying = Boolean(displayedSession?.isPlaying);
                    return (
                      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '20px' }}>
                        <button
                          onClick={() => handleMediaControl('skip_previous')}
                          className="btn-jelly surface-card"
                          style={{
                            width: '44px',
                            height: '44px',
                            borderRadius: '50%',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            background: 'var(--bg-surface-elevated)',
                            border: '1px solid var(--border-subtle)',
                            color: 'var(--text-primary)',
                          }}
                          aria-label="上一首"
                        >
                          <Icons.SkipBack />
                        </button>

                        <button
                          onClick={() => handleMediaControl(isPlaying ? 'pause' : 'play')}
                          className="btn-jelly"
                          style={{
                            width: '58px',
                            height: '58px',
                            borderRadius: '50%',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            background: 'var(--accent-primary)',
                            color: '#ffffff',
                            border: 'none',
                            boxShadow: '0 6px 18px var(--accent-glow)',
                          }}
                          aria-label={isPlaying ? t.nowPlaying.paused : t.nowPlaying.playing}
                        >
                          {isPlaying ? <Icons.Pause /> : <Icons.Play />}
                        </button>

                        <button
                          onClick={() => handleMediaControl('skip_next')}
                          className="btn-jelly surface-card"
                          style={{
                            width: '44px',
                            height: '44px',
                            borderRadius: '50%',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            background: 'var(--bg-surface-elevated)',
                            border: '1px solid var(--border-subtle)',
                            color: 'var(--text-primary)',
                          }}
                          aria-label="下一首"
                        >
                          <Icons.SkipForward />
                        </button>
                      </div>
                    );
                  })()}

                  {/* 快捷拉起应用按键 (尺寸与内部字号与播放器选择框 100% 物理对齐) */}
                  <div style={{ marginTop: '16px' }}>
                    <button
                      onClick={() => handleLaunchPlayer()}
                      className="btn-jelly"
                      style={{
                        height: '32px',
                        minHeight: '32px',
                        padding: '0 12px',
                        borderRadius: '8px',
                        background: 'var(--bg-surface-elevated)',
                        border: '1px solid var(--border-subtle)',
                        color: 'var(--text-primary)',
                        fontSize: '15px',
                        fontWeight: 500,
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '6px',
                        boxSizing: 'border-box',
                        cursor: 'pointer',
                      }}
                    >
                      <Icons.ExternalLink />
                      <span>{t.nowPlaying.openPlayer}</span>
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* ========== 2. 通知 Tab (Notifications) 严格领域隔离 ========== */}
            {activeTab === 'notifications' && (
              <div>
                {/* 行车安全提示 Banner */}
                <div className="glass-card" style={{ padding: '14px 16px', marginBottom: '16px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--status-warning)' }}>
                    <Icons.AlertTriangle />
                    <span style={{ fontSize: '18px', fontWeight: 600 }}>{t.notifications.safetyTitle}</span>
                  </div>
                  <p style={{ fontSize: '15px', color: 'var(--text-secondary)', marginTop: '6px', lineHeight: 1.5 }}>
                    {t.notifications.safetyDesc}
                  </p>
                </div>

                {/* 通讯播报与隐私过滤配置 Card */}
                <div className="surface-card" style={{ padding: '16px', marginBottom: '16px' }}>
                  <div style={{ fontSize: '18px', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '14px' }}>
                    {t.notifications.configTitle}
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>{t.notifications.wechatToggle}</div>
                        <div style={{ fontSize: '14px', color: 'var(--text-tertiary)', marginTop: '3px' }}>{t.notifications.wechatDesc}</div>
                      </div>
                      <Switch
                        checked={appConfig.wechat}
                        onChange={(checked) => updateConfig({ wechat: checked })}
                      />
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>{t.notifications.feishuToggle}</div>
                        <div style={{ fontSize: '14px', color: 'var(--text-tertiary)', marginTop: '3px' }}>{t.notifications.feishuDesc}</div>
                      </div>
                      <Switch
                        checked={appConfig.feishu}
                        onChange={(checked) => updateConfig({ feishu: checked })}
                      />
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>{t.notifications.dingtalkToggle}</div>
                        <div style={{ fontSize: '14px', color: 'var(--text-tertiary)', marginTop: '3px' }}>{t.notifications.dingtalkDesc}</div>
                      </div>
                      <Switch
                        checked={appConfig.dingtalk}
                        onChange={(checked) => updateConfig({ dingtalk: checked })}
                      />
                    </div>

                    <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>{t.notifications.filterGroup}</div>
                        <div style={{ fontSize: '14px', color: 'var(--text-tertiary)', marginTop: '3px' }}>{t.notifications.filterGroupDesc}</div>
                      </div>
                      <Switch
                        checked={!!appConfig.filterGroupChats}
                        onChange={(checked) => updateConfig({ filterGroupChats: checked })}
                      />
                    </div>
                  </div>
                </div>

                {/* 通讯记录列表 */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                  <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-secondary)' }}>
                    {t.notifications.recordTitle} ({logs.filter((l) => l.type === 'IM_NOTIFICATION').length})
                  </div>
                  <button
                    onClick={handleClearLogs}
                    className="btn-jelly"
                    style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '15px', color: 'var(--text-tertiary)', background: 'transparent', border: 'none' }}
                  >
                    <Icons.Trash /> {t.notifications.clear}
                  </button>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {logs.filter((l) => l.type === 'IM_NOTIFICATION').length > 0 ? (
                    logs
                      .filter((l) => l.type === 'IM_NOTIFICATION')
                      .map((log) => (
                        <div key={log.id} className="surface-card" style={{ padding: '14px' }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                            <span style={{ fontSize: '14px', fontWeight: 600, color: 'var(--accent-primary)' }}>{log.tag}</span>
                            <span className="font-mono" style={{ fontSize: '14px', color: 'var(--text-tertiary)' }}>
                              {new Date(log.timestamp).toLocaleTimeString()}
                            </span>
                          </div>
                          <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>{log.title}</div>
                          <div style={{ fontSize: '15px', color: 'var(--text-secondary)', marginTop: '4px' }}>{log.content}</div>
                        </div>
                      ))
                  ) : (
                    <div className="surface-card" style={{ padding: '32px', textAlign: 'center', color: 'var(--text-tertiary)', fontSize: '16px' }}>
                      {t.notifications.empty}
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* ========== 3. 媒体 Tab (Media) 专职管理播放器与车机会话 ========== */}
            {activeTab === 'media' && (
              <div>
                {/* 顶部媒体架构说明 (严格两行排版) */}
                <div className="surface-card" style={{ padding: '16px', marginBottom: '16px' }}>
                  <h3 style={{ fontSize: '18px', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '6px' }}>
                    {t.mediaTab.bannerTitle}
                  </h3>
                  <p style={{ fontSize: '15px', color: 'var(--text-secondary)', lineHeight: 1.6, whiteSpace: 'pre-line' }}>
                    {t.mediaTab.bannerDesc}
                  </p>
                </div>

                {/* 默认播放器指定与自动播放配置 */}
                <div className="surface-card" style={{ padding: '16px', marginBottom: '16px' }}>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <div>
                      <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>
                        {t.mediaTab.defaultPlayerTitle}
                      </div>
                      <div style={{ fontSize: '14px', color: 'var(--text-tertiary)', marginTop: '2px', marginBottom: '10px' }}>
                        {t.mediaTab.defaultPlayerDesc}
                      </div>
                      <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                        <div style={{ flex: 1 }}>
                          <CustomSelect
                            value={appConfig.defaultPlayerPackage || 'com.tencent.qqmusic'}
                            onChange={(val) => updateConfig({ defaultPlayerPackage: val })}
                            options={playerOptions}
                          />
                        </div>
                        <button
                          onClick={() => handleLaunchPlayer()}
                          className="btn-jelly"
                          style={{
                            height: '44px',
                            padding: '0 16px',
                            borderRadius: '12px',
                            background: 'var(--accent-primary)',
                            color: '#ffffff',
                            fontSize: '15px',
                            fontWeight: 600,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '6px',
                            whiteSpace: 'nowrap',
                            flexShrink: 0,
                            boxSizing: 'border-box',
                          }}
                        >
                          <Icons.ExternalLink />
                          {t.nowPlaying.openPlayer}
                        </button>
                      </div>
                    </div>

                    {/* 车机连接自动播放 Toggle */}
                    <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ paddingRight: '12px' }}>
                        <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>
                          {t.mediaTab.autoPlayTitle}
                        </div>
                        <div style={{ fontSize: '14px', color: 'var(--text-tertiary)', marginTop: '2px' }}>
                          {t.mediaTab.autoPlayDesc}
                        </div>
                      </div>
                      <Switch
                        checked={!!appConfig.autoPlayOnConnect}
                        onChange={(checked) => updateConfig({ autoPlayOnConnect: checked })}
                      />
                    </div>
                  </div>
                </div>

                {/* 当前活跃媒体源列表 */}
                <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '10px' }}>
                  {t.mediaTab.sourcesTitle} ({mediaSessions.length})
                </div>
                {mediaSessions.map((session) => (
                  <div key={session.packageName} className="surface-card" style={{ padding: '16px', marginBottom: '12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <div>
                        <div style={{ fontSize: '17px', fontWeight: 600, color: 'var(--text-primary)' }}>{session.title || '无曲目'}</div>
                        <div style={{ fontSize: '15px', color: 'var(--text-secondary)', marginTop: '2px' }}>{session.artist} · {session.album}</div>
                      </div>
                      <span
                        style={{
                          fontSize: '14px',
                          padding: '3px 9px',
                          borderRadius: '6px',
                          height: 'fit-content',
                          background: session.isPlaying ? 'var(--status-success-bg)' : 'var(--bg-surface-elevated)',
                          color: session.isPlaying ? 'var(--status-success)' : 'var(--text-tertiary)',
                          fontWeight: 500,
                        }}
                      >
                        {session.isPlaying ? t.nowPlaying.playing : t.nowPlaying.paused}
                      </span>
                    </div>
                    <div style={{ fontSize: '14px', color: 'var(--text-tertiary)', marginTop: '8px' }}>
                      {t.mediaTab.sourceApp}: {session.appName || session.packageName}
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* ========== 4. 设置 Tab (Settings & Permissions) ========== */}
            {activeTab === 'guide' && (
              <div>
                {/* 多语言切换卡片：使用紧凑 CustomSelect */}
                <div className="surface-card" style={{ padding: '16px', marginBottom: '16px' }}>
                  <div style={{ fontSize: '18px', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '8px' }}>
                    {t.settings.language}
                  </div>
                  <CustomSelect
                    value={lang}
                    onChange={handleLangChange}
                    options={LANGUAGE_OPTIONS}
                  />
                </div>

                {/* 系统权限与状态 Card */}
                <div className="surface-card" style={{ padding: '16px', marginBottom: '16px' }}>
                  <h3 style={{ fontSize: '18px', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '14px' }}>
                    {t.settings.permSection}
                  </h3>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ flex: 1, paddingRight: '14px', minWidth: 0 }}>
                        <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>{t.settings.notifPerm}</div>
                        <div style={{ fontSize: '14px', color: 'var(--text-tertiary)', marginTop: '2px' }}>{t.settings.notifPermDesc}</div>
                      </div>
                      <button
                        onClick={() => FahrmonyPlugin.openPermissionSettings({ type: 'notification_listener' })}
                        className="btn-jelly"
                        style={{
                          width: '84px',
                          minWidth: '84px',
                          flexShrink: 0,
                          height: '36px',
                          padding: '0 8px',
                          borderRadius: '10px',
                          fontSize: '15px',
                          fontWeight: 600,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          textAlign: 'center',
                          whiteSpace: 'nowrap',
                          boxSizing: 'border-box',
                          background: permissions.notificationListener ? 'var(--bg-surface-elevated)' : 'var(--accent-primary)',
                          color: permissions.notificationListener ? 'var(--status-success)' : '#ffffff',
                        }}
                      >
                        {permissions.notificationListener ? t.settings.granted : t.settings.toGrant}
                      </button>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ flex: 1, paddingRight: '14px', minWidth: 0 }}>
                        <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>{t.settings.batteryPerm}</div>
                        <div style={{ fontSize: '14px', color: 'var(--text-tertiary)', marginTop: '2px' }}>{t.settings.batteryPermDesc}</div>
                      </div>
                      <button
                        onClick={() => FahrmonyPlugin.openPermissionSettings({ type: 'battery_optimization' })}
                        className="btn-jelly"
                        style={{
                          width: '84px',
                          minWidth: '84px',
                          flexShrink: 0,
                          height: '36px',
                          padding: '0 8px',
                          borderRadius: '10px',
                          fontSize: '15px',
                          fontWeight: 600,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          textAlign: 'center',
                          whiteSpace: 'nowrap',
                          boxSizing: 'border-box',
                          background: 'var(--bg-surface-elevated)',
                          color: 'var(--text-primary)',
                          border: '1px solid var(--border-subtle)',
                        }}
                      >
                        {t.settings.toConfig}
                      </button>
                    </div>

                    {/* 行 3：受限设置说明（默认折叠，点击展开） */}
                    <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '12px' }}>
                      <div
                        onClick={() => setIsRestrictedExpanded(!isRestrictedExpanded)}
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          cursor: 'pointer',
                          padding: '0',
                          userSelect: 'none',
                          transition: 'opacity 140ms ease',
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <svg
                            width="16"
                            height="16"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="var(--status-warning)"
                            strokeWidth="2"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            style={{ flexShrink: 0 }}
                          >
                            <circle cx="12" cy="12" r="10" />
                            <line x1="12" y1="8" x2="12" y2="12" />
                            <line x1="12" y1="16" x2="12.01" y2="16" />
                          </svg>
                          <span style={{ fontSize: '16px', fontWeight: 600, color: 'var(--status-warning)' }}>
                            {t.settings.restrictedTitle}
                          </span>
                        </div>
                        <svg
                          width="16"
                          height="16"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="var(--text-tertiary)"
                          strokeWidth="2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          style={{
                            transform: isRestrictedExpanded ? 'rotate(180deg)' : 'rotate(0deg)',
                            transition: 'transform 200ms cubic-bezier(0.16, 1, 0.3, 1)',
                            flexShrink: 0,
                          }}
                        >
                          <polyline points="6 9 12 15 18 9" />
                        </svg>
                      </div>

                      {isRestrictedExpanded && (
                        <div
                          style={{
                            marginTop: '10px',
                            padding: '12px 14px',
                            borderRadius: '10px',
                            background: 'rgba(226, 169, 71, 0.08)',
                            border: '1px solid rgba(226, 169, 71, 0.22)',
                            animation: 'tabFadeIn 200ms cubic-bezier(0.16, 1, 0.3, 1)',
                          }}
                        >
                          <p
                            style={{
                              fontSize: '15px',
                              color: 'var(--text-secondary)',
                              lineHeight: 1.65,
                              whiteSpace: 'pre-line',
                              margin: 0,
                            }}
                          >
                            {t.settings.restrictedDesc}
                          </p>
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {/* Android Auto 车机端配置 */}
                <div className="surface-card" style={{ padding: '16px', marginBottom: '16px' }}>
                  <h3 style={{ fontSize: '18px', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '6px' }}>
                    {t.settings.aaConfigTitle}
                  </h3>
                  <p style={{ fontSize: '15px', color: 'var(--text-secondary)', lineHeight: 1.6, whiteSpace: 'pre-line' }}>
                    {t.settings.aaConfigDesc}
                  </p>
                </div>

                {/* “关于 Fahrmony” 入口卡片 */}
                <div
                  onClick={() => {
                    setShowAboutModal(true);
                    setAboutViewMode('info');
                  }}
                  className="surface-card btn-jelly"
                  style={{
                    padding: '14px 16px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    cursor: 'pointer',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <div style={{ color: 'var(--accent-primary)', display: 'flex' }}>
                      <Icons.Info />
                    </div>
                    <div style={{ fontSize: '18px', fontWeight: 600, color: 'var(--text-primary)' }}>
                      {t.settings.aboutTitle}
                    </div>
                  </div>
                  <div style={{ color: 'var(--text-tertiary)', display: 'flex', alignItems: 'center', justifyContent: 'center', width: '20px', height: '20px', flexShrink: 0 }}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="9 18 15 12 9 6" />
                    </svg>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 底部悬浮毛玻璃 Dock 导航栏 (自适应底部手势横条安全区) */}
      <nav
        className="glass-card"
        style={{
          position: 'fixed',
          bottom: 'calc(12px + var(--sab, 0px))',
          left: '16px',
          right: '16px',
          maxWidth: '448px',
          margin: '0 auto',
          display: 'flex',
          justifyContent: 'space-around',
          padding: '10px 0',
          borderRadius: '24px',
          zIndex: 100,
          animation: 'dockSlideUpIn 380ms cubic-bezier(0.16, 1, 0.3, 1) 70ms both',
          willChange: 'transform, opacity',
        }}
      >
        {[
          { key: 'overview', label: t.tabs.overview, Icon: Icons.Car },
          { key: 'notifications', label: t.tabs.notifications, Icon: Icons.Bell },
          { key: 'media', label: t.tabs.media, Icon: Icons.Music },
          { key: 'guide', label: t.tabs.settings, Icon: Icons.Settings },
        ].map(({ key, label, Icon }) => {
          const isActive = activeTab === key;
          return (
            <button
              key={key}
              onClick={() => setActiveTab(key as any)}
              className="btn-jelly"
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '4px',
                background: 'transparent',
                border: 'none',
                color: isActive ? 'var(--accent-primary)' : 'var(--text-tertiary)',
                padding: '4px 14px',
              }}
            >
              <div style={{ width: '24px', height: '24px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Icon />
              </div>
              <span style={{ fontSize: '15px', fontWeight: isActive ? 600 : 400 }}>{label}</span>
            </button>
          );
        })}
      </nav>

      {/* “关于 Fahrmony” 全局提权模态弹窗 (遵循 MyOmnis_design.md 第 4 节包含块隔离原则) */}
      {showAboutModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.65)',
            backdropFilter: 'blur(10px)',
            WebkitBackdropFilter: 'blur(10px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '20px',
            zIndex: 9999,
            boxSizing: 'border-box',
          }}
          onClick={() => setShowAboutModal(false)}
        >
          <div
            className="surface-card"
            style={{
              maxWidth: '380px',
              width: '100%',
              borderRadius: '24px',
              padding: '24px',
              background: 'var(--bg-surface)',
              border: '1px solid var(--border-subtle)',
              boxShadow: '0 24px 48px rgba(0, 0, 0, 0.45)',
              animation: 'modalPop 200ms cubic-bezier(0.16, 1, 0.3, 1)',
              boxSizing: 'border-box',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* 弹窗上半部分浏览框 */}
            <div style={{ minHeight: '240px', maxHeight: '300px', display: 'flex', flexDirection: 'column' }}>
              {aboutViewMode === 'info' ? (
                <div style={{ flex: 1, overflowY: 'auto', textAlign: 'center' }}>
                  <img
                    src="/logo.png?v=14"
                    alt="Fahrmony Logo"
                    style={{
                      width: '64px',
                      height: '64px',
                      borderRadius: '16px',
                      background: '#ffffff',
                      boxShadow: '0 8px 20px rgba(0, 0, 0, 0.25)',
                      marginBottom: '10px',
                      objectFit: 'cover',
                      transform: 'translateZ(0)',
                      backfaceVisibility: 'hidden',
                      imageRendering: '-webkit-optimize-contrast'
                    }}
                  />
                  <div style={{ fontSize: '21px', fontWeight: 700, color: 'var(--text-primary)' }}>
                    Fahrmony
                  </div>
                  <div style={{ fontSize: '15px', color: 'var(--accent-primary)', fontWeight: 600, marginTop: '2px' }}>
                    v1.0.0
                  </div>

                  <div style={{ marginTop: '14px', display: 'flex', flexWrap: 'wrap', gap: '6px', justifyContent: 'center' }}>
                    {['Android Jetpack Car App', 'MediaSession IPC', 'Capacitor 8 Native', 'React 19 + TS'].map((tag) => (
                      <span
                        key={tag}
                        style={{
                          fontSize: '13px',
                          padding: '4px 9px',
                          borderRadius: '6px',
                          background: 'var(--bg-surface-elevated)',
                          color: 'var(--text-tertiary)',
                          border: '1px solid var(--border-subtle)',
                        }}
                      >
                        {tag}
                      </span>
                    ))}
                  </div>

                  <div style={{ marginTop: '16px', textAlign: 'center', lineHeight: 1.5 }}>
                    <div style={{ fontSize: '14px', color: 'var(--text-secondary)' }}>
                      {t.settings.aboutTarget}
                    </div>
                    <div style={{ fontSize: '13px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                      {t.settings.copyright}
                    </div>
                  </div>
                </div>
              ) : (
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
                  <div style={{ fontSize: '17px', fontWeight: 700, color: 'var(--text-primary)', marginBottom: '10px', flexShrink: 0 }}>
                    {t.settings.changelog}
                  </div>
                  {/* 仅“更新日志”标题与下方双按钮之间的内容区域具有滚动能力 */}
                  <div style={{ flex: 1, overflowY: 'auto', paddingRight: '4px', display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '15px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                    <div>
                      <div style={{ fontWeight: 600, color: 'var(--accent-primary)' }}>v1.0.0</div>
                      <div style={{ marginTop: '4px' }}>
                        • 微信 / 飞书 / 钉钉 三大主流 IM 消息 Android Auto 车载通知桥接<br />
                        • QQ音乐、网易云音乐、小宇宙 等六大音源状态双端打通<br />
                        • Android Auto 原生全屏音频沉浸服务和高彩度抗黑键动态流光封面<br />
                        • 车载播放体验深度优化：上车自动续播、平滑防抖切歌与拔线断连防漏音<br />
                        • 独立后台守护架构：保障车载服务持久稳定运行，手机设置即时同步生效<br />
                        • 全局 392dp 基准流体动态自适应排版，多语言支持 (中/英/德/日)
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* 弹窗下半部分按钮栏 */}
            <div style={{ display: 'flex', gap: '10px', marginTop: '18px', borderTop: '1px solid var(--border-subtle)', paddingTop: '14px' }}>
              <button
                onClick={() => setAboutViewMode(aboutViewMode === 'info' ? 'changelog' : 'info')}
                className="btn-jelly"
                style={{
                  flex: 1,
                  padding: '11px 0',
                  borderRadius: '12px',
                  background: 'var(--bg-surface-elevated)',
                  border: '1px solid var(--border-subtle)',
                  color: 'var(--text-primary)',
                  fontSize: '16px',
                  fontWeight: 600,
                  textAlign: 'center',
                }}
              >
                {aboutViewMode === 'info' ? t.settings.changelog : t.settings.info}
              </button>
              <button
                onClick={() => setShowAboutModal(false)}
                className="btn-jelly"
                style={{
                  flex: 1,
                  padding: '11px 0',
                  borderRadius: '12px',
                  background: 'var(--accent-primary)',
                  color: '#ffffff',
                  fontSize: '16px',
                  fontWeight: 600,
                  textAlign: 'center',
                }}
              >
                {t.settings.close}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
