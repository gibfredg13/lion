import type { TvBoardRow } from './types';

const formatTime = (seconds: number) => {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
};

const medal = (rank: number) => (rank === 1 ? '🥇' : rank === 2 ? '🥈' : rank === 3 ? '🥉' : `#${rank}`);

interface Props {
  rows: TvBoardRow[];
  page: number;
  pageCount: number;
}

export default function TvBoardPanel({ rows, page, pageCount }: Props) {
  return (
    <div className="tv-panel">
      <div className="tv-panel-title">
        <span>Leaderboard</span>
        {pageCount > 1 && <span className="tv-panel-note">page {page + 1} of {pageCount}</span>}
      </div>
      <div className="tv-board-head">
        <span>Rank</span><span>Player</span><span>Level</span><span>Time</span><span>Tokens</span>
      </div>
      <div className="tv-panel-body">
        {rows.length === 0 ? (
          <div className="tv-empty">🦁 Waiting for challengers…</div>
        ) : rows.map((r) => (
          // Keyed on rank, not on any player identifier. The public payload deliberately carries
          // no id - one would be a correlation handle - and keying on the name breaks when two
          // people share one. Rank is stable within a render and unique by construction.
          <div key={r.rank} className={`tv-board-row rank-${r.rank <= 3 ? r.rank : 'n'}`}>
            <span className="tv-medal">{medal(r.rank)}</span>
            <span className="tv-name">
              {r.name}
              {r.finished && ' 🏁'}
              {r.carriedIn && <span className="tv-carried">carried in</span>}
            </span>
            <span className="tv-level">Level {r.level}</span>
            <span className="tv-num">{formatTime(r.durationSeconds)}</span>
            <span className="tv-tokens">{(r.tokens / 1000).toFixed(1)}K</span>
          </div>
        ))}
      </div>
    </div>
  );
}
