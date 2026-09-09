import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.tsx';
import './index.css';

// 1. 在首屏 React 挂载前同步完成屏幕自适应缩放，杜绝挂载后二次重排导致的 Dock 抽搐跳跃
if (typeof window !== 'undefined' && typeof document !== 'undefined' && document.body) {
  const minDimension = Math.min(window.screen.width, window.screen.height);
  const ratio = Math.max(0.85, Math.min(1.25, Number((minDimension / 392).toFixed(3))));
  document.body.style.zoom = String(ratio);
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
