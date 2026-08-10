import React, { useState, useEffect } from 'react';
import './Analytics.css';

interface AttackData {
  name: string;
  count: number;
  severity: string;
  percentage: number;
}

interface AttackHeatmap {
  attackTypes: AttackData[];
  totalAttacks: number;
}

interface TokenUsageByUser {
  displayName: string;
  email: string;
  prompts: number;
  inputTokens: number;
  outputTokens: number;
}

interface TimeSeriesData {
  date: string;
  tokens: number;
}

interface TopTrick {
  type: string;
  description: string;
  attempts: number;
  severity: string;
  usersAttempted: number;
}

const Analytics: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'heatmap' | 'timeseries' | 'users' | 'tricks'>('heatmap');
  const [heatmapData, setHeatmapData] = useState<AttackHeatmap | null>(null);
  const [timeseriesData, setTimeseriesData] = useState<TimeSeriesData[]>([]);
  const [usersData, setUsersData] = useState<TokenUsageByUser[]>([]);
  const [tricksData, setTricksData] = useState<TopTrick[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchAnalytics();
  }, [activeTab]);

  const fetchAnalytics = async () => {
    setLoading(true);
    try {
      if (activeTab === 'heatmap') {
        const response = await fetch('/api/admin/analytics/attack-heatmap');
        const data = await response.json();
        setHeatmapData(data);
      } else if (activeTab === 'timeseries') {
        const response = await fetch('/api/admin/analytics/token-usage-timeseries');
        const data = await response.json();
        setTimeseriesData(data);
      } else if (activeTab === 'users') {
        const response = await fetch('/api/admin/analytics/token-usage-by-user');
        const data = await response.json();
        setUsersData(data);
      } else if (activeTab === 'tricks') {
        const response = await fetch('/api/admin/analytics/top-tricks');
        const data = await response.json();
        setTricksData(data);
      }
    } catch (error) {
      console.error('Error fetching analytics:', error);
    } finally {
      setLoading(false);
    }
  };

  const exportData = async (format: 'csv' | 'json') => {
    try {
      const response = await fetch(`/api/admin/export/${format}`);
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `ctf-data.${format}`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (error) {
      console.error(`Error exporting ${format}:`, error);
    }
  };

  const getSeverityColor = (severity: string): string => {
    switch (severity) {
      case 'CRITICAL': return '#ff0000';
      case 'HIGH': return '#ff6600';
      case 'MEDIUM': return '#ffaa00';
      case 'LOW': return '#ffdd00';
      default: return '#cccccc';
    }
  };

  return (
    <div className="analytics-container">
      <div className="analytics-header">
        <h2>📊 Attack Analytics & Insights</h2>
        <div className="export-buttons">
          <button className="export-btn csv" onClick={() => exportData('csv')}>
            📥 Export CSV
          </button>
          <button className="export-btn json" onClick={() => exportData('json')}>
            📥 Export JSON
          </button>
        </div>
      </div>

      <div className="analytics-tabs">
        <button
          className={`tab-btn ${activeTab === 'heatmap' ? 'active' : ''}`}
          onClick={() => setActiveTab('heatmap')}
        >
          🔥 Attack Heatmap
        </button>
        <button
          className={`tab-btn ${activeTab === 'timeseries' ? 'active' : ''}`}
          onClick={() => setActiveTab('timeseries')}
        >
          📈 Token Trends
        </button>
        <button
          className={`tab-btn ${activeTab === 'users' ? 'active' : ''}`}
          onClick={() => setActiveTab('users')}
        >
          👤 User Stats
        </button>
        <button
          className={`tab-btn ${activeTab === 'tricks' ? 'active' : ''}`}
          onClick={() => setActiveTab('tricks')}
        >
          🎯 Top Tricks
        </button>
      </div>

      {loading ? (
        <div className="analytics-content">
          <p>Loading analytics...</p>
        </div>
      ) : (
        <div className="analytics-content">
          {activeTab === 'heatmap' && <HeatmapTab data={heatmapData} getSeverityColor={getSeverityColor} />}
          {activeTab === 'timeseries' && <TimeseriesTab data={timeseriesData} />}
          {activeTab === 'users' && <UsersTab data={usersData} />}
          {activeTab === 'tricks' && <TricksTab data={tricksData} getSeverityColor={getSeverityColor} />}
        </div>
      )}
    </div>
  );
};

