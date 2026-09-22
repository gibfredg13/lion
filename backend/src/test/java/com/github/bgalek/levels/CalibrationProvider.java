package com.github.bgalek.levels;

import com.github.bgalek.llm.DgxSparkLlmProvider;
import com.github.bgalek.llm.LlmProvider;

/** The live model the opt-in calibration tests measure against. */
final class CalibrationProvider {

    private CalibrationProvider() {
    }

    static LlmProvider live() {
        String baseUrl = System.getenv().getOrDefault("MERLIN_LLM_BASEURL", "http://192.168.1.145:42000");
        return new DgxSparkLlmProvider(baseUrl, null, System.getenv("MERLIN_LLM_DEFAULTMODEL"), true, 320);
    }
}
