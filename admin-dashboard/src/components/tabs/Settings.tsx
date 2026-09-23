import React, { useState, useEffect } from 'react';

/** Mirrors merlin.event.allowedEmailDomains, so the copy can name who can still get in. */
const ALLOWED_EMAIL_DOMAIN = 'ing.com';

export default function Settings() {
  const [accessCode, setAccessCode] = useState('');
  const [required, setRequired] = useState(true);
  const [newCode, setNewCode] = useState('');
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    fetch('/api/admin/access-code', { credentials: 'include' })
      .then(r => r.json())
      .then(d => { setAccessCode(d.code || ''); setRequired(d.required !== false); })
      .catch(() => {});
  }, []);

  /**
   * Optimistic, reverted on failure - the same pattern as the public feed switch on Overview.
   * At the moment you flip this, waiting on a round trip feels broken and you click it again.
   */
  const toggleRequired = async () => {
    const next = !required;
    setRequired(next);
    setMessage('');
    try {
      const res = await fetch('/api/admin/access-code/required', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ required: next }),
      });
      if (!res.ok) throw new Error(String(res.status));
      setMessage(next ? '✅ Players now need the access code' : '✅ Players can register without a code');
    } catch {
      setRequired(!next);
      setMessage('❌ Could not change the access code requirement');
    }
  };

  const updateCode = async () => {
    if (!newCode.trim()) return;
    setSaving(true);
    setMessage('');
    try {
      const res = await fetch('/api/admin/access-code', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ code: newCode.trim().toUpperCase() })
      });
      if (res.ok) {
        setAccessCode(newCode.trim().toUpperCase());
        setNewCode('');
        setMessage('✅ Access code updated successfully');
      } else {
        setMessage('❌ Failed to update access code');
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <h2 style={{ color: '#ff8c00', marginBottom: '24px' }}>🔑 Event Settings</h2>
      <div className="card" style={{ maxWidth: '500px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
          <h3 style={{ margin: 0, color: '#e2e8f0' }}>Event Access Code</h3>
          <span className={required ? 'badge badge-success' : 'badge badge-warning'}>
            {required ? 'REQUIRED' : 'NOT REQUIRED'}
          </span>
        </div>
        <p style={{ color: '#64748b', fontSize: '0.85rem', marginBottom: '16px' }}>
          {required
            ? 'Players need this code to register. Share it at the start of your event.'
            : `Anyone with an @${ALLOWED_EMAIL_DOMAIN} address can register without a code.`}
        </p>
        <button
          className={required ? 'btn-danger' : 'btn-primary'}
          onClick={toggleRequired}
          style={{ marginBottom: '20px' }}
        >
          {required ? '🔓 Stop requiring a code' : '🔒 Require a code'}
        </button>
        {/* Kept visible while the gate is off, dimmed, so switching back on does not mean
            retyping a code nobody wrote down. */}
        <div style={{ background: '#0d0d1a', padding: '12px 16px', borderRadius: '6px', marginBottom: '20px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', opacity: required ? 1 : 0.5 }}>
          <span style={{ fontFamily: 'monospace', fontSize: '1.2rem', letterSpacing: '0.1em', color: '#ff8c00', fontWeight: 700 }}>{accessCode || '—'}</span>
          <span style={{ color: '#64748b', fontSize: '0.8rem' }}>
            {required ? 'Current code' : 'Not in use'}
          </span>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <input type="text" value={newCode} onChange={e => setNewCode(e.target.value.toUpperCase())}
            placeholder="New access code (e.g. LIONHACK2026)"
            style={{ flex: 1, background: '#0d0d1a', border: '1px solid #2d3748', color: 'white', padding: '10px 12px', borderRadius: '6px', fontFamily: 'monospace', fontSize: '1rem' }}
          />
          <button className="btn-primary" onClick={updateCode} disabled={saving || !newCode.trim()}>
            {saving ? '⏳' : 'Update'}
          </button>
        </div>
        {message && <p style={{ marginTop: '12px', color: message.includes('✅') ? '#22c55e' : '#ef4444', fontSize: '0.9rem' }}>{message}</p>}
      </div>

      <div className="card" style={{ maxWidth: '500px', marginTop: '24px' }}>
        <h3 style={{ marginBottom: '12px', color: '#e2e8f0' }}>Quick Links</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <a href="http://localhost:8080" target="_blank" rel="noreferrer"
            style={{ color: '#ff8c00', textDecoration: 'none', fontSize: '0.9rem' }}>🎮 Open game (localhost:8080)</a>
          <a href="http://localhost:8080/leaderboard/tv" target="_blank" rel="noreferrer"
            style={{ color: '#ff8c00', textDecoration: 'none', fontSize: '0.9rem' }}>📺 Open TV leaderboard</a>
        </div>
      </div>
    </div>
  );
}
