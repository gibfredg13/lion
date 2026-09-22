import React, { useState, useEffect } from 'react';

interface HealthData {
  healthy: boolean;
  avgLatencyMs: number;
  availableModels: string[];
  provider: string;
  status?: string;
  backendId?: string;
  label?: string;
  endpoint?: string;
  resolvedModel?: string;
}

export default function DgxHealth() {
  const [health, setHealth] = useState<HealthData | null>(null);
  const [history, setHistory] = useState<{ time: string; latency: number }[]>([]);

  useEffect(() => {
    const load = () => {
      fetch('/api/admin/dgx-health', { credentials: 'include' })
        .then(r => r.json())
        .then(data => {
          setHealth(data);
          if (data.avgLatencyMs > 0) {
            setHistory(h => [...h.slice(-19), { time: new Date().toLocaleTimeString(), latency: data.avgLatencyMs }]);
          }
        }).catch(() => {});
    };
    load();
    const i = setInterval(load, 5000);
    return () => clearInterval(i);
  }, []);

  const maxLatency = Math.max(...history.map(h => h.latency), 1);

  return (
    <div>
      <h2 style={{ color: '#ff8c00', marginBottom: '24px' }}>🤖 GPU Health{health?.label ? ` — ${health.label}` : ''}</h2>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '16px', marginBottom: '32px' }}>
        <div className="metric-card">
          <div className="metric-icon">{health?.healthy ? '🟢' : '🔴'}</div>
          <div className="metric-value" style={{ color: health?.healthy ? '#22c55e' : '#ef4444' }}>
            {health?.healthy ? 'Online' : 'Offline'}
          </div>
          <div className="metric-label">Status</div>
        </div>
        <div className="metric-card">
          <div className="metric-icon">⚡</div>
          <div className="metric-value">{health?.avgLatencyMs ?? '—'}ms</div>
          <div className="metric-label">Avg Latency</div>
        </div>
        <div className="metric-card">
          <div className="metric-icon">🧠</div>
          <div className="metric-value">{health?.availableModels?.length ?? 0}</div>
          <div className="metric-label">Models Available</div>
        </div>
      </div>

      {health?.availableModels && health.availableModels.length > 0 && (
        <div className="card" style={{ marginBottom: '24px' }}>
          <h3 style={{ color: '#ff8c00', marginBottom: '12px' }}>Available Models</h3>
          {health.availableModels.map(m => (
            <div key={m} style={{ padding: '8px 12px', background: '#0d0d1a', borderRadius: '6px', marginBottom: '8px', fontFamily: 'monospace', color: '#94a3b8' }}>{m}</div>
          ))}
        </div>
      )}

      {history.length > 1 && (
        <div className="card">
          <h3 style={{ color: '#ff8c00', marginBottom: '16px' }}>Latency History</h3>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: '4px', height: '100px' }}>
            {history.map((h, i) => (
              <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px' }}>
                <div style={{
                  width: '100%', background: '#ff6600',
                  height: `${(h.latency / maxLatency) * 80}px`,
                  borderRadius: '2px 2px 0 0', minHeight: '2px'
                }} title={`${h.latency}ms at ${h.time}`} />
              </div>
            ))}
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', color: '#64748b', fontSize: '0.75rem', marginTop: '4px' }}>
            <span>{history[0]?.time}</span>
            <span>{history[history.length-1]?.time}</span>
          </div>
        </div>
      )}

      <div className="card" style={{ marginTop: '24px' }}>
        {/* Was hardcoded to the Spark's address, which became untrue the moment a second box existed. */}
        <p style={{ color: '#64748b', fontSize: '0.85rem' }}>
          Active backend: <code style={{ color: '#ff8c00' }}>{health?.endpoint ?? 'unknown'}</code>
          {health?.resolvedModel ? <> · model <code style={{ color: '#ff8c00' }}>{health.resolvedModel}</code></> : null}
        </p>
        <p style={{ color: '#64748b', fontSize: '0.85rem', marginTop: '4px' }}>Provider: <code style={{ color: '#94a3b8' }}>{health?.provider || 'unknown'}</code></p>
      </div>
    </div>
  );
}
