package com.github.bgalek.llm;

public sealed interface LlmMessage permits LlmMessage.System, LlmMessage.User {
    
    record System(String content) implements LlmMessage {
    }

    record User(String content) implements LlmMessage {
    }
}
