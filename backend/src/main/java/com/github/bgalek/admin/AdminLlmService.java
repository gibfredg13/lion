package com.github.bgalek.admin;

import com.github.bgalek.llm.LlmProvider;
import com.github.bgalek.llm.LlmRequest;
import com.github.bgalek.llm.LlmResponse;
import com.github.bgalek.llm.LlmMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AdminLlmService {
    private final LlmProvider llmProvider;
    private final List<LlmTestResult> testHistory = new ArrayList<>();
    private String currentProvider = "azure";
    private String defaultModel = "gpt-4";

    public AdminLlmService(LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    public AdminLlmConfigResponse getCurrentConfig() {
        return new AdminLlmConfigResponse(
                currentProvider,
                defaultModel,
                "***hidden***"
        );
    }

    public AdminLlmConfigResponse updateConfig(AdminLlmConfigRequest request) {
        this.currentProvider = request.provider();
        this.defaultModel = request.model();
        return getCurrentConfig();
    }

    public LlmTestResponse testPrompt(AdminLlmTestRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            LlmRequest llmRequest = new LlmRequest(
                    request.model() != null ? request.model() : defaultModel,
                    List.of(
                            new LlmMessage.SystemMessage(request.systemPrompt() != null ? request.systemPrompt() : "You are a helpful assistant."),
                            new LlmMessage.User(request.prompt())
                    ),
                    request.temperature() != null ? request.temperature() : 0.7,
                    request.maxTokens() != null ? request.maxTokens() : 2000
            );

            LlmResponse response = llmProvider.chat(llmRequest);
            long endTime = System.currentTimeMillis();
            long latencyMs = endTime - startTime;

            LlmTestResult result = new LlmTestResult(
                    Instant.now(),
                    currentProvider,
                    request.model() != null ? request.model() : defaultModel,
                    response.inputTokens(),
                    response.outputTokens(),
                    latencyMs
            );
            testHistory.add(result);

            return new LlmTestResponse(
                    true,
                    response.content(),
                    response.inputTokens(),
                    response.outputTokens(),
                    response.inputTokens() + response.outputTokens(),
                    latencyMs,
                    null
            );
        } catch (Exception e) {
            return new LlmTestResponse(
                    false,
                    null,
                    0,
                    0,
                    0,
                    System.currentTimeMillis() - startTime,
                    e.getMessage()
            );
        }
    }

    public List<LlmTestResult> getTestHistory() {
        return new ArrayList<>(testHistory);
    }

    public record AdminLlmConfigResponse(
            String currentProvider,
            String currentModel,
            String apiKeyMasked
    ) {
    }

    public record AdminLlmConfigRequest(
            String provider,
            String model,
            String apiKey,
            String baseUrl
    ) {
    }

    public record AdminLlmTestRequest(
            String prompt,
            String systemPrompt,
            String model,
            Double temperature,
            Integer maxTokens
    ) {
    }

    public record LlmTestResponse(
            boolean success,
            String response,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            long latencyMs,
            String error
    ) {
    }

    public record LlmTestResult(
            Instant timestamp,
            String provider,
            String model,
            int inputTokens,
            int outputTokens,
            long latencyMs
    ) {
    }
}
