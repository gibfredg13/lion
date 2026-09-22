import { useState } from 'react';
import { LEVEL_GUIDES } from '../../data/solutions';

/**
 * The routes that keep working all the way up.
 *
 * Derived from LEVEL_GUIDES rather than emitted alongside it, so it cannot go stale: it is a
 * different reading of the same measured data, and it survives every regeneration of solutions.ts
 * without the generator having to know this view exists.
 *
 * Level 7's two solutions - a jumble and a fragment - are open from level 1, because nothing below
 * level 7 can close them. A player who finds either one early skips the middle of the game without
 * realising, which is invisible in the per-level view: seven levels each showing a list of ways
 * through look like seven different puzzles even when one sentence beats all of them.
 */
function routeMap(): { family: string; levels: number[] }[] {
  const families = [...new Set(LEVEL_GUIDES.flatMap(g => g.attacks.map(a => a.family)))].sort();
  return families.map(family => ({
    family,
    levels: LEVEL_GUIDES.filter(g => g.attacks.some(a => a.family === family)).map(g => g.level),
  }));
}

/**
 * Facilitator's helper for players who are stuck.
 *
 * Built for someone standing at a player's shoulder, so nothing spoils by accident: hints reveal
 * one rung at a time, and the worked solutions stay collapsed until deliberately opened.
 */
