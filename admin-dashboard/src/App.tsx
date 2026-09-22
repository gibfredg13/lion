import React, { useState, useEffect } from 'react';
import { BrowserRouter, Route, Routes, Navigate } from 'react-router-dom';
import AdminLayout from './components/AdminLayout';
import LoginPage from './components/LoginPage';

interface AdminUser {
  userId: string;
  email: string;
  displayName: string;
  isAdmin: boolean;
}

export default function App() {
  const [user, setUser] = useState<AdminUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    fetch('/api/auth/me', { credentials: 'include' })
      .then(r => r.ok ? r.json() : null)
      .then(data => {
        if (data?.isAdmin) setUser(data);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  const handleLogin = async (email: string, password: string) => {
    setError('');
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ email, password })
    });
    if (!res.ok) {
      const data = await res.json().catch(() => ({}));
      setError(data.message || 'Login failed');
      return;
    }
    const data = await res.json();
    if (!data.isAdmin) {
      setError('Admin access required');
      return;
    }
    setUser(data);
  };

  const handleLogout = async () => {
    await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
    setUser(null);
  };

  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}>
      <span style={{ fontSize: '2rem' }}>🦁 Loading...</span>
    </div>
  );

  return (
    <BrowserRouter>
      <Routes>
        {!user ? (
          <>
            <Route path="/login" element={<LoginPage onLogin={handleLogin} error={error} />} />
            <Route path="*" element={<Navigate to="/login" replace />} />
          </>
        ) : (
          <Route path="*" element={<AdminLayout user={user} onLogout={handleLogout} />} />
        )}
      </Routes>
    </BrowserRouter>
  );
}
