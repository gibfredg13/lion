import React, { useState } from 'react';

interface Props {
  onLogin: (email: string, password: string) => Promise<void>;
  error: string;
}

export default function LoginPage({ onLogin, error }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    await onLogin(email, password);
    setLoading(false);
  };

  return (
    <div style={{
      minHeight: '100vh', background: 'linear-gradient(135deg, #ff6600 0%, #ff8c00 100%)',
      display: 'flex', alignItems: 'center', justifyContent: 'center'
    }}>
      <div style={{ width: '100%', maxWidth: '400px', padding: '20px' }}>
        <div style={{ textAlign: 'center', marginBottom: '30px' }}>
          <div style={{ fontSize: '3rem' }}>🦁</div>
          <h1 style={{ color: 'white', fontSize: '1.8rem', fontWeight: 700 }}>Admin War Room</h1>
          <p style={{ color: 'rgba(255,255,255,0.8)', marginTop: '8px' }}>The Lion's Den — Event Control</p>
        </div>
        <div style={{
          background: 'white', borderRadius: '12px', padding: '30px',
          boxShadow: '0 20px 60px rgba(0,0,0,0.3)'
        }}>
          {error && (
            <div style={{
              background: '#fee2e2', border: '1px solid #fca5a5', color: '#dc2626',
              padding: '10px 14px', borderRadius: '6px', marginBottom: '20px', fontSize: '0.9rem'
            }}>
              {error}
            </div>
          )}
          <form onSubmit={handleSubmit}>
            <div style={{ marginBottom: '16px' }}>
              <label style={{ display: 'block', marginBottom: '6px', color: '#374151', fontWeight: 600 }}>Email</label>
              <input type="email" required value={email} onChange={e => setEmail(e.target.value)}
                disabled={loading} placeholder="admin@example.com"
                style={{
                  width: '100%', padding: '10px 12px', border: '1px solid #d1d5db',
                  borderRadius: '6px', fontSize: '1rem', color: '#111'
                }}
              />
            </div>
            <div style={{ marginBottom: '24px' }}>
              <label style={{ display: 'block', marginBottom: '6px', color: '#374151', fontWeight: 600 }}>Password</label>
              <input type="password" required value={password} onChange={e => setPassword(e.target.value)}
                disabled={loading} placeholder="••••••••"
                style={{
                  width: '100%', padding: '10px 12px', border: '1px solid #d1d5db',
                  borderRadius: '6px', fontSize: '1rem', color: '#111'
                }}
              />
            </div>
            <button type="submit" disabled={loading} className="btn-primary" style={{ width: '100%', padding: '12px' }}>
              {loading ? '🦁 Entering...' : '🦁 Enter War Room'}
            </button>
          </form>
          <p style={{ textAlign: 'center', marginTop: '16px', color: '#6b7280', fontSize: '0.85rem' }}>
            Admin accounts only. First registered user is admin.
          </p>
        </div>
      </div>
    </div>
  );
}
