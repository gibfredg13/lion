import { useState, useEffect } from 'react';

interface Player {
  id: string; email: string; displayName: string; isAdmin: boolean;
  currentLevel: number; createdAt: string; lastLoginAt: string | null;
  attempts: number; tokens: number; lastActivity: string | null;
}
interface PromptRow {
  id: number; level: number; prompt: string; response: string;
  blocked: boolean; blockedBy: string | null; tokens: number; createdAt: string;
}

const api = (url: string, init?: RequestInit) =>
  fetch(url, { credentials: 'include', ...init });

export default function Players() {
  const [players, setPlayers] = useState<Player[]>([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState<{ text: string; bad?: boolean } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [history, setHistory] = useState<{ player: Player; rows: PromptRow[] } | null>(null);
  const [resetting, setResetting] = useState<Player | null>(null);
  const [newPassword, setNewPassword] = useState('');
  const [query, setQuery] = useState('');

  const load = () => api('/api/admin/users')
    .then(r => r.json()).then(setPlayers)
    .catch(() => setMsg({ text: 'Could not load players.', bad: true }))
    .finally(() => setLoading(false));

  useEffect(() => { load(); }, []);

  const say = (text: string, bad?: boolean) => {
    setMsg({ text, bad });
    setTimeout(() => setMsg(null), 4000);
  };

  const act = async (id: string, label: string, init: RequestInit, url: string) => {
    setBusy(id);
    try {
      const res = await api(url, init);
      const body = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(body.message || `${label} failed`);
      say(`${label} ✓`);
      await load();
      return body;
    } catch (e: any) {
      say(e.message || `${label} failed`, true);
    } finally {
      setBusy(null);
    }
  };

  const openHistory = async (p: Player) => {
    setBusy(p.id);
    try {
      const rows = await api(`/api/admin/users/${p.id}/prompts`).then(r => r.json());
      setHistory({ player: p, rows });
    } catch {
      say('Could not load prompt history.', true);
    } finally { setBusy(null); }
  };

  const doReset = async () => {
    if (!resetting) return;
    await act(resetting.id, 'Password reset', {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ newPassword }),
    }, `/api/admin/users/${resetting.id}/password`);
    setResetting(null); setNewPassword('');
  };

  const remove = (p: Player) => {
    const warn = p.attempts > 0
      ? `Delete ${p.email}? This also permanently deletes their ${p.attempts} recorded prompts.`
      : `Delete ${p.email}?`;
    if (!confirm(warn)) return;
    act(p.id, 'Deleted', { method: 'DELETE' }, `/api/admin/users/${p.id}`);
  };

  const shown = players.filter(p =>
    !query || (p.email + p.displayName).toLowerCase().includes(query.toLowerCase()));

  const btn: React.CSSProperties = { fontSize: '0.75rem', padding: '4px 9px' };
  const card: React.CSSProperties = {
    background: '#1a1a2e', border: '1px solid #2d3748', borderRadius: '8px', padding: '14px',
  };

  if (loading) return <div>Loading players…</div>;

  return (
    <div>
      <h2 style={{ color: '#ff8c00', marginBottom: '6px' }}>👥 Players</h2>
      <p style={{ color: '#64748b', marginBottom: '16px', fontSize: '0.85rem' }}>
        Every prompt a player sends is recorded. Deleting an account deletes its history too.
      </p>

      {msg && (
        <div style={{
          ...card, marginBottom: '14px', padding: '10px 14px',
          borderLeft: `4px solid ${msg.bad ? '#ef4444' : '#22c55e'}`,
          color: msg.bad ? '#fca5a5' : '#86efac',
        }}>{msg.text}</div>
      )}

      <input
        placeholder="Filter by name or email…"
        value={query}
        onChange={e => setQuery(e.target.value)}
        style={{
          width: '100%', maxWidth: '340px', marginBottom: '14px', padding: '8px 10px',
          background: '#0d0d1a', color: '#e2e8f0', border: '1px solid #2d3748', borderRadius: '6px',
        }}
      />

      <div style={{ display: 'grid', gap: '10px' }}>
        {shown.map(p => (
          <div key={p.id} style={{ ...card, borderLeft: `4px solid ${p.isAdmin ? '#ff6600' : '#2d3748'}` }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', flexWrap: 'wrap' }}>
              <div style={{ minWidth: '240px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <strong style={{ color: '#e2e8f0' }}>{p.displayName}</strong>
                  {p.isAdmin && <span className="badge badge-warning">admin</span>}
                </div>
                <div style={{ color: '#64748b', fontSize: '0.82rem' }}>{p.email}</div>
                <div style={{ color: '#94a3b8', fontSize: '0.82rem', marginTop: '6px', display: 'flex', gap: '14px', flexWrap: 'wrap' }}>
                  <span>Level <strong style={{ color: '#ff8c00' }}>{p.currentLevel}</strong></span>
                  <span>Prompts <strong style={{ color: '#e2e8f0' }}>{p.attempts}</strong></span>
                  <span>Tokens <strong style={{ color: '#e2e8f0' }}>{p.tokens}</strong></span>
                  <span>Last seen {p.lastActivity ? new Date(p.lastActivity).toLocaleString() : 'never played'}</span>
                </div>
              </div>
              <div style={{ display: 'flex', gap: '6px', alignItems: 'flex-start', flexWrap: 'wrap' }}>
                <button className="btn" style={btn} disabled={busy === p.id}
                        onClick={() => openHistory(p)}>
                  Prompts ({p.attempts})
                </button>
                <button className="btn" style={btn} disabled={busy === p.id}
                        onClick={() => { setResetting(p); setNewPassword(''); }}>
                  Reset password
                </button>
                <button className="btn" style={btn} disabled={busy === p.id}
                        onClick={() => act(p.id, 'Progress reset', { method: 'POST' },
                                           `/api/admin/users/${p.id}/reset-progress`)}>
                  Back to level 1
                </button>
                <button className="btn" style={btn} disabled={busy === p.id}
                        onClick={() => act(p.id, p.isAdmin ? 'Admin revoked' : 'Admin granted', {
                          method: 'PUT', headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({ isAdmin: !p.isAdmin }),
                        }, `/api/admin/users/${p.id}/admin`)}>
                  {p.isAdmin ? 'Revoke admin' : 'Make admin'}
                </button>
                <button className="btn" style={{ ...btn, borderColor: '#ef4444', color: '#fca5a5' }}
                        disabled={busy === p.id} onClick={() => remove(p)}>
                  Delete
                </button>
              </div>
            </div>
          </div>
        ))}
        {shown.length === 0 && <div style={{ color: '#64748b' }}>No players match that filter.</div>}
      </div>

      {/* Password reset */}
      {resetting && (
        <Modal title={`Reset password — ${resetting.email}`} onClose={() => setResetting(null)}>
          <p style={{ color: '#94a3b8', fontSize: '0.85rem' }}>
            Sets the password immediately. This skips the sign-up rules on purpose, so you can hand
            out something short and sayable at an event.
          </p>
          <input
            autoFocus type="text" placeholder="New password (min 4 characters)"
            value={newPassword} onChange={e => setNewPassword(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter' && newPassword.length >= 4) doReset(); }}
            style={{
              width: '100%', padding: '9px 10px', margin: '10px 0',
              background: '#0d0d1a', color: '#e2e8f0', border: '1px solid #2d3748', borderRadius: '6px',
            }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="btn" disabled={newPassword.length < 4} onClick={doReset}>Set password</button>
            <button className="btn" onClick={() => setResetting(null)}>Cancel</button>
          </div>
        </Modal>
      )}

      {/* Prompt history */}
      {history && (
        <Modal title={`Prompts — ${history.player.displayName}`} wide onClose={() => setHistory(null)}>
          {history.rows.length === 0
            ? <p style={{ color: '#64748b' }}>This player has not sent any prompts yet.</p>
            : (
              <div style={{ display: 'grid', gap: '8px', maxHeight: '62vh', overflowY: 'auto' }}>
                {history.rows.map(r => (
                  <div key={r.id} style={{ border: '1px solid #2d3748', borderRadius: '6px', padding: '10px' }}>
                    <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap', fontSize: '0.75rem', color: '#64748b' }}>
                      <span className="badge">Level {r.level}</span>
                      {r.blocked && <span className="badge badge-warning">blocked: {r.blockedBy}</span>}
                      <span>{new Date(r.createdAt).toLocaleString()}</span>
                      <span>{r.tokens} tokens</span>
                    </div>
                    <div style={{ marginTop: '8px', color: '#e2e8f0', fontFamily: 'monospace', fontSize: '0.8rem' }}>
                      ▸ {r.prompt}
                    </div>
                    <div style={{ marginTop: '6px', color: '#94a3b8', fontSize: '0.8rem', lineHeight: 1.45 }}>
                      {r.response}
                    </div>
                  </div>
                ))}
              </div>
            )}
        </Modal>
      )}
    </div>
  );
}

function Modal({ title, children, onClose, wide }:
  { title: string; children: React.ReactNode; onClose: () => void; wide?: boolean }) {
  return (
    <div onClick={onClose} style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', zIndex: 500,
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px',
    }}>
      <div onClick={e => e.stopPropagation()} style={{
        background: '#1a1a2e', border: '1px solid #2d3748', borderRadius: '10px', padding: '20px',
        width: '100%', maxWidth: wide ? '820px' : '460px',
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
          <strong style={{ color: '#ff8c00' }}>{title}</strong>
          <button className="btn" style={{ fontSize: '0.75rem', padding: '4px 9px' }} onClick={onClose}>Close</button>
        </div>
        {children}
      </div>
    </div>
  );
}
