package com.github.bgalek;

import org.springframework.jdbc.core.simple.JdbcClient;

class MerlinLogger {
    private final JdbcClient jdbcClient;

    MerlinLogger(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Records one attempt. `user_id` is what makes the admin analytics, the per-player token
     * accounting and the live feed possible - the table previously keyed on session id alone, so
     * nothing could be attributed back to an account.
     */
    void logAttempt(String userId, String session, int currentLevel, String prompt, String response,
                    boolean blocked, String blockedBy, int inputTokens, int outputTokens,
                    String gameSessionId) {
        String sql = """
                INSERT INTO logs (user_id, session, level, prompt, response, blocked, blocked_by, input_tokens, output_tokens, game_session_id)
                VALUES (:userId, :session, :level, :prompt, :response, :blocked, :blockedBy, :inputTokens, :outputTokens, :gameSessionId)
                """;
        this.jdbcClient.sql(sql)
                .param("userId", userId)
                .param("session", session)
                .param("level", currentLevel)
                .param("prompt", prompt)
                .param("response", response)
                .param("blocked", blocked)
                .param("blockedBy", blockedBy)
                .param("inputTokens", inputTokens)
                .param("outputTokens", outputTokens)
                .param("gameSessionId", gameSessionId)
                .update();
    }
}