const HeatmapTab: React.FC<{ data: AttackHeatmap | null; getSeverityColor: (s: string) => string }> = ({ data, getSeverityColor }) => {
  if (!data) return <div>No attack data available</div>;

  const maxCount = Math.max(...data.attackTypes.map(a => a.count), 1);

  return (
    <div className="heatmap-tab">
      <h3>🔥 Attack Pattern Frequency</h3>
      <div className="heatmap-stats">
        <div className="stat-card">
          <h4>Total Attacks Detected</h4>
          <p className="big-number">{data.totalAttacks}</p>
        </div>
        <div className="stat-card">
          <h4>Attack Types</h4>
          <p className="big-number">{data.attackTypes.length}</p>
        </div>
      </div>

      <div className="heatmap-grid">
        {data.attackTypes.map((attack, idx) => {
          const width = (attack.count / maxCount) * 100;
          return (
            <div key={idx} className="heatmap-item">
              <div className="heatmap-header">
                <span className="attack-name">{attack.name}</span>
                <span className={`severity-badge ${attack.severity.toLowerCase()}`}>
                  {attack.severity}
                </span>
              </div>
              <div className="heatmap-bar-container">
                <div
                  className="heatmap-bar"
                  style={{
                    width: `${width}%`,
                    backgroundColor: getSeverityColor(attack.severity),
                  }}
                />
              </div>
              <div className="heatmap-info">
                <span>{attack.count} attempts</span>
                <span>{attack.percentage.toFixed(1)}%</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

const TimeseriesTab: React.FC<{ data: TimeSeriesData[] }> = ({ data }) => {
  if (data.length === 0) return <div>No time series data available</div>;

  const maxTokens = Math.max(...data.map(d => d.tokens), 1);

  return (
    <div className="timeseries-tab">
      <h3>📈 Daily Token Usage Trend</h3>
      <div className="chart-container">
        <div className="chart">
          {data.map((point, idx) => {
            const height = (point.tokens / maxTokens) * 300;
            return (
              <div key={idx} className="chart-bar-wrapper">
                <div
                  className="chart-bar"
                  style={{ height: `${height}px` }}
                  title={`${point.date}: ${point.tokens} tokens`}
                />
                <div className="chart-label">{point.date}</div>
                <div className="chart-value">{(point.tokens / 1000).toFixed(1)}K</div>
              </div>
            );
          })}
        </div>
      </div>
      <div className="chart-stats">
        <p>Total tokens used: <strong>{data.reduce((sum, d) => sum + d.tokens, 0).toLocaleString()}</strong></p>
        <p>Average per day: <strong>{(data.reduce((sum, d) => sum + d.tokens, 0) / data.length).toLocaleString()}</strong></p>
      </div>
    </div>
  );
};

const UsersTab: React.FC<{ data: TokenUsageByUser[] }> = ({ data }) => {
  if (data.length === 0) return <div>No user data available</div>;

  return (
    <div className="users-tab">
      <h3>👤 Token Usage by User</h3>
      <div className="users-table-wrapper">
        <table className="users-table">
          <thead>
            <tr>
              <th>User</th>
              <th>Email</th>
              <th>Prompts</th>
              <th>Input Tokens</th>
              <th>Output Tokens</th>
              <th>Total Tokens</th>
            </tr>
          </thead>
          <tbody>
            {data.map((user, idx) => (
              <tr key={idx}>
                <td className="user-name">{user.displayName}</td>
                <td className="user-email">{user.email}</td>
                <td className="center">{user.prompts}</td>
                <td className="center">{user.inputTokens.toLocaleString()}</td>
                <td className="center">{user.outputTokens.toLocaleString()}</td>
                <td className="total-tokens">{(user.inputTokens + user.outputTokens).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

const TricksTab: React.FC<{ data: TopTrick[]; getSeverityColor: (s: string) => string }> = ({ data, getSeverityColor }) => {
  if (data.length === 0) return <div>No attack tricks detected</div>;

  return (
    <div className="tricks-tab">
      <h3>🎯 Top Attack Tricks Used</h3>
      <div className="tricks-grid">
        {data.map((trick, idx) => (
          <div key={idx} className="trick-card" style={{ borderLeftColor: getSeverityColor(trick.severity) }}>
            <div className="trick-header">
              <h4>{trick.type}</h4>
              <span className={`severity-badge ${trick.severity.toLowerCase()}`}>
                {trick.severity}
              </span>
            </div>
            <p className="trick-description">{trick.description}</p>
            <div className="trick-stats">
              <div className="trick-stat">
                <span className="stat-label">Attempts:</span>
                <span className="stat-value">{trick.attempts}</span>
              </div>
              <div className="trick-stat">
                <span className="stat-label">Users:</span>
                <span className="stat-value">{trick.usersAttempted}</span>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default Analytics;
