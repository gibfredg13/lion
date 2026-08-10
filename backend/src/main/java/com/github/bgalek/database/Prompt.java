package com.github.bgalek.database;

import java.time.Instant;

public class Prompt {
    private String id;
    private String userId;
    private String sessionId;
    private String promptText;
    private String model;
    private int level;
    private Instant timestamp;

    public Prompt(String id, String userId, String sessionId, String promptText, String model, int level, Instant timestamp) {
        this.id = id;
        this.userId = userId;
        this.sessionId = sessionId;
        this.promptText = promptText;
        this.model = model;
        this.level = level;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public String getPromptText() { return promptText; }
    public String getModel() { return model; }
    public int getLevel() { return level; }
    public Instant getTimestamp() { return timestamp; }
}
