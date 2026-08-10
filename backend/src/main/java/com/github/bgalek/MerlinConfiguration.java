package com.github.bgalek;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.github.bgalek.levels.MerlinLevel;
import com.github.bgalek.llm.AzureOpenAiLlmProvider;
import com.github.bgalek.llm.GeminiLlmProvider;
import com.github.bgalek.llm.LlmProvider;
import com.github.bgalek.llm.OllamaLlmProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.util.List;

@Configuration
@EnableConfigurationProperties({MerlinConfiguration.MerlinConfigurationProperties.class})
class MerlinConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    LlmProvider llmProvider(MerlinConfigurationProperties properties) {
        return switch (properties.llm.provider) {
            case "azure" -> new AzureOpenAiLlmProvider(azureOpenAiClient(properties));
            case "gemini" -> new GeminiLlmProvider(properties.llm.apiKey, properties.llm.defaultModel);
            case "ollama" -> new OllamaLlmProvider(properties.llm.baseUrl != null ? properties.llm.baseUrl : "http://localhost:11434");
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + properties.llm.provider);
        };
    }

    private OpenAIClient azureOpenAiClient(MerlinConfigurationProperties properties) {
        return new OpenAIClientBuilder()
                .credential(new AzureKeyCredential(properties.llm.apiKey))
                .endpoint(properties.llm.baseUrl)
                .buildClient();
    }

    @Bean
    MerlinService merlinService(List<MerlinLevel> levels,
                                MerlinConfigurationProperties properties,
                                LlmProvider llmProvider,
                                JdbcClient jdbcClient
    ) {
        MerlinLevelRepository merlinLevelRepository = new MerlinLevelRepository(levels);
        MerlinLeaderboardRepository merlinLeaderboardRepository = new MerlinLeaderboardRepository(jdbcClient);
        MerlinLogger merlinLogger = new MerlinLogger(jdbcClient);
        return new MerlinService(
                llmProvider,
                merlinLevelRepository,
                merlinLeaderboardRepository,
                merlinLogger,
                properties.passwords
        );
    }

    @Bean
    @Profile("default")
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry corsRegistry) {
                corsRegistry.addMapping("/api").allowedOrigins("http://localhost:3000");
            }
        };
    }


    @ConfigurationProperties(prefix = "merlin")
    record MerlinConfigurationProperties(MerlinLlmConfigurationProperties llm, List<String> passwords) {
        record MerlinLlmConfigurationProperties(
                String provider,
                String apiKey,
                String baseUrl,
                String defaultModel,
                String advancedModel
        ) {
        }
    }
}

