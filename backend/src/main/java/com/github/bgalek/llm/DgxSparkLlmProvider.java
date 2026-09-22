package com.github.bgalek.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * OpenAI-compatible LLM provider for the DGX Spark.
 * Connects to the local DGX Spark instance to provide LLM completions.
 */
public class DgxSparkLlmProvider implements LlmProvider {
    private static final Logger logger = getLogger(DgxSparkLlmProvider.class);
    private final String baseUrl;
    private final String apiKey;
    /**
     * Reasoning models (Qwen3, DeepSeek-R1, ...) put their chain-of-thought in `reasoning_content`
     * and the actual answer in `content`. Left to think freely they burn the whole token budget on
     * reasoning and return an EMPTY `content`, which reaches players as a blank reply from Leo.
     * Sending chat_template_kwargs.enable_thinking=false suppresses that.
     */
    private final boolean disableThinking;
    /** Upper bound on generated tokens. Levels ask Leo to be brief; long replies just slow the queue. */
    private final int maxTokensCap;
    /**
     * Collapse consecutive system messages into one before sending.
     * <p>
     * vLLM serving Qwen3.6 rejects a second system message outright - HTTP 400, "System message
     * must be at the beginning." Every level here sends between two and five of them, so without
     * this every level fails against that server with no usable error reaching the player. Joining
     * them with a blank line is equivalent for servers that accept the list form.
     */
    private final boolean mergeSystemMessages;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong lastLatencyMs = new AtomicLong(0);
    /** Resolved lazily from /v1/models when no model is configured. */
    private volatile String resolvedModel;
    /**
     * When the last /v1/models probe failed, as epoch millis.
     * <p>
     * resolveModel() holds the monitor while it probes, and a failed probe leaves resolvedModel
     * null - so against a box that is switched off, every single call queued behind another five
     * second timeout. Backing off means a dead backend costs one timeout a minute, not one per call.
     */
    private volatile long lastModelProbeFailedAt;
    private static final long MODEL_PROBE_BACKOFF_MS = 60_000;

    private final long[] latencyHistory = new long[20];
    private int latencyHistoryIndex = 0;
    private int latencyHistoryCount = 0;

