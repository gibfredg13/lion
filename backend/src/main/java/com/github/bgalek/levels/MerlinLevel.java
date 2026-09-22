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
        return "🦁 Nice try! I spotted that tactic. Leo doesn't fall for that.";
    }

    default String outputFilterResponse() {
        return "🦁 I almost revealed it — but my defenses caught that. Try a different approach!";
    }

    default String getModel() {
        return "";
    }

    String getLevelFinishedResponse();
}
