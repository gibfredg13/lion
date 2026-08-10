import React, { useEffect, useState, useRef } from 'react';
import './RealtimeBreachDashboard.css';

interface GuardrailBreach {
  id: string;
  sessionId: string;
  email: string;
  displayName: string;
  level: number;
  prompt: string;
  response: string;
  breachType: string;
  description: string;
  timestamp: string;
}

interface BreachStatistics {
  totalBreaches: number;
  byType: Record<string, number>;
  byLevel: Record<number, number>;
  byUser: Record<string, number>;
  timestamp: string;
}

const RealtimeBreachDashboard: React.FC = () => {
  const [breaches, setBreaches] = useState<GuardrailBreach[]>([]);
  const [statistics, setStatistics] = useState<BreachStatistics | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [autoScroll, setAutoScroll] = useState(true);
  const stompClientRef = useRef<any>(null);
  const feedEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Fetch initial data
    fetchBreaches();
    fetchStatistics();

    // Connect to WebSocket
    connectWebSocket();

    // Polling for REST API updates (backup if WebSocket fails)
    const pollInterval = setInterval(() => {
      fetchBreaches();
      fetchStatistics();
    }, 2000);

    return () => {
      clearInterval(pollInterval);
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
      }
    };
  }, []);

  useEffect(() => {
    if (autoScroll && feedEndRef.current) {
      feedEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [breaches, autoScroll]);

  const fetchBreaches = async () => {
    try {
      const response = await fetch('/api/realtime/breaches/recent/50');
      const data = await response.json();
      setBreaches(data);
    } catch (error) {
      console.error('Error fetching breaches:', error);
    }
  };

  const fetchStatistics = async () => {
    try {
      const response = await fetch('/api/realtime/statistics');
      const data = await response.json();
      setStatistics(data);
    } catch (error) {
      console.error('Error fetching statistics:', error);
    }
  };

  const connectWebSocket = () => {
    try {
      // Use standard WebSocket with fallback to SockJS
      const url = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/breaches`;
      
      const SockJS = (window as any).SockJS;
      const Stomp = (window as any).Stomp;

      if (typeof SockJS !== 'undefined' && typeof Stomp !== 'undefined') {
        const socket = new SockJS(url);
        const stompClient = Stomp.over(socket);

        stompClient.connect({}, () => {
          console.log('WebSocket connected');
          setIsConnected(true);

          stompClient.subscribe('/topic/breaches', (message: any) => {
            try {
              const breach = JSON.parse(message.body);
              setBreaches((prev) => [breach, ...prev].slice(0, 50));
              fetchStatistics();
            } catch (error) {
              console.error('Error parsing WebSocket message:', error);
            }
          });
        }, (error: any) => {
          console.error('WebSocket connection error:', error);
          setIsConnected(false);
        });

        stompClientRef.current = stompClient;
      }
    } catch (error) {
      console.error('WebSocket connection failed, using polling:', error);
      setIsConnected(false);
    }
  };

  const getBreach TypeColor = (type: string): string => {
    switch (type) {
      case 'OUTPUT_FILTER':
        return '#ff0000'; // Red - critical
      case 'PROMPT_INJECTION':
        return '#ff6600'; // Orange - high
      case 'INPUT_FILTER':
        return '#ffaa00'; // Yellow-orange - medium
      case 'CONTENT_POLICY':
        return '#ff0000'; // Red
      case 'RATE_LIMIT':
        return '#ffdd00'; // Yellow
      default:
        return '#cccccc'; // Gray
    }
  };

  const getBreachTypeSeverity = (type: string): string => {
    switch (type) {
      case 'OUTPUT_FILTER':
        return 'CRITICAL';
      case 'PROMPT_INJECTION':
        return 'HIGH';
      case 'INPUT_FILTER':
        return 'MEDIUM';
      default:
        return 'LOW';
    }
  };

  const formatTime = (timestamp: string): string => {
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
  };

  const topBreaches = statistics?.byUser
    ? Object.entries(statistics.byUser)
        .sort(([, a], [, b]) => b - a)
        .slice(0, 5)
    : [];

  return (
    <div className="realtime-breach-dashboard">
      <div className="breach-dashboard-header">
        <h1>🚨 Real-Time Guardrail Breach Monitor</h1>
        <div className="connection-status">
          <span className={`status-indicator ${isConnected ? 'connected' : 'disconnected'}`} />
          <span className="status-text">
            {isConnected ? 'Live WebSocket Connected' : 'Using Poll (WebSocket unavailable)'}
          </span>
        </div>
      </div>

      <div className="breach-dashboard-content">
        {/* Statistics Row */}
        <div className="statistics-row">
          <div className="stat-card total">
            <h3>Total Breaches Detected</h3>
            <p className="stat-value">{statistics?.totalBreaches || 0}</p>
          </div>

          <div className="stat-card">
            <h3>By Type</h3>
            <div className="stat-items">
              {statistics?.byType && Object.entries(statistics.byType).map(([type, count]) => (
                <div key={type} className="stat-item">
                  <span className="type-label">{type}</span>
                  <span className="type-count">{count}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="stat-card">
            <h3>By Level</h3>
            <div className="stat-items">
              {statistics?.byLevel && Object.entries(statistics.byLevel).map(([level, count]) => (
                <div key={level} className="stat-item">
                  <span className="level-label">Level {level}</span>
                  <span className="level-count">{count}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="stat-card top-users">
            <h3>Top Breach Attempts</h3>
            <div className="top-users-list">
              {topBreaches.map(([email, count], idx) => (
                <div key={email} className="top-user-item">
                  <span className="rank">#{idx + 1}</span>
                  <span className="user-email">{email}</span>
                  <span className="attempt-count">{count} attempts</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Live Feed Controls */}
        <div className="feed-controls">
          <h2>📊 Live Breach Feed</h2>
          <div className="controls">
            <button
              className={`auto-scroll-btn ${autoScroll ? 'active' : ''}`}
              onClick={() => setAutoScroll(!autoScroll)}
            >
              {autoScroll ? '✓ Auto-Scroll' : 'Paused'}
            </button>
            <button
              className="refresh-btn"
              onClick={() => {
                fetchBreaches();
                fetchStatistics();
              }}
            >
              🔄 Refresh
            </button>
          </div>
        </div>

        {/* Breach Feed */}
        <div className="breach-feed">
          {breaches.length === 0 ? (
            <div className="no-breaches">No breaches detected yet. Waiting for user attempts...</div>
          ) : (
            breaches.map((breach, idx) => (
              <div key={breach.id} className={`breach-item severity-${getBreachTypeSeverity(breach.breachType).toLowerCase()}`}>
                <div className="breach-header">
                  <div className="breach-meta">
                    <span className="breach-time">{formatTime(breach.timestamp)}</span>
                    <span className="breach-user">{breach.displayName}</span>
                    <span className="breach-email">{breach.email}</span>
                  </div>
                  <div className="breach-indicators">
                    <span className="breach-level">Level {breach.level}</span>
                    <span
                      className="breach-type"
                      style={{ backgroundColor: getBreachTypeColor(breach.breachType) }}
                    >
                      {breach.breachType}
                    </span>
                    <span className="breach-severity">{getBreachTypeSeverity(breach.breachType)}</span>
                  </div>
                </div>

                <div className="breach-description">{breach.description}</div>

                <div className="breach-details">
                  {breach.prompt && (
                    <div className="detail-item">
                      <span className="detail-label">Prompt Attempted:</span>
                      <span className="detail-text">{breach.prompt.substring(0, 100)}...</span>
                    </div>
                  )}
                  {breach.response && (
                    <div className="detail-item">
                      <span className="detail-label">AI Response:</span>
                      <span className="detail-text">{breach.response.substring(0, 100)}...</span>
                    </div>
                  )}
                </div>
              </div>
            ))
          )}
          <div ref={feedEndRef} />
        </div>
      </div>
    </div>
  );
};

export default RealtimeBreachDashboard;
