import React, { useState, useRef, useEffect } from 'react';

export interface SelectOption {
  value: string;
  label: string;
  icon?: React.ReactNode;
}

interface CustomSelectProps {
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  compact?: boolean;
}

export const CustomSelect: React.FC<CustomSelectProps> = ({
  value,
  onChange,
  options,
  placeholder,
  compact = false,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const selectedOption = options.find((opt) => opt.value === value);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isOpen]);

  const handleSelect = (val: string) => {
    onChange(val);
    setIsOpen(false);
    if (typeof window !== 'undefined' && window.navigator?.vibrate) {
      try {
        window.navigator.vibrate(6);
      } catch {
        // ignore
      }
    }
  };

  return (
    <div ref={containerRef} style={{ position: 'relative', display: 'inline-block', width: compact ? 'auto' : '100%' }}>
      {/* 触发按键 (移除缩放动效，防止点按跳动；高度固定 44px 与右侧按键对齐) */}
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '8px',
          width: '100%',
          height: compact ? '32px' : '44px',
          minHeight: compact ? '32px' : '44px',
          padding: compact ? '4px 12px' : '0 16px',
          borderRadius: compact ? '8px' : '12px',
          background: 'var(--bg-surface-elevated)',
          border: '1px solid var(--border-subtle)',
          color: 'var(--text-primary)',
          fontSize: compact ? '15px' : '16px',
          fontWeight: 500,
          cursor: 'pointer',
          outline: 'none',
          boxSizing: 'border-box',
          whiteSpace: 'nowrap',
          transition: 'border-color 140ms ease',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {selectedOption?.icon}
          <span style={{ whiteSpace: 'nowrap' }}>{selectedOption ? selectedOption.label : placeholder || '请选择'}</span>
        </div>
        <svg
          width="12"
          height="12"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          style={{
            transform: isOpen ? 'rotate(180deg)' : 'rotate(0deg)',
            transition: 'transform 180ms ease',
            color: 'var(--text-tertiary)',
            flexShrink: 0,
          }}
        >
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </button>

      {/* 展开浮层菜单 (重质感低透高雾化毛玻璃，根据最长内容自适应宽度，杜绝任何语言下被迫换行) */}
      {isOpen && (
        <div
          style={{
            position: 'absolute',
            top: 'calc(100% + 6px)',
            left: compact ? 'auto' : 0,
            right: 0,
            minWidth: compact ? 'max-content' : '100%',
            maxWidth: '280px',
            maxHeight: '380px',
            overflowY: 'auto',
            borderRadius: '14px',
            padding: '5px',
            zIndex: 300,
            backdropFilter: 'blur(36px)',
            WebkitBackdropFilter: 'blur(36px)',
            background: 'var(--bg-surface-elevated)',
            boxShadow: '0 16px 44px rgba(0, 0, 0, 0.42), 0 2px 6px rgba(0, 0, 0, 0.2)',
            border: '1px solid var(--border-glass)',
            boxSizing: 'border-box',
          }}
        >
          {options.map((opt) => {
            const isSelected = opt.value === value;
            return (
              <div
                key={opt.value}
                onClick={() => handleSelect(opt.value)}
                className="btn-jelly"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '12px',
                  padding: '10px 14px',
                  borderRadius: '9px',
                  fontSize: '16px',
                  cursor: 'pointer',
                  background: isSelected ? 'var(--accent-tint)' : 'transparent',
                  color: isSelected ? 'var(--accent-primary)' : 'var(--text-primary)',
                  fontWeight: isSelected ? 600 : 400,
                  transition: 'background-color 100ms ease',
                  whiteSpace: 'nowrap',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', whiteSpace: 'nowrap' }}>
                  {opt.icon}
                  <span style={{ whiteSpace: 'nowrap' }}>{opt.label}</span>
                </div>
                {isSelected && (
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="20 6 9 17 4 12" />
                  </svg>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
