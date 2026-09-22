package com.github.bgalek;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Set;

class MerlinLeaderboardRepository {
    private final JdbcClient jdbcClient;

    MerlinLeaderboardRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Records a completion. `name` must be supplied: the column is NOT NULL, and the original
     * insert omitted it, so every completion row was rejected and silently swallowed by the
     * caller's try/catch - which is why the leaderboard was always empty.
     */
    void addEntry(String userId, String session, String name, Instant sessionStart, String gameSessionId) {
        this.jdbcClient.sql("INSERT INTO leaderboard (user_id, session, name, started_at, game_session_id) VALUES (:userId, :session, :name, :startedAt, :gameSessionId)")
                .param("userId", userId, Types.VARCHAR)
                .param("session", session, Types.VARCHAR)
                .param("name", name == null || name.isBlank() ? "Anonymous" : name, Types.VARCHAR)
                .param("startedAt", Timestamp.from(sessionStart), Types.TIMESTAMP)
                .param("gameSessionId", gameSessionId, Types.VARCHAR)
                .update();
    }

    void addName(String session, String name) {
        this.jdbcClient.sql("UPDATE leaderboard set name = :name where session = :session")
                .param("name", name, Types.VARCHAR)
                .param("session", session, Types.VARCHAR)
                .update();
    }

    Set<MerlinService.LeaderboardEntry> getLeaderboard(String gameSessionId) {
        return this.jdbcClient
                .sql("SELECT * FROM leaderboard WHERE game_session_id = :gameSessionId "
                        + "ORDER BY finished_at LIMIT 100")
                .param("gameSessionId", gameSessionId, Types.VARCHAR)
                .query((rs, rowNum) -> new MerlinService.LeaderboardEntry(
                        rs.getString("session"),
                        rs.getString("name"),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("finished_at").toInstant())
                )
                .set();
    }

}
