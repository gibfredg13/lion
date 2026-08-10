package com.github.bgalek.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

public class OllamaLlmProvider implements LlmProvider {
    private static final Logger logger = getLogger(OllamaLlmProvider.class);
    private final HttpClient httpClient;
    private final URI baseUrl;
    private final ObjectMapper objectMapper;

    public OllamaLlmProvider(String baseUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            for (LlmMessage msg : request.messages()) {
                if (msg instanceof LlmMessage.SystemMessage system) {
                    messages.add(Map.of("role", "system", "content", system.content()));
                } else if (msg instanceof LlmMessage.User user) {
                    messages.add(Map.of("role", "user", "content", user.content()));
                }
            }

            Map<String, Object> payload = Map.of(
                    "model", request.model(),
                    "messages", messages,
                    "temperature", request.temperature(),
                    "stream", false
            );

            String jsonPayload = objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(baseUrl.resolve("api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Ollama request failed with status " + response.statusCode() + ": " + response.body());
            }

            JsonNode responseBody = objectMapper.readTree(response.body());
            String content = responseBody.get("message").get("content").asText();
            
            // Ollama doesn't return token counts, estimate based on content
            int outputTokens = estimateTokens(content);
            int inputTokens = estimateTokens(request.messages().stream()
                    .map(msg -> msg instanceof LlmMessage.SystemMessage s ? s.content() : ((LlmMessage.User) msg).content())
                    .reduce("", (a, b) -> a + " " + b));

            return new LlmResponse(content, inputTokens, outputTokens);
        } catch (Exception e) {
            logger.error("Ollama request failed", e);
            throw new RuntimeException("LLM request failed: " + e.getMessage(), e);
        }
    }

    private int estimateTokens(String text) {
        // Rough estimate: ~4 characters per token
        return Math.max(1, text.length() / 4);
    }
}
