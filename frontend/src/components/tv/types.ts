/** Mirrors backend TvSnapshot. A player here is a display name and a position, nothing more. */
export interface TvBoardRow {
  rank: number;
  name: string;
  level: number;
  durationSeconds: number;
  score: number;
  tokens: number;
  finished: boolean;
  /** They brought this level in from a previous run rather than earning it here. */
  carriedIn: boolean;
}

export interface TvFunnelBar {
  level: number;
  players: number;
  attempts: number;
  enabled: boolean;
}

export interface TvFeedItem {
  id: number;
  name: string;
  level: number;
  prompt: string;
  response: string;
  blocked: boolean;
  blockedBy: string | null;
  at: string | null;
}

export interface TvVitals {
  playersOnline: number;
  playersTotal: number;
  attemptsLastMinute: number;
  attemptsTotal: number;
  tokensTotal: number;
  backendLabel: string | null;
  backendHealthy: boolean;
  backendLatencyMs: number;
}

export interface TvSnapshot {
  eventName: string;
  sessionLabel: string;
  serverTime: string;
  board: TvBoardRow[];
  funnel: TvFunnelBar[];
  feed: TvFeedItem[];
  feedEnabled: boolean;
  vitals: TvVitals;
}
