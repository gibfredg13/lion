package com.github.bgalek.admin;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Leaderboards, built from `users` joined to the attempt log.
 * <p>
 * Two views, because a completion-only board stays empty for most of an event - most players never
 * beat all seven levels, so nothing would ever appear on the projector:
 * <ul>
 *   <li><b>progress</b> - every player ranked by how far they got, then how fast. Populated from
 *       the first minute.</li>
 *   <li><b>hall of fame</b> - only players who cleared every level, ranked by total time.</li>
 * </ul>
 * Duration is measured from a player's first attempt to their last, not from session creation:
 * a re-login used to reset the clock and hand someone an artificially good time.
 */
public class AdminLeaderboardService {

    private static final Logger logger = getLogger(AdminLeaderboardService.class);

    private final JdbcClient jdbcClient;
    private final int maxLevel;
    /** The run to report on when a caller does not name one. */
    private final java.util.function.Supplier<String> activeGameSessionId;

    public AdminLeaderboardService(JdbcClient jdbcClient, int maxLevel,
                                   java.util.function.Supplier<String> activeGameSessionId) {
        this.jdbcClient = jdbcClient;
        this.maxLevel = maxLevel;
        this.activeGameSessionId = activeGameSessionId;
    }

    /**
     * The session filter is part of the JOIN, not a WHERE clause.
     * <p>
     * In a WHERE it would silently turn this LEFT JOIN into an INNER one and drop every player who
     * has not attempted anything in this run - which is exactly the population a carry-on session
     * starts with. They would vanish from the board rather than appear at the level they carried.
     */
    private static final String BASE_QUERY = """
            SELECT u.id                                   AS user_id,
                   u.display_name                         AS display_name,
                   u.email                                AS email,
                   u.current_level                        AS current_level,
                   COALESCE(MIN(l.created_at), u.created_at) AS started_at,
                   MAX(l.created_at)                      AS last_seen,
                   COALESCE(SUM(l.input_tokens + l.output_tokens), 0) AS total_tokens,
                   COUNT(l.id)                            AS attempts
            FROM users u
            LEFT JOIN logs l ON l.user_id = u.id AND l.game_session_id = :gameSessionId
            GROUP BY u.id, u.display_name, u.email, u.current_level, u.created_at
            """;

    /**
     * Every player's whole timeline in one round trip, folded into the ranked rows in memory. Not
     * joined into BASE_QUERY: that already groups over `logs`, and a second one-to-many join would
     * multiply the token sum and the attempt count. Not queried per row either - the projector
     * board refreshes every ten seconds, so that would be a round trip per player per refresh.
     */
    /** A plain WHERE here: there is no outer join to collapse, and an empty timeline is correct. */
    private static final String TIMELINE_QUERY = """
            SELECT user_id, level, MIN(reached_at) AS reached_at
            FROM level_progress
            WHERE user_id IS NOT NULL AND game_session_id = :gameSessionId
            GROUP BY user_id, level
            """;

    public List<AdminLeaderboardResponse> getLeaderboard() {
        return getLeaderboard(activeGameSessionId.get());
    }

    /** @param gameSessionId a past run, for the admin dashboard's history picker. */
    public List<AdminLeaderboardResponse> getLeaderboard(String gameSessionId) {
        return rank(query(gameSessionId), null, gameSessionId);
    }

    /**
     * True when the caller is browsing a run that is no longer live.
     * <p>
     * It changes where a player's level comes from. {@code users.current_level} says where someone
     * is <em>now</em>, which is the right answer for the run in progress and the wrong one for a
     * finished run - starting a new session in reset mode sets that column to 1 for everybody, so
     * a past run would otherwise redraw itself with the whole field back at level one.
     */
    /** The level a row should be reported at, given whether the run is live or finished. */
    private static int effectiveLevel(Row r, Map<String, NavigableMap<Integer, Instant>> timelines,
                                      boolean historical) {
        if (!historical) return r.currentLevel;
        NavigableMap<Integer, Instant> milestones = timelines.get(r.userId);
        return milestones == null || milestones.isEmpty() ? r.currentLevel : Math.max(milestones.lastKey(), 1);
    }

    private boolean historical(String gameSessionId) {
        String active = activeGameSessionId.get();
        return active != null && !active.equals(gameSessionId);
    }

    /** Players who have reached at least the given level. */
    public List<AdminLeaderboardResponse> getLeaderboardByProgress(int level) {
        return getLeaderboardByProgress(level, activeGameSessionId.get());
    }

