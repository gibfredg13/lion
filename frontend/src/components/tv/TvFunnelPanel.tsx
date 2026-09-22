import type { TvFunnelBar } from './types';

/** A level holding more than this share of the field is the wall, and worth colouring. */
const WALL_SHARE = 0.4;

export default function TvFunnelPanel({ bars }: { bars: TvFunnelBar[] }) {
  const total = bars.reduce((sum, b) => sum + b.players, 0);
  const peak = Math.max(...bars.map((b) => b.players), 1);

  return (
    <div className="tv-panel">
      <div className="tv-panel-title">
        <span>Where everyone is</span>
        <span className="tv-panel-note">{total} playing</span>
      </div>
      <div className="tv-panel-body">
        <div className="tv-funnel">
          {bars.map((b) => {
            const share = total === 0 ? 0 : b.players / total;
            const width = `${(b.players / peak) * 100}%`;
            const cls = !b.enabled ? 'off' : share >= WALL_SHARE ? 'wall' : '';
            return (
              <div className="tv-funnel-row" key={b.level}>
                <span className="tv-funnel-label">
                  Level {b.level}{!b.enabled && ' ⏸'}
                </span>
                <div className="tv-funnel-track">
                  <div className={`tv-funnel-fill ${cls}`} style={{ width }} />
                </div>
                <span className="tv-funnel-count">{b.players}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
