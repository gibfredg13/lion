import React, { useState, useEffect } from 'react';
import './Leaderboard.css';

interface LeaderboardEntry {
  rank: number;
  name: string;
  email: string;
  currentLevel: number;
  maxLevelReached: number;
  startedAt: string;
  finishedAt: string;
  durationSeconds: number;
  totalTokensUsed: number;
}

const Leaderboard: React.FC = () => {
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterLevel, setFilterLevel] = useState<number | null>(null);

  useEffect(() => {
    fetchLeaderboard();
  }, [filterLevel]);

  const fetchLeaderboard = async () => {
    setLoading(true);
    try {
      const url = filterLevel
        ? `/api/admin/leaderboard?level=${filterLevel}`
        : '/api/admin/leaderboard';
      const response = await fetch(url, { credentials: "include" });
      const data = await response.json();
      setLeaderboard(data);
    } catch (error) {
      console.error('Error fetching leaderboard:', error);
    } finally {
      setLoading(false);
    }
  };

  const formatTime = (seconds: number): string => {
    const hours = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    if (hours > 0) {
      return `${hours}h ${mins}m`;
    }
    return `${mins}m`;
  };

  const getMedalEmoji = (rank: number): string => {
    if (rank === 1) return '🥇';
    if (rank === 2) return '🥈';
    if (rank === 3) return '🥉';
    return `#${rank}`;
  };

  return (
    <div className="leaderboard-container">
      <div className="leaderboard-header">
        <div className="header-content">
          <h1>🏆 ING CTF Leaderboard</h1>
          <p>Security Challenge Rankings</p>
        </div>
      </div>

      <div className="leaderboard-filters">
        <button
          className={`filter-btn ${!filterLevel ? 'active' : ''}`}
          onClick={() => setFilterLevel(null)}
        >
          All Levels
        </button>
        {[1, 2, 3, 4, 5, 6, 7].map((level) => (
          <button
            key={level}
            className={`filter-btn ${filterLevel === level ? 'active' : ''}`}
            onClick={() => setFilterLevel(level)}
          >
            Level {level}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="leaderboard-content">
          <p className="loading">Loading leaderboard...</p>
        </div>
      ) : leaderboard.length === 0 ? (
        <div className="leaderboard-content">
          <p className="empty">No participants yet</p>
        </div>
      ) : (
        <div className="leaderboard-content">
          <div className="leaderboard-table-wrapper">
            <table className="leaderboard-table">
              <thead>
                <tr>
                  <th className="rank-col">Rank</th>
                  <th className="name-col">Name</th>
                  <th className="email-col">Email</th>
                  <th className="level-col">Level</th>
                  <th className="time-col">Time</th>
                  <th className="tokens-col">Tokens</th>
                  <th className="date-col">Completed</th>
                </tr>
              </thead>
              <tbody>
                {leaderboard.map((entry, index) => (
                  <tr key={index} className={`rank-${Math.min(entry.rank, 3)}`}>
                    <td className="rank-col">
                      <div className="rank-badge">{getMedalEmoji(entry.rank)}</div>
                    </td>
                    <td className="name-col">
                      <span className="name-text">{entry.name}</span>
                    </td>
                    <td className="email-col">{entry.email}</td>
                    <td className="level-col">
                      <span className="level-badge">Level {entry.maxLevelReached}</span>
                    </td>
                    <td className="time-col">{formatTime(entry.durationSeconds)}</td>
                    <td className="tokens-col">{(entry.totalTokensUsed / 1000).toFixed(1)}K</td>
                    <td className="date-col">
                      {entry.finishedAt
                        ? new Date(entry.finishedAt).toLocaleDateString()
                        : 'In Progress'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {leaderboard.length > 0 && (
            <div className="leaderboard-stats">
              <div className="stat-card">
                <h4>👥 Total Participants</h4>
                <p>{leaderboard.length}</p>
              </div>
              <div className="stat-card">
                <h4>⚡ Fastest Time</h4>
                <p>
                  {formatTime(
                    Math.min(...leaderboard.map((e) => e.durationSeconds))
                  )}
                </p>
              </div>
              <div className="stat-card">
                <h4>📊 Avg Level</h4>
                <p>
                  {(
                    leaderboard.reduce((sum, e) => sum + e.maxLevelReached, 0) /
                    leaderboard.length
                  ).toFixed(1)}
                </p>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default Leaderboard;
