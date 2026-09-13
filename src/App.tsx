import { useState, useEffect, useLayoutEffect, useRef, useMemo } from 'react';
import { Preferences } from '@capacitor/preferences';
import { Capacitor } from '@capacitor/core';
import { StatusBar, Style } from '@capacitor/status-bar';
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
import { SplashView } from './components/SplashView.tsx';

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
  SunMoon: () => (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 3a9 9 0 0 1 0 18V3z" fill="currentColor" />
    </svg>
  ),
  Play: () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style={{ transform: 'translateX(2px)' }}>
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
  Qishui: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" style={{ borderRadius: '4.5px', flexShrink: 0, display: 'block' }}>
      <rect width="24" height="24" rx="5" fill="#121212" />
      {/* 官方汽水音乐黑底荧光绿连音符徽标 */}
      <circle cx="8" cy="15.5" r="2.5" fill="#22F26B" />
      <circle cx="16" cy="13" r="2.5" fill="#22F26B" />
      <path d="M10.5 15.5V6.5l8-2V13" stroke="#22F26B" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10.5 9.8l8-2" stroke="#22F26B" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  ),
  Bodian: () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" style={{ borderRadius: '4.5px', flexShrink: 0, display: 'block' }}>
      <rect width="24" height="24" rx="5" fill="#25E072" />
      {/* 官方波点音乐绿底一体化黑胶b徽标：平滑加粗立柱、相切黑胶外环、内圈镂空绿环、轴孔黑色微波点 */}
      <rect x="6" y="4.5" width="3.2" height="14.5" rx="1.6" fill="#111111" />
      <circle cx="13.6" cy="14.5" r="5.5" fill="#111111" />
      <circle cx="13.6" cy="14.5" r="2.6" fill="#25E072" />
      <circle cx="13.6" cy="14.5" r="1.1" fill="#111111" />
    </svg>
  ),
};

