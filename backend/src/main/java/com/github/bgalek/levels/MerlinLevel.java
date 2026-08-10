package com.github.bgalek.levels;

import com.github.bgalek.llm.LlmRequest;

public interface MerlinLevel {

    int getOrder();

    LlmRequest prompt(String prompt, String secret);

    default boolean outputFilter(String output, String secret) {
        return false;
    }

    default boolean inputFilter(String input) {
        return false;
    }

    default String inputFilterResponse() {
        return "I have detected a manipulation attempt. Your question was blocked.";
    }

    default String outputFilterResponse() {
        return "Sorry I can't tell you that.";
    }

    default String getModel() {
        return "hackmerlin-gpt35";
    }

    String getLevelFinishedResponse();
}