    public List<AdminLeaderboardResponse> getLeaderboardByProgress(int level, String gameSessionId) {
        return rank(query(gameSessionId), level, gameSessionId);
    }

    /** Only players who cleared every level - completion is current_level > maxLevel. */
    public List<AdminLeaderboardResponse> getHallOfFame() {
        return getHallOfFame(activeGameSessionId.get());
    }

    public List<AdminLeaderboardResponse> getHallOfFame(String gameSessionId) {
        return rank(query(gameSessionId), maxLevel + 1, gameSessionId);
    }

    private List<Row> query(String gameSessionId) {
        try {
            return jdbcClient.sql(BASE_QUERY).param("gameSessionId", gameSessionId).query((rs, n) -> new Row(
                    rs.getString("user_id"),
                    rs.getString("display_name"),
                    rs.getString("email"),
                    rs.getInt("current_level"),
                    rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant(),
                    rs.getTimestamp("last_seen") == null ? null : rs.getTimestamp("last_seen").toInstant(),
                    rs.getInt("total_tokens"),
                    rs.getInt("attempts")
            )).list();
        } catch (Exception e) {
            logger.error("Failed to load leaderboard", e);
            return List.of();
        }
    }

    private Map<String, NavigableMap<Integer, Instant>> levelTimelines(String gameSessionId) {
        Map<String, NavigableMap<Integer, Instant>> byUser = new HashMap<>();
        try {
            jdbcClient.sql(TIMELINE_QUERY).param("gameSessionId", gameSessionId).query((rs, n) -> new Milestone(
                    rs.getString("user_id"),
                    rs.getInt("level"),
                    rs.getTimestamp("reached_at") == null ? null : rs.getTimestamp("reached_at").toInstant()
            )).list().forEach(m -> {
                if (m.userId() == null || m.reachedAt() == null) return;
                byUser.computeIfAbsent(m.userId(), k -> new TreeMap<>()).put(m.level(), m.reachedAt());
            });
        } catch (Exception e) {
            // A missing or unreadable table must blank the new columns, not the whole board.
            logger.error("Failed to load level timeline", e);
        }
        return byUser;
    }

    private List<AdminLeaderboardResponse> rank(List<Row> rows, Integer minLevel, String gameSessionId) {
        Map<String, NavigableMap<Integer, Instant>> timelines = levelTimelines(gameSessionId);
        boolean historical = historical(gameSessionId);
        List<Row> filtered = new ArrayList<>();
        for (Row r : rows) {
            int level = effectiveLevel(r, timelines, historical);
            // Registered but never played - in this run. A past run excludes anyone who sat it out.
            if (r.attempts == 0 && level <= 1) continue;
            if (minLevel != null && level < minLevel) continue;
            filtered.add(r);
        }
        // Furthest first, then fastest. Someone still playing ranks below an equal who finished.
        filtered.sort((a, b) -> {
            int byLevel = Integer.compare(effectiveLevel(b, timelines, historical),
                    effectiveLevel(a, timelines, historical));
            if (byLevel != 0) return byLevel;
            // A player carried into this run who has not attempted anything yet has a duration of
            // zero, and zero is the fastest time there is - so without this they would lead their
            // level tier on the projector for doing nothing at all. Unplayed rows go last.
            boolean aIdle = a.attempts() == 0;
            boolean bIdle = b.attempts() == 0;
            if (aIdle != bIdle) return aIdle ? 1 : -1;
            return Long.compare(a.durationSeconds(), b.durationSeconds());
        });
        List<AdminLeaderboardResponse> out = new ArrayList<>();
        int rank = 1;
        for (Row r : filtered) {
            NavigableMap<Integer, Instant> milestones =
                    timelines.getOrDefault(r.userId, Collections.emptyNavigableMap());
            // floorEntry, not get: a player whose current level predates the timeline (a backfill
            // gap) still gets the most recent milestone at or below it rather than nothing.
            // For a finished run the level reached is the high-water mark of that run's timeline.
            // For the live run it is the account's current level, because a player sitting on a
            // level they have not cleared yet has no milestone above it.
            int level = effectiveLevel(r, timelines, historical);
            Map.Entry<Integer, Instant> current = milestones.floorEntry(level);
            out.add(new AdminLeaderboardResponse(
                    rank++,
                    r.displayName,
                    r.email,
                    level,
                    level,
                    r.startedAt,
                    level > maxLevel ? r.lastSeen : null,
                    r.durationSeconds(),
                    r.totalTokens,
                    score(r, level),
                    current == null ? null : current.getValue(),
                    milestones.entrySet().stream()
                            .map(e -> new LevelMilestone(e.getKey(), e.getValue()))
                            .toList(),
                    r.attempts
            ));
        }
        return out;
    }

