import React, { useState, useEffect, useRef } from 'react';
import FahrmonyPlugin from '../plugins/FahrmonyPlugin';
import { type Translations } from '../i18n';

interface PlayModeCalibrationPopoverProps {
  packageName: string;
  t: Translations;
  onCalibrated?: (mode: string) => void;
}

type ModeType = 'REPEAT_ALL' | 'SHUFFLE' | 'REPEAT_ONE' | 'HEARTBEAT';

export const PlayModeCalibrationPopover: React.FC<PlayModeCalibrationPopoverProps> = ({
  packageName,
  t,
  onCalibrated,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [isFadingOut, setIsFadingOut] = useState(false);
  const [selectedMode, setSelectedMode] = useState<ModeType>('REPEAT_ALL');
  const containerRef = useRef<HTMLDivElement>(null);
  const closeTimerRef = useRef<number | null>(null);

  // 打开气泡时，从 Native 查询车机端当前显示的播放模式
  const fetchCurrentMode = async () => {
    try {
      const res = await FahrmonyPlugin.getPlayModeState({ packageName });
      if (res && res.mode) {
        setSelectedMode(res.mode as ModeType);
      }
    } catch {
      // 降级使用本地默认
    }
  };

  const handleToggle = () => {
    if (!isOpen) {
      fetchCurrentMode();
      setIsFadingOut(false);
      setIsOpen(true);
    } else {
      setIsOpen(false);
      setIsFadingOut(false);
    }
  };

  // 点击空白处外部关闭
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
        setIsFadingOut(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  // 组件卸载时清理定时器
  useEffect(() => {
    return () => {
      if (closeTimerRef.current !== null) {
        window.clearTimeout(closeTimerRef.current);
      }
    };
  }, []);

  const handleSelectMode = async (mode: ModeType) => {
    setSelectedMode(mode);

    // 触觉震动反馈
    if (typeof window !== 'undefined' && window.navigator?.vibrate) {
      try {
        window.navigator.vibrate(8);
      } catch {
        // ignore
      }
    }

    // 调用 Native 桥接单边校准 (严禁向底层网易云透传Action)
    try {
      await FahrmonyPlugin.calibratePlayMode({ packageName, mode });
      onCalibrated?.(mode);
    } catch {
      // ignore
    }

    // 用户点按正确选项后，0.5s (500ms) 自然平滑淡出关闭
    if (closeTimerRef.current !== null) {
      window.clearTimeout(closeTimerRef.current);
    }
    closeTimerRef.current = window.setTimeout(() => {
      setIsFadingOut(true);
      window.setTimeout(() => {
        setIsOpen(false);
        setIsFadingOut(false);
      }, 180);
    }, 500);
  };

  const allModes: { key: ModeType; label: string; icon: React.ReactNode }[] = [
    {
      key: 'REPEAT_ALL',
      label: t.nowPlaying.repeatAll || '列表循环',
      icon: (
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="17 1 21 5 17 9" />
          <path d="M3 11V9a4 4 0 0 1 4-4h14" />
          <polyline points="7 23 3 19 7 15" />
          <path d="M21 13v2a4 4 0 0 1-4 4H3" />
        </svg>
      ),
    },
    {
      key: 'SHUFFLE',
      label: t.nowPlaying.shuffle || '随机播放',
      icon: (
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="16 3 21 3 21 8" />
          <line x1="4" y1="20" x2="21" y2="3" />
          <polyline points="21 16 21 21 16 21" />
          <line x1="15" y1="15" x2="21" y2="21" />
          <line x1="4" y1="4" x2="9" y2="9" />
        </svg>
      ),
    },
    {
      key: 'REPEAT_ONE',
      label: t.nowPlaying.repeatOne || '单曲循环',
      icon: (
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="17 1 21 5 17 9" />
          <path d="M3 11V9a4 4 0 0 1 4-4h14" />
          <polyline points="7 23 3 19 7 15" />
          <path d="M21 13v2a4 4 0 0 1-4 4H3" />
          <path d="M11 10h1v4" strokeWidth="2" />
        </svg>
      ),
    },
    {
      key: 'HEARTBEAT',
      label: t.nowPlaying.heartbeat || '心动模式',
      icon: (
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
        </svg>
      ),
    },
  ];

  const modes = packageName === 'com.netease.cloudmusic'
    ? allModes
    : allModes.filter((m) => m.key !== 'HEARTBEAT');

  return (
    <div ref={containerRef} style={{ display: 'flex', alignItems: 'center' }}>
      {/* 触发按键：高度32px, 8px圆角，更换为专业调校扳手Icon */}
      <button
        type="button"
        onClick={handleToggle}
        className="btn-jelly"
        style={{
          height: '32px',
          minHeight: '32px',
          padding: '0 12px',
          borderRadius: '8px',
          background: isOpen ? 'var(--accent-glow)' : 'var(--bg-surface-elevated)',
          border: isOpen ? '1px solid var(--accent-primary)' : '1px solid var(--border-subtle)',
          color: isOpen ? 'var(--accent-primary)' : 'var(--text-primary)',
          fontSize: '15px',
          fontWeight: 500,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '6px',
          boxSizing: 'border-box',
          cursor: 'pointer',
          whiteSpace: 'nowrap',
          transition: 'all 180ms ease',
        }}
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
        </svg>
        <span>{t.nowPlaying.calibrateMode || '播放模式校准'}</span>
      </button>

      {/* 气泡下拉框：以双按钮总容器为定位锚点绝对居中 (left: 50%, transform: translateX(-50%))，面板收敛至紧凑的 300px 严丝合缝适配德语长词，间距等距统一为8px */}
      {isOpen && (
        <div
          style={{
            position: 'absolute',
            top: 'calc(100% + 8px)',
            left: '50%',
            transform: 'translateX(-50%)',
            width: '316px',
            maxWidth: 'calc(100vw - 32px)',
            padding: '8px',
            borderRadius: '14px',
            background: 'var(--bg-surface-elevated)',
            backdropFilter: 'blur(36px)',
            WebkitBackdropFilter: 'blur(36px)',
            boxShadow: '0 16px 44px rgba(0, 0, 0, 0.42), 0 2px 6px rgba(0, 0, 0, 0.2)',
            border: '1px solid var(--border-glass)',
            boxSizing: 'border-box',
            zIndex: 400,
            opacity: isFadingOut ? 0 : 1,
            transition: 'opacity 180ms ease, transform 180ms ease',
            pointerEvents: isFadingOut ? 'none' : 'auto',
          }}
        >
          {/* 第一行：副标题说明 (字号增大 1px 至 13px，单行居中不换行) */}
          <div
            style={{
              fontSize: '13px',
              fontWeight: 500,
              color: 'var(--text-tertiary)',
              textAlign: 'center',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              padding: '2px 0 6px 0',
              letterSpacing: '-0.2px',
            }}
          >
            {t.nowPlaying.calibrateSubtitle || '请选择音频App中当前播放模式以校准'}
          </div>

          {/* 分割线 (下外边距与网格间距等距统一为 8px) */}
          <div
            style={{
              width: '100%',
              height: '1px',
              background: 'var(--border-subtle)',
              marginBottom: '8px',
            }}
          />

          {/* 2x2 模式选择网格 (内部间距与面板边距等距统一为 8px，使用 minmax(0, 1fr) 严防长词溢出) */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
              gap: '8px',
            }}
          >
            {modes.map((item) => {
              const isSelected = selectedMode === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => handleSelectMode(item.key)}
                  className="btn-jelly"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '6px',
                    height: '34px',
                    padding: '0 8px',
                    borderRadius: '8px',
                    background: isSelected ? 'var(--accent-glow)' : 'var(--bg-surface)',
                    border: isSelected ? '1.5px solid var(--accent-primary)' : '1px solid var(--border-subtle)',
                    color: isSelected ? 'var(--accent-primary)' : 'var(--text-primary)',
                    cursor: 'pointer',
                    fontSize: '13px',
                    fontWeight: isSelected ? 600 : 500,
                    outline: 'none',
                    boxSizing: 'border-box',
                    transition: 'all 140ms ease',
                    whiteSpace: 'nowrap',
                    minWidth: 0,
                  }}
                >
                  <span style={{ display: 'inline-flex', alignItems: 'center', flexShrink: 0 }}>
                    {item.icon}
                  </span>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {item.label}
                  </span>
                </button>
              );
            })}
            {modes.length === 3 && (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  height: '34px',
                  padding: '0 8px',
                  borderRadius: '8px',
                  boxSizing: 'border-box',
                  textAlign: 'center',
                  userSelect: 'none',
                  minWidth: 0,
                }}
              >
                <span
                  style={{
                    fontSize: '11px',
                    fontWeight: 500,
                    color: 'var(--text-tertiary)',
                    lineHeight: 1.25,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    maxWidth: '100%',
                    letterSpacing: '-0.2px',
                  }}
                >
                  {t.nowPlaying.kugouFormalNotice1 || '在酷狗正式版下'}
                </span>
                <span
                  style={{
                    fontSize: '11px',
                    fontWeight: 500,
                    color: 'var(--text-tertiary)',
                    lineHeight: 1.25,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    maxWidth: '100%',
                    letterSpacing: '-0.2px',
                  }}
                >
                  {t.nowPlaying.kugouFormalNotice2 || '仅支持车机→手机的单向控制'}
                </span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
