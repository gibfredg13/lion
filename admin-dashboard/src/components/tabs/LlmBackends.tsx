import React, { useState, useEffect, useCallback } from 'react';

interface Backend {
  id: string;
  label: string;
  kind: string;
  baseUrl: string;
  model: string | null;
  maxConcurrent: number;
  maxTokens: number;
  waitSeconds: number;
  mergeSystemMessages: boolean;
  active: boolean;
  healthy: boolean;
  avgLatencyMs: number;
  inFlight: number;
  availableSlots: number;
  availableModels: string[];
  checkedAt: string | null;
  error: string | null;
}

interface TestResult {
  backendId: string;
  success: boolean;
  response: string | null;
  inputTokens: number;
  outputTokens: number;
  latencyMs: number;
  error: string | null;
}

interface Pinned {
  running: boolean;
  backendId: string;
  backendLabel: string;
}

const cardStyle: React.CSSProperties = {
  background: '#1a1a2e',
  border: '1px solid #2d3748',
  borderRadius: '8px',
  padding: '20px',
};

export default function LlmBackends() {
  const [backends, setBackends] = useState<Backend[]>([]);
  const [pinned, setPinned] = useState<Pinned | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [testPrompt, setTestPrompt] = useState('What do you guard?');
  const [tests, setTests] = useState<Record<string, TestResult>>({});

  const load = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/llm/backends', { credentials: 'include' });
      if (res.ok) setBackends(await res.json());
      const pin = await fetch('/api/admin/calibration/pinned', { credentials: 'include' });
      if (pin.ok) setPinned(await pin.json());
    } catch (e) {
      console.error(e);
    }
  }, []);

  useEffect(() => {
    load();
    const i = setInterval(load, 5000);
    return () => clearInterval(i);
  }, [load]);

  const activate = async (b: Backend) => {
    // Every player mid-question is redirected to a different model by this, so it is worth a pause.
    if (!window.confirm(`Switch every player to ${b.label}?\n\nAnswers already in flight finish on the current box.`)) {
      return;
    }
    setBusy(b.id);
    setError(null);
    setNotice(null);
    try {
      const res = await fetch(`/api/admin/llm/backends/${b.id}/activate`, {
        method: 'POST',
        credentials: 'include',
      });
      if (!res.ok) {
        setError(`Could not switch to ${b.label}: ${await res.text()}`);
      } else {
        const result = await res.json();
        setBackends(result.backends);
        if (result.note) setNotice(result.note);
      }
    } catch (e) {
      setError(`Could not switch to ${b.label}: ${e}`);
    } finally {
      setBusy(null);
      load();
    }
  };

  const runTest = async (b: Backend) => {
    setBusy(`test-${b.id}`);
    try {
      const res = await fetch(`/api/admin/llm/backends/${b.id}/test`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt: testPrompt }),
      });
      if (res.ok) {
        const result: TestResult = await res.json();
        setTests((prev) => ({ ...prev, [b.id]: result }));
      }
    } catch (e) {
      console.error(e);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div>
      <h2 style={{ color: '#ff8c00', marginBottom: '8px' }}>🔀 LLM Backend</h2>
      <p style={{ color: '#94a3b8', marginTop: 0, marginBottom: '24px', fontSize: '0.9rem' }}>
        Which box answers the players. Switching takes effect on the next request — no restart — and
        is remembered across restarts.
      </p>

      {error && (
        <div style={{ ...cardStyle, borderColor: '#ef4444', color: '#fca5a5', marginBottom: '16px' }}>{error}</div>
      )}
      {notice && (
        <div style={{ ...cardStyle, borderColor: '#f59e0b', color: '#fcd34d', marginBottom: '16px' }}>{notice}</div>
      )}
      {pinned?.running && (
        <div style={{ ...cardStyle, borderColor: '#f59e0b', color: '#fcd34d', marginBottom: '16px' }}>
          A calibration run is in progress and pinned to <strong>{pinned.backendLabel}</strong>. Switching
          now will not move it — it finishes on the box it started on.
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {backends.length === 0 && (
          <div style={{ ...cardStyle, color: '#94a3b8' }}>No backends declared.</div>
        )}
        {backends.map((b) => {
          const test = tests[b.id];
          return (
            <div
              key={b.id}
              style={{ ...cardStyle, borderColor: b.active ? '#ff8c00' : '#2d3748' }}
              data-testid={`backend-${b.id}`}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '12px' }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                    <span style={{ fontSize: '1.15rem', fontWeight: 'bold', color: '#e2e8f0' }}>{b.label}</span>
                    <span style={{ color: b.healthy ? '#22c55e' : '#ef4444' }}>
                      {b.healthy ? '🟢 Online' : '🔴 Offline'}
                    </span>
                    {b.active && (
                      <span style={{ padding: '2px 10px', borderRadius: '12px', fontSize: '0.75rem', fontWeight: 600, background: '#ff660033', color: '#ff8c00', border: '1px solid #ff8c0066' }}>
                        ACTIVE
                      </span>
                    )}
                  </div>
                  <div style={{ color: '#94a3b8', fontSize: '0.85rem', marginTop: '6px' }}>
                    <code style={{ color: '#ff8c00' }}>{b.baseUrl}</code> · {b.kind}
                  </div>
                  <div style={{ color: '#94a3b8', fontSize: '0.85rem', marginTop: '4px' }}>
                    model: {b.model || '(auto-detect)'} · {b.avgLatencyMs}ms avg ·{' '}
                    {b.inFlight}/{b.maxConcurrent} in flight · {b.maxTokens} max tokens
                  </div>
                  {b.error && (
                    <div style={{ color: '#f59e0b', fontSize: '0.8rem', marginTop: '6px' }}>{b.error}</div>
                  )}
                </div>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    className="btn"
                    style={{ padding: '8px 16px', fontSize: '0.85rem' }}
                    disabled={busy === `test-${b.id}`}
                    onClick={() => runTest(b)}
                  >
                    {busy === `test-${b.id}` ? 'Testing…' : 'Test'}
                  </button>
                  {!b.active && (
                    <button
                      className="btn"
                      style={{ backgroundColor: '#22c55e', color: '#fff', border: 'none', padding: '8px 16px', borderRadius: '6px', fontWeight: 'bold', cursor: 'pointer', fontSize: '0.85rem' }}
                      disabled={busy === b.id}
                      onClick={() => activate(b)}
                    >
                      {busy === b.id ? 'Switching…' : 'Activate'}
                    </button>
                  )}
                </div>
              </div>

              {test && (
                <div style={{ marginTop: '14px', padding: '12px', background: '#0d0d1a', borderRadius: '6px', border: '1px solid #2d3748' }}>
                  {test.success ? (
                    <>
                      <div style={{ color: '#e2e8f0', fontSize: '0.9rem' }}>Leo: "{test.response}"</div>
                      <div style={{ color: '#64748b', fontSize: '0.8rem', marginTop: '6px' }}>
                        {test.latencyMs}ms · {test.inputTokens} in / {test.outputTokens} out
                      </div>
                    </>
                  ) : (
                    <div style={{ color: '#ef4444', fontSize: '0.85rem' }}>{test.error}</div>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>

      <div style={{ ...cardStyle, marginTop: '24px' }}>
        <label style={{ color: '#94a3b8', fontSize: '0.85rem' }} htmlFor="test-prompt">
          Test prompt — sent to one backend only, without moving players onto it
        </label>
        <input
          id="test-prompt"
          value={testPrompt}
          onChange={(e) => setTestPrompt(e.target.value)}
          style={{ width: '100%', marginTop: '8px', padding: '10px', background: '#0d0d1a', border: '1px solid #2d3748', borderRadius: '6px', color: '#e2e8f0' }}
        />
      </div>
    </div>
  );
}
