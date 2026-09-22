import React, { useState, useEffect, useCallback } from 'react';

interface LeaderboardEntry {
  rank: number;
  name: string;
  email: string | null;
  maxLevelReached: number;
  score: number;
  finishedAt: string | null;
  durationSeconds: number;
  totalTokensUsed: number;
}

const LeaderboardTV: React.FC = () => {
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [currentTime, setCurrentTime] = useState(new Date());
  const [page, setPage] = useState(0);
  const [lastUpdate, setLastUpdate] = useState(new Date());
  const PAGE_SIZE = 10;

  const fetchLeaderboard = useCallback(async () => {
    try {
      const res = await fetch('/api/leaderboard/progress', { credentials: 'include' });
      const data = await res.json();
      setLeaderboard(data);
      setLastUpdate(new Date());
    } catch (e) {
      console.error('Leaderboard fetch failed', e);
    }
  }, []);

  useEffect(() => {
    fetchLeaderboard();
    const interval = setInterval(fetchLeaderboard, 10000);
    return () => clearInterval(interval);
  }, [fetchLeaderboard]);

  useEffect(() => {
    const clock = setInterval(() => setCurrentTime(new Date()), 1000);
    return () => clearInterval(clock);
  }, []);

  useEffect(() => {
    if (leaderboard.length <= PAGE_SIZE) return;
    const timer = setInterval(() => {
      setPage(p => (p + 1) % Math.ceil(leaderboard.length / PAGE_SIZE));
    }, 15000);
    return () => clearInterval(timer);
  }, [leaderboard.length]);

  const formatTime = (seconds: number) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (h > 0) return `${h}h ${m}m`;
    if (m > 0) return `${m}m ${s}s`;
    return `${s}s`;
  };

  const getRankStyle = (rank: number): React.CSSProperties => {
    if (rank === 1) return { borderLeft: '4px solid #FFD700', background: 'rgba(255,215,0,0.08)' };
    if (rank === 2) return { borderLeft: '4px solid #C0C0C0', background: 'rgba(192,192,192,0.06)' };
    if (rank === 3) return { borderLeft: '4px solid #CD7F32', background: 'rgba(205,127,50,0.06)' };
    return { borderLeft: '4px solid #333' };
  };

  const getMedal = (rank: number) => {
    if (rank === 1) return '🥇';
    if (rank === 2) return '🥈';
    if (rank === 3) return '🥉';
    return `#${rank}`;
  };

  const pageStart = page * PAGE_SIZE;
  const visibleEntries = leaderboard.slice(pageStart, pageStart + PAGE_SIZE);
  // finishedAt is set by the server only for players who cleared every level, so the count does
  // not depend on the frontend knowing how many levels there are.
  const totalCompleted = leaderboard.filter(e => e.finishedAt != null).length;

  return (
    <div style={{
      width: '100vw', height: '100vh', background: '#0d0d1a',
      color: 'white', fontFamily: "'Segoe UI', sans-serif",
      display: 'flex', flexDirection: 'column', overflow: 'hidden'
    }}>
      {/* Header */}
      <div style={{
        background: 'linear-gradient(135deg, #ff6600 0%, #ff8c00 100%)',
        padding: '20px 40px', display: 'flex',
        justifyContent: 'space-between', alignItems: 'center'
      }}>
        <div>
          <div style={{ fontSize: '3rem', fontWeight: 900, letterSpacing: '0.05em' }}>
            🦁 THE LION'S DEN
          </div>
          <div style={{ fontSize: '1.2rem', opacity: 0.9 }}>
            AI Security Challenge — Live Leaderboard
          </div>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: '2.5rem', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
            {currentTime.toLocaleTimeString()}
          </div>
          <div style={{ fontSize: '0.9rem', opacity: 0.8 }}>
            Updated: {lastUpdate.toLocaleTimeString()}
          </div>
        </div>
      </div>

      {/* Table Header */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '80px 1fr 120px 120px 100px',
        padding: '12px 40px',
        background: '#1a1a2e',
        fontSize: '0.85rem', color: '#ff8c00',
        fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.1em'
      }}>
        <span>Rank</span><span>Player</span><span>Level</span><span>Time</span><span>Tokens</span>
      </div>

      {/* Entries */}
      <div style={{ flex: 1, overflowY: 'hidden' }}>
        {visibleEntries.length === 0 ? (
          <div style={{ textAlign: 'center', marginTop: '4rem', fontSize: '1.5rem', opacity: 0.5 }}>
            🦁 Waiting for challengers...
          </div>
        ) : visibleEntries.map((entry) => (
          // Keyed on rank, not email: this endpoint strips email addresses, so every row used to
          // share the key `null`. React then reused the wrong rows as the board reordered, which
          // it does on every ten-second poll.
          <div key={entry.rank} style={{
            display: 'grid',
            gridTemplateColumns: '80px 1fr 120px 120px 100px',
            padding: '16px 40px',
            borderBottom: '1px solid #222',
            fontSize: '1.4rem',
            transition: 'all 0.3s ease',
            ...getRankStyle(entry.rank)
          }}>
            <span style={{ fontSize: '1.6rem' }}>{getMedal(entry.rank)}</span>
            <span style={{ fontWeight: 600 }}>{entry.name}</span>
            <span style={{ color: '#ff8c00', fontWeight: 700 }}>Level {entry.maxLevelReached}</span>
            <span style={{ fontVariantNumeric: 'tabular-nums' }}>{formatTime(entry.durationSeconds)}</span>
            <span style={{ opacity: 0.7, fontSize: '1.1rem' }}>{(entry.totalTokensUsed/1000).toFixed(1)}K</span>
          </div>
        ))}
      </div>

      {/* Footer ticker */}
      <div style={{
        background: '#1a1a2e', borderTop: '1px solid #ff6600',
        padding: '10px 40px', display: 'flex',
        justifyContent: 'space-between', alignItems: 'center',
        fontSize: '0.9rem'
      }}>
        <span style={{ color: '#ff8c00' }}>
          👥 {leaderboard.length} players competing &nbsp;•&nbsp; 
          🏆 {totalCompleted} fully completed &nbsp;•&nbsp;
          🎯 Challenge is live
        </span>
        <span style={{ opacity: 0.4, fontSize: '0.8rem' }}>
          🦁 The Lion's Den — Press F11 for fullscreen
        </span>
      </div>
    </div>
  );
};

export default LeaderboardTV;
