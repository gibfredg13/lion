import React, { useState, useEffect } from 'react';
import './Leaderboard.css';

interface LevelMilestone {
  level: number;
  reachedAt: string;
}

interface LeaderboardEntry {
  rank: number;
  name: string;
  currentLevel: number;
  maxLevelReached: number;
  score: number;
  finishedAt: string | null;
  startedAt: string;
  durationSeconds: number;
  totalTokensUsed: number;
  /** When they reached the level they are on now. Null if it predates the timeline. */
  levelReachedAt: string | null;
  /** Ascending by level; a milestone above LEVELS means the run was completed. */
  levelTimeline: LevelMilestone[];
}

// Fix 4: Also fetch from the public session-leaderboard endpoint for completed players

const LEVELS = [1, 2, 3, 4, 5, 6, 7];
const COLUMN_COUNT = 8;

const Leaderboard: React.FC = () => {
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterLevel, setFilterLevel] = useState<number | null>(null);
  const [expandedRank, setExpandedRank] = useState<number | null>(null);

  useEffect(() => {
    fetchLeaderboard();
  }, [filterLevel]);

  const fetchLeaderboard = async () => {
    setLoading(true);
    try {
      const url = filterLevel
        ? `/api/leaderboard/progress?level=${filterLevel}`
        : '/api/leaderboard/progress';
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

  /**
   * Date alone says nothing at a single-day event, where every row would read the same. The
   * time of day is the whole point of the column.
   */
  const formatMoment = (iso: string | null): string =>
    iso ? new Date(iso).toLocaleString() : '—';

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
          <h1>🏆 The Lion's Den — Leaderboard</h1>
          <p>Who can outsmart Leo?</p>
        </div>
      </div>

      <div className="leaderboard-filters">
        <button
          className={`filter-btn ${!filterLevel ? 'active' : ''}`}
          onClick={() => setFilterLevel(null)}
        >
          All Levels
        </button>
        {LEVELS.map((level) => (
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
                  <th className="level-col">Level</th>
                  <th className="reached-col">Reached At</th>
                  <th className="score-col">Score</th>
                  <th className="time-col">Time</th>
                  <th className="tokens-col">Tokens</th>
                  <th className="date-col">Completed</th>
                </tr>
              </thead>
              <tbody>
                {leaderboard.map((entry, index) => {
                  const timeline = entry.levelTimeline ?? [];
                  const expanded = expandedRank === entry.rank;
                  return (
                    <React.Fragment key={index}>
                      <tr
                        className={`rank-${Math.min(entry.rank, 3)} ${
                          timeline.length ? 'expandable' : ''
                        }`}
                        onClick={() =>
                          timeline.length &&
                          setExpandedRank(expanded ? null : entry.rank)
                        }
                      >
                        <td className="rank-col">
                          <div className="rank-badge">{getMedalEmoji(entry.rank)}</div>
                          {timeline.length > 0 && (
                            <span className="expand-caret" aria-hidden="true">
                              {expanded ? '▾' : '▸'}
                            </span>
                          )}
                        </td>
                        <td className="name-col">
                          <span className="name-text">{entry.name}</span>
                        </td>
                        <td className="level-col">
                          <span className="level-badge">Level {entry.maxLevelReached ?? entry.currentLevel}</span>
                        </td>
                        <td className="reached-col">{formatMoment(entry.levelReachedAt)}</td>
                        <td className="score-col">
                          {entry.score}
                        </td>
                        <td className="time-col">{formatTime(entry.durationSeconds)}</td>
                        <td className="tokens-col">{(entry.totalTokensUsed / 1000).toFixed(1)}K</td>
                        <td className="date-col">
                          {entry.finishedAt ? formatMoment(entry.finishedAt) : 'In Progress'}
                        </td>
                      </tr>
                      {expanded && (
                        <tr className="timeline-row">
                          <td colSpan={COLUMN_COUNT}>
                            <ol className="timeline">
                              {timeline.map((milestone) => (
                                <li key={milestone.level}>
                                  <span className="timeline-level">
                                    {/* advanceLevel writes maxLevel + 1 on the final win, so a
                                        milestone past the last trial is the finish, not a level. */}
                                    {milestone.level > LEVELS.length
                                      ? '🏁 Completed'
                                      : `Level ${milestone.level}`}
                                  </span>
                                  <span className="timeline-at">
                                    {formatMoment(milestone.reachedAt)}
                                  </span>
                                </li>
                              ))}
                            </ol>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })}
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
                <h4>🏆 Most Levels Cracked</h4>
                <p>
                  Level {Math.max(0, ...leaderboard.map(e => e.maxLevelReached))}
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
