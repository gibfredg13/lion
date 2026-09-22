import type { TvFeedItem } from './types';

interface Props {
  items: TvFeedItem[];
  enabled: boolean;
}

export default function TvFeedPanel({ items, enabled }: Props) {
  return (
    <div className="tv-panel">
      <div className="tv-panel-title">
        <span>Live attempts</span>
        {enabled && items.length > 0 && <span className="tv-panel-note">newest first</span>}
      </div>
      <div className="tv-panel-body">
        {/* Never an empty box when the feed is off - a blank panel reads as a broken app. */}
        {!enabled ? (
          <div className="tv-empty">🔒 Feed paused by the game master</div>
        ) : items.length === 0 ? (
          <div className="tv-empty">Nobody has spoken to Leo yet</div>
        ) : (
          <div className="tv-feed">
            {items.map((item) => (
              <div key={item.id} className={`tv-feed-item ${item.blocked ? 'blocked' : ''}`}>
                <div className="tv-feed-top">
                  <span className="tv-feed-name">{item.name}</span>
                  <span className="tv-feed-level">
                    {item.blocked ? `⚠ ${item.blockedBy ?? 'blocked'}` : `Level ${item.level}`}
                  </span>
                </div>
                <div className="tv-feed-prompt">💬 {item.prompt}</div>
                <div className="tv-feed-response">🦁 {item.response}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
