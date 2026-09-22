import React, { useState, useEffect } from 'react';
import { useTvSnapshot } from './useTvSnapshot';
import TvBoardPanel from './TvBoardPanel';
import TvFunnelPanel from './TvFunnelPanel';
import TvFeedPanel from './TvFeedPanel';
import TvVitalsPanel from './TvVitalsPanel';
import './tv.css';

/** Eight rows fit the shorter board panel; the rest rotate. */
const PAGE_SIZE = 8;
const PAGE_MS = 15000;

const ago = (ms: number) => {
  const m = Math.floor(ms / 60000);
  if (m >= 60) return `${Math.floor(m / 60)}h ${m % 60}m ago`;
  if (m >= 1) return `${m}m ago`;
  return `${Math.floor(ms / 1000)}s ago`;
};

/**
 * The event display, for a screen nobody is sitting in front of.
 *
 * A fixed grid rather than rotating panels: rotation would hide the funnel three quarters of the
 * time, and the funnel is the thing an organiser glances up at to decide whether a level is too
 * hard. Only the leaderboard rotates, and only when it has more players than fit.
 */
const LeaderboardTV: React.FC = () => {
  const { snapshot, stale, staleMs } = useTvSnapshot();
  const [page, setPage] = useState(0);
  const [clock, setClock] = useState(new Date());

  useEffect(() => {
    const t = setInterval(() => setClock(new Date()), 1000);
    return () => clearInterval(t);
  }, []);

  const board = snapshot?.board ?? [];
  const pageCount = Math.max(1, Math.ceil(board.length / PAGE_SIZE));

  useEffect(() => {
    // Guarded: without it a board of three players flickers through a single page forever.
    if (board.length <= PAGE_SIZE) {
      setPage(0);
      return;
    }
    const t = setInterval(() => setPage((p) => (p + 1) % Math.ceil(board.length / PAGE_SIZE)), PAGE_MS);
    return () => clearInterval(t);
  }, [board.length]);

  const visible = board.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);

  return (
    <div className="tv-root">
      <div>
        <div className="tv-header">
          <div>
            <div className="tv-title">🦁 {snapshot?.eventName ?? "THE LION'S DEN"}</div>
            <div className="tv-subtitle">
              AI Security Challenge{snapshot?.sessionLabel ? ` · ${snapshot.sessionLabel}` : ''}
            </div>
          </div>
          <div>
            <div className="tv-clock">{clock.toLocaleTimeString()}</div>
            <div className="tv-clock-sub">
              {/* The server's own clock, not this machine's: venue PCs are often wrong. */}
              {snapshot ? `data ${new Date(snapshot.serverTime).toLocaleTimeString()}` : 'connecting…'}
            </div>
          </div>
        </div>
        {stale && (
          <div className="tv-stale">
            ⚠ LIVE DATA PAUSED — last update {ago(staleMs)}
          </div>
        )}
      </div>

      <div className="tv-body">
        <TvBoardPanel rows={visible} page={page} pageCount={pageCount} />
        <div className="tv-col">
          <TvFunnelPanel bars={snapshot?.funnel ?? []} />
          <TvFeedPanel items={snapshot?.feed ?? []} enabled={snapshot?.feedEnabled ?? false} />
        </div>
      </div>

      <TvVitalsPanel
        vitals={snapshot?.vitals ?? {
          playersOnline: 0, playersTotal: 0, attemptsLastMinute: 0, attemptsTotal: 0,
          tokensTotal: 0, backendLabel: null, backendHealthy: false, backendLatencyMs: 0,
        }}
      />
    </div>
  );
};

export default LeaderboardTV;
