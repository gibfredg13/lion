package com.github.bgalek;

import org.slf4j.Logger;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * When each player first reached each level. `users.current_level` records where someone is now;
 * this records when they got there, which is what the leaderboard's timestamp column and the
 * per-level timeline are built from.
 * <p>
 * Every write is best-effort and swallows its own failure: bookkeeping must never be the reason a
 * player cannot advance, the same discipline {@link MerlinService#saveLeaderboardEntry} follows.
 */
class MerlinLevelProgressRepository {

    private static final Logger logger = getLogger(MerlinLevelProgressRepository.class);

    private final JdbcClient jdbcClient;

    MerlinLevelProgressRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Records a first arrival at a level. Check-then-insert rather than an upsert: the dev database
     * is HSQLDB and production is Postgres, and the two share no ON CONFLICT / MERGE syntax. The
     * unique constraint is the real guard - this only keeps the common path quiet.
     */
    void recordLevelReached(String userId, int level, Instant reachedAt, String gameSessionId) {
        if (userId == null) return;
        try {
            if (exists(userId, level, gameSessionId)) return;
            jdbcClient.sql("""
                            INSERT INTO level_progress (user_id, level, reached_at, game_session_id)
                            VALUES (:userId, :level, :reachedAt, :gameSessionId)
                            """)
                    .param("userId", userId)
                    .param("level", level)
                    .param("reachedAt", Timestamp.from(reachedAt))
                    .param("gameSessionId", gameSessionId)
                    .update();
        } catch (Exception e) {
            logger.error("Failed to record level progress for {} at level {}", userId, level, e);
        }
    }

    /**
     * Seeds the level-1 milestone so a timeline is not missing its own start. Nobody is ever
     * <em>advanced into</em> level 1 - they begin there - so nothing on the advance path would
     * write it. Backdated to the player's first logged attempt, which is the same instant the
     * leaderboard already reports as started_at.
     * <p>
     * Deliberately not written at registration: accounts are created for people who may never
     * play, and the board already excludes them.
     */
    void ensureStartLevel(String userId, String gameSessionId) {
        if (userId == null) return;
        try {
            if (exists(userId, 1, gameSessionId)) return;
            // Scoped to this run: a player carried into a second session started it when they first
            // played in it, not when they first played months ago.
            List<Timestamp> first = jdbcClient
                    .sql("""
                            SELECT MIN(created_at) AS first_attempt FROM logs
                             WHERE user_id = :userId AND game_session_id = :gameSessionId
                            """)
                    .param("userId", userId)
                    .param("gameSessionId", gameSessionId)
                    .query((rs, n) -> rs.getTimestamp("first_attempt"))
                    .list();
            Timestamp firstAttempt = first.isEmpty() ? null : first.getFirst();
            recordLevelReached(userId, 1, firstAttempt == null ? Instant.now() : firstAttempt.toInstant(),
                    gameSessionId);
        } catch (Exception e) {
            logger.error("Failed to seed level 1 progress for {}", userId, e);
        }
    }

    /**
     * Drops one player's timeline. Used when they restart from level 1: milestones from a run that
     * no longer exists would put a level-5 timestamp next to a current level of 1.
     */
    void clearForUser(String userId, String gameSessionId) {
        if (userId == null) return;
        try {
            // Only this run's milestones. A previous event's timeline is history and stays.
            jdbcClient.sql("DELETE FROM level_progress WHERE user_id = :userId "
                            + "AND game_session_id = :gameSessionId")
                    .param("userId", userId)
                    .param("gameSessionId", gameSessionId)
                    .update();
        } catch (Exception e) {
            logger.error("Failed to clear level progress for {}", userId, e);
        }
    }

    /**
     * One-shot migration for players already mid-event when the timeline shipped. The first attempt
     * logged at a level is when that level was reached, so the attempt log reconstructs the whole
     * thing. Guarded on the table being empty rather than per-row, so it runs once: a per-row guard
     * would resurrect the old timeline of anyone who had since reset their progress.
     */
    int backfillFromLogsIfEmpty() {
        try {
            Integer existing = jdbcClient.sql("SELECT COUNT(*) FROM level_progress")
                    .query(Integer.class)
                    .single();
            if (existing != null && existing > 0) return 0;
            // The session is read off the log row rather than passed in, so this cannot write an
            // untagged row that every session-scoped read would then be unable to see. Grouping by
            // it too is a semantic fix, not only a syntactic one: first arrival is now per run.
            int inserted = jdbcClient.sql("""
                    INSERT INTO level_progress (user_id, level, reached_at, game_session_id)
                    SELECT l.user_id, l.level, MIN(l.created_at), l.game_session_id
                    FROM logs l
                    WHERE l.user_id IS NOT NULL
                    GROUP BY l.user_id, l.level, l.game_session_id
                    """).update();
            if (inserted > 0) {
                logger.info("Backfilled {} level-progress rows from the attempt log", inserted);
            }
            return inserted;
        } catch (Exception e) {
            logger.error("Level progress backfill failed", e);
            return 0;
        }
    }

    private boolean exists(String userId, int level, String gameSessionId) {
        Integer count = jdbcClient
                .sql("SELECT COUNT(*) FROM level_progress WHERE user_id = :userId AND level = :level "
                        + "AND game_session_id = :gameSessionId")
                .param("userId", userId)
                .param("level", level)
                .param("gameSessionId", gameSessionId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }
}
