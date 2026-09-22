package com.github.bgalek.session;

import com.github.bgalek.database.SettingsRepository;
import org.slf4j.Logger;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Brings an existing database up to the game-session model, once, at boot.
 * <p>
 * There is no migration tool in this project; runners execute after the context refresh and
 * therefore after schema.sql, which is the established stand-in. This one also absorbs the level
 * timeline backfill that used to be a runner of its own: two runners have no defined relative
 * order, and the backfill writes level_progress rows that need a session id to be stamped with.
 * Getting that order wrong writes rows with a null session, which every session-scoped read then
 * cannot see.
 */
public class GameSessionBootstrap {

    private static final Logger logger = getLogger(GameSessionBootstrap.class);

    private final JdbcClient jdbcClient;
    private final SettingsRepository settings;
    private final GameSessionService sessions;
    /**
     * The level-timeline backfill, handed in as a callback rather than as the repository itself:
     * that class is package-private to com.github.bgalek and exporting it only so this migration
     * could call one method would widen its surface for no reason.
     */
    private final Runnable levelProgressBackfill;

    public GameSessionBootstrap(JdbcClient jdbcClient, SettingsRepository settings,
                                GameSessionService sessions,
                                Runnable levelProgressBackfill) {
        this.jdbcClient = jdbcClient;
        this.settings = settings;
        this.sessions = sessions;
        this.levelProgressBackfill = levelProgressBackfill;
    }

    public void run() {
        widenLevelProgressUniqueness();
        verifyUniquenessCoversSession();

        String stored = settings.get(GameSessionService.ACTIVE_SESSION_KEY).orElse(null);
        if (stored != null && exists(stored)) {
            sessions.adoptActive(stored, labelOf(stored));
            logger.info("Active game session: {} ({})", labelOf(stored), stored);
            levelProgressBackfill.run();
            return;
        }

        String id;
        String label;
        if (count("SELECT COUNT(*) FROM game_sessions") == 0) {
            id = UUID.randomUUID().toString();
            label = "Session 1";
            // Backdated to the earliest attempt on record, so the first session does not claim to
            // have started at the moment this code was deployed.
            insertFirstSession(id, label, earliestLogTimestamp());
            // Only ever on the very first bootstrap. Later on, an untagged row belongs to whichever
            // run was live when it was written, and adopting it into the current one would be a
            // guess - a wrong one for anything written before the most recent rollover.
            adoptOrphanRows(id);
        } else {
            id = queryString("SELECT id FROM game_sessions ORDER BY started_at DESC LIMIT 1");
            label = labelOf(id);
            logger.warn("No active game session was stored; adopting the most recent one, {}", label);
        }

        settings.put(GameSessionService.ACTIVE_SESSION_KEY, id);
        sessions.adoptActive(id, label);
        levelProgressBackfill.run();
    }

    /**
     * Widens the level_progress uniqueness from (user, level) to (user, level, session).
     * <p>
     * In Java rather than schema.sql because there is no portable idempotent form: Postgres has
     * DROP CONSTRAINT IF EXISTS and HSQLDB does not, and spring.sql.init aborts startup on the
     * first failing statement. Each step stands alone - on a database that has already been
     * migrated the DROP fails and the ADD must still not be skipped.
     * <p>
     * Identifiers are left unquoted so they fold to whichever case each engine stores them in,
     * upper on HSQLDB and lower on Postgres, which lets one spelling match both.
     */
    private void widenLevelProgressUniqueness() {
        tolerate("ALTER TABLE level_progress DROP CONSTRAINT uq_level_progress_user_level");
        tolerate("ALTER TABLE level_progress ADD CONSTRAINT uq_level_progress_user_level_session "
                + "UNIQUE (user_id, level, game_session_id)");
    }

    /**
     * Says so loudly if the widening did not take.
     * <p>
     * The step above tolerates failure by design, and recordLevelReached swallows the unique
     * violation that a surviving two-column constraint would cause - so without this check the
     * symptom is a second run whose timeline is simply, silently empty.
     */
    private void verifyUniquenessCoversSession() {
        try {
            Integer covering = jdbcClient.sql("""
                            SELECT COUNT(*) FROM information_schema.constraint_column_usage
                             WHERE LOWER(table_name) = 'level_progress'
                               AND LOWER(column_name) = 'game_session_id'
                            """)
                    .query(Integer.class).single();
            if (covering == null || covering == 0) {
                logger.error("level_progress uniqueness does not cover game_session_id. A second run "
                        + "will not record a timeline, because re-reaching a level will be rejected as a "
                        + "duplicate and the rejection is swallowed. Fix the constraint before the event.");
            }
        } catch (Exception e) {
            logger.warn("Could not verify the level_progress uniqueness constraint", e);
        }
    }

    private void adoptOrphanRows(String id) {
        int logs = update("UPDATE logs SET game_session_id = :id WHERE game_session_id IS NULL", id);
        int progress = update("UPDATE level_progress SET game_session_id = :id WHERE game_session_id IS NULL", id);
        int board = update("UPDATE leaderboard SET game_session_id = :id WHERE game_session_id IS NULL", id);
        logger.info("Adopted existing data into the first game session: {} attempts, {} timeline rows, "
                + "{} leaderboard entries", logs, progress, board);
    }

    private void insertFirstSession(String id, String label, Instant startedAt) {
        jdbcClient.sql("""
                        INSERT INTO game_sessions (id, label, started_at, reset_levels, created_by)
                        VALUES (:id, :label, :startedAt, false, 'bootstrap')
                        """)
                .param("id", id).param("label", label)
                .param("startedAt", Timestamp.from(startedAt))
                .update();
        logger.info("Created the first game session '{}', backdated to {}", label, startedAt);
    }

    private Instant earliestLogTimestamp() {
        try {
            Timestamp earliest = jdbcClient.sql("SELECT MIN(created_at) FROM logs")
                    .query(Timestamp.class).optional().orElse(null);
            return earliest == null ? Instant.now() : earliest.toInstant();
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private boolean exists(String id) {
        return count("SELECT COUNT(*) FROM game_sessions WHERE id = '" + id.replace("'", "''") + "'") > 0;
    }

    private String labelOf(String id) {
        try {
            return jdbcClient.sql("SELECT label FROM game_sessions WHERE id = :id")
                    .param("id", id).query(String.class).optional().orElse("Session");
        } catch (Exception e) {
            return "Session";
        }
    }

    private int count(String sql) {
        Integer n = jdbcClient.sql(sql).query(Integer.class).single();
        return n == null ? 0 : n;
    }

    private String queryString(String sql) {
        return jdbcClient.sql(sql).query(String.class).single();
    }

    private int update(String sql, String id) {
        return jdbcClient.sql(sql).param("id", id).update();
    }

    /** Logged at INFO, not ERROR: after the first boot, failure here is the expected outcome. */
    private void tolerate(String sql) {
        try {
            jdbcClient.sql(sql).update();
            logger.info("Schema step applied: {}", sql);
        } catch (Exception e) {
            logger.info("Schema step skipped ({}): {}", e.getMessage(), sql);
        }
    }
}
