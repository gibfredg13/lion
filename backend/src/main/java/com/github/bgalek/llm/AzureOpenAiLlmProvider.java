package com.github.bgalek.llm;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

public class AzureOpenAiLlmProvider implements LlmProvider {
    private static final Logger logger = getLogger(AzureOpenAiLlmProvider.class);
    private final OpenAIClient client;

    public AzureOpenAiLlmProvider(OpenAIClient client) {
        this.client = client;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        List<ChatRequestMessage> messages = new ArrayList<>();
        for (LlmMessage msg : request.messages()) {
            if (msg instanceof LlmMessage.SystemMessage system) {
                messages.add(new ChatRequestSystemMessage(system.content()));
            } else if (msg instanceof LlmMessage.User user) {
                messages.add(new ChatRequestUserMessage(user.content()));
            }
        }

        ChatCompletionsOptions options = new ChatCompletionsOptions(messages);
        options.setTemperature(request.temperature());
        options.setMaxTokens(request.maxTokens());

        try {
            var completion = client.getChatCompletions(request.model(), options);
            String content = completion.getChoices().stream()
                    .findFirst()
                    .map(choice -> choice.getMessage().getContent())
                    .orElse("");

            int inputTokens = completion.getUsage().getPromptTokens();
            int outputTokens = completion.getUsage().getCompletionTokens();

            return new LlmResponse(content, inputTokens, outputTokens);
        } catch (Exception e) {
            logger.error("Azure OpenAI request failed", e);
            throw new RuntimeException("LLM request failed: " + e.getMessage(), e);
        }
    }
}
