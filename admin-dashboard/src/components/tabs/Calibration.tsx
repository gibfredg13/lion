import React, { useState, useEffect, useRef } from 'react';

interface AttackResult {
  family: string;
  prompt: string;
  response: string;
  leaked: boolean;
  how: string;
  blocked: boolean;
  blockedBy: string | null;
}

interface TokenUsage {
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  llmCalls: number;
  failedCalls?: number;
}

interface LevelScore {
  level: number;
  name: string;
  status: string;  // PASS, FAIL, ERROR, UNREACHABLE, DEGRADED, PENDING
  winningFamilies: string[];
  winningChannels: string[];
  leakCount: number;
  blockedCount: number;
  answeredCount: number;
  substantiveRate: number;
  invariants: Record<string, boolean>;
  attacks: AttackResult[];
  tokens?: TokenUsage;
  error?: string | null;
}

interface InvariantResult {
  name: string;
  passed: boolean;
  detail: string;
  /** False when the run could not actually test this - an unreachable backend proves nothing. */
  measured?: boolean;
}

interface RouteEntry {
  family: string;
  levels: number[];
  everywhere: boolean;
}

interface CalibrationRun {
  id: string;
  startedAt: string;
  completedAt: string | null;
  status: string;
  invariantsPassed: number;
  invariantsTotal: number;
  selectedLevels?: number[];
  partial?: boolean;
  backendId?: string;
  backendLabel?: string;
  model?: string | null;
  levels: LevelScore[];
  invariants: InvariantResult[];
  routeMap: RouteEntry[];
  tokens?: TokenUsage;
}

interface LevelDefinitionSummary {
  order: number;
  name: string;
}

interface ActiveBackend {
  id: string;
  label: string;
  model: string | null;
  healthy: boolean;
}

const EMPTY_TOKENS: TokenUsage = { inputTokens: 0, outputTokens: 0, totalTokens: 0, llmCalls: 0 };

const addTokens = (a: TokenUsage, b?: TokenUsage): TokenUsage =>
  !b ? a : {
    inputTokens: a.inputTokens + (b.inputTokens || 0),
    outputTokens: a.outputTokens + (b.outputTokens || 0),
    totalTokens: a.totalTokens + (b.totalTokens || 0),
    llmCalls: a.llmCalls + (b.llmCalls || 0),
  };

const fmt = (n: number) => n.toLocaleString();

const statusColor = (status: string) => {
  switch (status) {
    case 'PASS': return '#22c55e';
    case 'FAIL': return '#ef4444';
    // Not a result at all: the calls never landed, so the level was never actually tested.
    case 'UNREACHABLE': return '#f59e0b';
    case 'DEGRADED': return '#f59e0b';
    case 'ERROR': return '#f59e0b';
    default: return '#64748b';
  }
};

interface PhaseResult {
  concurrentUsers: number;
  totalRequests: number;
  successfulRequests: number;
  failedRequests: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  requestsPerSecond: number;
  errorRate: number;
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  status: string;
}

interface StressResult {
  id: string;
  backendId?: string;
  backendLabel?: string;
  model?: string | null;
  startedAt: string;
  completedAt: string | null;
  status: string;
  phases: PhaseResult[];
  estimatedCapacity: number | null;
  capacityNote: string;
  totalInputTokens?: number;
  totalOutputTokens?: number;
  totalTokens?: number;
}

const cardStyle: React.CSSProperties = {
  background: '#1a1a2e',
  border: '1px solid #2d3748',
  borderRadius: '8px',
  padding: '20px',
};

const badgeStyle = (color: string): React.CSSProperties => ({
  display: 'inline-block',
  padding: '2px 8px',
  borderRadius: '12px',
  fontSize: '0.75rem',
  fontWeight: 600,
  backgroundColor: `${color}33`,
  color: color,
  border: `1px solid ${color}66`,
  marginRight: '8px',
});

