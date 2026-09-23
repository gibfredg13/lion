package com.github.bgalek.admin;

import org.slf4j.Logger;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * The worked solutions the Help Desk reads, as last measured by a calibration run.
 * <p>
 * Deliberately not {@code app_settings}: that table's value column is {@code varchar(255)} and a
 * whole answer key is tens of kilobytes of prompts and replies.
 */
@Repository
public class AnswerKeyRepository {

    private static final Logger logger = getLogger(AnswerKeyRepository.class);

    private final JdbcClient jdbcClient;

    public AnswerKeyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public record Row(int level, String family, String prompt, String reply, String how,
                      int wins, int runs, String runId, String backendLabel, String model,
                      Instant measuredAt) {}

    public List<Row> all() {
        try {
            return jdbcClient.sql("""
                            SELECT level, family, prompt, reply, how, wins, runs, run_id,
                                   backend_label, model, measured_at
                              FROM answer_key ORDER BY level, family
                            """)
                    .query((rs, n) -> new Row(
                            rs.getInt("level"), rs.getString("family"), rs.getString("prompt"),
                            rs.getString("reply"), rs.getString("how"),
                            rs.getInt("wins"), rs.getInt("runs"), rs.getString("run_id"),
                            rs.getString("backend_label"), rs.getString("model"),
                            rs.getTimestamp("measured_at") == null
                                    ? null : rs.getTimestamp("measured_at").toInstant()))
                    .list();
        } catch (RuntimeException e) {
            // The Help Desk falls back to the bundled key, which is the safe direction to fail:
            // showing a stale answer key beats showing none to someone stood next to a player.
            logger.warn("Could not read the measured answer key", e);
            return List.of();
        }
    }

    /**
     * Replaces the given levels wholesale, and touches no others.
     * <p>
     * Delete-then-insert per level rather than an upsert: a family that stopped working must
     * disappear from the section, and HSQLDB - which the dev profile runs - has no
     * {@code ON CONFLICT} even in PostgreSQL syntax mode.
     */
    public void replaceLevels(Set<Integer> levels, List<Row> rows) {
        if (levels.isEmpty()) return;
        try {
            for (int level : levels) {
                jdbcClient.sql("DELETE FROM answer_key WHERE level = :level").param("level", level).update();
            }
            for (Row row : rows) {
                jdbcClient.sql("""
                                INSERT INTO answer_key
                                       (level, family, prompt, reply, how, wins, runs, run_id,
                                        backend_label, model, measured_at)
                                VALUES (:level, :family, :prompt, :reply, :how, :wins, :runs, :runId,
                                        :backendLabel, :model, :measuredAt)
                                """)
                        .param("level", row.level()).param("family", row.family())
                        .param("prompt", row.prompt()).param("reply", row.reply())
                        .param("how", row.how())
                        .param("wins", row.wins()).param("runs", row.runs())
                        .param("runId", row.runId()).param("backendLabel", row.backendLabel())
                        .param("model", row.model())
                        .param("measuredAt", row.measuredAt() == null
                                ? null : java.sql.Timestamp.from(row.measuredAt()))
                        .update();
            }
        } catch (RuntimeException e) {
            // Never fails the calibration run it hangs off: the run's own result is the thing the
            // operator asked for, and it is already saved by the time this is called.
            logger.warn("Could not save the measured answer key for levels {}", levels, e);
        }
    }
}
