package com.github.bgalek.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

public class GeminiLlmProvider implements LlmProvider {
    private static final Logger logger = getLogger(GeminiLlmProvider.class);
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public GeminiLlmProvider(String apiKey, String model) {
        this.httpClient = HttpClient.newHttpClient();
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        try {
            List<Map<String, Object>> contents = new ArrayList<>();
            
            for (LlmMessage msg : request.messages()) {
                String role = "user";
                String text = msg instanceof LlmMessage.SystemMessage system ? system.content() : ((LlmMessage.User) msg).content();
                
                contents.add(Map.of(
                        "role", role,
                        "parts", List.of(Map.of("text", text))
                ));
            }

            Map<String, Object> payload = Map.of(
                    "contents", contents,
                    "generationConfig", Map.of(
                            "temperature", request.temperature(),
                            "maxOutputTokens", request.maxTokens()
                    )
            );

            String jsonPayload = objectMapper.writeValueAsString(payload);
            String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + encodedApiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Gemini request failed with status " + response.statusCode() + ": " + response.body());
            }

            JsonNode responseBody = objectMapper.readTree(response.body());
            String content = responseBody.get("candidates")
                    .get(0)
                    .get("content")
                    .get("parts")
                    .get(0)
                    .get("text")
                    .asText();

            JsonNode usageMetadata = responseBody.get("usageMetadata");
            int inputTokens = usageMetadata.get("promptTokenCount").asInt();
            int outputTokens = usageMetadata.get("candidatesTokenCount").asInt();

            return new LlmResponse(content, inputTokens, outputTokens);
        } catch (Exception e) {
            logger.error("Gemini request failed", e);
            throw new RuntimeException("LLM request failed: " + e.getMessage(), e);
        }
    }
}