export default function Calibration() {
  const [activeTab, setActiveTab] = useState<'calibration' | 'stress' | 'guide'>('calibration');

  // Calibration State
  const [calibRunning, setCalibRunning] = useState(false);
  const [levels, setLevels] = useState<LevelScore[]>([]);
  const [currentRun, setCurrentRun] = useState<CalibrationRun | null>(null);
  const [invariants, setInvariants] = useState<InvariantResult[]>([]);
  const [routeMap, setRouteMap] = useState<RouteEntry[]>([]);
  const [calibHistory, setCalibHistory] = useState<CalibrationRun[]>([]);
  const calibSourceRef = useRef<EventSource | null>(null);
  const [showCalibHistory, setShowCalibHistory] = useState(false);
  const [expandedLevels, setExpandedLevels] = useState<Record<number, boolean>>({});
  const [availableLevels, setAvailableLevels] = useState<LevelDefinitionSummary[]>([]);
  const [selectedLevels, setSelectedLevels] = useState<number[]>([]);
  // Two views of the same bill: what the finished levels cost, and what the run counter says it
  // has spent so far. The run counter also covers the invariant probes and the level in flight, so
  // it is never the smaller of the two - take whichever is ahead and the total only ever climbs.
  const [levelTokenSum, setLevelTokenSum] = useState<TokenUsage>(EMPTY_TOKENS);
  const [runTokens, setRunTokens] = useState<TokenUsage>(EMPTY_TOKENS);
  const [calibError, setCalibError] = useState<string | null>(null);
  const [activeBackend, setActiveBackend] = useState<ActiveBackend | null>(null);

  // Stress State
  const [stressRunning, setStressRunning] = useState(false);
  const [stressMode, setStressMode] = useState<'rampup' | 'fixed'>('rampup');
  const [stressConcurrency, setStressConcurrency] = useState(10);
  const [phases, setPhases] = useState<PhaseResult[]>([]);
  const [currentStress, setCurrentStress] = useState<StressResult | null>(null);
  const [stressHistory, setStressHistory] = useState<StressResult[]>([]);
  const stressSourceRef = useRef<EventSource | null>(null);
  const [showStressHistory, setShowStressHistory] = useState(false);
  const [expandedStressRun, setExpandedStressRun] = useState<string | null>(null);

  useEffect(() => {
    fetchLevels();
    fetchActiveBackend();
    fetchCalibrationHistory();
    fetchStressHistory();
    return () => {
      calibSourceRef.current?.close();
      stressSourceRef.current?.close();
    };
  }, []);

  const fetchLevels = async () => {
    try {
      const res = await fetch('/api/admin/levels', { credentials: 'include' });
      if (!res.ok) return;
      const defs = await res.json();
      if (!Array.isArray(defs)) return;
      const summaries: LevelDefinitionSummary[] = defs
        .map((d: any) => ({ order: d.order, name: d.name }))
        .sort((a: LevelDefinitionSummary, b: LevelDefinitionSummary) => a.order - b.order);
      setAvailableLevels(summaries);
      setSelectedLevels(summaries.map((d) => d.order));
    } catch (e) {
      console.error(e);
    }
  };

  const fetchActiveBackend = async () => {
    try {
      const res = await fetch('/api/admin/llm/backends', { credentials: 'include' });
      if (!res.ok) return;
      const all = await res.json();
      const active = Array.isArray(all) ? all.find((b: any) => b.active) : null;
      if (active) {
        setActiveBackend({ id: active.id, label: active.label, model: active.model, healthy: active.healthy });
      }
    } catch (e) {
      console.error(e);
    }
  };

  const fetchCalibrationHistory = async () => {
    try {
      const res = await fetch('/api/admin/calibration/history', { credentials: 'include' });
      if (res.ok) setCalibHistory(await res.json());
    } catch (e) {
      console.error(e);
    }
  };

  const fetchStressHistory = async () => {
    try {
      const res = await fetch('/api/admin/stress/history', { credentials: 'include' });
      if (res.ok) setStressHistory(await res.json());
    } catch (e) {
      console.error(e);
    }
  };

  const toggleLevelSelected = (order: number) => {
    setSelectedLevels((prev) =>
      prev.includes(order) ? prev.filter((l) => l !== order) : [...prev, order].sort((a, b) => a - b)
    );
  };

  const levelsToRun = selectedLevels.length > 0 ? selectedLevels : availableLevels.map((l) => l.order);

  const liveTokens = runTokens.totalTokens >= levelTokenSum.totalTokens ? runTokens : levelTokenSum;

  const startCalibration = () => {
    setCalibRunning(true);
    setLevels([]);
    setInvariants([]);
    setRouteMap([]);
    setCurrentRun(null);
    setLevelTokenSum(EMPTY_TOKENS);
    setRunTokens(EMPTY_TOKENS);
    setCalibError(null);
    // EventSource cannot POST, so the chosen levels ride on the query string.
    const query = levelsToRun.length > 0 ? `?levels=${levelsToRun.join(',')}` : '';
    const source = new EventSource(`/api/admin/calibration/stream${query}`, { withCredentials: true });
    source.addEventListener('level', (e) => {
      const score: LevelScore = JSON.parse(e.data);
      setLevels((prev) => [...prev.filter((l) => l.level !== score.level), score].sort((a, b) => a.level - b.level));
      setLevelTokenSum((prev) => addTokens(prev, score.tokens));
    });
    source.addEventListener('ping', (e) => {
      // Keepalive between levels; it also carries the running token total, which includes the
      // invariant probes the per-level events do not.
      try {
        const beat = JSON.parse(e.data);
        if (beat?.tokens) setRunTokens(beat.tokens);
      } catch {
        /* a heartbeat we cannot read is still a heartbeat */
      }
    });
    source.addEventListener('failed', (e: any) => {
      if (typeof e?.data === 'string') setCalibError(e.data);
    });
    source.addEventListener('complete', (e) => {
      const run: CalibrationRun = JSON.parse(e.data);
      setCurrentRun(run);
      setInvariants(run.invariants || []);
      setRouteMap(run.routeMap || []);
      if (run.levels?.length) setLevels(run.levels);
      if (run.tokens) setRunTokens(run.tokens);
      if (run.status && run.status !== 'COMPLETED') setCalibError(`Run ${run.status.toLowerCase()}`);
      setCalibRunning(false);
      source.close();
      fetchCalibrationHistory();
    });
    source.onerror = () => {
      setCalibRunning(false);
      source.close();
    };
    calibSourceRef.current = source;
  };

  const cancelCalibration = async () => {
    try {
      await fetch('/api/admin/calibration/cancel', { method: 'POST', credentials: 'include' });
      setCalibRunning(false);
      calibSourceRef.current?.close();
    } catch (e) {
      console.error(e);
    }
  };

  const startStress = async () => {
    setStressRunning(true);
    setPhases([]);
    setCurrentStress(null);
    try {
      await fetch('/api/admin/stress/start', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mode: stressMode, concurrentUsers: stressConcurrency }),
      });
      const source = new EventSource('/api/admin/stress/stream', { withCredentials: true });
      source.addEventListener('phase', (e) => {
        const phase: PhaseResult = JSON.parse(e.data);
        setPhases((prev) => [...prev, phase]);
      });
      source.addEventListener('complete', (e) => {
        const result: StressResult = JSON.parse(e.data);
        setCurrentStress(result);
        setStressRunning(false);
        source.close();
        fetchStressHistory();
      });
      source.onerror = () => {
        setStressRunning(false);
        source.close();
      };
      stressSourceRef.current = source;
    } catch (e) {
      console.error(e);
      setStressRunning(false);
    }
  };

  const cancelStress = async () => {
    try {
      await fetch('/api/admin/stress/cancel', { method: 'POST', credentials: 'include' });
      setStressRunning(false);
      stressSourceRef.current?.close();
    } catch (e) {
      console.error(e);
    }
  };

  const toggleLevelExpand = (lvl: number) => {
    setExpandedLevels(prev => ({ ...prev, [lvl]: !prev[lvl] }));
  };

  const totalStressTokens = phases.reduce((acc, p) => acc + (p.totalTokens || 0), 0);
  const totalInputTokens = phases.reduce((acc, p) => acc + (p.inputTokens || 0), 0);
  const totalOutputTokens = phases.reduce((acc, p) => acc + (p.outputTokens || 0), 0);

  const renderCalibration = () => {
    const matrixLevels = availableLevels.length > 0
      ? availableLevels.map((l) => l.order)
      : [1, 2, 3, 4, 5, 6, 7];
    const passedInvariants = invariants.filter((i) => i.passed).length;
    const totalInvariants = invariants.length || 7;
    const unmeasuredCount = invariants.filter((i) => i.measured === false).length;
    const healthColor =
      passedInvariants === totalInvariants ? '#22c55e' : passedInvariants >= 5 ? '#f59e0b' : '#ef4444';

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <button
            className="btn"
            style={{ backgroundColor: calibRunning ? '#64748b' : '#22c55e', color: '#fff', border: 'none', padding: '10px 20px', borderRadius: '6px', cursor: calibRunning ? 'not-allowed' : 'pointer', fontWeight: 'bold' }}
            onClick={startCalibration}
            disabled={calibRunning}
          >
            {calibRunning ? 'Running Calibration...' : 'Start Calibration'}
          </button>
          {calibRunning && (
            <button
              className="btn"
              style={{ backgroundColor: '#ef4444', color: '#fff', border: 'none', padding: '10px 20px', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold' }}
              onClick={cancelCalibration}
            >
              Cancel
            </button>
          )}
          <span style={{ color: '#94a3b8' }}>
            {calibRunning
              ? `Running 23 attack prompts across ${levelsToRun.length} level${levelsToRun.length === 1 ? '' : 's'} (${levels.length} done)...`
              : currentRun
                ? `Last run finished: ${new Date(currentRun.completedAt || currentRun.startedAt).toLocaleTimeString()}`
                : 'Ready to start.'}
          </span>
        </div>

        {activeBackend && (
          <div style={{ ...cardStyle, borderColor: activeBackend.healthy ? '#2d3748' : '#ef4444' }}>
            <span style={{ color: '#94a3b8' }}>Calibrating against </span>
            <strong style={{ color: '#ff8c00' }}>{activeBackend.label}</strong>
            <span style={{ color: '#94a3b8' }}> · {activeBackend.model || 'auto-detected model'}</span>
            {!activeBackend.healthy && (
              <span style={{ color: '#ef4444', marginLeft: '8px' }}>
                — this backend is not answering. A run will be refused rather than producing an empty scorecard.
              </span>
            )}
          </div>
        )}

        {calibError && (
          <div style={{ ...cardStyle, borderColor: '#ef4444', color: '#fca5a5' }}>
            Calibration problem: {calibError}
          </div>
        )}

        <div style={{ ...cardStyle }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
            <h3 style={{ margin: 0, color: '#e2e8f0' }}>Levels to Calibrate</h3>
            <div style={{ display: 'flex', gap: '8px' }}>
              <button
                className="btn"
                style={{ padding: '6px 14px', fontSize: '0.85rem' }}
                disabled={calibRunning}
                onClick={() => setSelectedLevels(availableLevels.map((l) => l.order))}
              >
                Select All
              </button>
              <button
                className="btn"
                style={{ padding: '6px 14px', fontSize: '0.85rem' }}
                disabled={calibRunning}
                onClick={() => setSelectedLevels([])}
              >
                Clear
              </button>
            </div>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginTop: '14px' }}>
            {availableLevels.length === 0 && (
              <span style={{ color: '#94a3b8', fontSize: '0.85rem' }}>No level definitions loaded.</span>
            )}
            {availableLevels.map((lvl) => {
              const on = selectedLevels.includes(lvl.order);
              return (
                <label
                  key={lvl.order}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 12px',
                    borderRadius: '6px', cursor: calibRunning ? 'not-allowed' : 'pointer',
                    backgroundColor: on ? '#ff660022' : '#0d0d1a',
                    border: `1px solid ${on ? '#ff8c00' : '#2d3748'}`,
                    color: on ? '#ff8c00' : '#94a3b8', fontSize: '0.85rem',
                  }}
                >
                  <input
                    type="checkbox"
                    checked={on}
                    disabled={calibRunning}
                    onChange={() => toggleLevelSelected(lvl.order)}
                    aria-label={`Level ${lvl.order}`}
                  />
                  L{lvl.order} · {lvl.name}
                </label>
              );
            })}
          </div>
          <div style={{ color: '#64748b', fontSize: '0.8rem', marginTop: '12px' }}>
            {selectedLevels.length === 0
              ? 'Nothing selected - the whole ladder will run.'
              : `${selectedLevels.length} of ${availableLevels.length} levels selected. Each level sends 23 prompts (more on retries), so a full ladder is the slowest and most expensive run.`}
          </div>
        </div>

        <div style={{ ...cardStyle }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
            <h3 style={{ margin: 0, color: '#e2e8f0' }}>Token Usage</h3>
            <span style={{ color: '#64748b', fontSize: '0.8rem' }}>
              {calibRunning ? 'Live - updates as levels finish' : 'Last run'}
            </span>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '12px' }}>
            {[
              { label: 'Total Tokens', value: liveTokens.totalTokens, color: '#ff8c00' },
              { label: 'Input Tokens', value: liveTokens.inputTokens, color: '#60a5fa' },
              { label: 'Output Tokens', value: liveTokens.outputTokens, color: '#22c55e' },
              { label: 'LLM Calls', value: liveTokens.llmCalls, color: '#a78bfa' },
            ].map((stat) => (
              <div key={stat.label} style={{ padding: '12px', backgroundColor: '#0d0d1a', borderRadius: '6px', border: '1px solid #2d3748' }}>
                <div style={{ color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>{stat.label}</div>
                <div style={{ color: stat.color, fontSize: '1.5rem', fontWeight: 'bold' }}>{fmt(stat.value || 0)}</div>
              </div>
            ))}
          </div>
        </div>

        {invariants.length > 0 && (
          <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: '24px' }}>
            <div style={{ ...cardStyle, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center' }}>
              <h3 style={{ margin: '0 0 16px 0', color: '#e2e8f0' }}>Health Score</h3>
              <div style={{ width: '130px', height: '130px', borderRadius: '50%', border: `8px solid ${healthColor}`, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', fontSize: '2.2rem', fontWeight: 'bold', color: healthColor }}>
                {passedInvariants}/{totalInvariants}
                <span style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: '2px' }}>Invariants</span>
              </div>
              <p style={{ color: unmeasuredCount > 0 ? '#f59e0b' : healthColor, fontWeight: 600, marginTop: '12px', fontSize: '0.9rem' }}>
                {unmeasuredCount === totalInvariants
                  ? 'Nothing measured - the backend did not answer'
                  : unmeasuredCount > 0
                    ? `${unmeasuredCount} not measured, ${totalInvariants - passedInvariants - unmeasuredCount} gaps`
                    : passedInvariants === totalInvariants
                      ? 'All Invariants Verified'
                      : `${totalInvariants - passedInvariants} Invariant Gaps Found`}
              </p>
            </div>

            <div style={{ ...cardStyle }}>
              <h3 style={{ margin: '0 0 16px 0', color: '#e2e8f0' }}>7 Invariant Verification Checklist</h3>
              {currentRun?.partial && (
                <div style={{ color: '#f59e0b', fontSize: '0.8rem', marginBottom: '12px' }}>
                  Partial run ({(currentRun.selectedLevels || []).map((l) => `L${l}`).join(', ')}) - the
                  cross-level invariants only saw the levels you picked.
                </div>
              )}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '12px' }}>
                {invariants.map((inv) => (
                  <div key={inv.name} style={{ display: 'flex', alignItems: 'flex-start', gap: '10px', padding: '10px', backgroundColor: '#0d0d1a', borderRadius: '6px', border: '1px solid #2d3748' }}>
                    <span style={{ color: inv.measured === false ? '#64748b' : inv.passed ? '#22c55e' : '#ef4444', fontWeight: 'bold', fontSize: '1.2rem' }}>
                      {inv.measured === false ? '–' : inv.passed ? '✓' : '✗'}
                    </span>
                    <div>
                      <div style={{ fontWeight: 600, color: '#e2e8f0' }}>{inv.name}</div>
                      <div style={{ fontSize: '0.8rem', color: '#94a3b8' }}>{inv.detail}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {levels.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <h3 style={{ margin: 0, color: '#e2e8f0' }}>Per-Level Scorecards</h3>
            {levels.map((lvl) => (
              <div key={lvl.level} style={{ ...cardStyle }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer' }} onClick={() => toggleLevelExpand(lvl.level)}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ fontWeight: 'bold', fontSize: '1.1rem', color: '#e2e8f0' }}>
                        Level {lvl.level}: {lvl.name}
                      </span>
                      <span style={badgeStyle(statusColor(lvl.status))}>{lvl.status}</span>
                    </div>
                    <div style={{ color: '#94a3b8', fontSize: '0.85rem', marginTop: '4px' }}>
                      {lvl.leakCount} leaks · {lvl.blockedCount} blocked · {lvl.answeredCount} answered · {Math.round(lvl.substantiveRate * 100)}% substantive
                      {lvl.tokens ? ` · ${fmt(lvl.tokens.totalTokens)} tokens in ${fmt(lvl.tokens.llmCalls)} calls` : ''}
                      {lvl.tokens?.failedCalls ? ` · ${fmt(lvl.tokens.failedCalls)} failed` : ''}
                    </div>
                    {lvl.error && (
                      <div style={{ color: '#f59e0b', fontSize: '0.8rem', marginTop: '4px' }}>{lvl.error}</div>
                    )}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <div style={{ display: 'flex', flexWrap: 'wrap', maxWidth: '380px', justifyContent: 'flex-end', gap: '4px' }}>
                      {lvl.winningFamilies.map(f => (
                        <span key={f} style={badgeStyle('#ff8c00')}>{f}</span>
                      ))}
                    </div>
                    <span style={{ color: '#94a3b8' }}>{expandedLevels[lvl.level] ? '▲' : '▼'}</span>
                  </div>
                </div>

                {expandedLevels[lvl.level] && (
                  <div style={{ marginTop: '16px', borderTop: '1px solid #2d3748', paddingTop: '16px' }}>
                    <h4 style={{ margin: '0 0 12px 0', color: '#94a3b8', fontSize: '0.85rem', textTransform: 'uppercase' }}>Attacks Tested</h4>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                      {lvl.attacks.map((att, idx) => (
                        <div key={idx} style={{ padding: '10px', backgroundColor: '#0d0d1a', borderRadius: '4px', border: '1px solid #2d3748' }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                            <span style={{ fontWeight: 600, color: '#e2e8f0' }}>{att.family}</span>
                            <span style={{ color: att.leaked ? '#ef4444' : '#22c55e', fontSize: '0.8rem', fontWeight: 600 }}>
                              {att.leaked ? `LEAKED (${att.how})` : att.blocked ? `BLOCKED by ${att.blockedBy}` : 'HELD'}
                            </span>
                          </div>
                          <div style={{ color: '#94a3b8', fontSize: '0.85rem', marginBottom: '4px' }}>Prompt: "{att.prompt}"</div>
                          <div style={{ color: '#64748b', fontSize: '0.8rem', fontStyle: 'italic' }}>Leo: "{att.response}"</div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        {routeMap.length > 0 && (
          <div style={{ ...cardStyle }}>
            <h3 style={{ margin: '0 0 16px 0', color: '#e2e8f0' }}>Route Matrix (Which Trick Beats Which Level)</h3>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.9rem' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid #2d3748', color: '#94a3b8' }}>
                    <th style={{ padding: '8px' }}>Attack Family</th>
                    {matrixLevels.map(l => (
                      <th key={l} style={{ padding: '8px', textAlign: 'center' }}>L{l}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {routeMap.map(row => (
                    <tr key={row.family} style={{ borderBottom: '1px solid #2d374811', backgroundColor: row.everywhere ? '#ff660011' : 'transparent' }}>
                      <td style={{ padding: '8px', color: '#e2e8f0', fontWeight: row.everywhere ? 600 : 400 }}>
                        {row.family} {row.everywhere && <span style={{ color: '#ff8c00', fontSize: '0.75rem' }}>(Universal)</span>}
                      </td>
                      {matrixLevels.map(l => (
                        <td key={l} style={{ padding: '8px', textAlign: 'center', color: row.levels.includes(l) ? '#22c55e' : '#64748b' }}>
                          {row.levels.includes(l) ? '●' : '·'}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {calibHistory.length > 0 && (
          <div style={{ ...cardStyle }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, color: '#e2e8f0' }}>Calibration History</h3>
              <button
                className="btn"
                style={{ padding: '6px 14px', fontSize: '0.85rem' }}
                onClick={() => setShowCalibHistory(!showCalibHistory)}
              >
                {showCalibHistory ? 'Hide History' : 'Show History'}
              </button>
            </div>
            {showCalibHistory && (
              <div style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {calibHistory.map(run => (
                  <div key={run.id} style={{ padding: '12px', backgroundColor: '#0d0d1a', border: '1px solid #2d3748', borderRadius: '4px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <div style={{ color: '#e2e8f0', fontWeight: 500 }}>{new Date(run.startedAt).toLocaleString()}</div>
                      <div style={{ color: '#94a3b8', fontSize: '0.85rem' }}>
                        {run.selectedLevels?.length
                          ? `Levels ${run.selectedLevels.join(', ')}`
                          : 'All levels'}
                        {run.tokens ? ` · ${fmt(run.tokens.totalTokens)} tokens` : ''}
                        {run.backendLabel ? ` · ${run.backendLabel}` : ''}
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                      <span style={badgeStyle(run.status === 'COMPLETED' ? '#22c55e' : run.status === 'FAILED' ? '#ef4444' : '#f59e0b')}>{run.status}</span>
                      <span style={{ color: '#e2e8f0', fontWeight: 'bold' }}>{run.invariantsPassed}/{run.invariantsTotal} Invariants</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    );
  };

  const renderStress = () => {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
        
        {/* Executive Summary Callout */}
        <div style={{ ...cardStyle, borderLeft: '4px solid #ff8c00', backgroundColor: '#1e1b2e' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
            <h3 style={{ margin: 0, color: '#ff8c00', display: 'flex', alignItems: 'center', gap: '8px' }}>
              ⚡ Throughput &amp; Capacity Summary
            </h3>
            {/* Read from the active backend. This used to name one specific box and quote its
                memory bandwidth, which became wrong the moment a second box existed. */}
            <span style={badgeStyle('#ff8c00')}>
              Hardware: {activeBackend?.label ?? 'active backend'}
            </span>
          </div>
          <p style={{ color: '#cbd5e1', fontSize: '0.95rem', lineHeight: '1.6', margin: '0 0 12px 0' }}>
            <strong>How the test works:</strong> The stress engine tests <em>Burst Concurrency</em> using virtual threads. In Phase 16, <strong>16 concurrent calls are sent at the exact same instant</strong> with 3 turns each (48 rapid calls).
          </p>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '12px', marginTop: '12px' }}>
            <div style={{ backgroundColor: '#0d0d1a', padding: '12px', borderRadius: '6px', border: '1px solid #2d3748' }}>
              <div style={{ color: '#ef4444', fontWeight: 'bold', marginBottom: '4px' }}>🛑 Where it bottlenecks (&gt;5s / &gt;10s)</div>
              <div style={{ color: '#94a3b8', fontSize: '0.85rem' }}>
                Depends entirely on the backend. A fixed-slot server (llama.cpp) queues
                simultaneous calls linearly, so 8–16 at once pushes latency to tens of seconds. A
                continuously-batching server (vLLM) absorbs them nearly for free. Read the measured
                numbers below rather than assuming either.
              </div>
            </div>
            <div style={{ backgroundColor: '#0d0d1a', padding: '12px', borderRadius: '6px', border: '1px solid #2d3748' }}>
              <div style={{ color: '#22c55e', fontWeight: 'bold', marginBottom: '4px' }}>👥 Real Human Event Capacity</div>
              <div style={{ color: '#94a3b8', fontSize: '0.85rem' }}>
                Real players have a 25–40s think time between prompts. A room of <strong>20–30 active players</strong> averages ~0.67 req/s, well within capacity.
              </div>
            </div>
            <div style={{ backgroundColor: '#0d0d1a', padding: '12px', borderRadius: '6px', border: '1px solid #2d3748' }}>
              <div style={{ color: '#3b82f6', fontWeight: 'bold', marginBottom: '4px' }}>🚀 Free 4×–8× Speedup</div>
              <div style={{ color: '#94a3b8', fontSize: '0.85rem' }}>
                Enable continuous batching on DGX: <code style={{ color: '#ff8c00', fontSize: '0.8rem' }}>--parallel 8 --cont-batching</code>. Batches 8 users in parallel in ~4–6s.
              </div>
            </div>
          </div>
        </div>

        {/* Configuration Bar */}
        <div style={{ ...cardStyle, display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <h3 style={{ margin: 0, color: '#e2e8f0' }}>Stress Test Controls</h3>
          <div style={{ display: 'flex', gap: '16px', alignItems: 'center', flexWrap: 'wrap' }}>
            <button
              className="btn"
              style={{ padding: '8px 16px', borderRadius: '4px', border: stressMode === 'rampup' ? '1px solid #ff6600' : '1px solid #2d3748', backgroundColor: stressMode === 'rampup' ? '#ff660033' : '#0d0d1a', color: stressMode === 'rampup' ? '#ff6600' : '#e2e8f0', cursor: 'pointer' }}
              onClick={() => setStressMode('rampup')}
              disabled={stressRunning}
            >
              Ramp Up (Auto-detect)
            </button>
            <button
              className="btn"
              style={{ padding: '8px 16px', borderRadius: '4px', border: stressMode === 'fixed' ? '1px solid #ff6600' : '1px solid #2d3748', backgroundColor: stressMode === 'fixed' ? '#ff660033' : '#0d0d1a', color: stressMode === 'fixed' ? '#ff6600' : '#e2e8f0', cursor: 'pointer' }}
              onClick={() => setStressMode('fixed')}
              disabled={stressRunning}
            >
              Fixed Load
            </button>
            {stressMode === 'fixed' && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ color: '#94a3b8' }}>Concurrent Users:</span>
                <input
                  type="number"
                  min={1}
                  max={100}
                  value={stressConcurrency}
                  onChange={(e) => setStressConcurrency(parseInt(e.target.value) || 10)}
                  disabled={stressRunning}
                  style={{ padding: '6px', borderRadius: '4px', border: '1px solid #2d3748', backgroundColor: '#0d0d1a', color: '#e2e8f0', width: '70px' }}
                />
              </div>
            )}
          </div>
          <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
            <button
              className="btn"
              style={{ backgroundColor: stressRunning ? '#64748b' : '#ef4444', color: '#fff', border: 'none', padding: '10px 24px', borderRadius: '6px', cursor: stressRunning ? 'not-allowed' : 'pointer', fontWeight: 'bold' }}
              onClick={startStress}
              disabled={stressRunning}
            >
              {stressRunning ? 'Running Stress Test...' : 'Start Stress Test'}
            </button>
            {stressRunning && (
              <button
                className="btn"
                style={{ backgroundColor: '#2d3748', color: '#fff', border: 'none', padding: '10px 24px', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold' }}
                onClick={cancelStress}
              >
                Cancel
              </button>
            )}
            <span style={{ color: '#94a3b8' }}>
              {stressRunning ? 'Load testing in progress...' : currentStress ? `Last completed: ${new Date(currentStress.completedAt || currentStress.startedAt).toLocaleTimeString()}` : 'Ready to benchmark.'}
            </span>
          </div>
        </div>

        {/* Capacity Result Banner */}
        {currentStress && currentStress.estimatedCapacity !== null && (
          <div style={{ ...cardStyle, border: `2px solid ${currentStress.estimatedCapacity >= 20 ? '#22c55e' : currentStress.estimatedCapacity >= 10 ? '#f59e0b' : '#ef4444'}`, backgroundColor: `${currentStress.estimatedCapacity >= 20 ? '#22c55e' : currentStress.estimatedCapacity >= 10 ? '#f59e0b' : '#ef4444'}11` }}>
            <h2 style={{ margin: '0 0 8px 0', color: currentStress.estimatedCapacity >= 20 ? '#22c55e' : currentStress.estimatedCapacity >= 10 ? '#f59e0b' : '#ef4444', textAlign: 'center' }}>
              🎯 {currentStress.backendLabel ?? 'This backend'} can handle ~{currentStress.estimatedCapacity} concurrent users
            </h2>
            <div style={{ textAlign: 'center', color: '#e2e8f0', fontSize: '1.05rem' }}>{currentStress.capacityNote}</div>
          </div>
        )}

        {/* Live Token Usage Overview */}
        {(phases.length > 0 || (currentStress && currentStress.totalTokens)) && (
          <div style={{ ...cardStyle }}>
            <h3 style={{ margin: '0 0 16px 0', color: '#e2e8f0', display: 'flex', alignItems: 'center', gap: '8px' }}>
              🔢 Token Usage Telemetry
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '16px' }}>
              <div style={{ backgroundColor: '#0d0d1a', padding: '14px', borderRadius: '6px', border: '1px solid #2d3748' }}>
                <div style={{ color: '#64748b', fontSize: '0.8rem', textTransform: 'uppercase' }}>Total Tokens</div>
                <div style={{ color: '#ff8c00', fontSize: '1.6rem', fontWeight: 700 }}>
                  {(currentStress?.totalTokens || totalStressTokens).toLocaleString()}
                </div>
              </div>
              <div style={{ backgroundColor: '#0d0d1a', padding: '14px', borderRadius: '6px', border: '1px solid #2d3748' }}>
                <div style={{ color: '#64748b', fontSize: '0.8rem', textTransform: 'uppercase' }}>Input Tokens</div>
                <div style={{ color: '#e2e8f0', fontSize: '1.6rem', fontWeight: 600 }}>
                  {(currentStress?.totalInputTokens || totalInputTokens).toLocaleString()}
                </div>
              </div>
              <div style={{ backgroundColor: '#0d0d1a', padding: '14px', borderRadius: '6px', border: '1px solid #2d3748' }}>
                <div style={{ color: '#64748b', fontSize: '0.8rem', textTransform: 'uppercase' }}>Output Tokens</div>
                <div style={{ color: '#e2e8f0', fontSize: '1.6rem', fontWeight: 600 }}>
                  {(currentStress?.totalOutputTokens || totalOutputTokens).toLocaleString()}
                </div>
              </div>
              <div style={{ backgroundColor: '#0d0d1a', padding: '14px', borderRadius: '6px', border: '1px solid #2d3748' }}>
                <div style={{ color: '#64748b', fontSize: '0.8rem', textTransform: 'uppercase' }}>Avg Tokens / Req</div>
                <div style={{ color: '#22c55e', fontSize: '1.6rem', fontWeight: 600 }}>
                  {phases.length > 0 ? Math.round(totalStressTokens / Math.max(1, phases.reduce((acc, p) => acc + p.totalRequests, 0))) : '~60'}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Phase Telemetry Cards */}
        {phases.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <h3 style={{ margin: 0, color: '#e2e8f0' }}>Phase-by-Phase Realtime Telemetry</h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '16px' }}>
              {phases.map((phase, idx) => (
                <div key={idx} style={{ ...cardStyle }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}>
                    <span style={{ color: '#e2e8f0', fontWeight: 'bold' }}>Phase {idx + 1} ({phase.totalRequests} Requests)</span>
                    <span style={badgeStyle('#3b82f6')}>{phase.concurrentUsers} Concurrent Users</span>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                    <div>
                      <div style={{ color: '#64748b', fontSize: '0.8rem', textTransform: 'uppercase' }}>Avg Latency</div>
                      <div style={{ color: phase.avgLatencyMs > 5000 ? '#ef4444' : '#e2e8f0', fontSize: '1.2rem', fontWeight: 600 }}>
                        {Math.round(phase.avgLatencyMs)}ms
                      </div>
                    </div>
                    <div>
                      <div style={{ color: '#64748b', fontSize: '0.8rem', textTransform: 'uppercase' }}>p95 Latency</div>
                      <div style={{ color: phase.p95LatencyMs > 10000 ? '#ef4444' : '#e2e8f0', fontSize: '1.2rem', fontWeight: 600 }}>
                        {Math.round(phase.p95LatencyMs)}ms
                      </div>
                    </div>
                    <div>
                      <div style={{ color: '#64748b', fontSize: '0.8rem', textTransform: 'uppercase' }}>Throughput</div>
                      <div style={{ color: '#22c55e', fontSize: '1.2rem', fontWeight: 600 }}>{phase.requestsPerSecond.toFixed(1)} req/s</div>
                    </div>
                    <div>
                      <div style={{ color: '#64748b', fontSize: '0.8rem', textTransform: 'uppercase' }}>Error Rate</div>
                      <div style={{ color: phase.errorRate > 0.05 ? '#ef4444' : '#22c55e', fontSize: '1.2rem', fontWeight: 600 }}>
                        {(phase.errorRate * 100).toFixed(1)}%
                      </div>
                    </div>
                    {phase.totalTokens !== undefined && (
                      <div style={{ gridColumn: 'span 2', marginTop: '4px', paddingTop: '8px', borderTop: '1px solid #2d3748', display: 'flex', justifyContent: 'space-between', color: '#94a3b8', fontSize: '0.85rem' }}>
                        <span>Tokens: {phase.totalTokens.toLocaleString()}</span>
                        <span>({phase.inputTokens || 0} in / {phase.outputTokens || 0} out)</span>
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Stress Test History */}
        {stressHistory.length > 0 && (
          <div style={{ ...cardStyle }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, color: '#e2e8f0' }}>Stress Test History</h3>
              <button
                className="btn"
                style={{ padding: '6px 14px', fontSize: '0.85rem' }}
                onClick={() => setShowStressHistory(!showStressHistory)}
              >
                {showStressHistory ? 'Hide History' : 'Show History'}
              </button>
            </div>
            {showStressHistory && (
              <div style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {stressHistory.map(run => (
                  <div key={run.id} style={{ padding: '14px', backgroundColor: '#0d0d1a', border: '1px solid #2d3748', borderRadius: '6px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer' }} onClick={() => setExpandedStressRun(expandedStressRun === run.id ? null : run.id)}>
                      <div>
                        <div style={{ color: '#e2e8f0', fontWeight: 600 }}>{new Date(run.startedAt).toLocaleString()}</div>
                        <div style={{ color: '#94a3b8', fontSize: '0.85rem' }}>Run ID: {run.id} · {run.capacityNote}</div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                        <span style={badgeStyle(run.status === 'COMPLETED' ? '#22c55e' : run.status === 'FAILED' ? '#ef4444' : '#f59e0b')}>{run.status}</span>
                        {run.estimatedCapacity !== null && (
                          <span style={{ color: '#ff8c00', fontWeight: 'bold' }}>Capacity: ~{run.estimatedCapacity} users</span>
                        )}
                        {run.totalTokens !== undefined && (
                          <span style={{ color: '#94a3b8', fontSize: '0.85rem' }}>{run.totalTokens.toLocaleString()} tokens</span>
                        )}
                        <span style={{ color: '#94a3b8' }}>{expandedStressRun === run.id ? '▲' : '▼'}</span>
                      </div>
                    </div>

                    {expandedStressRun === run.id && run.phases && run.phases.length > 0 && (
                      <div style={{ marginTop: '12px', paddingTop: '12px', borderTop: '1px solid #2d3748' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem', textAlign: 'left' }}>
                          <thead>
                            <tr style={{ color: '#94a3b8', borderBottom: '1px solid #2d3748' }}>
                              <th style={{ padding: '6px' }}>Concurrency</th>
                              <th style={{ padding: '6px' }}>Requests</th>
                              <th style={{ padding: '6px' }}>Avg Latency</th>
                              <th style={{ padding: '6px' }}>P95 Latency</th>
                              <th style={{ padding: '6px' }}>Throughput</th>
                              <th style={{ padding: '6px' }}>Tokens</th>
                            </tr>
                          </thead>
                          <tbody>
                            {run.phases.map((ph, pIdx) => (
                              <tr key={pIdx} style={{ borderBottom: '1px solid #2d374811' }}>
                                <td style={{ padding: '6px', color: '#e2e8f0' }}>{ph.concurrentUsers} Users</td>
                                <td style={{ padding: '6px', color: '#94a3b8' }}>{ph.successfulRequests}/{ph.totalRequests}</td>
                                <td style={{ padding: '6px', color: ph.avgLatencyMs > 5000 ? '#ef4444' : '#e2e8f0' }}>{Math.round(ph.avgLatencyMs)}ms</td>
                                <td style={{ padding: '6px', color: '#94a3b8' }}>{Math.round(ph.p95LatencyMs)}ms</td>
                                <td style={{ padding: '6px', color: '#22c55e' }}>{ph.requestsPerSecond.toFixed(1)} req/s</td>
                                <td style={{ padding: '6px', color: '#ff8c00' }}>{ph.totalTokens?.toLocaleString() || '-'}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    );
  };

  const renderGuide = () => {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
        <div style={{ ...cardStyle }}>
          <h2 style={{ margin: '0 0 16px 0', color: '#ff8c00' }}>📘 Metric Guide & Methodology</h2>
          <p style={{ color: '#cbd5e1', lineHeight: '1.6' }}>
            This page explains how all benchmark measurements are conducted, how the numbers are calculated, and what they mean for live hackathon events.
          </p>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '16px' }}>
          <div style={{ ...cardStyle }}>
            <h3 style={{ margin: '0 0 12px 0', color: '#3b82f6' }}>⚡ Stress Test Metrics</h3>
            <ul style={{ paddingLeft: '20px', color: '#cbd5e1', lineHeight: '1.7', margin: 0 }}>
              <li>
                <strong>Concurrent Users (Virtual Threads):</strong> Number of parallel workers launched simultaneously. In Ramp-up mode, the test steps through 1, 2, 4, 8, 16, 32, 64 users.
              </li>
              <li>
                <strong>Average Latency:</strong> Total round-trip time from when the request leaves the backend until the full completion text is received, divided by successful requests.
              </li>
              <li>
                <strong>P95 Latency (95th Percentile):</strong> 95% of all requests completed within this duration. This catches queuing lag and tail-latency spikes that averages hide.
              </li>
              <li>
                <strong>Throughput (RPS):</strong> Total completed inferences divided by the wall-clock duration of the test phase.
              </li>
              <li>
                <strong>Capacity Estimate:</strong> The highest concurrency tier where <em>Avg Latency &lt; 5,000ms</em> AND <em>Error Rate &lt; 5%</em>.
              </li>
            </ul>
          </div>

          <div style={{ ...cardStyle }}>
            <h3 style={{ margin: '0 0 12px 0', color: '#22c55e' }}>🔬 Calibration & Invariant Metrics</h3>
            <ul style={{ paddingLeft: '20px', color: '#cbd5e1', lineHeight: '1.7', margin: 0 }}>
              <li>
                <strong>Health Score (X/7):</strong> Number of mechanical game ladder invariants that hold true across all 7 levels.
              </li>
              <li>
                <strong>canonicalOpens:</strong> Confirms that each level's intended solve technique successfully extracts the password.
              </li>
              <li>
                <strong>canonicalCloses:</strong> Confirms that the attack that defeated the level below is now successfully blocked.
              </li>
              <li>
                <strong>setShrinks:</strong> Checks that the count of viable attack families strictly narrows as the player climbs higher.
              </li>
              <li>
                <strong>notAWall:</strong> Ensures Leo's substantive response rate is &ge;40% (meaning Leo does not stonewall and refuse innocent conversation).
              </li>
              <li>
                <strong>noSkeletonKeys:</strong> Verifies that no single attack prompt beats more than 3 levels in a row.
              </li>
              <li>
                <strong>everyFamilyCloses:</strong> Ensures that all attack channels opened on lower rungs are closed by Level 7.
              </li>
            </ul>
          </div>
        </div>

        <div style={{ ...cardStyle }}>
          <h3 style={{ margin: '0 0 12px 0', color: '#ff6600' }}>💡 Sustained Load vs. Burst Concurrency</h3>
          <p style={{ color: '#cbd5e1', lineHeight: '1.7', margin: 0 }}>
            In stress testing, 16 concurrent users means <strong>16 clients firing simultaneously without a single millisecond of pause</strong>. In real life, hackathon players read Leo's response, think of a strategy, and type their next prompt—averaging <strong>25 to 40 seconds between turns</strong>.
            Therefore, an infrastructure that bottlenecks at 4 simultaneous burst calls can comfortably host <strong>20 to 30 active human players</strong> in a live room without issue.
          </p>
        </div>
      </div>
    );
  };

  return (
    <div style={{ padding: '24px', color: '#e2e8f0', minHeight: '100vh', backgroundColor: '#0d0d1a' }}>
      <div style={{ marginBottom: '24px', borderBottom: '1px solid #2d3748', display: 'flex', gap: '32px' }}>
        <div
          style={{ padding: '12px 0', cursor: 'pointer', borderBottom: activeTab === 'stress' ? '2px solid #ff6600' : '2px solid transparent', color: activeTab === 'stress' ? '#ff6600' : '#94a3b8', fontWeight: activeTab === 'stress' ? 600 : 'normal' }}
          onClick={() => setActiveTab('stress')}
        >
          🔥 Stress Test & Capacity
        </div>
        <div
          style={{ padding: '12px 0', cursor: 'pointer', borderBottom: activeTab === 'calibration' ? '2px solid #ff6600' : '2px solid transparent', color: activeTab === 'calibration' ? '#ff6600' : '#94a3b8', fontWeight: activeTab === 'calibration' ? 600 : 'normal' }}
          onClick={() => setActiveTab('calibration')}
        >
          ⚡ Level Calibration
        </div>
        <div
          style={{ padding: '12px 0', cursor: 'pointer', borderBottom: activeTab === 'guide' ? '2px solid #ff6600' : '2px solid transparent', color: activeTab === 'guide' ? '#ff6600' : '#94a3b8', fontWeight: activeTab === 'guide' ? 600 : 'normal' }}
          onClick={() => setActiveTab('guide')}
        >
          📘 Metric Guide & Methodology
        </div>
      </div>

      {activeTab === 'calibration' && renderCalibration()}
      {activeTab === 'stress' && renderStress()}
      {activeTab === 'guide' && renderGuide()}
    </div>
  );
}
