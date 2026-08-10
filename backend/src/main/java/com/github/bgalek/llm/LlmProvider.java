package com.github.bgalek.llm;

public interface LlmProvider {
    LlmResponse chat(LlmRequest request);
}