    public DgxSparkLlmProvider(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, null, true, 320, true);
    }

    public DgxSparkLlmProvider(String baseUrl, String apiKey, String configuredModel, boolean disableThinking, int maxTokensCap) {
        this(baseUrl, apiKey, configuredModel, disableThinking, maxTokensCap, true);
    }

    public DgxSparkLlmProvider(String baseUrl, String apiKey, String configuredModel, boolean disableThinking,
                               int maxTokensCap, boolean mergeSystemMessages) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.resolvedModel = (configuredModel == null || configuredModel.isBlank()) ? null : configuredModel;
        this.disableThinking = disableThinking;
        this.mergeSystemMessages = mergeSystemMessages;
        this.maxTokensCap = maxTokensCap > 0 ? maxTokensCap : 320;
        this.httpClient = HttpClient.newBuilder()
                // Pinned to HTTP/1.1. Java's default is HTTP/2, which over cleartext means an h2c
                // upgrade attempt - and vLLM behind uvicorn answers that by dropping the request
                // body, then rejecting the request for having no body:
                //   400 {'type': 'missing', 'loc': 'body', 'input': 'None'}
                // llama.cpp tolerated the upgrade, so this only surfaced on moving to vLLM, and it
                // reads like a malformed payload rather than a protocol problem.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Returns the model id to send, discovering it from /v1/models on first use when unconfigured.
     * llama.cpp tolerates a missing `model` field but vLLM and TGI reject the request outright,
     * so we always try to send a real one.
     */
    private String resolveModel() {
        String current = resolvedModel;
        if (current != null) return current;
        // Outside the monitor on purpose: a backed-off caller must not queue behind the one
        // caller that is currently spending five seconds discovering the box is still down.
        if (System.currentTimeMillis() - lastModelProbeFailedAt < MODEL_PROBE_BACKOFF_MS) return null;
        synchronized (this) {
            if (resolvedModel != null) return resolvedModel;
            if (System.currentTimeMillis() - lastModelProbeFailedAt < MODEL_PROBE_BACKOFF_MS) return null;
            List<String> models = getAvailableModels();
            if (!models.isEmpty()) {
                resolvedModel = models.get(0);
                lastModelProbeFailedAt = 0;
                logger.info("Auto-detected LLM model from {}/v1/models: {}", baseUrl, resolvedModel);
            } else {
                lastModelProbeFailedAt = System.currentTimeMillis();
                logger.warn("Could not auto-detect a model from {}/v1/models - sending requests without a "
                        + "model field, and not probing again for {}s", baseUrl, MODEL_PROBE_BACKOFF_MS / 1000);
            }
            return resolvedModel;
        }
    }

    /**
     * Turns the request's messages into the OpenAI wire form, optionally collapsing a run of system
     * messages into a single one.
     * <p>
     * The levels are written as a list of separate system messages because they read better that
     * way and because each rung of the ladder adds one. Some servers take that list; vLLM serving
     * Qwen3.6 does not, and answers HTTP 400 for the second one. Joining a run with a blank line
     * preserves both the content and its order.
     */
    List<Map<String, String>> buildMessages(List<LlmMessage> source) {
        List<Map<String, String>> messages = new ArrayList<>();
        StringBuilder pendingSystem = null;
        for (LlmMessage msg : source) {
            if (msg instanceof LlmMessage.SystemMessage sm) {
                if (mergeSystemMessages) {
                    if (pendingSystem == null) pendingSystem = new StringBuilder(sm.content());
                    else pendingSystem.append("\n\n").append(sm.content());
                } else {
                    messages.add(Map.of("role", "system", "content", sm.content()));
                }
            } else if (msg instanceof LlmMessage.User um) {
                if (pendingSystem != null) {
                    messages.add(Map.of("role", "system", "content", pendingSystem.toString()));
                    pendingSystem = null;
                }
                messages.add(Map.of("role", "user", "content", um.content()));
            }
        }
        // A request that is nothing but system messages still has to send them.
        if (pendingSystem != null) messages.add(Map.of("role", "system", "content", pendingSystem.toString()));
        return messages;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        try {
            long startTime = System.currentTimeMillis();

            var bodyMap = new java.util.HashMap<String, Object>();
            String model = (request.model() != null && !request.model().isBlank()) ? request.model() : resolveModel();
            if (model != null && !model.isBlank()) bodyMap.put("model", model);
            bodyMap.put("temperature", request.temperature());
            int maxTokens = request.maxTokens() > 0 ? Math.min(request.maxTokens(), maxTokensCap) : maxTokensCap;
            bodyMap.put("max_tokens", maxTokens);
            if (disableThinking) {
                bodyMap.put("chat_template_kwargs", Map.of("enable_thinking", false));
            }

            bodyMap.put("messages", buildMessages(request.messages()));

            String requestBody = objectMapper.writeValueAsString(bodyMap);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + (apiKey != null ? apiKey : "empty"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            long endTime = System.currentTimeMillis();
            recordLatency(endTime - startTime);

            if (response.statusCode() >= 400) {
                logger.error("Error from LLM API at {}: HTTP {} {}", baseUrl, response.statusCode(), response.body());
                // 5xx is the server failing; 4xx is us sending something it will never accept, and
                // retrying either against the same box is pointless. Only 5xx counts as "unreachable".
                if (response.statusCode() >= 500) {
                    throw new LlmUnavailableException("HTTP " + response.statusCode() + " from " + baseUrl);
                }
                throw new RuntimeException("HTTP Error " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choice = root.path("choices").get(0);
            JsonNode message = choice.path("message");
            String content = message.path("content").asText("");
            String finishReason = choice.path("finish_reason").asText("");

            // A reasoning model that ran out of budget returns empty content plus a long
            // reasoning_content. Falling back keeps Leo talking instead of going silent.
            if (content.isBlank()) {
                // vLLM spells this `reasoning`; llama.cpp and the Qwen docs spell it
                // `reasoning_content`. Accept either, or an empty reply reaches the player.
                String reasoning = message.path("reasoning_content").asText("");
                if (reasoning.isBlank()) reasoning = message.path("reasoning").asText("");
                if (!reasoning.isBlank()) {
                    logger.warn("LLM returned empty content with {} chars of reasoning_content (finish_reason={}). "
                            + "Falling back to reasoning_content - set merlin.llm.disableThinking=true to avoid this.",
                            reasoning.length(), finishReason);
                    content = reasoning;
                }
            }
            if ("length".equals(finishReason)) {
                logger.warn("LLM response truncated at max_tokens={} - the reply reaching the player is cut off", maxTokens);
            }

            int inputTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int outputTokens = root.path("usage").path("completion_tokens").asInt(0);

            return new LlmResponse(content, inputTokens, outputTokens);
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (ConnectException | HttpTimeoutException e) {
            // The box is off, or not answering. Callers that measure - calibration especially -
            // need this told apart from a model that answered and refused.
            logger.error("LLM at {} is unreachable", baseUrl, e);
            throw new LlmUnavailableException("Cannot reach the LLM at " + baseUrl + ": " + e, e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Also an IOException, but ours, not the network's - it must not read as "box is down".
            logger.error("Could not read or write JSON for the LLM at {}", baseUrl, e);
            throw new RuntimeException("Malformed LLM payload at " + baseUrl, e);
        } catch (IOException e) {
            logger.error("LLM at {} failed mid-request", baseUrl, e);
            throw new LlmUnavailableException("Lost the connection to the LLM at " + baseUrl + ": " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException("Interrupted waiting on the LLM at " + baseUrl, e);
        } catch (Exception e) {
            logger.error("Failed to chat with the LLM at {}", baseUrl, e);
            throw new RuntimeException("Failed to chat with the LLM at " + baseUrl, e);
        }
    }

    private synchronized void recordLatency(long latency) {
        lastLatencyMs.set(latency);
        latencyHistory[latencyHistoryIndex] = latency;
        latencyHistoryIndex = (latencyHistoryIndex + 1) % latencyHistory.length;
        if (latencyHistoryCount < latencyHistory.length) {
            latencyHistoryCount++;
        }
    }

    public long getLatencyMs() {
        return lastLatencyMs.get();
    }

    public synchronized long getAverageLatencyMs() {
        if (latencyHistoryCount == 0) return 0;
        long sum = 0;
        for (int i = 0; i < latencyHistoryCount; i++) {
            sum += latencyHistory[i];
        }
        return sum / latencyHistoryCount;
    }

    /** The model actually being sent to the LLM, or null if none could be resolved. */
    public String getResolvedModel() {
        return resolvedModel;
    }

    public List<String> getAvailableModels() {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/models"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + (apiKey != null ? apiKey : "empty"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response.body());
            List<String> models = new ArrayList<>();
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode model : data) {
                    models.add(model.path("id").asText());
                }
            }
            return models;
        } catch (Exception e) {
            logger.error("Failed to fetch available models from DGX Spark", e);
            return Collections.emptyList();
        }
    }

    public boolean isHealthy() {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/models"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + (apiKey != null ? apiKey : "empty"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
