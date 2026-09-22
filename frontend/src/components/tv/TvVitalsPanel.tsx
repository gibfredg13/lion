import type { TvVitals } from './types';

const compact = (n: number) => (n >= 1000 ? `${(n / 1000).toFixed(1)}K` : String(n));

export default function TvVitalsPanel({ vitals }: { vitals: TvVitals }) {
  const tiles = [
    { label: 'Online now', value: String(vitals.playersOnline) },
    { label: 'Players', value: String(vitals.playersTotal) },
    { label: 'Attempts / min', value: String(vitals.attemptsLastMinute) },
    { label: 'Attempts', value: compact(vitals.attemptsTotal) },
    { label: 'Tokens', value: compact(vitals.tokensTotal) },
    {
      // "Leo is slow" and "Leo is down" are indistinguishable from a player's seat.
      label: vitals.backendLabel ?? 'Model',
      value: vitals.backendHealthy ? `${vitals.backendLatencyMs}ms` : 'DOWN',
      cls: vitals.backendHealthy ? 'good' : 'bad',
    },
  ];

  return (
    <div className="tv-vitals">
      {tiles.map((t) => (
        <div className="tv-vital" key={t.label}>
          <div className={`tv-vital-value ${t.cls ?? ''}`}>{t.value}</div>
          <div className="tv-vital-label">{t.label}</div>
        </div>
      ))}
    </div>
  );
}