export default function HelpDesk() {
  const [level, setLevel] = useState(1);
  const [rungs, setRungs] = useState(0);
  const [showAnswers, setShowAnswers] = useState(false);
  const [openAttack, setOpenAttack] = useState<number | null>(null);
  const [copied, setCopied] = useState<string | null>(null);
  const [showMap, setShowMap] = useState(false);

  const guide = LEVEL_GUIDES.find(g => g.level === level)!;

  const pick = (n: number) => {
    setLevel(n); setRungs(0); setShowAnswers(false); setOpenAttack(null);
  };

  const copy = (text: string) => {
    navigator.clipboard?.writeText(text).then(
      () => { setCopied(text); setTimeout(() => setCopied(null), 1500); },
      () => {}
    );
  };

  const card: React.CSSProperties = {
    background: '#1a1a2e', border: '1px solid #2d3748', borderRadius: '8px', padding: '16px',
  };

  return (
    <div style={{ maxWidth: '860px' }}>
      <h2 style={{ color: '#ff8c00', marginBottom: '6px' }}>🛟 Help Desk</h2>
      <p style={{ color: '#94a3b8', marginBottom: '4px' }}>
        For helping players who are stuck. Work <strong>down</strong> the hint ladder — a player
        handed the answer learns nothing, and so does one stuck for twenty minutes.
      </p>
      <p style={{ color: '#64748b', fontSize: '0.82rem', marginBottom: '16px' }}>
        Every player has a different password, so you cannot read one off your own screen. Every
        prompt here works whatever the word is.
      </p>

      {/* Route map. Ask what stopped working before handing out a hint for what has not started. */}
      <div style={{ ...card, marginBottom: '20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '10px' }}>
          <div>
            <div style={{ fontWeight: 700, color: '#e2e8f0' }}>Route map — which trick works where</div>
            <div style={{ color: '#64748b', fontSize: '0.8rem', marginTop: '2px' }}>
              Ask <em>what stopped working?</em> before <em>what have you tried?</em> — no spoilers,
              it names no prompts.
            </div>
          </div>
          <button className="btn" onClick={() => setShowMap(m => !m)}>
            {showMap ? 'Hide' : 'Show map'}
          </button>
        </div>

        {showMap && (
          <div style={{ marginTop: '14px' }}>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ borderCollapse: 'collapse', fontSize: '0.82rem', minWidth: '420px' }}>
                <thead>
                  <tr>
                    <th style={{ textAlign: 'left', padding: '4px 10px 8px 0', color: '#94a3b8', fontWeight: 500 }}>
                      Route
                    </th>
                    {LEVEL_GUIDES.map(g => (
                      <th key={g.level} style={{ padding: '4px 8px 8px', color: '#94a3b8', fontWeight: 500 }}>
                        {g.level}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {routeMap().map(({ family, levels }) => {
                    // Data attributes rather than colour alone: a route that beats every rung is
                    // the whole reason this panel exists, and it should be assertable without a
                    // test having to read a hex value out of an inline style.
                    const everywhere = levels.length === LEVEL_GUIDES.length;
                    return (
                      <tr
                        key={family}
                        data-route={family}
                        data-everywhere={everywhere ? 'true' : 'false'}
                        style={{ borderTop: '1px solid #2d3748' }}
                      >
                        <td style={{
                          padding: '6px 10px 6px 0', color: everywhere ? '#ff8c00' : '#e2e8f0',
                          textTransform: 'capitalize', whiteSpace: 'nowrap',
                          fontWeight: everywhere ? 700 : 400,
                        }}>
                          {family}
                        </td>
                        {LEVEL_GUIDES.map(g => (
                          <td
                            key={g.level}
                            data-level={g.level}
                            data-win={levels.includes(g.level) ? 'true' : 'false'}
                            style={{
                              padding: '6px 8px', textAlign: 'center',
                              color: levels.includes(g.level) ? (everywhere ? '#ff8c00' : '#22c55e') : '#334155',
                            }}
                          >
                            {levels.includes(g.level) ? '●' : '·'}
                          </td>
                        ))}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <p style={{ color: '#94a3b8', fontSize: '0.8rem', margin: '14px 0 0', lineHeight: 1.6 }}>
              A long unbroken row is a rung the player skipped. The rows in{' '}
              <span style={{ color: '#ff8c00', fontWeight: 700 }}>orange</span> work at every level —
              they are level 7's own solutions, and nothing lower down can close them. A player who
              found one of those early has been repeating it since, and the way to help is not a
              hint: ask them to beat the next level a <em>different</em> way.
            </p>
          </div>
        )}
      </div>

      {/* Level picker */}
      <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '20px' }}>
        {LEVEL_GUIDES.map(g => (
          <button
            key={g.level}
            onClick={() => pick(g.level)}
            style={{
              padding: '10px 16px', borderRadius: '8px', cursor: 'pointer',
              border: `1px solid ${g.level === level ? '#ff6600' : '#2d3748'}`,
              background: g.level === level ? '#ff6600' : '#1a1a2e',
              color: g.level === level ? '#fff' : '#94a3b8',
              fontWeight: g.level === level ? 700 : 500, fontSize: '0.95rem',
            }}
          >
            {g.level}
          </button>
        ))}
      </div>

      <div style={{ ...card, borderLeft: '4px solid #ff6600', marginBottom: '16px' }}>
        <div style={{ fontSize: '1.15rem', fontWeight: 700, color: '#e2e8f0' }}>
          Level {guide.level} — {guide.name}
        </div>
        <p style={{ color: '#94a3b8', margin: '6px 0 12px' }}>{guide.summary}</p>
        <div style={{ fontSize: '0.85rem', color: '#94a3b8' }}>
          <strong style={{ color: '#e2e8f0' }}>What defends it:</strong> {guide.defence}
        </div>
        {guide.blocked.length > 0 && (
          <div style={{ fontSize: '0.85rem', color: '#94a3b8', marginTop: '8px' }}>
            <strong style={{ color: '#e2e8f0' }}>Questions are rejected unseen</strong> if they
            contain{' '}
            {guide.blocked.map(w => (
              <code key={w} style={{ background: '#0d0d1a', padding: '1px 6px', borderRadius: '4px', marginRight: '4px' }}>{w}</code>
            ))}
            — if a player gets an instant refusal, this is why.
          </div>
        )}
      </div>

      {/* Hint ladder */}
      <div style={{ ...card, marginBottom: '16px' }}>
        <div style={{ fontWeight: 700, color: '#e2e8f0', marginBottom: '10px' }}>Hint ladder</div>
        {guide.hints.map((h, i) => (
          <div
            key={i}
            style={{
              display: 'flex', gap: '10px', alignItems: 'flex-start', padding: '10px 0',
              borderTop: i === 0 ? 'none' : '1px solid #2d3748',
            }}
          >
            <span style={{
              minWidth: '22px', height: '22px', borderRadius: '50%', flexShrink: 0,
              background: i < rungs ? '#ff6600' : '#2d3748', color: '#fff',
              fontSize: '0.75rem', display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>{i + 1}</span>
            {i < rungs
              ? <span style={{ color: '#e2e8f0' }}>{h}</span>
              : <span style={{ color: '#475569', fontStyle: 'italic' }}>hidden</span>}
          </div>
        ))}
        <div style={{ display: 'flex', gap: '8px', marginTop: '12px' }}>
          <button
            className="btn"
            disabled={rungs >= guide.hints.length}
            onClick={() => setRungs(r => r + 1)}
          >
            {rungs === 0 ? 'Give first hint' : rungs >= guide.hints.length ? 'No hints left' : 'Give next hint'}
          </button>
          {rungs > 0 && <button className="btn" onClick={() => setRungs(0)}>Reset</button>}
        </div>
      </div>

      {/* Worked solutions, hidden until asked for */}
      <div style={card}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <div style={{ fontWeight: 700, color: '#e2e8f0' }}>
              Worked solutions — {guide.attacks.length} different ways
            </div>
            <div style={{ color: '#64748b', fontSize: '0.8rem', marginTop: '2px' }}>
              Full spoilers. Every one of these actually beat this level.
            </div>
          </div>
          <button className="btn" onClick={() => setShowAnswers(s => !s)}>
            {showAnswers ? 'Hide' : 'Show answers'}
          </button>
        </div>

        {showAnswers && (
          <div style={{ marginTop: '16px', display: 'grid', gap: '10px' }}>
            {guide.attacks.map((a, i) => (
              <div key={i} style={{ border: '1px solid #2d3748', borderRadius: '6px', padding: '12px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '10px' }}>
                  <span className="badge" style={{ textTransform: 'capitalize' }}>{a.family}</span>
                  <button className="btn" style={{ fontSize: '0.75rem', padding: '4px 10px' }}
                          onClick={() => copy(a.prompt)}>
                    {copied === a.prompt ? '✓ Copied' : 'Copy prompt'}
                  </button>
                </div>
                <div style={{
                  marginTop: '10px', background: '#0d0d1a', borderRadius: '6px', padding: '10px',
                  color: '#e2e8f0', fontFamily: 'monospace', fontSize: '0.82rem',
                }}>
                  {a.prompt}
                </div>
                <p style={{ color: '#94a3b8', fontSize: '0.82rem', margin: '10px 0 0' }}>
                  <strong style={{ color: '#e2e8f0' }}>Why it works:</strong> {a.why}{' '}
                  <em style={{ color: '#64748b' }}>({a.how}.)</em>
                </p>
                <button
                  className="btn"
                  style={{ fontSize: '0.75rem', padding: '4px 10px', marginTop: '10px' }}
                  onClick={() => setOpenAttack(openAttack === i ? null : i)}
                >
                  {openAttack === i ? 'Hide Leo’s reply' : 'Show Leo’s reply'}
                </button>
                {openAttack === i && (
                  <div style={{
                    marginTop: '8px', background: '#0d0d1a', borderRadius: '6px', padding: '10px',
                    color: '#94a3b8', fontSize: '0.82rem', lineHeight: 1.5,
                  }}>
                    {a.reply}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <p style={{ color: '#64748b', fontSize: '0.8rem', marginTop: '20px' }}>
        If nobody can clear a level, that is a tuning problem, not a player problem — go to{' '}
        <strong>Level Control</strong> and <em>raise</em> the temperature. A colder Leo refuses more,
        which gives players nothing to work with.
      </p>
    </div>
  );
}
