package com.github.bgalek.admin;

import java.time.Instant;
import java.util.List;

public class AdminLeaderboardService {

    public List<AdminLeaderboardResponse> getLeaderboard() {
        return List.of();
    }

    public List<AdminLeaderboardResponse> getLeaderboardByProgress(int level) {
        return List.of();
    }

    public AdminLeaderboardStatsResponse getStats() {
        return new AdminLeaderboardStatsResponse(
                0,
                0,
                0,
                0
        );
    }

    public void resetLeaderboard() {
        // Clear leaderboard data
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
            int totalTokensUsed
    ) {
    }

    public record AdminLeaderboardStatsResponse(
            int totalParticipants,
            int completedLevel7,
            long averageTimeSeconds,
            int currentlyActive
    ) {
    }
}
