import React, { useState, useEffect } from 'react';

interface Stats {
  totalParticipants: number;
  completedLevel7: number;
  averageTimeSeconds: number;
  currentlyActive: number;
}

interface TokenStats {
  totalTokens: number;
  tokensLastHour: number;
}

interface DgxHealth {
  healthy: boolean;
  avgLatencyMs: number;
  provider: string;
}

export default function Overview() {
  const [feedEnabled, setFeedEnabled] = useState(false);

  const loadTvSettings = () => {
    fetch('/api/admin/tv/settings', { credentials: 'include' })
      .then(r => r.json()).then(d => setFeedEnabled(!!d.feedEnabled)).catch(() => {});
  };

  /**
   * Optimistic, and reverted on failure. At the moment you need this, a five-second round trip
   * feels broken and you click it four more times.
   */
  const toggleFeed = async () => {
    const next = !feedEnabled;
    setFeedEnabled(next);
    try {
      const res = await fetch('/api/admin/tv/feed-enabled', {
        method: 'PUT',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled: next }),
      });
      if (!res.ok) throw new Error(String(res.status));
    } catch {
      setFeedEnabled(!next);
    }
  };

  const [stats, setStats] = useState<Stats | null>(null);
  const [tokens, setTokens] = useState<TokenStats | null>(null);
  const [dgx, setDgx] = useState<DgxHealth | null>(null);

  useEffect(() => {
    const fetch_ = () => {
      fetch('/api/admin/leaderboard/stats', { credentials: 'include' }).then(r => r.json()).then(setStats).catch(() => {});
      fetch('/api/admin/token-stats', { credentials: 'include' }).then(r => r.json()).then(setTokens).catch(() => {});
      fetch('/api/admin/dgx-health', { credentials: 'include' }).then(r => r.json()).then(setDgx).catch(() => {});
      loadTvSettings();
    };
    fetch_();
    const interval = setInterval(fetch_, 5000);
    return () => clearInterval(interval);
  }, []);

  const metrics = [
    { icon: '👥', label: 'Total Players', value: stats?.totalParticipants ?? '—' },
    { icon: '🎯', label: 'Active Now', value: stats?.currentlyActive ?? '—' },
    { icon: '🏆', label: 'Fully Completed', value: stats?.completedLevel7 ?? '—' },
    { icon: '⏱️', label: 'Avg Time (min)', value: stats?.averageTimeSeconds ? Math.round(stats.averageTimeSeconds / 60) : '—' },
    { icon: '🔢', label: 'Total Tokens', value: tokens?.totalTokens ? `${(tokens.totalTokens/1000).toFixed(1)}K` : '—' },
    {
      icon: dgx?.healthy ? '🟢' : '🔴',
      label: 'DGX Spark',
      value: dgx?.healthy ? `${dgx.avgLatencyMs}ms` : 'Offline'
    },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: '24px', color: '#ff8c00' }}>🦁 Event Overview</h2>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '16px', marginBottom: '32px' }}>
        {metrics.map(m => (
          <div key={m.label} className="metric-card">
            <div className="metric-icon">{m.icon}</div>
            <div className="metric-value">{m.value}</div>
            <div className="metric-label">{m.label}</div>
          </div>
        ))}
      </div>
      <div className="card">
        <h3 style={{ marginBottom: '12px', color: '#ff8c00' }}>Quick Actions</h3>
        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          <button className="btn-primary" onClick={() => window.open('/leaderboard/tv', '_blank')}>📺 Open TV Mode</button>
          <button className="btn-primary" onClick={() => window.open('http://localhost:8080', '_blank')}>🎮 Open Game</button>
          {/* An emergency control, so it sits beside the TV link rather than in Settings. Named
              distinctly from the Live Feed tab's Pause, which only affects the admin's own view. */}
          <button
            className={feedEnabled ? 'btn-danger' : 'btn-primary'}
            onClick={toggleFeed}
          >
            {feedEnabled ? '🔇 Hide public feed' : '📢 Show public feed'}
          </button>
          <span
            className={feedEnabled ? 'badge badge-success' : 'badge badge-warning'}
            style={{ alignSelf: 'center' }}
          >
            Public feed {feedEnabled ? 'LIVE' : 'HIDDEN'}
          </span>
        </div>
      </div>
    </div>
  );
}