const getPlayerOptions = (t: (typeof translations)[LanguageKey]) => [
  { value: 'com.tencent.qqmusic', label: t.bridge.qqmusic, icon: <PlayerIcons.QQMusic /> },
  { value: 'com.netease.cloudmusic', label: t.bridge.netease, icon: <PlayerIcons.NetEase /> },
  { value: 'com.luna.music', label: t.bridge.qishui, icon: <PlayerIcons.Qishui /> },
  { value: 'cn.wenyu.bodian', label: t.bridge.bodian, icon: <PlayerIcons.Bodian /> },
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

// 官方明确支持纳管的 8 个媒体应用白名单 (含酷狗主包与前台Service包名，屏蔽淘宝等非音乐应用)
const SUPPORTED_PLAYER_PACKAGES = [
  'com.tencent.qqmusic',
  'com.netease.cloudmusic',
  'com.luna.music',
  'cn.wenyu.bodian',
  'kugou.service',
  'com.kugou.android',
  'cn.kuwo.player',
  'com.ximalaya.ting.android',
  'app.podcast.cosmos',
];

export default function App() {
  const [activeTab, setActiveTab] = useState<'overview' | 'notifications' | 'media' | 'guide'>('overview');
  type ThemeMode = 'system' | 'dark' | 'light';
  const [themeMode, setThemeMode] = useState<ThemeMode>(() => {
    try {
      const saved = localStorage.getItem('fahrmony_theme');
      if (saved === 'system' || saved === 'dark' || saved === 'light') return saved as ThemeMode;
    } catch { }
    return 'system';
  });

  const [systemIsDark, setSystemIsDark] = useState<boolean>(() => {
    if (typeof window !== 'undefined' && window.matchMedia) {
      return window.matchMedia('(prefers-color-scheme: dark)').matches;
    }
    return false;
  });

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const listener = (e: MediaQueryListEvent) => {
      setSystemIsDark(e.matches);
    };
    mql.addEventListener('change', listener);
    return () => mql.removeEventListener('change', listener);
  }, []);

  const effectiveTheme: 'dark' | 'light' = themeMode === 'system' ? (systemIsDark ? 'dark' : 'light') : themeMode;

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', effectiveTheme);
    document.body.setAttribute('data-theme', effectiveTheme);
    const bg = effectiveTheme === 'dark' ? '#0A0C10' : '#F8FAFC';
    document.documentElement.style.backgroundColor = bg;
    document.body.style.backgroundColor = bg;
  }, [effectiveTheme]);

  // 全屏纯色开屏生命周期控制 (参考 MyOmnis_react 独立组件设计：总时长 <= 1.2s，由 SplashView 闭环控制，过渡结束设为 false 卸载)
  const [showSplash, setShowSplash] = useState(true);

  const [lang, setLang] = useState<LanguageKey>('zh-CN');
  const [isScrollable, setIsScrollable] = useState<boolean>(false);

  // 关于弹窗状态 (遵循 MyOmnis_design.md 提权原则)
  const [showAboutModal, setShowAboutModal] = useState<boolean>(false);
  const [aboutViewMode, setAboutViewMode] = useState<'info' | 'changelog'>('info');

  // 更新检测 Toast 状态与防抖双击 ref
  const [updateToast, setUpdateToast] = useState<{ show: boolean; message: string; url?: string }>({ show: false, message: '' });
  const lastLogoClickTimeRef = useRef<number>(0);
  const isCheckingUpdateRef = useRef<boolean>(false);

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
  const [authorizedPlayers, setAuthorizedPlayers] = useState<string[]>([]);
  const [appConfig, setAppConfig] = useState<AppBridgeConfig>({
    wechat: true,
    feishu: false,
    dingtalk: false,
    qq: false,
    qqmusic: true,
    netease: true,
    kugou: true,
    kuwo: true,
    ximalaya: true,
    xiaoyuzhou: true,
    autoPlayOnConnect: true,
    defaultPlayerPackage: '',
    filterGroupChats: false,
    hidePreviewContent: false,
    rawPlayerCard: false,
  });

  const selectedPlayerPkg = appConfig.defaultPlayerPackage || '';
  // 概览页大卡片精准映射当前选择播放器的实际后台实况：有对应后台会话则如实展示，无则进入干净的未播放等待态
  const displayedSession = useMemo(() => {
    if (!selectedPlayerPkg) return null;
    return mediaSessions.find((s) => s.packageName === selectedPlayerPkg) || null;
  }, [mediaSessions, selectedPlayerPkg]);

  // 严格白名单过滤后的活跃播放源列表 (彻底屏蔽淘宝等无关会话)
  const supportedMediaSessions = useMemo(() => {
    return mediaSessions.filter((s) => SUPPORTED_PLAYER_PACKAGES.includes(s.packageName));
  }, [mediaSessions]);

  // 封面非线性淡入淡出与切歌双图层过渡机制
  const [activeArtwork, setActiveArtwork] = useState<string | null>(null);
  const [prevArtwork, setPrevArtwork] = useState<string | null>(null);

  useEffect(() => {
    const nextArt = (displayedSession && displayedSession.artworkData) ? displayedSession.artworkData : null;
    if (nextArt !== activeArtwork) {
      if (activeArtwork) {
        setPrevArtwork(activeArtwork);
      }
      setActiveArtwork(nextArt);
    }
  }, [displayedSession?.artworkData]);

  useEffect(() => {
    if (prevArtwork) {
      const timer = setTimeout(() => {
        setPrevArtwork(null);
      }, 450);
      return () => clearTimeout(timer);
    }
  }, [prevArtwork]);

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

  // Logo 双击主动检测更新 (<=320ms 双击阈值)
  const handleLogoClick = async () => {
    const now = Date.now();
    if (now - lastLogoClickTimeRef.current <= 320) {
      lastLogoClickTimeRef.current = 0;
      if (isCheckingUpdateRef.current) return;
      isCheckingUpdateRef.current = true;

      setUpdateToast({
        show: true,
        message: t.settings.checkingUpdateToast,
      });

      try {
        const res = await FahrmonyPlugin.checkUpdate();
        if (res && res.hasUpdate && res.downloadUrl) {
          setUpdateToast({
            show: true,
            message: t.settings.updateFoundToast,
            url: res.downloadUrl,
          });
        } else {
          setUpdateToast({
            show: true,
            message: t.settings.upToDateToast,
          });
        }
      } catch {
        setUpdateToast({
          show: true,
          message: t.settings.upToDateToast,
        });
      } finally {
        isCheckingUpdateRef.current = false;
      }
    } else {
      lastLogoClickTimeRef.current = now;
    }
  };

  useEffect(() => {
    if (!updateToast.show) return;
    const duration = updateToast.url ? 6000 : 2500;
    const timer = setTimeout(() => {
      setUpdateToast({ show: false, message: '' });
    }, duration);
    return () => clearTimeout(timer);
  }, [updateToast.show, updateToast.url, updateToast.message]);

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

    // 读取持久化主题偏好 (默认跟随系统: system)
    Preferences.get({ key: 'fahrmony_theme' }).then(({ value }) => {
      if (value === 'system' || value === 'dark' || value === 'light') {
        setThemeMode(value);
      } else {
        setThemeMode('system');
      }
    });

    // 读取持久化语言
    Preferences.get({ key: 'fahrmony_lang' }).then(({ value }) => {
      if (value && (value in translations)) {
        setLang(value as LanguageKey);
      }
    });

    // 读取持久化配置 (含旧版本向后兼容平滑迁移)
    Preferences.get({ key: 'fahrmony_app_config' }).then(({ value }) => {
      if (value) {
        try {
          const parsed = JSON.parse(value);
          setAppConfig((prev) => ({ ...prev, ...parsed }));
          // 向后兼容升级：若旧版本中已持久化过有效播放器，自动补齐到授权记忆中，防止升级后误跳
          if (parsed.defaultPlayerPackage && typeof parsed.defaultPlayerPackage === 'string') {
            setAuthorizedPlayers((prev) => Array.from(new Set([...prev, parsed.defaultPlayerPackage])));
          }
        } catch {
          // ignore
        }
      }
    });

    // 读取已确认授权过的播放器列表 (杜绝后续切换重复拉起跳转)
    Preferences.get({ key: 'fahrmony_authorized_players' }).then(({ value }) => {
      if (value) {
        try {
          const parsed = JSON.parse(value);
          if (Array.isArray(parsed)) {
            setAuthorizedPlayers((prev) => Array.from(new Set([...prev, ...parsed])));
          }
        } catch {
          // ignore
        }
      }
    });

    // 首次启动通知权限询问核检
    Preferences.get({ key: 'has_prompted_post_notifications' }).then(({ value }) => {
      if (!value) {
        Preferences.set({ key: 'has_prompted_post_notifications', value: 'true' });
        FahrmonyPlugin.checkPermissions().then((perm) => {
          if (!perm.postNotifications && FahrmonyPlugin.requestNotificationPermission) {
            FahrmonyPlugin.requestNotificationPermission().then((res) => {
              if (res && res.granted) {
                setPermissions((p) => ({ ...p, postNotifications: true }));
              }
            }).catch(() => { });
          }
        }).catch(() => { });
      }
    });
  }, []);

  // 顶部提示胶囊 Toast 状态管理 (带 1s 相同消息去重抑制)
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimerRef = useRef<any>(null);
  const lastToastRef = useRef<{ msg: string; time: number }>({ msg: '', time: 0 });

  const showToast = (msg: string) => {
    const now = Date.now();
    if (lastToastRef.current.msg === msg && now - lastToastRef.current.time < 1000) {
      return;
    }
    lastToastRef.current = { msg, time: now };
    if (toastTimerRef.current) {
      clearTimeout(toastTimerRef.current);
    }
    setToastMessage(msg);
    toastTimerRef.current = setTimeout(() => {
      setToastMessage(null);
    }, 2500);
  };

  // 监听车机连接状态跃迁 (由未连接 false 跃迁为已连接 true 时触发 Toast 弹窗，首次冷启动不误弹)
  const prevIsCarConnectedRef = useRef<boolean | null>(null);
  useEffect(() => {
    if (prevIsCarConnectedRef.current === false && bridgeStatus.isCarConnected === true) {
      showToast(t.status.carConnectedToast);
    }
    prevIsCarConnectedRef.current = bridgeStatus.isCarConnected;
  }, [bridgeStatus.isCarConnected, t]);

  // 2. 主题三态循环切换：浅色 -> 深色 -> 跟随系统 -> 浅色 (无文字歧义，配合顶部胶囊轻提示)
  const toggleTheme = async () => {
    let nextMode: ThemeMode;
    let toastMsg: string;
    if (themeMode === 'light') {
      nextMode = 'dark';
      toastMsg = t.settings.themeDarkToast;
    } else if (themeMode === 'dark') {
      nextMode = 'system';
      toastMsg = t.settings.themeSystemToast;
    } else {
      nextMode = 'light';
      toastMsg = t.settings.themeLightToast;
    }
    setThemeMode(nextMode);
    showToast(toastMsg);
    try {
      localStorage.setItem('fahrmony_theme', nextMode);
    } catch {
      // ignore
    }
    await Preferences.set({ key: 'fahrmony_theme', value: nextMode });
  };

  // 吸取 MyOmnis 架构优点：纯前端接管沉浸式状态栏底色与深浅模式自动反色 (零侵入 Android 原生 Window)
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    const updateStatusBar = async () => {
      try {
        const isDark = effectiveTheme === 'dark';
        const baseHex = isDark ? '#0A0C10' : '#F8FAFC';
        const statusStyle = isDark ? Style.Dark : Style.Light;
        await StatusBar.setBackgroundColor({ color: baseHex });
        await StatusBar.setStyle({ style: statusStyle });
      } catch {
        // ignore
      }
    };
    updateStatusBar();
  }, [effectiveTheme]);

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
      // 同源会话去重与白名单过滤：只收录已支持的 8 个播放源，彻底屏蔽淘宝等非支持应用
      const rawSessions = (media.sessions || []).filter((s) => SUPPORTED_PLAYER_PACKAGES.includes(s.packageName));
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
        // 白名单守卫：非受支持的媒体源 (如淘宝等) 坚决不推入活跃会话列表
        if (!SUPPORTED_PLAYER_PACKAGES.includes(event.packageName)) return;
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
            artworkData: event.artworkData ?? null,
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

    // 监听车机连接/断连事件，实时更新并提示
    let carConnHandle: any = null;
    FahrmonyPlugin.addListener('carConnectionChanged', (event) => {
      if (event && typeof event.connected === 'boolean') {
        setBridgeStatus((prev) => ({ ...prev, isCarConnected: event.connected }));
        if (event.connected) {
          showToast(t.status.carConnectedToast);
        }
      }
    }).then((handle) => {
      carConnHandle = handle;
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
      if (carConnHandle?.remove) {
        carConnHandle.remove();
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

  // 媒体指令发送 (带同步重入与 350ms 防抖锁保护，定向绑定当前大卡片对应播放器包名)
  const isMediaCommandInFlightRef = useRef(false);
  const handleMediaControl = async (action: 'play' | 'pause' | 'skip_next' | 'skip_previous') => {
    if (isMediaCommandInFlightRef.current) return;
    isMediaCommandInFlightRef.current = true;
    try {
      const targetPkg = selectedPlayerPkg || displayedSession?.packageName || undefined;
      await FahrmonyPlugin.sendMediaCommand({ action, packageName: targetPkg });
      setTimeout(refreshNativeState, 200);
    } catch (e) {
      console.warn('Media command error:', e);
    } finally {
      setTimeout(() => {
        isMediaCommandInFlightRef.current = false;
      }, 350);
    }
  };

  // 清除日志
  const handleClearLogs = async () => {
    await FahrmonyPlugin.clearLogs();
    setLogs([]);
  };

  // 启动播放器 (带未查找到该App的胶囊弹窗提示)
  const handleLaunchPlayer = async (pkg?: string) => {
    const target = pkg || appConfig.defaultPlayerPackage;
    if (!target) return;
    try {
      const res = await FahrmonyPlugin.launchApp({ packageName: target });
      if (!res || !res.success) {
        showToast('未查找到该App');
      }
    } catch {
      showToast('未查找到该App');
    }
  };

  // 选择播放器：写入持久化配置；仅首次切换到该播放器时执行 300ms 延时自然拉起以激活授权流，已确认过的播放器仅做切换不再跳转
  const handleSelectPlayer = (pkg: string) => {
    updateConfig({ defaultPlayerPackage: pkg });
    if (!pkg) return;

    // 智能判断：若播放器此前已授权确认过，或当前后台已有活跃会话，则静默切换，绝不再跳出应用
    const isAlreadyConfirmed = authorizedPlayers.includes(pkg) || mediaSessions.some((s) => s.packageName === pkg);
    if (!isAlreadyConfirmed) {
      const nextAuthorized = [...authorizedPlayers, pkg];
      setAuthorizedPlayers(nextAuthorized);
      Preferences.set({ key: 'fahrmony_authorized_players', value: JSON.stringify(nextAuthorized) });

      setTimeout(() => {
        handleLaunchPlayer(pkg);
      }, 300);
    }
  };

  // 真实视口感知容器锚点 (支持 ResizeObserver 与原生 120fps 硬件级 Overscroll)
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const transformContentRef = useRef<HTMLDivElement>(null);

  // 真实溢出门禁判定：通过对比内容层自然高度与滚动容器可用视口高度，若未超出物理可视范围，严格锁定 overflowY 为 hidden，杜绝无意义上下滑动
  useLayoutEffect(() => {
    const container = scrollContainerRef.current;
    const content = transformContentRef.current;
    if (!container || !content) return;

    const checkOverflow = () => {
      // 读取容器的计算内边距，获取真正的中间可视净空高度 (扣除顶部栏和底部导航栏占用的内边距)
      const computed = window.getComputedStyle(container);
      const paddingTop = parseFloat(computed.paddingTop) || 0;
      const paddingBottom = parseFloat(computed.paddingBottom) || 0;
      const availableHeight = container.clientHeight - paddingTop - paddingBottom;
      const contentHeight = content.offsetHeight;

      // 仅当卡片内容真实总高严格大于中间可用可视高度时，才放行滚动 (+2px 亚像素抖动防护)
      const canScroll = contentHeight > availableHeight + 2;
      setIsScrollable(canScroll);

      // 若处于不可滚动状态，强行复位滚动位置至顶部，杜绝无意义的留白残留
      if (!canScroll && container.scrollTop !== 0) {
        container.scrollTop = 0;
      }
    };

    checkOverflow();

    // 采用现代 ResizeObserver 毫秒级感知 DOM 真实高度形变（折叠展开、日志列表增删、多语言重排等）
    let resizeObserver: ResizeObserver | null = null;
    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => {
        checkOverflow();
      });
      resizeObserver.observe(content);
      resizeObserver.observe(container);
    }

    window.addEventListener('resize', checkOverflow);

    return () => {
      if (resizeObserver) resizeObserver.disconnect();
      window.removeEventListener('resize', checkOverflow);
    };
  }, [activeTab]);

  // 智能 Toast 文本分行：中文环境保持单行 nowrap 绝对不折行；英/德/日环境在逗号处自然折为双行居中
  const renderToastContent = (message: string, currentLang: LanguageKey) => {
    if (currentLang === 'zh-CN') {
      return (
        <span style={{ whiteSpace: 'nowrap', lineHeight: 1.4 }}>
          {message}
        </span>
      );
    }

    const splitRegex = /([,，、。]\s*)/;
    const match = message.match(splitRegex);
    if (match && match.index !== undefined) {
      const delimiterIndex = match.index + match[1].length;
      const line1 = message.substring(0, delimiterIndex).trim();
      const line2 = message.substring(delimiterIndex).trim();
      if (line1 && line2) {
        return (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', lineHeight: 1.35 }}>
            <span style={{ whiteSpace: 'nowrap' }}>{line1}</span>
            <span style={{ whiteSpace: 'nowrap' }}>{line2}</span>
          </div>
        );
      }
    }

    return (
      <span style={{ whiteSpace: 'nowrap', lineHeight: 1.4 }}>
        {message}
      </span>
    );
  };

  return (
    <div style={{ height: '100dvh', width: '100%', maxWidth: '480px', margin: '0 auto', display: 'flex', flexDirection: 'column', overflow: 'hidden', position: 'relative', background: 'var(--bg-main)', transition: 'background-color 300ms cubic-bezier(0.4, 0, 0.2, 1)' }}>

      {/* 沉浸式纯色全屏开屏 (参考 MyOmnis_react 独立组件设计：Portal 顶层渲染，纯色底全屏覆盖，Outfit 品牌字样水平居中、垂直 45% 非线性淡入) */}
      {showSplash && (
        <SplashView
          onFinish={() => setShowSplash(false)}
          theme={effectiveTheme}
        />
      )}

      {/* 顶部胶囊弹窗 (Top Capsule Toast: 动态自适应深浅色偏好) */}
      {toastMessage && (
        <div
          style={{
            position: 'fixed',
            top: 'calc(16px + var(--sat, 0px))',
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 99999,
            background: effectiveTheme === 'dark' ? 'rgba(24, 27, 34, 0.92)' : 'rgba(241, 245, 249, 0.92)',
            backdropFilter: 'blur(20px)',
            WebkitBackdropFilter: 'blur(20px)',
            color: effectiveTheme === 'dark' ? '#f8fafc' : '#0f172a',
            border: effectiveTheme === 'dark' ? '1px solid rgba(255, 255, 255, 0.14)' : '1px solid rgba(0, 0, 0, 0.08)',
            boxShadow: effectiveTheme === 'dark' ? '0 8px 30px rgba(0, 0, 0, 0.35)' : '0 8px 30px rgba(0, 0, 0, 0.12)',
            padding: '8px 18px',
            borderRadius: '9999px',
            fontSize: '15px',
            fontWeight: 500,
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            animation: 'toastDropIn 240ms cubic-bezier(0.16, 1, 0.3, 1)',
            pointerEvents: 'none',
            whiteSpace: 'nowrap',
            transition: 'background-color 200ms ease, color 200ms ease, border-color 200ms ease, box-shadow 200ms ease',
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
          height: 'calc(var(--header-base-height, 58px) + var(--sat, 0px) + 3px)',
          paddingTop: 'calc(var(--sat, 0px) + 3px)',
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
            aria-label="切换外观偏好"
          >
            {themeMode === 'light' ? <Icons.Sun /> : (themeMode === 'dark' ? <Icons.Moon /> : <Icons.SunMoon />)}
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
          overscrollBehaviorY: 'contain',
          boxSizing: 'border-box',
          paddingTop: 'calc(77px + var(--sat, 0px))',
          paddingBottom: 'calc(88px + var(--sab, 0px))',
          WebkitMaskImage: isScrollable
            ? 'linear-gradient(to bottom, black 0%, black calc(100% - 96px - var(--sab, 0px)), transparent calc(100% - 12px - var(--sab, 0px)))'
            : 'none',
          maskImage: isScrollable
            ? 'linear-gradient(to bottom, black 0%, black calc(100% - 96px - var(--sab, 0px)), transparent calc(100% - 12px - var(--sab, 0px)))'
            : 'none',
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

                    {/* IM 状态指示徽章：未启用时不显示，启用项按微信、飞书、钉钉顺序排列 */}
                    <div style={{ display: 'flex', gap: '8px' }}>
                      {appConfig.wechat && (
                        <span
                          style={{
                            fontSize: '13px',
                            padding: '3px 8px',
                            borderRadius: '6px',
                            background: 'var(--status-success-bg)',
                            color: 'var(--status-success)',
                            fontWeight: 600,
                          }}
                        >
                          微信
                        </span>
                      )}
                      {appConfig.feishu && (
                        <span
                          style={{
                            fontSize: '13px',
                            padding: '3px 8px',
                            borderRadius: '6px',
                            background: 'var(--status-success-bg)',
                            color: 'var(--status-success)',
                            fontWeight: 600,
                          }}
                        >
                          飞书
                        </span>
                      )}
                      {appConfig.dingtalk && (
                        <span
                          style={{
                            fontSize: '13px',
                            padding: '3px 8px',
                            borderRadius: '6px',
                            background: 'var(--status-success-bg)',
                            color: 'var(--status-success)',
                            fontWeight: 600,
                          }}
                        >
                          钉钉
                        </span>
                      )}
                      {appConfig.qq && (
                        <span
                          style={{
                            fontSize: '13px',
                            padding: '3px 8px',
                            borderRadius: '6px',
                            background: 'var(--status-success-bg)',
                            color: 'var(--status-success)',
                            fontWeight: 600,
                          }}
                        >
                          QQ
                        </span>
                      )}
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
                        value={appConfig.defaultPlayerPackage || ''}
                        placeholder={t.mediaTab.selectPlayerPlaceholder}
                        onChange={handleSelectPlayer}
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
                      {/* 底层 Placeholder 占位 (左移 1px 至 left: -2px，下移 2px 至 top: 6px 达成极致光学视觉居中) */}
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
                      <div style={{ transform: 'scale(1.8)', opacity: 0.9, position: 'relative', zIndex: 1, top: '6px', left: '-2px' }}>
                        <Icons.Music />
                      </div>

                      {/* 切歌过渡底衬图层 (Backing Crossfade Layer) */}
                      {prevArtwork && (
                        <img
                          src={prevArtwork}
                          alt="Previous Album Artwork"
                          style={{
                            position: 'absolute',
                            inset: 0,
                            width: '100%',
                            height: '100%',
                            objectFit: 'cover',
                            borderRadius: '19px',
                            zIndex: 2,
                          }}
                        />
                      )}

                      {/* 当前活跃真实专辑封面 (非线性淡入浮现动画 cubic-bezier(0.16, 1, 0.3, 1)) */}
                      {activeArtwork && (
                        <img
                          key={activeArtwork.slice(-32)}
                          src={activeArtwork}
                          alt="Album Artwork"
                          style={{
                            position: 'absolute',
                            inset: 0,
                            width: '100%',
                            height: '100%',
                            objectFit: 'cover',
                            borderRadius: '19px',
                            zIndex: 3,
                            animation: 'coverFadeIn 450ms cubic-bezier(0.16, 1, 0.3, 1) forwards',
                          }}
                          onError={() => {
                            setActiveArtwork(null);
                          }}
                        />
                      )}
                    </div>
                  </div>

                  {/* 歌曲与艺术家信息 (与外部音乐 App 实时保持一致) */}
                  <div style={{ marginBottom: '17px' }}>
                    {(() => {
                      const hasTrack = Boolean(displayedSession && displayedSession.title && displayedSession.title.trim().length > 0);
                      const targetPlayerName = playerOptions.find((p) => p.value === selectedPlayerPkg)?.label || t.appName;
                      const emptyHeader = selectedPlayerPkg ? `${targetPlayerName} · ${t.nowPlaying.emptyTitle}` : t.nowPlaying.emptyTitle;
                      return (
                        <>
                          <div style={{ fontSize: '19px', fontWeight: 700, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {hasTrack ? displayedSession!.title : emptyHeader}
                          </div>
                          <div style={{ fontSize: '15px', color: 'var(--text-secondary)', marginTop: '4px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {hasTrack
                              ? `${displayedSession!.artist || '未知艺术家'}${displayedSession!.album ? ' · ' + displayedSession!.album : ''}`
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

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>{t.notifications.qqToggle}</div>
                        <div style={{ fontSize: '14px', color: 'var(--text-tertiary)', marginTop: '3px' }}>{t.notifications.qqDesc}</div>
                      </div>
                      <Switch
                        checked={appConfig.qq}
                        onChange={(checked) => updateConfig({ qq: checked })}
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
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 16px', marginBottom: '12px' }}>
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
                            value={appConfig.defaultPlayerPackage || ''}
                            placeholder={t.mediaTab.selectPlayerPlaceholder}
                            onChange={handleSelectPlayer}
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

                    {/* 音频 App 原始播放卡片 Toggle */}
                    <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ paddingRight: '12px' }}>
                        <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>
                          {t.mediaTab.rawCardTitle}
                        </div>
                        <div style={{ fontSize: '14px', color: 'var(--text-tertiary)', marginTop: '2px' }}>
                          {t.mediaTab.rawCardDesc}
                        </div>
                      </div>
                      <Switch
                        checked={!!appConfig.rawPlayerCard}
                        onChange={(checked) => updateConfig({ rawPlayerCard: checked })}
                      />
                    </div>
                  </div>
                </div>

                {/* 当前活跃媒体源列表 (严格仅渲染受支持的 8 个播放源) */}
                <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-secondary)', padding: '0 16px', marginBottom: '10px' }}>
                  {t.mediaTab.sourcesTitle} ({supportedMediaSessions.length})
                </div>
                {supportedMediaSessions.map((session) => (
                  <div key={session.packageName} className="surface-card" style={{ padding: '16px', marginBottom: '12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '12px' }}>
                      <div style={{ flex: 1, minWidth: 0, paddingRight: '4px' }}>
                        <div style={{ fontSize: '17px', fontWeight: 600, color: 'var(--text-primary)', wordBreak: 'break-word', overflowWrap: 'break-word' }}>
                          {session.title || '无曲目'}
                        </div>
                        <div style={{ fontSize: '15px', color: 'var(--text-secondary)', marginTop: '2px', wordBreak: 'break-word', overflowWrap: 'break-word' }}>
                          {session.artist} · {session.album}
                        </div>
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
                          whiteSpace: 'nowrap',
                          flexShrink: 0,
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
                    {/* 行 0：通知提醒权限 (Fahrmony 自身通知权限) */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ flex: 1, paddingRight: '14px', minWidth: 0 }}>
                        <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>{t.settings.postNotifPerm}</div>
                        <div style={{ fontSize: '14px', color: 'var(--text-tertiary)', marginTop: '2px' }}>{t.settings.postNotifPermDesc}</div>
                      </div>
                      <button
                        onClick={async () => {
                          if (!permissions.postNotifications) {
                            try {
                              if (FahrmonyPlugin.requestNotificationPermission) {
                                const res = await FahrmonyPlugin.requestNotificationPermission();
                                if (res && res.granted) {
                                  setPermissions((p) => ({ ...p, postNotifications: true }));
                                  return;
                                }
                              }
                            } catch { }
                            await FahrmonyPlugin.openPermissionSettings({ type: 'app_notification' });
                          }
                        }}
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
                          background: permissions.postNotifications ? 'var(--bg-surface-elevated)' : 'var(--accent-primary)',
                          color: permissions.postNotifications ? 'var(--status-success)' : '#ffffff',
                        }}
                      >
                        {permissions.postNotifications ? t.settings.granted : t.settings.toGrant}
                      </button>
                    </div>

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

      {/* 底部悬浮毛玻璃 Dock 导航栏 (自适应底部手势横条安全区，开屏期间彻底隐藏，过渡完成后平滑滑入) */}
      {!showSplash && (
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
      )}

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
                  <div
                    onClick={handleLogoClick}
                    style={{
                      cursor: 'pointer',
                      display: 'inline-block',
                      userSelect: 'none',
                      WebkitUserSelect: 'none',
                      WebkitTapHighlightColor: 'transparent',
                    }}
                    title="Double tap to check for updates"
                  >
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
                        imageRendering: '-webkit-optimize-contrast',
                        pointerEvents: 'none',
                      }}
                    />
                    <div style={{ fontSize: '21px', fontWeight: 700, color: 'var(--text-primary)' }}>
                      Fahrmony
                    </div>
                    <div style={{ fontSize: '15px', color: 'var(--accent-primary)', fontWeight: 600, marginTop: '2px' }}>
                      v1.1.5
                    </div>
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
                  <div style={{ flex: 1, overflowY: 'auto', paddingRight: '4px', display: 'flex', flexDirection: 'column', gap: '14px', fontSize: '15px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                    <div>
                      <div style={{ fontWeight: 600, color: 'var(--accent-primary)' }}>v1.1.5</div>
                      <div style={{ marginTop: '4px' }}>
                        • 新增: 更新检测功能，每晚后台静默检测一次，双击关于信息弹窗内的Logo也可发起更新检测<br />
                        • 优化: 全面隐藏滚动条，页面跟手拉伸与回弹效果<br />
                        • 优化: 一些 UI & UX 细节
                      </div>
                    </div>
                    <div>
                      <div style={{ fontWeight: 600, color: 'var(--accent-primary)' }}>v1.1.0</div>
                      <div style={{ marginTop: '4px' }}>
                        • 新增: 支持汽水音乐、波点音乐播放控制，QQ 消息通知播报<br />
                        • 新增: 媒体页“音频App原始播放卡片”开关，支持直接显示原始专辑封面<br />
                        • 新增: 深浅色支持“跟随系统”，胶囊弹窗轻提醒<br />
                        • 修复: 车机未连接时状态指示灯误显示绿色的问题<br />
                        • 优化: 播放器控制绑定机制，播放器封面显示，通知路由处理机制<br />
                        • 优化: 首次安装默认设定，权限引导，开屏页面，UI & UX 细节完善
                      </div>
                    </div>
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

      {/* GitHub 更新检测 Toast 提示组件 (超高层级覆盖模态弹窗，泛边缘舒适可点击热区，支持随内容自适应延伸) */}
      {updateToast.show && (
        <div
          style={{
            position: 'fixed',
            top: 'calc(env(safe-area-inset-top, 0px) + 28px)',
            left: 0,
            right: 0,
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            zIndex: 100000,
            pointerEvents: 'none',
            padding: '0 16px',
            boxSizing: 'border-box',
          }}
        >
          <div
            onClick={(e) => {
              e.stopPropagation();
              if (updateToast.url) {
                window.open(updateToast.url, '_blank');
                setUpdateToast({ show: false, message: '' });
              }
            }}
            style={{
              pointerEvents: 'auto',
              cursor: updateToast.url ? 'pointer' : 'default',
              padding: '12px 22px',
              borderRadius: '9999px',
              background: 'var(--bg-surface-elevated, #1c2128)',
              color: 'var(--text-primary, #ffffff)',
              border: '1px solid var(--border-subtle, rgba(255, 255, 255, 0.15))',
              boxShadow: '0 12px 32px rgba(0, 0, 0, 0.55)',
              fontSize: '16px',
              fontWeight: 600,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '9px',
              animation: 'modalPop 200ms cubic-bezier(0.16, 1, 0.3, 1)',
              width: 'fit-content',
              maxWidth: 'calc(100vw - 32px)',
              boxSizing: 'border-box',
              textAlign: 'center',
              userSelect: 'none',
              WebkitUserSelect: 'none',
            }}
          >
            {updateToast.url && (
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--accent-primary, #38bdf8)" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="7 10 12 15 17 10" />
                <line x1="12" y1="15" x2="12" y2="3" />
              </svg>
            )}
            {renderToastContent(updateToast.message, lang)}
          </div>
        </div>
      )}
    </div>
  );
}
