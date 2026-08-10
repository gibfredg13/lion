import React, { useState, useEffect } from 'react';
import './AdminDashboard.css';
import Analytics from './Analytics';

interface User {
  sessionId: string;
  email: string;
  displayName: string;
  createdAt: string;
  totalTokensUsed: number;
}

interface LeaderboardStats {
  totalParticipants: number;
  completedLevel7: number;
  averageTimeSeconds: number;
  currentlyActive: number;
}

const AdminDashboard: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'dashboard' | 'users' | 'llm' | 'leaderboard' | 'analytics' | 'login-history'>('dashboard');
  const [users, setUsers] = useState<User[]>([]);
  const [stats, setStats] = useState<LeaderboardStats | null>(null);
  const [loginHistory, setLoginHistory] = useState<any[]>([]);
  const [loginStats, setLoginStats] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchData();
  }, [activeTab]);

  const fetchData = async () => {
    setLoading(true);
    try {
      if (activeTab === 'users') {
        const response = await fetch('/api/admin/users', { credentials: "include" });
        const data = await response.json();
        setUsers(data);
      } else if (activeTab === 'leaderboard' || activeTab === 'dashboard') {
        const response = await fetch('/api/admin/leaderboard/stats', { credentials: "include" });
        const data = await response.json();
        setStats(data);
      } else if (activeTab === 'login-history') {
        const [historyRes, statsRes] = await Promise.all([
          fetch('/api/admin/login-history', { credentials: "include" }),
          fetch('/api/admin/login-stats', { credentials: "include" })
        ]);
        const historyData = await historyRes.json();
        const statsData = await statsRes.json();
        setLoginHistory(historyData);
        setLoginStats(statsData);
      }
    } catch (error) {
      console.error('Error fetching data:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="admin-dashboard">
      <div className="admin-header">
        <div className="admin-header-content">
          <h1>🏦 ING Security Challenge Admin</h1>
          <p>Manage users, LLM providers, and track CTF progress</p>
        </div>
      </div>

      <div className="admin-nav">
        <button
          className={`nav-btn ${activeTab === 'dashboard' ? 'active' : ''}`}
          onClick={() => setActiveTab('dashboard')}
        >
          📊 Dashboard
        </button>
        <button
          className={`nav-btn ${activeTab === 'users' ? 'active' : ''}`}
          onClick={() => setActiveTab('users')}
        >
          👥 Users ({users.length})
        </button>
        <button
          className={`nav-btn ${activeTab === 'login-history' ? 'active' : ''}`}
          onClick={() => setActiveTab('login-history')}
        >
          🔐 Login History
        </button>
        <button
          className={`nav-btn ${activeTab === 'llm' ? 'active' : ''}`}
          onClick={() => setActiveTab('llm')}
        >
          🤖 LLM Config
        </button>
        <button
          className={`nav-btn ${activeTab === 'leaderboard' ? 'active' : ''}`}
          onClick={() => setActiveTab('leaderboard')}
        >
          🏆 Leaderboard
        </button>
        <button
          className={`nav-btn ${activeTab === 'analytics' ? 'active' : ''}`}
          onClick={() => setActiveTab('analytics')}
        >
          📈 Analytics
        </button>
      </div>

      <div className="admin-content">
        {activeTab === 'dashboard' && <DashboardTab stats={stats} users={users} />}
        {activeTab === 'users' && <UsersTab users={users} loading={loading} />}
        {activeTab === 'login-history' && <LoginHistoryTab loginHistory={loginHistory} loginStats={loginStats} loading={loading} />}
        {activeTab === 'llm' && <LlmConfigTab />}
        {activeTab === 'leaderboard' && <LeaderboardTab stats={stats} loading={loading} />}
        {activeTab === 'analytics' && <Analytics />}
      </div>
    </div>
  );
};

const DashboardTab: React.FC<{ stats: LeaderboardStats | null; users: User[] }> = ({ stats, users }) => {
  const totalTokens = users.reduce((sum, user) => sum + user.totalTokensUsed, 0);

  return (
    <div className="tab-content dashboard-tab">
      <h2>Platform Overview</h2>
      <div className="metrics-grid">
        <div className="metric-card">
          <div className="metric-icon">👥</div>
          <div className="metric-info">
            <p>Total Participants</p>
            <h3>{stats?.totalParticipants || 0}</h3>
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-icon">✅</div>
          <div className="metric-info">
            <p>Completed Challenge</p>
            <h3>{stats?.completedLevel7 || 0}</h3>
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-icon">⏱️</div>
          <div className="metric-info">
            <p>Avg Time (min)</p>
            <h3>{stats?.averageTimeSeconds ? (stats.averageTimeSeconds / 60).toFixed(1) : '0'}</h3>
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-icon">🎯</div>
          <div className="metric-info">
            <p>Active Users</p>
            <h3>{stats?.currentlyActive || 0}</h3>
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-icon">🔢</div>
          <div className="metric-info">
            <p>Total Tokens Used</p>
            <h3>{(totalTokens / 1000).toFixed(1)}K</h3>
          </div>
        </div>
      </div>
    </div>
  );
};

const UsersTab: React.FC<{ users: User[]; loading: boolean }> = ({ users, loading }) => {
  if (loading) return <div className="tab-content">Loading users...</div>;

  return (
    <div className="tab-content users-tab">
      <h2>Registered Users & Usage</h2>
      <div className="users-table-wrapper">
        <table className="users-table">
          <thead>
            <tr>
              <th>Email</th>
              <th>Display Name</th>
              <th>Session ID</th>
              <th>Joined</th>
              <th>Tokens Used</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.sessionId}>
                <td className="email-cell">{user.email}</td>
                <td>{user.displayName}</td>
                <td className="session-id">{user.sessionId.substring(0, 8)}...</td>
                <td>{new Date(user.createdAt).toLocaleDateString()}</td>
                <td className="tokens-cell">{user.totalTokensUsed.toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

const LlmConfigTab: React.FC = () => {
  const [config] = useState({ currentProvider: 'azure', currentModel: 'gpt-4' });
  const [testPrompt, setTestPrompt] = useState('');
  const [testResult, setTestResult] = useState<any>(null);
  const [testing, setTesting] = useState(false);

  const handleTestPrompt = async () => {
    setTesting(true);
    try {
      const response = await fetch('/api/admin/llm/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: "include",
        body: JSON.stringify({
          prompt: testPrompt,
          systemPrompt: 'You are a helpful security-focused AI assistant.',
          model: config.currentModel,
          temperature: 0.7,
          maxTokens: 1000,
        }),
      });
      const data = await response.json();
      setTestResult(data);
    } catch (error) {
      console.error('Error testing prompt:', error);
    } finally {
      setTesting(false);
    }
  };

  return (
    <div className="tab-content llm-tab">
      <h2>LLM Provider Management</h2>

      <div className="llm-section">
        <h3>Current Configuration</h3>
        <div className="config-display">
          <div className="config-item">
            <label>Provider:</label>
            <span>{config.currentProvider}</span>
          </div>
          <div className="config-item">
            <label>Model:</label>
            <span>{config.currentModel}</span>
          </div>
        </div>
      </div>

      <div className="llm-section">
        <h3>Testing Playground</h3>
        <div className="playground">
          <textarea
            placeholder="Enter a test prompt..."
            value={testPrompt}
            onChange={(e) => setTestPrompt(e.target.value)}
            className="prompt-input"
            rows={6}
          />
          <button
            onClick={handleTestPrompt}
            disabled={testing || !testPrompt.trim()}
            className="test-btn"
          >
            {testing ? 'Testing...' : '🧪 Test Prompt'}
          </button>

          {testResult && (
            <div className={`test-result ${testResult.success ? 'success' : 'error'}`}>
              <h4>{testResult.success ? '✅ Success' : '❌ Error'}</h4>
              {testResult.success ? (
                <>
                  <p className="response">{testResult.response}</p>
                  <div className="token-stats">
                    <span>Input: {testResult.inputTokens}</span>
                    <span>Output: {testResult.outputTokens}</span>
                    <span>Total: {testResult.totalTokens}</span>
                    <span>Latency: {testResult.latencyMs}ms</span>
                  </div>
                </>
              ) : (
                <p className="error-msg">{testResult.error}</p>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

const LeaderboardTab: React.FC<{ stats: LeaderboardStats | null; loading: boolean }> = ({ stats, loading }) => {
  if (loading) return <div className="tab-content">Loading leaderboard...</div>;

  return (
    <div className="tab-content leaderboard-tab">
      <h2>CTF Challenge Leaderboard</h2>
      <div className="leaderboard-stats">
        <div className="stat">
          <h4>Total Participants</h4>
          <p>{stats?.totalParticipants || 0}</p>
        </div>
        <div className="stat">
          <h4>Completed Level 7</h4>
          <p>{stats?.completedLevel7 || 0}</p>
        </div>
        <div className="stat">
          <h4>Average Time</h4>
          <p>{stats?.averageTimeSeconds ? (stats.averageTimeSeconds / 60).toFixed(1) : '0'} min</p>
        </div>
      </div>
      <button className="reset-btn" onClick={() => alert('Reset leaderboard functionality')}>
        🔄 Reset Leaderboard
      </button>
    </div>
  );
};

const LoginHistoryTab: React.FC<{ loginHistory: any[]; loginStats: any; loading: boolean }> = ({ loginHistory, loginStats, loading }) => {
  if (loading) return <div className="tab-content">Loading login history...</div>;

  return (
    <div className="tab-content login-history-tab">
      <h2>🔐 Login History & Activity</h2>
      
      {loginStats && (
        <div className="login-stats">
          <div className="stat">
            <h4>Logins Today</h4>
            <p>{loginStats.loginsToday}</p>
          </div>
          <div className="stat">
            <h4>Logins This Week</h4>
            <p>{loginStats.loginsThisWeek}</p>
          </div>
          <div className="stat">
            <h4>Logins This Month</h4>
            <p>{loginStats.loginsThisMonth}</p>
          </div>
        </div>
      )}

      <div className="login-history-table">
        <h3>Recent Logins</h3>
        <table>
          <thead>
            <tr>
              <th>Email</th>
              <th>Login Time</th>
              <th>IP Address</th>
              <th>User Agent</th>
            </tr>
          </thead>
          <tbody>
            {loginHistory.map((entry: any) => (
              <tr key={entry.id}>
                <td>{entry.email}</td>
                <td>{new Date(entry.loginTime).toLocaleString()}</td>
                <td>{entry.ipAddress || 'N/A'}</td>
                <td className="user-agent">{entry.userAgent ? entry.userAgent.substring(0, 50) + '...' : 'N/A'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default AdminDashboard;
