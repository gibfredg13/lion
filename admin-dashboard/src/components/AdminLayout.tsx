import React, { useState } from 'react';
import { Routes, Route, useNavigate, useLocation, Navigate } from 'react-router-dom';
import Overview from './tabs/Overview';
import LiveFeed from './tabs/LiveFeed';
import Players from './tabs/Players';
import TokenStats from './tabs/TokenStats';
import DgxHealth from './tabs/DgxHealth';
import LlmBackends from './tabs/LlmBackends';
import Sessions from './tabs/Sessions';
import LevelControl from './tabs/LevelControl';
import LeaderboardAdmin from './tabs/LeaderboardAdmin';
import Settings from './tabs/Settings';
import HelpDesk from './tabs/HelpDesk';
import Calibration from './tabs/Calibration';

interface Props {
  user: { displayName: string; email: string; isAdmin: boolean };
  onLogout: () => void;
}

const NAV_ITEMS = [
  { path: '/', label: '📊 Overview', exact: true },
  { path: '/live-feed', label: '📡 Live Feed' },
  { path: '/players', label: '👥 Players' },
  { path: '/tokens', label: '🔢 Token Stats' },
  { path: '/dgx-health', label: '🤖 GPU Health' },
  { path: '/llm-backends', label: '🔀 LLM Backend' },
  { path: '/levels', label: '⚙️ Level Control' },
  { path: '/leaderboard', label: '🏆 Leaderboard' },
  { path: '/sessions', label: '🎬 Sessions' },
  { path: '/help-desk', label: '🛟 Help Desk' },
  { path: '/calibration', label: '🔬 Calibration' },
  { path: '/settings', label: '🔑 Settings' },
];

export default function AdminLayout({ user, onLogout }: Props) {
  const location = useLocation();
  const navigate = useNavigate();

  const sidebarStyle: React.CSSProperties = {
    width: '220px', minHeight: '100vh', background: '#1a1a2e',
    borderRight: '1px solid #2d3748', display: 'flex', flexDirection: 'column',
    position: 'fixed', top: 0, left: 0, zIndex: 100
  };

  const mainStyle: React.CSSProperties = {
    marginLeft: '220px', minHeight: '100vh',
    background: '#0d0d1a', display: 'flex', flexDirection: 'column'
  };

  return (
    <div style={{ display: 'flex' }}>
      {/* Sidebar */}
      <div style={sidebarStyle}>
        <div style={{ padding: '24px 20px', borderBottom: '1px solid #2d3748' }}>
          <div style={{ fontSize: '1.5rem', fontWeight: 900, color: '#ff6600' }}>🦁 War Room</div>
          <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '4px' }}>The Lion's Den Admin</div>
        </div>
        <nav style={{ flex: 1, padding: '12px 0' }}>
          {NAV_ITEMS.map(item => {
            const isActive = item.exact
              ? location.pathname === item.path
              : location.pathname.startsWith(item.path) && item.path !== '/';
            const finalActive = item.path === '/' ? location.pathname === '/' : isActive;
            return (
              <button key={item.path}
                onClick={() => navigate(item.path)}
                style={{
                  width: '100%', textAlign: 'left', padding: '10px 20px',
                  background: finalActive ? 'rgba(255,102,0,0.15)' : 'transparent',
                  color: finalActive ? '#ff8c00' : '#94a3b8',
                  borderLeft: finalActive ? '3px solid #ff6600' : '3px solid transparent',
                  border: 'none', cursor: 'pointer', fontSize: '0.9rem',
                  transition: 'all 0.2s', fontFamily: 'inherit'
                }}
              >
                {item.label}
              </button>
            );
          })}
        </nav>
        <div style={{ padding: '16px 20px', borderTop: '1px solid #2d3748', fontSize: '0.8rem', color: '#64748b' }}>
          <div style={{ fontWeight: 600, color: '#94a3b8', marginBottom: '4px' }}>{user.displayName}</div>
          <div style={{ marginBottom: '12px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{user.email}</div>
          <button onClick={onLogout} style={{
            background: 'rgba(239,68,68,0.15)', color: '#ef4444', border: '1px solid rgba(239,68,68,0.3)',
            borderRadius: '6px', padding: '6px 12px', cursor: 'pointer', fontSize: '0.8rem', fontFamily: 'inherit', width: '100%'
          }}>Logout</button>
        </div>
      </div>

      {/* Main content */}
      <div style={mainStyle}>
        {/* Top bar */}
        <div style={{
          padding: '14px 24px', background: '#1a1a2e',
          borderBottom: '1px solid #2d3748', display: 'flex',
          alignItems: 'center', justifyContent: 'space-between'
        }}>
          <span style={{ color: '#ff8c00', fontWeight: 700, fontSize: '1rem' }}>
            {NAV_ITEMS.find(i => i.path === '/' ? location.pathname === '/' : location.pathname.startsWith(i.path) && i.path !== '/')?.label || '📊 Overview'}
          </span>
          <span style={{ color: '#64748b', fontSize: '0.8rem' }}>
            🟢 Live &nbsp;•&nbsp; {new Date().toLocaleDateString()}
          </span>
        </div>
        <div style={{ flex: 1, padding: '24px' }}>
          <Routes>
            <Route path="/" element={<Overview />} />
            <Route path="/live-feed" element={<LiveFeed />} />
            <Route path="/players" element={<Players />} />
            <Route path="/tokens" element={<TokenStats />} />
            <Route path="/dgx-health" element={<DgxHealth />} />
          <Route path="/llm-backends" element={<LlmBackends />} />
            <Route path="/levels" element={<LevelControl />} />
            <Route path="/leaderboard" element={<LeaderboardAdmin />} />
          <Route path="/sessions" element={<Sessions />} />
            <Route path="/help-desk" element={<HelpDesk />} />
            <Route path="/calibration" element={<Calibration />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </div>
      </div>
    </div>
  );
}
