import React from 'react';

interface SwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  id?: string;
}

export const Switch: React.FC<SwitchProps> = ({ checked, onChange, disabled = false, id }) => {
  const handleClick = () => {
    if (disabled) return;
    if (typeof window !== 'undefined' && window.navigator?.vibrate) {
      try {
        window.navigator.vibrate(8);
      } catch {
        // ignore
      }
    }
    onChange(!checked);
  };

  return (
    <button
      id={id}
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={handleClick}
      style={{
        width: '44px',
        height: '24px',
        borderRadius: '12px',
        background: checked ? 'var(--accent-primary)' : 'var(--bg-surface-elevated)',
        border: checked ? '1px solid transparent' : '1px solid var(--border-subtle)',
        boxShadow: checked ? '0 0 12px var(--accent-glow)' : 'inset 0 1px 2px rgba(0, 0, 0, 0.15)',
        position: 'relative',
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.5 : 1,
        transition: 'background-color 300ms cubic-bezier(0.4, 0, 0.2, 1), border-color 300ms cubic-bezier(0.4, 0, 0.2, 1), box-shadow 300ms cubic-bezier(0.4, 0, 0.2, 1)',
        padding: '2px',
        display: 'inline-flex',
        alignItems: 'center',
        boxSizing: 'border-box',
        outline: 'none',
        flexShrink: 0,
      }}
    >
      <span
        style={{
          width: '18px',
          height: '18px',
          borderRadius: '50%',
          background: '#ffffff',
          boxShadow: '0 2px 5px rgba(0, 0, 0, 0.28), 0 1px 2px rgba(0, 0, 0, 0.12)',
          transform: checked ? 'translateX(20px)' : 'translateX(1px)',
          transition: 'transform 260ms cubic-bezier(0.16, 1, 0.3, 1)',
          display: 'block',
        }}
      />
    </button>
  );
};
