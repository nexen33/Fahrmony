import React, { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';

interface SplashViewProps {
  onFinish: () => void;
  theme: 'dark' | 'light';
}

/**
 * 全屏纯色开屏视图
 * 1. 挂载于 document.body 顶层 Portal，突破 maxWidth 480px 与相对容器约束；
 * 2. 纯色背景严格适配深浅模式 (#0a0c10 / #f8fafc)；
 * 3. Outfit 字体品牌字样水平居中、垂直 45% 靠上，轻快非线性从左至右淡入；
 * 4. 整体时长严格控制在 1.2s 以内 (0~820ms 展出，820~1140ms 非线性平滑淡出并彻底卸载)。
 */
export const SplashView: React.FC<SplashViewProps> = ({ onFinish, theme }) => {
  const [isExiting, setIsExiting] = useState(false);
  const onFinishRef = useRef(onFinish);

  useEffect(() => {
    onFinishRef.current = onFinish;
  }, [onFinish]);

  useEffect(() => {
    // 0 ~ 820ms: 展出并完成 Outfit 品牌字样从左至右轻快非线性淡入
    const exitTimer = setTimeout(() => {
      setIsExiting(true);
    }, 820);

    // 820ms ~ 1140ms: 320ms 非线性淡出，1140ms 执行回调卸载 DOM (总耗时 1.14s < 1.2s)
    const doneTimer = setTimeout(() => {
      onFinishRef.current();
    }, 1140);

    return () => {
      clearTimeout(exitTimer);
      clearTimeout(doneTimer);
    };
  }, []);

  const isDark = theme === 'dark';
  const bgColor = isDark ? '#0a0c10' : '#f8fafc';
  const textColor = isDark ? '#f1f5f9' : '#0f172a';

  const splashDom = (
    <div
      id="fahrmony-splash-view"
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        width: '100%',
        height: '100%',
        zIndex: 999999,
        backgroundColor: bgColor,
        opacity: isExiting ? 0 : 1,
        transition: 'opacity 320ms cubic-bezier(0.16, 1, 0.3, 1)',
        pointerEvents: isExiting ? 'none' : 'auto',
        userSelect: 'none',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          position: 'absolute',
          top: '45%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          fontFamily: "'Outfit', var(--font-sans), sans-serif",
          fontSize: '38px',
          fontWeight: 700,
          letterSpacing: '-0.02em',
          color: textColor,
          WebkitMaskImage: 'linear-gradient(to right, #000 0%, #000 50%, transparent 100%)',
          maskImage: 'linear-gradient(to right, #000 0%, #000 50%, transparent 100%)',
          WebkitMaskSize: '220% 100%',
          maskSize: '220% 100%',
          animation: 'splashTextMaskReveal 520ms cubic-bezier(0.16, 1, 0.3, 1) forwards',
          whiteSpace: 'nowrap',
          display: 'inline-block',
        }}
      >
        Fahrmony
      </div>
    </div>
  );

  if (typeof document === 'undefined') return null;
  return createPortal(splashDom, document.body);
};
