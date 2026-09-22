import { useState, useEffect, useCallback, useRef } from 'react';
import type { TvSnapshot } from './types';

const POLL_MS = 5000;
/** Six missed polls. Long enough to ride out a blip, short enough to catch a dead backend. */
const STALE_MS = 30000;

/**
 * Polls the projector payload and, unlike every other fetch in this codebase, admits when it has
 * stopped working.
 *
 * Every other poller here ends `.catch(() => {})`, which on an unattended screen is the difference
 * between "the event looks quiet" and "the backend has been down for forty minutes". The board
 * would keep showing a perfectly plausible hour-old picture.
 */
export function useTvSnapshot() {
  const [snapshot, setSnapshot] = useState<TvSnapshot | null>(null);
  const [lastOk, setLastOk] = useState<number>(0);
  const [now, setNow] = useState<number>(Date.now());
  const started = useRef<number>(Date.now());

  const fetchSnapshot = useCallback(async () => {
    try {
      const res = await fetch('/api/tv/snapshot');
      // Checked explicitly: a 500 with an HTML body either throws inside .json() or resolves to
      // something useless, and neither was distinguishable from success.
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setSnapshot(await res.json());
      setLastOk(Date.now());
    } catch {
      // Swallowed on purpose - the staleness below is how a failure becomes visible.
    }
  }, []);

  useEffect(() => {
    fetchSnapshot();
    const poll = setInterval(fetchSnapshot, POLL_MS);
    const tick = setInterval(() => setNow(Date.now()), 1000);
    // A screen that was asleep otherwise shows a twenty-minute-old board until the next poll.
    const onVisible = () => { if (!document.hidden) fetchSnapshot(); };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      clearInterval(poll);
      clearInterval(tick);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [fetchSnapshot]);

  const reference = lastOk || started.current;
  const staleMs = now - reference;
  return { snapshot, stale: staleMs > STALE_MS, staleMs };
}
