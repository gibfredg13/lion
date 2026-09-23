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
        return "I will not answer that. Try asking a different way.";
    }

    default String outputFilterResponse() {
        return "I started to answer, then thought better of it. Try a different way in.";
    }

    default String getModel() {
        return "";
    }

    String getLevelFinishedResponse();
}
