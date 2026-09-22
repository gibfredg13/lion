package com.github.bgalek.llm;

/** Thrown when every LLM slot is occupied. Surfaced to the player in character, never as a 500. */
public class LlmBusyException extends RuntimeException {
    public LlmBusyException() {
        super("All LLM slots are busy");
    }
}
