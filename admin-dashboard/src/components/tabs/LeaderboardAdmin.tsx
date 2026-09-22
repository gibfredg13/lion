import React, { useState, useEffect, useCallback } from 'react';

interface Entry {
  rank: number;
  name: string;
  email: string;
  maxLevelReached: number;
  durationSeconds: number;
  totalTokensUsed: number;
  finishedAt?: string;
}

interface GameSession {
  id: string;
  label: string;
  endedAt: string | null;
  active: boolean;
  players: number;
  attempts: number;
}

export default function LeaderboardAdmin() {
  const [entries, setEntries] = useState<Entry[]>([]);
  const [loading, setLoading] = useState(true);
  const [sessions, setSessions] = useState<GameSession[]>([]);
  const [sessionId, setSessionId] = useState('');

  const viewing = sessions.find(s => s.id === sessionId);
  const historical = viewing != null && !viewing.active;

  const load = useCallback(() => {
    const query = sessionId ? `?sessionId=${encodeURIComponent(sessionId)}` : '';
    fetch(`/api/admin/leaderboard${query}`, { credentials: 'include' })
      .then(r => r.json()).then(setEntries).catch(() => [])
      .finally(() => setLoading(false));
  }, [sessionId]);

  useEffect(() => {
    fetch('/api/admin/game-sessions', { credentials: 'include' })
      .then(r => r.json()).then(setSessions).catch(() => []);
  }, []);

  useEffect(() => {
    load();
    // A finished run cannot change, so polling it is pure noise on the network and the eye.
    if (historical) return;
    const i = setInterval(load, 10000);
    return () => clearInterval(i);
  }, [load, historical]);

  const formatTime = (s: number) => {
    const h = Math.floor(s/3600), m = Math.floor((s%3600)/60);
    return h > 0 ? `${h}h ${m}m` : `${m}m`;
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <h2 style={{ color: '#ff8c00' }}>🏆 Leaderboard Management</h2>
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <select
            aria-label="Session"
            value={sessionId}
            onChange={(e) => { setSessionId(e.target.value); setLoading(true); }}
            style={{ padding: '8px 12px', background: '#1a1a2e', color: '#e2e8f0', border: '1px solid #2d3748', borderRadius: '6px' }}
          >
            <option value="">Current session</option>
            {sessions.filter(s => !s.active).map(s => (
              <option key={s.id} value={s.id}>{s.label} · {s.players} players</option>
            ))}
          </select>
          <button className="btn-primary" onClick={load}>🔄 Refresh</button>
        </div>
      </div>
      {/* Starting a new session replaced the old "Reset Scores" button. That one ran
          DELETE FROM logs, so the only way to begin again was to destroy the last event.
          The remaining erase-everything control lives in the Sessions tab's danger zone. */}
      {historical && (
        <div style={{ background: 'rgba(245,158,11,0.15)', border: '1px solid #f59e0b', color: '#fcd34d',
                      borderRadius: '6px', padding: '10px 14px', marginBottom: '16px' }}>
          📼 Viewing <strong>{viewing!.label}</strong> — history, not live.
        </div>
      )}
      <div className="card">
        {loading ? <p>Loading...</p> : entries.length === 0 ? (
          <p style={{ textAlign: 'center', color: '#64748b', padding: '40px' }}>No entries yet. Players will appear here as they progress.</p>
        ) : (
          <table>
            <thead><tr><th>Rank</th><th>Player</th><th>Email</th><th>Level</th><th>Time</th><th>Tokens</th><th>Completed</th></tr></thead>
            <tbody>
              {entries.map((e, i) => (
                <tr key={i}>
                  <td style={{ fontSize: '1.2rem' }}>{e.rank === 1 ? '🥇' : e.rank === 2 ? '🥈' : e.rank === 3 ? '🥉' : `#${e.rank}`}</td>
                  <td style={{ fontWeight: 600 }}>{e.name}</td>
                  <td style={{ color: '#64748b' }}>{e.email}</td>
                  <td><span className="badge badge-orange">L{e.maxLevelReached}</span></td>
                  <td>{formatTime(e.durationSeconds)}</td>
                  <td>{(e.totalTokensUsed/1000).toFixed(1)}K</td>
                  <td style={{ color: '#64748b', fontSize: '0.85rem' }}>{e.finishedAt ? new Date(e.finishedAt).toLocaleString() : 'In Progress'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
