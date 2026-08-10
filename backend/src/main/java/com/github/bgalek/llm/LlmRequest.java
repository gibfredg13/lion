package com.github.bgalek.llm;

import java.util.List;

public record LlmRequest(
        String model,
        List<LlmMessage> messages,
        double temperature,
        int maxTokens
) {
    public LlmRequest(String model, List<LlmMessage> messages, double temperature) {
        this(model, messages, temperature, 4000);
    }
}
