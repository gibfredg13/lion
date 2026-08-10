package com.github.bgalek.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AdminUserService {
    private final Map<String, UserSession> userSessions = new HashMap<>();
    private final Map<String, UserUsage> userUsage = new HashMap<>();

    public void trackUserSession(String sessionId, String email, String displayName) {
        userSessions.put(sessionId, new UserSession(sessionId, email, displayName, Instant.now(), null));
    }

    public void trackUserActivity(String sessionId, String activity, int tokens) {
        userUsage.computeIfAbsent(sessionId, k -> new UserUsage(sessionId, 0, 0, Instant.now()))
                .recordActivity(activity, tokens);
    }

    public List<AdminUserResponse> getAllUsers() {
        return userSessions.values().stream()
                .map(session -> new AdminUserResponse(
                        session.sessionId(),
                        session.email(),
                        session.displayName(),
                        session.createdAt(),
                        userUsage.getOrDefault(session.sessionId(), new UserUsage(session.sessionId(), 0, 0, session.createdAt())).getTotalTokens()
                ))
                .collect(Collectors.toList());
    }

    public record UserSession(String sessionId, String email, String displayName, Instant createdAt, Instant lastActivityAt) {
    }

    public static class UserUsage {
        private final String sessionId;
        private int totalTokens;
        private int requestCount;
        private final Instant createdAt;

        public UserUsage(String sessionId, int totalTokens, int requestCount, Instant createdAt) {
            this.sessionId = sessionId;
            this.totalTokens = totalTokens;
            this.requestCount = requestCount;
            this.createdAt = createdAt;
        }

        public void recordActivity(String activity, int tokens) {
            this.totalTokens += tokens;
            this.requestCount++;
        }

        public int getTotalTokens() {
            return totalTokens;
        }

        public int getRequestCount() {
            return requestCount;
        }
    }

    public record AdminUserResponse(
            String sessionId,
            String email,
            String displayName,
            Instant createdAt,
            int totalTokensUsed
    ) {
    }
}
