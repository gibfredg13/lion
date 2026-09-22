import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useSession } from '../hooks/session';
import './Navigation.css';

const Navigation: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const session = useSession();

  const isActive = (path: string): boolean => location.pathname === path;

  const handleLogout = async () => {
    await fetch('/api/auth/logout', { method: 'POST', credentials: "include" });
    navigate('/login');
  };

  return (
    <nav className="ing-navigation">
      <div className="nav-container">
        <Link to="/" className="nav-logo">
          {/* The label collapses to the emoji alone on narrow phones - see Navigation.css. */}
          🦁 <span className="nav-logo-text">The Lion&apos;s Den</span>
        </Link>

        <div className="nav-links">
          <Link
            to="/"
            className={`nav-link ${isActive('/') ? 'active' : ''}`}
          >
            🎯 <span className="nav-link-text">Challenge</span>
          </Link>
          <Link
            to="/leaderboard"
            className={`nav-link ${isActive('/leaderboard') ? 'active' : ''}`}
          >
            🏆 <span className="nav-link-text">Leaderboard</span>
          </Link>
          {session.data?.isAdmin && (
            <Link
              to="/admin"
              className={`nav-link admin-link ${isActive('/admin') ? 'active' : ''}`}
            >
              ⚙️ <span className="nav-link-text">Admin</span>
            </Link>
          )}
          {session.data?.displayName && (
            <div className="nav-user-section">
              <span className="nav-user-name">{session.data.displayName}</span>
              <button onClick={handleLogout} className="nav-logout-btn">
                Logout
              </button>
            </div>
          )}
        </div>
      </div>
    </nav>
  );
};

export default Navigation;
