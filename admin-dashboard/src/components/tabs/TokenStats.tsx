import React, { useState, useEffect } from 'react';

interface TokenData {
  totalTokens: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  tokensLastHour: number;
}

interface UserTokenData {
  displayName: string;
  email: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
}

export default function TokenStats() {
  const [stats, setStats] = useState<TokenData | null>(null);
  const [userStats, setUserStats] = useState<UserTokenData[]>([]);

  useEffect(() => {
    const load = () => {
      fetch('/api/admin/token-stats', { credentials: 'include' }).then(r => r.json()).then(setStats).catch(() => {});
      fetch('/api/admin/analytics/token-usage-by-user', { credentials: 'include' }).then(r => r.json()).then(setUserStats).catch(() => {});
    };
    load();
    const i = setInterval(load, 5000);
    return () => clearInterval(i);
  }, []);

  return (
    <div>
      <h2 style={{ color: '#ff8c00', marginBottom: '24px' }}>🔢 Token Analytics</h2>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px', marginBottom: '32px' }}>
        {[
          { icon: '📊', label: 'Total Tokens', value: stats ? `${(stats.totalTokens/1000).toFixed(1)}K` : '—' },
          { icon: '📥', label: 'Input Tokens', value: stats ? `${(stats.totalInputTokens/1000).toFixed(1)}K` : '—' },
          { icon: '📤', label: 'Output Tokens', value: stats ? `${(stats.totalOutputTokens/1000).toFixed(1)}K` : '—' },
          { icon: '⚡', label: 'Last Hour', value: stats ? `${(stats.tokensLastHour/1000).toFixed(1)}K` : '—' },
        ].map(m => (
          <div key={m.label} className="metric-card">
            <div className="metric-icon">{m.icon}</div>
            <div className="metric-value">{m.value}</div>
            <div className="metric-label">{m.label}</div>
          </div>
        ))}
      </div>
      <div className="card">
        <h3 style={{ color: '#ff8c00', marginBottom: '16px' }}>Per-Player Token Usage</h3>
        <table>
          <thead><tr><th>Player</th><th>Email</th><th>Input</th><th>Output</th><th>Total</th></tr></thead>
          <tbody>
            {userStats.sort((a,b) => b.totalTokens - a.totalTokens).map((u, i) => (
              <tr key={i}>
                <td style={{ fontWeight: 600 }}>{u.displayName}</td>
                <td style={{ color: '#64748b' }}>{u.email}</td>
                <td>{(u.inputTokens/1000).toFixed(1)}K</td>
                <td>{(u.outputTokens/1000).toFixed(1)}K</td>
                <td><span className="badge badge-orange">{(u.totalTokens/1000).toFixed(1)}K</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
