import { registerPlugin, PluginListenerHandle } from '@capacitor/core';

export interface PermissionStatusResult {
  notificationListener: boolean;
  batteryOptimized: boolean;
  postNotifications: boolean;
  isRestrictedSettingsDetected?: boolean;
}

export interface BridgeStatusResult {
  isForegroundRunning: boolean;
  isCarConnected: boolean;
  activeSessionCount: number;
  capturedLogCount: number;
}

export interface MediaSessionItem {
  packageName: string;
  appName: string;
  title: string;
  artist: string;
  album: string;
  isPlaying: boolean;
  duration: number;
  position: number;
  artworkBase64?: string;
}

export interface MediaSessionChangedEvent {
  hasActiveSession: boolean;
  packageName?: string;
  appName?: string;
  title?: string;
  artist?: string;
  album?: string;
  isPlaying?: boolean;
  duration?: number;
  position?: number;
}

export interface BridgeLogEntry {
  id: string;
  timestamp: number;
  type: 'IM_NOTIFICATION' | 'MEDIA_SESSION' | 'AUTO_PLAY' | 'SYSTEM';
  tag: string;
  title: string;
  content: string;
  rawExtras?: string;
}

export interface AppBridgeConfig {
  wechat: boolean;
  feishu: boolean;
  dingtalk: boolean;
  qqmusic: boolean;
  netease: boolean;
  kugou: boolean;
  kuwo: boolean;
  ximalaya: boolean;
  xiaoyuzhou: boolean;
  autoPlayOnConnect?: boolean;
  defaultPlayerPackage?: string;
  filterGroupChats?: boolean;
  hidePreviewContent?: boolean;
}

export interface FahrmonyPluginInterface {
  checkPermissions(): Promise<PermissionStatusResult>;
  openPermissionSettings(options: { type: 'notification_listener' | 'battery_optimization' | 'restricted_settings' | 'app_details' }): Promise<{ success: boolean }>;
  getBridgeStatus(): Promise<BridgeStatusResult>;
  getActiveMediaSessions(): Promise<{ sessions: MediaSessionItem[] }>;
  sendMediaCommand(options: { packageName?: string; action: 'play' | 'pause' | 'skip_next' | 'skip_previous' }): Promise<{ success: boolean }>;
  getCapturedLogs(): Promise<{ logs: BridgeLogEntry[] }>;
  clearLogs(): Promise<{ success: boolean }>;
  setAppBridgeConfig(options: { config: Partial<AppBridgeConfig> }): Promise<{ success: boolean }>;
  getAppBridgeConfig(): Promise<{ config: AppBridgeConfig }>;
  launchApp(options: { packageName: string }): Promise<{ success: boolean }>;
  addListener(
    eventName: 'mediaSessionChanged',
    listenerFunc: (data: MediaSessionChangedEvent) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
}

let mockConfig: AppBridgeConfig = {
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
};

const FahrmonyPlugin = registerPlugin<FahrmonyPluginInterface>('FahrmonyPlugin', {
  // Web 调试降级 Mock 实现 (当在浏览器中独立测试时自动无缝回退，杜绝白屏)
  web: () => ({
    checkPermissions: async () => ({
      notificationListener: false,
      batteryOptimized: true,
      postNotifications: true,
      isRestrictedSettingsDetected: false,
    }),
    openPermissionSettings: async (options: { type: 'notification_listener' | 'battery_optimization' | 'restricted_settings' | 'app_details' }) => {
      console.log('[Web Mock] Open permission settings:', options.type);
      return { success: true };
    },
    getBridgeStatus: async () => ({
      isForegroundRunning: true,
      isCarConnected: false,
      activeSessionCount: 1,
      capturedLogCount: 2,
    }),
    getActiveMediaSessions: async () => ({
      sessions: [
        {
          packageName: 'com.tencent.qqmusic',
          appName: 'QQ音乐',
          title: '晴天 (Live)',
          artist: '周杰伦',
          album: '叶惠美',
          isPlaying: true,
          duration: 269000,
          position: 112000,
        },
      ],
    }),
    sendMediaCommand: async (options: { packageName?: string; action: 'play' | 'pause' | 'skip_next' | 'skip_previous' }) => {
      console.log('[Web Mock] Send media command:', options);
      return { success: true };
    },
    getCapturedLogs: async () => ({
      logs: [
        {
          id: 'mock-1',
          timestamp: Date.now() - 45000,
          type: 'IM_NOTIFICATION',
          tag: '微信',
          title: '张工',
          content: '下午两点车机联调测试已安排完毕，请注意查收',
        },
        {
          id: 'mock-2',
          timestamp: Date.now() - 15000,
          type: 'IM_NOTIFICATION',
          tag: '飞书',
          title: '项目组通知',
          content: '合拍 Android Auto 桥接版本 v1.0.0 测试通过',
        }
      ],
    }),
    clearLogs: async () => ({ success: true }),
    setAppBridgeConfig: async (options: { config: Partial<AppBridgeConfig> }) => {
      mockConfig = { ...mockConfig, ...options.config };
      return { success: true };
    },
    getAppBridgeConfig: async () => ({
      config: mockConfig
    }),
    launchApp: async (options: { packageName: string }) => {
      console.log('[Web Mock] Launch app intent for package:', options.packageName);
      alert(`[Fahrmony 模拟环境] 已发送启动应用 Intent：${options.packageName}`);
      return { success: true };
    },
    addListener: async () => ({
      remove: async () => {},
    } as any),
  }),
});

export default FahrmonyPlugin;
