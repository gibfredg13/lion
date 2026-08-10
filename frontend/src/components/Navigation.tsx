import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import './Navigation.css';

const Navigation: React.FC = () => {
  const location = useLocation();

  const isActive = (path: string): boolean => location.pathname === path;

  return (
    <nav className="ing-navigation">
      <div className="nav-container">
        <Link to="/" className="nav-logo">
          🏦 ING Security Challenge
        </Link>

        <div className="nav-links">
          <Link
            to="/"
            className={`nav-link ${isActive('/') ? 'active' : ''}`}
          >
            🎯 Challenge
          </Link>
          <Link
            to="/leaderboard"
            className={`nav-link ${isActive('/leaderboard') ? 'active' : ''}`}
          >
            🏆 Leaderboard
          </Link>
          <Link
            to="/admin"
            className={`nav-link admin-link ${isActive('/admin') ? 'active' : ''}`}
          >
            ⚙️ Admin
          </Link>
        </div>
      </div>
    </nav>
  );
};

export default Navigation;
