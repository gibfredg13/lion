import React, { useState, useEffect, useCallback } from 'react';

interface GameSession {
  id: string;
  label: string;
  startedAt: string;
  endedAt: string | null;
  resetLevels: boolean;
  createdBy: string | null;
  players: number;
  attempts: number;
  active: boolean;
}

const cardStyle: React.CSSProperties = {
  background: '#1a1a2e',
  border: '1px solid #2d3748',
  borderRadius: '8px',
  padding: '20px',
};

const fmt = (iso: string | null) =>
  iso ? new Date(iso).toLocaleString() : '—';

export default function Sessions() {
  const [sessions, setSessions] = useState<GameSession[]>([]);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [label, setLabel] = useState('');
  const [resetLevels, setResetLevels] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [eraseText, setEraseText] = useState('');
  const [disabledLevels, setDisabledLevels] = useState<number[]>([]);

  const load = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/game-sessions', { credentials: 'include' });
      if (res.ok) setSessions(await res.json());
      // Level gates are in-memory and survive a rollover, so the dialog warns rather than resets.
      const stats = await fetch('/api/admin/level-stats', { credentials: 'include' });
      if (stats.ok) {
        const rows = await stats.json();
        setDisabledLevels(rows.filter((r: any) => r.enabled === false).map((r: any) => r.level));
      }
    } catch (e) {
      console.error(e);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const openDialog = () => {
    setLabel(`Session ${sessions.length + 1}`);
    setResetLevels(false);
    setError(null);
    setDialogOpen(true);
  };

  const start = async () => {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const res = await fetch('/api/admin/game-sessions', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ label, resetLevels }),
      });
      if (!res.ok) {
        setError(await res.text());
      } else {
        setDialogOpen(false);
        await load();
      }
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  };

  const discard = async (s: GameSession) => {
    if (!window.confirm(`Discard "${s.label}" and its ${s.attempts} attempts?\n\nThis cannot be undone. Player levels are not changed.`)) return;
    try {
      const res = await fetch(`/api/admin/game-sessions/${s.id}`, { method: 'DELETE', credentials: 'include' });
      if (!res.ok) setError(await res.text());
      await load();
    } catch (e) {
      setError(String(e));
    }
  };

  const eraseEverything = async () => {
    if (eraseText !== 'ERASE') return;
    try {
      await fetch('/api/admin/leaderboard/reset', { method: 'POST', credentials: 'include' });
      setEraseText('');
      await load();
    } catch (e) {
      setError(String(e));
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h2 style={{ color: '#ff8c00', margin: 0 }}>🎬 Sessions</h2>
        <button className="btn-primary" onClick={openDialog}>+ New Session</button>
      </div>
      <p style={{ color: '#94a3b8', marginTop: 0, marginBottom: '24px', fontSize: '0.9rem' }}>
        A session is one run of the event. Starting a new one gives a fresh board and leaves every
        previous run's attempts, timings and rankings exactly where they are.
      </p>

      {error && (
        <div style={{ ...cardStyle, borderColor: '#ef4444', color: '#fca5a5', marginBottom: '16px' }}>{error}</div>
      )}

      <div style={{ ...cardStyle, padding: 0, overflow: 'hidden' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th>Session</th><th>Started</th><th>Ended</th><th>Players</th><th>Attempts</th><th>Levels</th><th></th>
            </tr>
          </thead>
          <tbody>
            {sessions.length === 0 && (
              <tr><td colSpan={7} style={{ color: '#94a3b8', padding: '20px' }}>No sessions yet.</td></tr>
            )}
            {sessions.map((s) => (
              <tr key={s.id} data-testid={`session-${s.id}`}>
                <td style={{ color: '#e2e8f0', fontWeight: 600 }}>
                  {s.label}{' '}
                  {s.active && <span className="badge badge-success">LIVE</span>}
                </td>
                <td style={{ color: '#94a3b8' }}>{fmt(s.startedAt)}</td>
                <td style={{ color: '#94a3b8' }}>{fmt(s.endedAt)}</td>
                <td style={{ color: '#e2e8f0' }}>{s.players}</td>
                <td style={{ color: '#e2e8f0' }}>{s.attempts}</td>
                <td>
                  <span className={s.resetLevels ? 'badge badge-warning' : 'badge badge-orange'}>
                    {s.resetLevels ? 'reset to 1' : 'carried on'}
                  </span>
                </td>
                <td style={{ textAlign: 'right' }}>
                  {!s.active && (
                    <button className="btn-danger" onClick={() => discard(s)}>Discard</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div style={{ ...cardStyle, marginTop: '32px', borderColor: '#ef4444' }}>
        <h3 style={{ color: '#ef4444', marginTop: 0 }}>Danger zone</h3>
        <p style={{ color: '#94a3b8', fontSize: '0.9rem' }}>
          Erase every session's attempts, timelines and rankings, and send all players back to level 1.
          This is for clearing test data before a real event — to start a fresh run without losing
          anything, use <strong>New Session</strong> instead.
        </p>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <input
            aria-label="Type ERASE to confirm"
            placeholder="Type ERASE to confirm"
            value={eraseText}
            onChange={(e) => setEraseText(e.target.value)}
            style={{ padding: '8px 12px', background: '#0d0d1a', border: '1px solid #2d3748', borderRadius: '6px', color: '#e2e8f0' }}
          />
          <button className="btn-danger" disabled={eraseText !== 'ERASE'} onClick={eraseEverything}>
            Erase ALL data
          </button>
        </div>
      </div>

      {dialogOpen && (
        <div
          role="dialog"
          aria-label="New Session"
          style={{
            position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', zIndex: 200,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}
          onClick={() => !busy && setDialogOpen(false)}
        >
          <div style={{ ...cardStyle, width: 'min(560px, 92vw)' }} onClick={(e) => e.stopPropagation()}>
            <h3 style={{ color: '#ff8c00', marginTop: 0 }}>New Session</h3>

            <label style={{ color: '#94a3b8', fontSize: '0.85rem' }} htmlFor="session-label">Name</label>
            <input
              id="session-label"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              style={{ width: '100%', margin: '6px 0 18px', padding: '10px', background: '#0d0d1a', border: '1px solid #2d3748', borderRadius: '6px', color: '#e2e8f0' }}
            />

            <div style={{ color: '#94a3b8', fontSize: '0.85rem', marginBottom: '8px' }}>Player levels</div>
            {[
              { value: false, title: 'Let players carry on', body: 'Everyone keeps the level they reached, and their score carries with them.' },
              { value: true, title: 'Send everyone back to Level 1', body: 'Everyone restarts from the beginning. Previous results are kept and stay browsable.' },
            ].map((opt) => (
              <label
                key={String(opt.value)}
                style={{
                  display: 'block', padding: '12px', marginBottom: '8px', borderRadius: '6px',
                  cursor: 'pointer', background: '#0d0d1a',
                  border: `1px solid ${resetLevels === opt.value ? '#ff8c00' : '#2d3748'}`,
                }}
              >
                <input
                  type="radio"
                  name="resetLevels"
                  checked={resetLevels === opt.value}
                  onChange={() => setResetLevels(opt.value)}
                />{' '}
                <strong style={{ color: '#e2e8f0' }}>{opt.title}</strong>
                <div style={{ color: '#94a3b8', fontSize: '0.85rem', marginLeft: '22px' }}>{opt.body}</div>
              </label>
            ))}

            <div style={{ color: '#f59e0b', fontSize: '0.85rem', marginTop: '14px' }}>
              ⚠ Secret words are re-drawn for every player. Anyone holding an answer right now will
              need to ask Leo again.
            </div>
            {disabledLevels.length > 0 && (
              <div style={{ color: '#f59e0b', fontSize: '0.85rem', marginTop: '6px' }}>
                ⚠ Levels currently disabled stay disabled: {disabledLevels.join(', ')}
              </div>
            )}
            {sessions.find((s) => s.active) && (
              <div style={{ color: '#94a3b8', fontSize: '0.85rem', marginTop: '6px' }}>
                ⓘ {sessions.find((s) => s.active)!.label} will be closed and archived.
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
              <button className="btn-danger" disabled={busy} onClick={() => setDialogOpen(false)}>Cancel</button>
              <button className="btn-primary" disabled={busy} onClick={start}>
                {busy ? 'Starting…' : `Start ${label}`}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
