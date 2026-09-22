import { useState, useEffect } from 'react';

interface LevelStat {
  level: number;
  enabled: boolean;
  attempts: number;
  players: number;
  completions: number;
}

/** Mirrors LevelDefinition on the backend. */
interface LevelDefinition {
  order: number;
  name: string;
  description: string;
  systemMessages: string[];
  temperature: number;
  maxTokens: number;
  inputFilterKeywords: string[];
  outputFilter: 'NONE' | 'PLAIN' | 'REVERSED' | 'NORMALISED' | 'JUDGE';
  /** Only read when outputFilter is JUDGE. Blank falls back to the built-in prompt. */
  judgePrompt: string | null;
  inputFilterResponse: string | null;
  outputFilterResponse: string | null;
  finishedResponse: string;
}

const FILTERS: LevelDefinition['outputFilter'][] = ['NONE', 'PLAIN', 'REVERSED', 'NORMALISED', 'JUDGE'];

const FILTER_HELP: Record<string, string> = {
  NONE: 'Anything Leo says reaches the player.',
  PLAIN: 'Blocks the secret written out or spelled letter by letter. Acrostics get through — that is deliberate, they close at NORMALISED.',
  REVERSED: 'Also blocks the secret written backwards.',
  NORMALISED: 'Also blocks lookalike letters, accents, leetspeak, base64, hex, binary, A1Z26, Morse, NATO phonetic, rotation ciphers and acrostics. Everything a deterministic check can enumerate — and nothing it cannot.',
  JUDGE: 'Also asks a second model whether a reader could name the word. Catches riddles and translations, which nothing above can. Costs an extra LLM call per turn.',
};

