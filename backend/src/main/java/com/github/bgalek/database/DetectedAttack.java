package com.github.bgalek.database;

import java.time.Instant;

public class DetectedAttack {
    private String id;
    private String userId;
    private String promptId;
    private String attackType;
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private String description;
    private Instant timestamp;

    public DetectedAttack(String id, String userId, String promptId, String attackType, String severity, String description, Instant timestamp) {
        this.id = id;
        this.userId = userId;
        this.promptId = promptId;
        this.attackType = attackType;
        this.severity = severity;
        this.description = description;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getPromptId() { return promptId; }
    public String getAttackType() { return attackType; }
    public String getSeverity() { return severity; }
    public String getDescription() { return description; }
    public Instant getTimestamp() { return timestamp; }
}
