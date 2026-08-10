package com.github.bgalek.database;

import java.time.Instant;

public class LlmResponse {
    private String id;
    private String promptId;
    private String responseText;
    private int inputTokens;
    private int outputTokens;
    private long latencyMs;
    private Instant timestamp;

    public LlmResponse(String id, String promptId, String responseText, int inputTokens, int outputTokens, long latencyMs, Instant timestamp) {
        this.id = id;
        this.promptId = promptId;
        this.responseText = responseText;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public String getPromptId() { return promptId; }
    public String getResponseText() { return responseText; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public long getLatencyMs() { return latencyMs; }
    public Instant getTimestamp() { return timestamp; }
}
