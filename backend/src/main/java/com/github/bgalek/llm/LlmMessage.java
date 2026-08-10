package com.github.bgalek.llm;

public sealed interface LlmMessage permits LlmMessage.SystemMessage, LlmMessage.User {
    
    record SystemMessage(String content) implements LlmMessage {
    }

    record User(String content) implements LlmMessage {
    }
}

