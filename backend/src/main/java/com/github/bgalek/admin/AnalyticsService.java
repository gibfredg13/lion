package com.github.bgalek.admin;

import com.github.bgalek.database.DetectedAttack;
import com.github.bgalek.database.Prompt;
import com.github.bgalek.database.User;
import com.github.bgalek.database.LlmResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class AnalyticsService {

    private final List<User> users;
    private final List<Prompt> prompts;
    private final List<LlmResponse> responses;
    private final List<DetectedAttack> attacks;

    public AnalyticsService(List<User> users, List<Prompt> prompts, List<LlmResponse> responses, List<DetectedAttack> attacks) {
        this.users = users;
        this.prompts = prompts;
        this.responses = responses;
        this.attacks = attacks;
    }

    public AttackHeatmapResponse getAttackHeatmap() {
        Map<String, Integer> attackCounts = new TreeMap<>();
        Map<String, String> severityMap = new TreeMap<>();

        for (DetectedAttack attack : attacks) {
            attackCounts.merge(attack.getAttackType(), 1, Integer::sum);
            severityMap.put(attack.getAttackType(), attack.getSeverity());
        }

        List<AttackHeatmapResponse.AttackData> data = attackCounts.entrySet().stream()
                .map(e -> new AttackHeatmapResponse.AttackData(
                        e.getKey(),
                        e.getValue(),
                        severityMap.get(e.getKey()),
                        ((double) e.getValue() / Math.max(attacks.size(), 1)) * 100
                ))
                .sorted((a, b) -> Integer.compare(b.count, a.count))
                .collect(Collectors.toList());

        return new AttackHeatmapResponse(data, attacks.size());
    }

    public List<TokenUsageByUserResponse> getTokenUsageByUser() {
        Map<String, UserTokenStats> stats = new HashMap<>();

        for (Prompt prompt : prompts) {
            stats.putIfAbsent(prompt.getUserId(), new UserTokenStats());
            UserTokenStats userStats = stats.get(prompt.getUserId());
            userStats.promptCount++;

            Optional<User> user = users.stream().filter(u -> u.getId().equals(prompt.getUserId())).findFirst();
            if (user.isPresent()) {
                userStats.displayName = user.get().getDisplayName();
                userStats.email = user.get().getEmail();
            }
        }

        for (LlmResponse response : responses) {
            Optional<Prompt> prompt = prompts.stream().filter(p -> p.getId().equals(response.getPromptId())).findFirst();
            if (prompt.isPresent()) {
                String userId = prompt.get().getUserId();
                if (stats.containsKey(userId)) {
                    UserTokenStats userStats = stats.get(userId);
                    userStats.totalInputTokens += response.getInputTokens();
                    userStats.totalOutputTokens += response.getOutputTokens();
                }
            }
        }

        return stats.values().stream()
                .map(s -> new TokenUsageByUserResponse(s.displayName, s.email, s.promptCount, s.totalInputTokens, s.totalOutputTokens))
                .sorted((a, b) -> Integer.compare(b.totalTokens, a.totalTokens))
                .collect(Collectors.toList());
    }

    public List<TimeSeriesTokensResponse> getTokenUsageTimeSeries() {
        Map<String, Long> dailyTokens = new TreeMap<>();

        for (LlmResponse response : responses) {
            String date = response.getTimestamp().toString().substring(0, 10);
            dailyTokens.merge(date, (long) (response.getInputTokens() + response.getOutputTokens()), Long::sum);
        }

        return dailyTokens.entrySet().stream()
                .map(e -> new TimeSeriesTokensResponse(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public List<TopTricksResponse> getTopTricks() {
        Map<String, TrickStats> tricks = new HashMap<>();

        for (DetectedAttack attack : attacks) {
            tricks.putIfAbsent(attack.getAttackType(), new TrickStats(attack.getDescription(), attack.getSeverity()));
            TrickStats stats = tricks.get(attack.getAttackType());
            stats.attempts++;

            Optional<User> user = users.stream()
                    .filter(u -> u.getId().equals(attack.getUserId()))
                    .findFirst();
            if (user.isPresent()) {
                stats.attemptingUsers.add(user.get().getDisplayName());
            }
        }

        return tricks.entrySet().stream()
                .map(e -> new TopTricksResponse(
                        e.getKey(),
                        e.getValue().description,
                        e.getValue().attempts,
                        e.getValue().severity,
                        e.getValue().attemptingUsers.size()
                ))
                .sorted((a, b) -> Integer.compare(b.attempts, a.attempts))
                .limit(10)
                .collect(Collectors.toList());
    }

    public byte[] exportAsCSV() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos);

        writer.println("User,Email,Prompts,Input Tokens,Output Tokens,Total Tokens,Attack Attempts,Severity");

        Map<String, UserTokenStats> userStats = new HashMap<>();
        Map<String, Integer> userAttacks = new HashMap<>();

        for (Prompt prompt : prompts) {
            userStats.putIfAbsent(prompt.getUserId(), new UserTokenStats());
            userStats.get(prompt.getUserId()).promptCount++;
        }

        for (LlmResponse response : responses) {
            Optional<Prompt> prompt = prompts.stream().filter(p -> p.getId().equals(response.getPromptId())).findFirst();
            if (prompt.isPresent()) {
                String userId = prompt.get().getUserId();
                userStats.computeIfPresent(userId, (k, v) -> {
                    v.totalInputTokens += response.getInputTokens();
                    v.totalOutputTokens += response.getOutputTokens();
                    return v;
                });
            }
        }

        for (DetectedAttack attack : attacks) {
            userAttacks.merge(attack.getUserId(), 1, Integer::sum);
        }

        for (User user : users) {
            UserTokenStats stats = userStats.getOrDefault(user.getId(), new UserTokenStats());
            int attackCount = userAttacks.getOrDefault(user.getId(), 0);
            String maxSeverity = attacks.stream()
                    .filter(a -> a.getUserId().equals(user.getId()))
                    .map(DetectedAttack::getSeverity)
                    .max(Comparator.comparingInt(AnalyticsService::severityScore))
                    .orElse("NONE");

            writer.printf("%s,%s,%d,%d,%d,%d,%d,%s%n",
                    user.getDisplayName(),
                    user.getEmail(),
                    stats.promptCount,
                    stats.totalInputTokens,
                    stats.totalOutputTokens,
                    stats.totalInputTokens + stats.totalOutputTokens,
                    attackCount,
                    maxSeverity
            );
        }

        writer.flush();
        writer.close();
        return baos.toByteArray();
    }

    public String exportAsJSON() {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportDate", Instant.now().toString());
        export.put("totalUsers", users.size());
        export.put("totalPrompts", prompts.size());
        export.put("totalTokensUsed", responses.stream().mapToInt(r -> r.getInputTokens() + r.getOutputTokens()).sum());
        export.put("totalAttacksDetected", attacks.size());
        export.put("topTricks", getTopTricks());
        export.put("userStats", getTokenUsageByUser());
        export.put("timeSeries", getTokenUsageTimeSeries());

        return convertToJSON(export);
    }

    private static int severityScore(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private String convertToJSON(Object obj) {
        // Simple JSON conversion (in production use Jackson/Gson)
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":");
                sb.append(convertToJSON(e.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(convertToJSON(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else if (obj instanceof String) {
            return "\"" + obj.toString().replace("\"", "\\\"") + "\"";
        }
        return obj != null ? obj.toString() : "null";
    }

    static class UserTokenStats {
        String displayName = "Unknown";
        String email = "unknown@example.com";
        int promptCount = 0;
        int totalInputTokens = 0;
        int totalOutputTokens = 0;
    }

    static class TrickStats {
        String description;
        String severity;
        int attempts = 0;
        Set<String> attemptingUsers = new HashSet<>();

        TrickStats(String description, String severity) {
            this.description = description;
            this.severity = severity;
        }
    }

    public record AttackHeatmapResponse(
            List<AttackData> attackTypes,
            int totalAttacks
    ) {
        public record AttackData(
                String name,
                int count,
                String severity,
                double percentage
        ) {}
    }

    public record TokenUsageByUserResponse(
            String displayName,
            String email,
            int prompts,
            int inputTokens,
            int outputTokens
    ) {
        public int totalTokens() {
            return inputTokens + outputTokens;
        }
    }

    public record TimeSeriesTokensResponse(
            String date,
            long tokens
    ) {}

    public record TopTricksResponse(
            String type,
            String description,
            int attempts,
            String severity,
            int usersAttempted
    ) {}
}
