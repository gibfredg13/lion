package com.github.bgalek.tv;

import com.github.bgalek.admin.AdminLeaderboardService;
import com.github.bgalek.admin.LevelGateService;
import com.github.bgalek.admin.LlmBackendAdminService;
import com.github.bgalek.database.SettingsRepository;
import com.github.bgalek.levels.LevelDefinitionService;
import com.github.bgalek.session.GameSessionService;
import org.slf4j.Logger;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

/** Assembles the projector payload. Every field is chosen by hand; nothing is passed through. */
@Service
public class TvService {

    private static final Logger logger = getLogger(TvService.class);

    static final String FEED_ENABLED_KEY = "tv.feedEnabled";
    private static final int FEED_SIZE = 8;
    private static final int PROMPT_CHARS = 140;
    private static final int RESPONSE_CHARS = 200;
    /** Two minutes, not the ten the admin stats use. Ten minutes is not "online" on a screen. */
    private static final int ONLINE_SECONDS = 120;

    private final JdbcClient jdbcClient;
    private final AdminLeaderboardService leaderboard;
    private final GameSessionService gameSessions;
    private final LevelDefinitionService levels;
    private final LevelGateService levelGates;
    private final LlmBackendAdminService backends;
    private final SettingsRepository settings;

    /**
     * Cached rather than read per poll. It changes twice a day, and SettingsRepository#get
     * swallows its exception and returns empty - so a database hiccup would silently flip the
     * feed to its default in the middle of an event.
     */
    private volatile boolean feedEnabled;

    public TvService(JdbcClient jdbcClient, AdminLeaderboardService leaderboard,
                     GameSessionService gameSessions, LevelDefinitionService levels,
                     LevelGateService levelGates, LlmBackendAdminService backends,
                     SettingsRepository settings) {
        this.jdbcClient = jdbcClient;
        this.leaderboard = leaderboard;
        this.gameSessions = gameSessions;
        this.levels = levels;
        this.levelGates = levelGates;
        this.backends = backends;
        this.settings = settings;
    }

    /** Defaults to off: a feed you have to switch on is a feed that cannot surprise you. */
    public void loadFeedSetting() {
        this.feedEnabled = settings.get(FEED_ENABLED_KEY).map("true"::equals).orElse(false);
        logger.info("Public TV feed is {}", feedEnabled ? "ON" : "OFF");
    }

    public boolean feedEnabled() {
        return feedEnabled;
    }

    public void setFeedEnabled(boolean enabled) {
        this.feedEnabled = enabled;
        settings.put(FEED_ENABLED_KEY, Boolean.toString(enabled));
        logger.warn("Public TV feed switched {}", enabled ? "ON" : "OFF");
    }

    public TvSnapshot snapshot() {
        String sessionId = gameSessions.activeId();
        return new TvSnapshot(
                "The Lion's Den",
                gameSessions.activeLabel(),
                Instant.now(),
                board(),
                funnel(sessionId),
                feedEnabled ? feed(sessionId) : List.of(),
                feedEnabled,
                vitals(sessionId));
    }

    /**
     * Narrowed field by field from the admin row, not merely stripped of its email.
     * <p>
     * The admin record also carries a per-level timeline, which is a behavioural trace of one
     * person's evening and has no business on a wall.
     */
    private List<TvSnapshot.TvBoardRow> board() {
        List<TvSnapshot.TvBoardRow> out = new ArrayList<>();
        try {
            for (var r : leaderboard.getLeaderboard()) {
                out.add(new TvSnapshot.TvBoardRow(
                        r.rank(), r.name(), r.currentLevel(), r.durationSeconds(),
                        r.score(), r.totalTokensUsed(), r.finishedAt() != null,
                        r.attemptsThisSession() == 0));
            }
        } catch (Exception e) {
            logger.warn("TV board unavailable", e);
        }
        return out;
    }

    /**
     * Who sits where right now, one bar per level.
     * <p>
     * The player count comes from users.current_level rather than this run's timeline: after a
     * reset that column is 1 for everyone and after a carry-on it is the level they brought, which
     * is the truth the funnel wants in both cases. Counting timeline rows instead would show levels
     * one to four empty at the start of a second run while players were demonstrably past them.
     */
    private List<TvSnapshot.TvFunnelBar> funnel(String sessionId) {
        Map<Integer, Integer> players = new LinkedHashMap<>();
        Map<Integer, Integer> attempts = new LinkedHashMap<>();
        try {
            jdbcClient.sql("""
                            SELECT u.current_level AS "level", COUNT(*) AS "players"
                            FROM users u
                            WHERE u.current_level > 1
                               OR EXISTS (SELECT 1 FROM logs l
                                           WHERE l.user_id = u.id AND l.game_session_id = :gameSessionId)
                            GROUP BY u.current_level
                            """)
                    .param("gameSessionId", sessionId)
                    .query().listOfRows()
                    .forEach(row -> players.put(((Number) row.get("level")).intValue(),
                            ((Number) row.get("players")).intValue()));

            jdbcClient.sql("""
                            SELECT level AS "level", COUNT(*) AS "attempts"
                            FROM logs WHERE game_session_id = :gameSessionId
                            GROUP BY level
                            """)
                    .param("gameSessionId", sessionId)
                    .query().listOfRows()
                    .forEach(row -> attempts.put(((Number) row.get("level")).intValue(),
                            ((Number) row.get("attempts")).intValue()));
        } catch (Exception e) {
            logger.warn("TV funnel unavailable", e);
        }

        // Zero-filled across the whole ladder: a missing bar is a funnel that lies by omission.
        List<TvSnapshot.TvFunnelBar> out = new ArrayList<>();
        for (int level = 1; level <= levels.count(); level++) {
            out.add(new TvSnapshot.TvFunnelBar(level,
                    players.getOrDefault(level, 0),
                    attempts.getOrDefault(level, 0),
                    levelGates.isLevelEnabled(level)));
        }
        return out;
    }

