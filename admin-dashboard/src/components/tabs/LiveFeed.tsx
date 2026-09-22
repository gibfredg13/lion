import React, { useState, useEffect, useRef } from 'react';

/** Mirrors the row shape of GET /api/admin/analytics/recent-prompts exactly. */
interface PromptEntry {
  id: number;
  displayName: string;
  email: string;
  level: number;
  prompt: string;
  response: string;
  createdAt: string;
  blocked: boolean;
  blockedBy: string | null;
  tokens: number;
}

/** Never render "Invalid Date" at an event: a missing or unparseable stamp shows as a dash. */
const formatTime = (iso?: string) => {
  if (!iso) return '—';
  const at = new Date(iso);
  return Number.isNaN(at.getTime()) ? '—' : at.toLocaleTimeString();
};

export default function LiveFeed() {
  const [feed, setFeed] = useState<PromptEntry[]>([]);
  const [paused, setPaused] = useState(false);
  const [filterLevel, setFilterLevel] = useState<number | null>(null);
  const feedRef = useRef<HTMLDivElement>(null);
  const pausedRef = useRef(paused);
  pausedRef.current = paused;

  useEffect(() => {
    // Poll for recent prompts every 3 seconds
    const poll = async () => {
      if (pausedRef.current) return;
      try {
        const res = await fetch('/api/admin/analytics/recent-prompts?limit=50', { credentials: 'include' });
        if (res.ok) {
          const data = await res.json();
          setFeed(data);
        }
      } catch (e) { /* ignore */ }
    };
    poll();
    const interval = setInterval(poll, 3000);
    return () => clearInterval(interval);
  }, []);

  const filtered = filterLevel ? feed.filter(e => e.level === filterLevel) : feed;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ color: '#ff8c00' }}>📡 Live Prompt Feed</h2>
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <select onChange={e => setFilterLevel(e.target.value ? Number(e.target.value) : null)}
            style={{ background: '#1a1a2e', color: 'white', border: '1px solid #2d3748', padding: '6px 10px', borderRadius: '6px' }}>
            <option value="">All Levels</option>
            {[1,2,3,4,5,6,7].map(l => <option key={l} value={l}>Level {l}</option>)}
          </select>
          <button onClick={() => setPaused(p => !p)} className={paused ? 'btn-primary' : 'btn-danger'}>
            {paused ? '▶ Resume' : '⏸ Pause'}
          </button>
        </div>
      </div>
      <div ref={feedRef} style={{ height: 'calc(100vh - 220px)', overflowY: 'auto' }}>
        {filtered.length === 0 ? (
          <div className="card" style={{ textAlign: 'center', padding: '40px', color: '#64748b' }}>
            🦁 Waiting for player prompts...
          </div>
        ) : filtered.map((entry, i) => (
          <div key={entry.id ?? i} className="card" style={{ marginBottom: '12px', borderLeft: entry.blocked ? '3px solid #ef4444' : '3px solid #2d3748' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
              <span style={{ fontWeight: 600, color: '#e2e8f0' }}>{entry.displayName}</span>
              <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                {entry.blocked && <span className="badge badge-danger">⚠ {entry.blockedBy ?? 'blocked'}</span>}
                <span className="badge badge-orange">Level {entry.level}</span>
                <span style={{ color: '#64748b', fontSize: '0.8rem' }}>{formatTime(entry.createdAt)}</span>
              </div>
            </div>
            <div style={{ color: '#94a3b8', fontSize: '0.85rem', marginBottom: '6px' }}>💬 {entry.prompt}</div>
            <div style={{ color: '#e2e8f0', fontSize: '0.85rem', borderTop: '1px solid #2d3748', paddingTop: '6px' }}>🦁 {entry.response}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