export default function LevelControl() {
  const [levels, setLevels] = useState<LevelStat[]>([]);
  const [defs, setDefs] = useState<LevelDefinition[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<number | null>(null);
  const [editing, setEditing] = useState<number | null>(null);
  const [draft, setDraft] = useState<LevelDefinition | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    Promise.all([
      fetch('/api/admin/level-stats', { credentials: 'include' }).then(r => r.json()),
      fetch('/api/admin/levels', { credentials: 'include' }).then(r => r.json()),
    ])
      .then(([stats, definitions]) => { setLevels(stats); setDefs(definitions); })
      .catch(() => setError('Could not load levels. Are you signed in as an admin?'))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const toggleLevel = async (level: number, enabled: boolean) => {
    setSaving(level);
    try {
      const res = await fetch(`/api/admin/level/${level}/enabled`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ enabled }),
      });
      if (!res.ok) throw new Error();
      setLevels(ls => ls.map(l => (l.level === level ? { ...l, enabled } : l)));
    } catch {
      setError(`Could not change level ${level}.`);
    } finally {
      setSaving(null);
    }
  };

  const save = async () => {
    if (!draft) return;
    setSaving(draft.order);
    setError(null);
    try {
      const res = await fetch(`/api/admin/levels/${draft.order}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(draft),
      });
      if (!res.ok) throw new Error(await res.text());
      const saved: LevelDefinition = await res.json();
      setDefs(ds => ds.map(d => (d.order === saved.order ? saved : d)));
      setEditing(null);
      setDraft(null);
    } catch (e) {
      setError(`Could not save level ${draft.order}: ${e}`);
    } finally {
      setSaving(null);
    }
  };

  if (loading) return <div>Loading levels...</div>;

  return (
    <div>
      <h2 style={{ color: '#ff8c00', marginBottom: '8px' }}>⚙️ Level Control</h2>
      <p style={{ color: '#64748b', marginBottom: '8px' }}>
        Enable, disable and retune levels while the event is running. Changes take effect on the next
        request — no restart.
      </p>
      <p style={{ color: '#64748b', marginBottom: '24px', fontSize: '0.85rem' }}>
        Edits here are held in memory and are lost on restart. Once you are happy with a level, copy
        it back into <code>backend/src/main/resources/levels.yml</code> and re-run the calibration
        test — it checks that each level still blocks the attack that beat the level below it, that
        each level is still beatable the way it was designed to be, and that Leo is defending rather
        than simply refusing.
      </p>
      <p style={{ color: '#64748b', marginBottom: '24px', fontSize: '0.85rem' }}>
        <strong style={{ color: '#94a3b8' }}>Two rules when you retune a rung.</strong> Every
        prohibition you add must be paired with a permission naming the channel this level leaves
        open — a level that closes a channel without opening one is a wall, and a stuck player looks
        exactly like a thinking one. And no single sentence should beat more than three levels: check
        the <strong>Help Desk</strong> route map after a change, because a level that is beatable
        seven ways, none of them new, is not a level.
      </p>

      {error && (
        <div className="card" style={{ borderLeft: '4px solid #ef4444', marginBottom: '16px', color: '#fca5a5' }}>
          {error}
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {defs.map(def => {
          const stat = levels.find(l => l.level === def.order)
            || { level: def.order, enabled: true, attempts: 0, players: 0, completions: 0 };
          const isEditing = editing === def.order;
          const d = isEditing && draft ? draft : def;
          return (
            <div
              key={def.order}
              className="card"
              style={{
                borderLeft: `4px solid ${stat.enabled ? '#ff6600' : '#2d3748'}`,
                opacity: stat.enabled ? 1 : 0.6,
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '6px' }}>
                    <span style={{ fontSize: '1.2rem', fontWeight: 700, color: '#ff8c00' }}>Level {def.order}</span>
                    <span style={{ fontWeight: 600, color: '#e2e8f0' }}>{def.name}</span>
                    <span className={`badge ${stat.enabled ? 'badge-success' : 'badge-warning'}`}>
                      {stat.enabled ? 'Enabled' : 'Disabled'}
                    </span>
                    <span className="badge" title={FILTER_HELP[def.outputFilter]}>{def.outputFilter}</span>
                  </div>
                  <p style={{ color: '#64748b', fontSize: '0.85rem', marginBottom: '10px' }}>{def.description}</p>
                  <div style={{ display: 'flex', gap: '16px', fontSize: '0.85rem', color: '#94a3b8', flexWrap: 'wrap' }}>
                    <span>Attempts: <strong style={{ color: '#e2e8f0' }}>{stat.attempts}</strong></span>
                    <span>Players: <strong style={{ color: '#e2e8f0' }}>{stat.players}</strong></span>
                    <span>Cleared by: <strong style={{ color: '#22c55e' }}>{stat.completions}</strong></span>
                    <span>Temp: <strong style={{ color: '#e2e8f0' }}>{def.temperature}</strong></span>
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px', whiteSpace: 'nowrap' }}>
                  <button
                    className="btn"
                    onClick={() => {
                      setEditing(isEditing ? null : def.order);
                      setDraft(isEditing ? null : { ...def });
                    }}
                  >
                    {isEditing ? 'Cancel' : 'Edit'}
                  </button>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '10px', cursor: 'pointer' }}>
                    <span style={{ color: stat.enabled ? '#22c55e' : '#64748b', fontSize: '0.9rem' }}>
                      {saving === def.order ? '⏳' : stat.enabled ? 'ON' : 'OFF'}
                    </span>
                    <div style={{ position: 'relative' }}>
                      <input
                        type="checkbox"
                        checked={stat.enabled}
                        disabled={saving !== null}
                        onChange={e => toggleLevel(def.order, e.target.checked)}
                        style={{
                          width: '40px', height: '22px', appearance: 'none',
                          background: stat.enabled ? '#ff6600' : '#374151',
                          borderRadius: '11px', cursor: 'pointer', transition: 'background 0.2s',
                        }}
                      />
                      <div style={{
                        position: 'absolute', top: '3px', left: stat.enabled ? '21px' : '3px',
                        width: '16px', height: '16px', background: 'white', borderRadius: '50%',
                        transition: 'left 0.2s', pointerEvents: 'none',
                      }} />
                    </div>
                  </label>
                </div>
              </div>

              {isEditing && draft && (
                <div style={{ marginTop: '18px', borderTop: '1px solid #2d3748', paddingTop: '16px', display: 'grid', gap: '12px' }}>
                  <label style={{ display: 'grid', gap: '4px' }}>
                    <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                      System messages — one per line. <code>{'{secret}'}</code> becomes the player's password.
                    </span>
                    <textarea
                      rows={Math.max(6, d.systemMessages.length + 1)}
                      value={draft.systemMessages.join('\n')}
                      onChange={e => setDraft({ ...draft, systemMessages: e.target.value.split('\n').filter(Boolean) })}
                      style={{ width: '100%', background: '#0d0d1a', color: '#e2e8f0', border: '1px solid #2d3748', borderRadius: '6px', padding: '8px', fontFamily: 'monospace', fontSize: '0.8rem' }}
                    />
                  </label>

                  <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
                    <label style={{ display: 'grid', gap: '4px' }}>
                      <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>Temperature</span>
                      <input
                        type="number" step="0.1" min="0" max="1" value={draft.temperature}
                        onChange={e => setDraft({ ...draft, temperature: parseFloat(e.target.value) })}
                        style={{ background: '#0d0d1a', color: '#e2e8f0', border: '1px solid #2d3748', borderRadius: '6px', padding: '6px', width: '90px' }}
                      />
                    </label>
                    <label style={{ display: 'grid', gap: '4px' }}>
                      <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>Max tokens</span>
                      <input
                        type="number" step="10" min="20" max="800" value={draft.maxTokens}
                        onChange={e => setDraft({ ...draft, maxTokens: parseInt(e.target.value, 10) })}
                        style={{ background: '#0d0d1a', color: '#e2e8f0', border: '1px solid #2d3748', borderRadius: '6px', padding: '6px', width: '90px' }}
                      />
                    </label>
                    <label style={{ display: 'grid', gap: '4px' }}>
                      <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>Output filter</span>
                      <select
                        value={draft.outputFilter}
                        onChange={e => setDraft({ ...draft, outputFilter: e.target.value as LevelDefinition['outputFilter'] })}
                        style={{ background: '#0d0d1a', color: '#e2e8f0', border: '1px solid #2d3748', borderRadius: '6px', padding: '6px' }}
                      >
                        {FILTERS.map(f => <option key={f} value={f}>{f}</option>)}
                      </select>
                    </label>
                    <label style={{ display: 'grid', gap: '4px', flex: 1, minWidth: '240px' }}>
                      <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                        Blocked words or phrases, comma separated — matched whole-word, so "pass" will
                        not block "compass". Prefer phrases: a bare common word blocks innocent questions.
                      </span>
                      <input
                        value={draft.inputFilterKeywords.join(', ')}
                        onChange={e => setDraft({ ...draft, inputFilterKeywords: e.target.value.split(',').map(s => s.trim()).filter(Boolean) })}
                        style={{ background: '#0d0d1a', color: '#e2e8f0', border: '1px solid #2d3748', borderRadius: '6px', padding: '6px', width: '100%' }}
                      />
                    </label>
                  </div>

                  <p style={{ fontSize: '0.78rem', color: '#64748b', margin: 0 }}>{FILTER_HELP[draft.outputFilter]}</p>

                  {draft.outputFilter === 'JUDGE' && (
                    <label style={{ display: 'grid', gap: '4px' }}>
                      <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                        Judge prompt — the single most useful dial mid-event. Loosen it if nobody is
                        finishing; tighten it if the top level is falling to riddles. Keep it terse
                        and keep it asking for one word: an explanatory rewrite measured 0/8, because
                        the model answers in prose and prose is not "true".
                      </span>
                      <textarea
                        value={draft.judgePrompt ?? ''}
                        onChange={e => setDraft({ ...draft, judgePrompt: e.target.value })}
                        rows={4}
                        style={{ background: '#0d0d1a', color: '#e2e8f0', border: '1px solid #2d3748', borderRadius: '6px', padding: '6px', width: '100%', fontFamily: 'inherit' }}
                      />
                    </label>
                  )}

                  <div style={{ display: 'flex', gap: '10px' }}>
                    <button className="btn" onClick={save} disabled={saving !== null}>
                      {saving === draft.order ? 'Saving...' : 'Apply to live game'}
                    </button>
                    <button className="btn" onClick={() => { setEditing(null); setDraft(null); }}>Discard</button>
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