    /**
     * The most recent attempts.
     * <p>
     * Deliberately not the admin's recent-prompts query, which selects the player's email address.
     */
    private List<TvSnapshot.TvFeedItem> feed(String sessionId) {
        List<TvSnapshot.TvFeedItem> out = new ArrayList<>();
        try {
            jdbcClient.sql("""
                            SELECT l.id AS "id", l.level AS "level", l.prompt AS "prompt",
                                   l.response AS "response", l.blocked AS "blocked",
                                   l.blocked_by AS "blockedBy", l.created_at AS "createdAt",
                                   u.display_name AS "displayName"
                            FROM logs l JOIN users u ON u.id = l.user_id
                            WHERE l.game_session_id = :gameSessionId
                            ORDER BY l.created_at DESC LIMIT :limit
                            """)
                    .param("gameSessionId", sessionId)
                    .param("limit", FEED_SIZE)
                    .query().listOfRows()
                    .forEach(row -> out.add(new TvSnapshot.TvFeedItem(
                            ((Number) row.get("id")).longValue(),
                            (String) row.get("displayName"),
                            ((Number) row.get("level")).intValue(),
                            clip((String) row.get("prompt"), PROMPT_CHARS),
                            clip((String) row.get("response"), RESPONSE_CHARS),
                            Boolean.TRUE.equals(row.get("blocked")),
                            (String) row.get("blockedBy"),
                            row.get("createdAt") instanceof Timestamp t ? t.toInstant() : null)));
        } catch (Exception e) {
            logger.warn("TV feed unavailable", e);
        }
        return out;
    }

    private TvSnapshot.TvVitals vitals(String sessionId) {
        int online = 0;
        int total = 0;
        long lastMinute = 0;
        long attempts = 0;
        long tokens = 0;
        try {
            // Joined to users, not counted straight off logs. Deleting an account leaves its
            // attempts behind, so a bare COUNT(DISTINCT user_id) counts people who no longer
            // exist - and then the headline figure disagrees with the funnel bars beside it,
            // which is exactly the inconsistency a room notices on a three-metre screen.
            online = countInt("""
                    SELECT COUNT(DISTINCT l.user_id) FROM logs l JOIN users u ON u.id = l.user_id
                     WHERE l.game_session_id = :gameSessionId AND l.created_at > :cutoff
                    """, sessionId, Instant.now().minusSeconds(ONLINE_SECONDS));
            lastMinute = countLong("""
                    SELECT COUNT(*) FROM logs
                     WHERE game_session_id = :gameSessionId AND created_at > :cutoff
                    """, sessionId, Instant.now().minusSeconds(60));
            total = countInt("SELECT COUNT(DISTINCT l.user_id) FROM logs l "
                            + "JOIN users u ON u.id = l.user_id WHERE l.game_session_id = :gameSessionId",
                    sessionId, null);
            attempts = countLong("SELECT COUNT(*) FROM logs WHERE game_session_id = :gameSessionId",
                    sessionId, null);
            tokens = countLong("""
                    SELECT COALESCE(SUM(input_tokens + output_tokens), 0) FROM logs
                     WHERE game_session_id = :gameSessionId
                    """, sessionId, null);
        } catch (Exception e) {
            logger.warn("TV vitals unavailable", e);
        }

        String label = null;
        boolean healthy = false;
        long latency = 0;
        try {
            // Only these three fields. BackendView also carries the base URL, which is an internal
            // address and must never reach an unauthenticated response.
            var active = backends.backends().stream().filter(b -> b.active()).findFirst().orElse(null);
            if (active != null) {
                label = active.label();
                healthy = active.healthy();
                latency = active.avgLatencyMs();
            }
        } catch (Exception e) {
            logger.warn("TV backend vitals unavailable", e);
        }
        return new TvSnapshot.TvVitals(online, total, lastMinute, attempts, tokens, label, healthy, latency);
    }

    private int countInt(String sql, String sessionId, Instant cutoff) {
        return (int) countLong(sql, sessionId, cutoff);
    }

    private long countLong(String sql, String sessionId, Instant cutoff) {
        var stmt = jdbcClient.sql(sql).param("gameSessionId", sessionId);
        if (cutoff != null) stmt = stmt.param("cutoff", Timestamp.from(cutoff));
        Long n = stmt.query(Long.class).single();
        return n == null ? 0 : n;
    }

    private static String clip(String text, int max) {
        if (text == null) return "";
        String trimmed = text.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }
}
