package com.github.bgalek.llm;

public record LlmResponse(
        String content,
        int inputTokens,
        int outputTokens
) {
    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
