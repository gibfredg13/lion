package com.github.bgalek.tv;

import java.time.Instant;
import java.util.List;

/**
 * Everything the projector shows, in one payload.
 * <p>
 * One request rather than four, for two reasons. Four polls land at four different instants, and
 * "42 players" beside funnel bars that sum to 39 is exactly the inconsistency an audience notices
 * on a three-metre screen. And one endpoint is one privacy boundary to audit rather than four.
 */
public record TvSnapshot(
        String eventName,
        String sessionLabel,
        Instant serverTime,
        List<TvBoardRow> board,
        List<TvFunnelBar> funnel,
        /** Empty, never null, when the game master has switched the feed off. */
        List<TvFeedItem> feed,
        /** So the panel can say it is hidden rather than simply look broken. */
        boolean feedEnabled,
        TvVitals vitals
) {
    /**
     * A player as the room sees them: a display name and a position. No email, no account id.
     *
     * @param carriedIn true when they brought this level in from a previous run rather than
     *                  earning it here, which is otherwise indistinguishable on the board.
     */
    public record TvBoardRow(int rank, String name, int level, long durationSeconds,
                             int score, int tokens, boolean finished, boolean carriedIn) {}

    /** @param enabled false tells the room a level is switched off, not that nobody is good enough. */
    public record TvFunnelBar(int level, int players, int attempts, boolean enabled) {}

    /**
     * One attempt, as theatre.
     * <p>
     * Both texts are truncated here rather than in the browser: Leo's reply is bounded only by the
     * level's token budget and would otherwise push every other row off the panel.
     */
    public record TvFeedItem(long id, String name, int level, String prompt, String response,
                             boolean blocked, String blockedBy, Instant at) {}

    /**
     * @param backendLabel the model's friendly name only. Never the base URL - that is an internal
     *                     LAN address, and a projector is a poor place to publish the network map.
     */
    public record TvVitals(int playersOnline, int playersTotal, long attemptsLastMinute,
                           long attemptsTotal, long tokensTotal,
                           String backendLabel, boolean backendHealthy, long backendLatencyMs) {}
}
