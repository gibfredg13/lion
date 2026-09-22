package com.github.bgalek.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Stores finished calibration and stress runs.
 * <p>
 * Run history used to live in a list in memory, capped at twenty and lost on every restart, which
 * made comparing one backend against another impossible - the numbers were gone before the second
 * box had been tried. The {@code calibration_results} table had been declared since the beginning
 * and never written to; this fills it.
 */
@Repository
public class CalibrationRunRepository {

    private static final Logger logger = getLogger(CalibrationRunRepository.class);

    public static final String CALIBRATION = "CALIBRATION";
    public static final String STRESS_TEST = "STRESS_TEST";

    private final JdbcClient jdbcClient;
    /**
     * Spring's mapper, not a new one: these records carry {@link Instant} fields, which need the
     * JavaTimeModule the auto-configured bean already registers, and the same ISO-8601 rendering
     * the REST layer uses so the dashboard's date parsing keeps working.
     */
    private final ObjectMapper objectMapper;

    public CalibrationRunRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public record RunTag(String backendId, String backendLabel, String model,
                         Integer maxTokens, Integer maxConcurrent) {}

    /**
     * Update-then-insert rather than an upsert: HSQLDB, which the dev profile runs, has no
     * {@code ON CONFLICT} even in PostgreSQL syntax mode. There is one writer per run, so the
     * race this leaves open cannot occur.
     */
    public void save(String runId, String type, String status, Instant startedAt, Instant completedAt,
                     Integer invariantsPassed, Integer invariantsTotal, Integer estimatedCapacity,
                     RunTag tag, long totalTokens, int llmCalls, int failedCalls, Object details) {
        String json;
        try {
            json = objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            logger.error("Could not serialise run {}; storing it without details", runId, e);
            json = null;
        }
        try {
            int updated = jdbcClient.sql("""
                            UPDATE calibration_results
                               SET status = :status, completed_at = :completedAt,
                                   invariants_passed = :ip, invariants_total = :it,
                                   estimated_capacity = :cap, backend_id = :backendId,
                                   backend_label = :backendLabel, model = :model,
                                   max_tokens = :maxTokens, max_concurrent = :maxConcurrent,
                                   total_tokens = :totalTokens, llm_calls = :llmCalls,
                                   failed_calls = :failedCalls, details_json = :details
                             WHERE run_id = :runId
                            """)
                    .param("status", status)
                    .param("completedAt", completedAt == null ? null : Timestamp.from(completedAt))
                    .param("ip", invariantsPassed).param("it", invariantsTotal)
                    .param("cap", estimatedCapacity)
                    .param("backendId", tag.backendId()).param("backendLabel", tag.backendLabel())
                    .param("model", tag.model())
                    .param("maxTokens", tag.maxTokens()).param("maxConcurrent", tag.maxConcurrent())
                    .param("totalTokens", totalTokens).param("llmCalls", llmCalls)
                    .param("failedCalls", failedCalls).param("details", json)
                    .param("runId", runId)
                    .update();
            if (updated == 0) {
                jdbcClient.sql("""
                                INSERT INTO calibration_results
                                    (run_id, type, status, started_at, completed_at, invariants_passed,
                                     invariants_total, estimated_capacity, backend_id, backend_label, model,
                                     max_tokens, max_concurrent, total_tokens, llm_calls, failed_calls, details_json)
                                VALUES (:runId, :type, :status, :startedAt, :completedAt, :ip, :it, :cap,
                                        :backendId, :backendLabel, :model, :maxTokens, :maxConcurrent,
                                        :totalTokens, :llmCalls, :failedCalls, :details)
                                """)
                        .param("runId", runId).param("type", type).param("status", status)
                        .param("startedAt", Timestamp.from(startedAt))
                        .param("completedAt", completedAt == null ? null : Timestamp.from(completedAt))
                        .param("ip", invariantsPassed).param("it", invariantsTotal)
                        .param("cap", estimatedCapacity)
                        .param("backendId", tag.backendId()).param("backendLabel", tag.backendLabel())
                        .param("model", tag.model())
                        .param("maxTokens", tag.maxTokens()).param("maxConcurrent", tag.maxConcurrent())
                        .param("totalTokens", totalTokens).param("llmCalls", llmCalls)
                        .param("failedCalls", failedCalls).param("details", json)
                        .update();
            }
        } catch (RuntimeException e) {
            // Bookkeeping must never fail the run that produced it.
            logger.error("Could not store run {}", runId, e);
        }
    }

    /** Newest first. {@code backendId} null means every backend. */
    public <T> List<T> recent(String type, String backendId, int limit, Class<T> detailsType) {
        // The filter is built into the statement rather than passed as ":backendId IS NULL OR ...".
        // Postgres cannot infer a bare parameter's type in an IS NULL test and rejects the
        // statement outright with a grammar error, which costs the whole history listing.
        boolean filtered = backendId != null && !backendId.isBlank();
        String sql = """
                SELECT details_json FROM calibration_results
                 WHERE type = :type AND details_json IS NOT NULL
                """
                + (filtered ? "   AND backend_id = :backendId\n" : "")
                + " ORDER BY started_at DESC LIMIT :limit";
        try {
            var statement = jdbcClient.sql(sql).param("type", type).param("limit", limit);
            if (filtered) statement = statement.param("backendId", backendId);
            List<String> rows = statement.query(String.class).list();
            List<T> out = new ArrayList<>();
            for (String row : rows) {
                try {
                    out.add(objectMapper.readValue(row, detailsType));
                } catch (Exception e) {
                    logger.warn("Skipping a stored run that could not be read back", e);
                }
            }
            return out;
        } catch (RuntimeException e) {
            logger.error("Could not read run history", e);
            return List.of();
        }
    }

    public <T> Optional<T> byRunId(String runId, Class<T> detailsType) {
        try {
            return jdbcClient.sql("SELECT details_json FROM calibration_results WHERE run_id = :runId")
                    .param("runId", runId)
                    .query(String.class).optional()
                    .map(row -> {
                        try {
                            return objectMapper.readValue(row, detailsType);
                        } catch (Exception e) {
                            logger.warn("Stored run {} could not be read back", runId, e);
                            return null;
                        }
                    });
        } catch (RuntimeException e) {
            logger.error("Could not read run {}", runId, e);
            return Optional.empty();
        }
    }
}