    /**
     * Levels cleared dominate; time is only a tie-breaker. Scoring lives here rather than in the
     * browser so the page, the projector view and the admin tab cannot disagree.
     */
    private int score(Row r, int level) {
        int levelsCleared = Math.max(0, level - 1);
        long speedBonus = Math.max(0, 600 - r.durationSeconds()) / 10;
        return (int) (levelsCleared * 100L + speedBonus);
    }

    public AdminLeaderboardStatsResponse getStats() {
        return getStats(activeGameSessionId.get());
    }

    public AdminLeaderboardStatsResponse getStats(String gameSessionId) {
        List<AdminLeaderboardResponse> all = getLeaderboard(gameSessionId);
        int completed = 0;
        long totalSeconds = 0;
        int active = 0;
        Instant cutoff = Instant.now().minusSeconds(600);
        List<Row> rows = query(gameSessionId);
        for (Row r : rows) {
            if (r.lastSeen != null && r.lastSeen.isAfter(cutoff)) active++;
        }
        for (AdminLeaderboardResponse e : all) {
            if (e.maxLevelReached() > maxLevel) {
                completed++;
                totalSeconds += e.durationSeconds();
            }
        }
        return new AdminLeaderboardStatsResponse(
                all.size(),
                completed,
                completed == 0 ? 0 : totalSeconds / completed,
                active
        );
    }

    /**
     * Erases every run's player data. Not the way to start a new event any more - that is
     * {@link com.github.bgalek.session.GameSessionService#startNewSession}, which keeps everything
     * and simply stops counting it. This remains for the one job it is still right for: clearing
     * test data before the real thing.
     */
    public void resetLeaderboard() {
        try {
            jdbcClient.sql("DELETE FROM leaderboard").update();
            // Without this an admin reset leaves every old timeline attached to a level of 1.
            jdbcClient.sql("DELETE FROM level_progress").update();
            jdbcClient.sql("UPDATE users SET current_level = 1").update();
            jdbcClient.sql("DELETE FROM logs").update();
            logger.warn("Leaderboard reset: all player progress and attempt logs cleared");
        } catch (Exception e) {
            logger.error("Failed to reset leaderboard", e);
        }
    }

    private record Milestone(String userId, int level, Instant reachedAt) {
    }

    private record Row(String userId, String displayName, String email, int currentLevel,
                       Instant startedAt, Instant lastSeen, int totalTokens, int attempts) {
        long durationSeconds() {
            if (startedAt == null || lastSeen == null) return 0;
            return Math.max(0, lastSeen.getEpochSecond() - startedAt.getEpochSecond());
        }
    }

    /**
     * One point on a player's timeline. A list of these rather than a map keyed by level, because
     * Jackson stringifies integer map keys - awkward to type in the browser and easy to mis-order.
     * The list arrives ascending by level.
     */
    public record LevelMilestone(int level, Instant reachedAt) {
    }

    public record AdminLeaderboardResponse(
            int rank,
            String name,
            String email,
            int currentLevel,
            int maxLevelReached,
            Instant startedAt,
            Instant finishedAt,
            long durationSeconds,
            int totalTokensUsed,
            int score,
            /** When the player reached the level they are on now. Null if it predates the timeline. */
            Instant levelReachedAt,
            /** Ascending by level. A milestone above maxLevel means the run was completed. */
            List<LevelMilestone> levelTimeline,
            /**
             * Attempts in THIS run only. Zero on a player carried in from a previous session who
             * has not played yet - which is the difference between a level that was earned here
             * and one that was brought along. Not personal data, so it survives withoutEmail().
             */
            int attemptsThisSession
    ) {
        /** Player-facing view: same row without the email address. */
        public AdminLeaderboardResponse withoutEmail() {
            return new AdminLeaderboardResponse(rank, name, null, currentLevel, maxLevelReached,
                    startedAt, finishedAt, durationSeconds, totalTokensUsed, score,
                    levelReachedAt, levelTimeline, attemptsThisSession);
        }
    }

    public record AdminLeaderboardStatsResponse(
            int totalParticipants,
            int completedLevel7,
            long averageTimeSeconds,
            int currentlyActive
    ) {
    }
}
