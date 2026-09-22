package com.github.bgalek.session;

import com.github.bgalek.database.SettingsRepository;
import org.slf4j.Logger;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Which run of the event is live, and how to start the next one.
 * <p>
 * Starting again used to mean {@code DELETE FROM logs} - a fresh board bought by destroying the
 * previous event. Here a run is a row, every attempt is tagged with it, and starting the next one
 * is a pointer move.
 * <p>
 * The pointer lives in {@code app_settings} under {@value #ACTIVE_SESSION_KEY} rather than as an
 * {@code is_active} flag on the table. "Exactly one row is true" has no portable expression -
 * Postgres does it with a partial unique index, HSQLDB has no such thing, and schema.sql bans
 * CREATE UNIQUE INDEX outright - so it would have to be maintained by clearing every row and
 * setting one, which leaves two active sessions if it fails halfway. A pointer can only point at
 * one thing.
 */
@Service
public class GameSessionService {

    private static final Logger logger = getLogger(GameSessionService.class);

    public static final String ACTIVE_SESSION_KEY = "game.activeSessionId";

    private final JdbcClient jdbcClient;
    private final SettingsRepository settings;

    /**
     * Read on every player turn to decide whether a browser's cached level belongs to this run, so
     * it is deliberately a field and not a database lookup.
     */
    private volatile String activeId;
    private volatile String activeLabel = "Session 1";

    public GameSessionService(JdbcClient jdbcClient, SettingsRepository settings) {
        this.jdbcClient = jdbcClient;
        this.settings = settings;
    }

    public String activeId() {
        return activeId;
    }

    public String activeLabel() {
        return activeLabel;
    }

    /** Package-private: only the bootstrap runner may set this without starting a new run. */
    void adoptActive(String id, String label) {
        this.activeId = id;
        this.activeLabel = label;
    }

    /**
     * Ends the current run and begins a new one. Deletes nothing.
     *
     * @param resetLevels true sends every player back to level 1. Players who are still logged in
     *                    keep their level in an HttpSession attribute that no UPDATE can reach, so
     *                    the reset is completed lazily - see MerlinService#getCurrentLevel.
     */
    @Transactional
    public GameSession startNewSession(String label, boolean resetLevels, String createdBy) {
        String previous = activeId;
        String id = UUID.randomUUID().toString();
        String cleanLabel = label == null || label.isBlank() ? defaultLabel() : label.trim();

        jdbcClient.sql("""
                        INSERT INTO game_sessions (id, label, started_at, reset_levels, created_by)
                        VALUES (:id, :label, :startedAt, :resetLevels, :createdBy)
                        """)
                .param("id", id).param("label", cleanLabel)
                .param("startedAt", Timestamp.from(Instant.now()))
                .param("resetLevels", resetLevels).param("createdBy", createdBy)
                .update();

        if (previous != null) {
            jdbcClient.sql("UPDATE game_sessions SET ended_at = CURRENT_TIMESTAMP "
                            + "WHERE id = :prev AND ended_at IS NULL")
                    .param("prev", previous).update();
        }

        if (resetLevels) {
            // No DELETE. The previous run's logs and timelines stay exactly where they are, tagged
            // with the previous session id, which is the entire point of the feature.
            int affected = jdbcClient.sql("UPDATE users SET current_level = 1").update();
            logger.info("New session '{}': {} players sent back to level 1", cleanLabel, affected);
        } else {
            logger.info("New session '{}': players keep the level they had reached", cleanLabel);
        }

        settings.put(ACTIVE_SESSION_KEY, id);

        // Published only once the transaction commits. In between, `users.current_level` may be
        // half-reset, and a request that rehydrated from it would read a level that is about to
        // be rolled back.
        publishAfterCommit(id, cleanLabel);

        return new GameSession(id, cleanLabel, Instant.now(), null, resetLevels, createdBy, 0, 0, true);
    }

    private void publishAfterCommit(String id, String label) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    adoptActive(id, label);
                }
            });
        } else {
            adoptActive(id, label);
        }
    }

    private String defaultLabel() {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM game_sessions").query(Integer.class).single();
        return "Session " + ((count == null ? 0 : count) + 1);
    }

    /** Newest first, with the counts that make the admin's session picker worth reading. */
    public List<GameSession> list() {
        List<GameSession> out = new ArrayList<>();
        jdbcClient.sql("""
                        SELECT s.id, s.label, s.started_at, s.ended_at, s.reset_levels, s.created_by,
                               (SELECT COUNT(DISTINCT l.user_id) FROM logs l WHERE l.game_session_id = s.id) AS players,
                               (SELECT COUNT(*) FROM logs l WHERE l.game_session_id = s.id)               AS attempts
                        FROM game_sessions s
                        ORDER BY s.started_at DESC
                        """)
                .query((rs, rowNum) -> new GameSession(
                        rs.getString("id"),
                        rs.getString("label"),
                        rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toInstant(),
                        rs.getBoolean("reset_levels"),
                        rs.getString("created_by"),
                        rs.getInt("players"),
                        rs.getLong("attempts"),
                        rs.getString("id").equals(activeId)))
                .list()
                .forEach(out::add);
        return out;
    }

    public Optional<GameSession> byId(String id) {
        return list().stream().filter(s -> s.id().equals(id)).findFirst();
    }

    /**
     * Discards a run and everything recorded during it. For a botched test round, not for history.
     *
     * @throws IllegalStateException if asked to delete the run that is currently live
     */
    @Transactional
    public void delete(String id) {
        if (id.equals(activeId)) {
            throw new IllegalStateException("The active session cannot be deleted. Start a new one first.");
        }
        // Player levels are deliberately untouched: deleting the record of a run is not un-playing it.
        jdbcClient.sql("DELETE FROM logs WHERE game_session_id = :id").param("id", id).update();
        jdbcClient.sql("DELETE FROM level_progress WHERE game_session_id = :id").param("id", id).update();
        jdbcClient.sql("DELETE FROM leaderboard WHERE game_session_id = :id").param("id", id).update();
        jdbcClient.sql("DELETE FROM game_sessions WHERE id = :id").param("id", id).update();
        logger.warn("Game session {} deleted along with its attempts and timelines", id);
    }
}
