package com.github.bgalek;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.github.bgalek.levels.LevelDefinition;
import com.github.bgalek.levels.LevelDefinitionService;
import com.github.bgalek.levels.MerlinLevel;
import com.github.bgalek.llm.AzureOpenAiLlmProvider;
import com.github.bgalek.llm.GeminiLlmProvider;
import com.github.bgalek.llm.LlmBackendRouter;
import com.github.bgalek.llm.LlmProvider;
import com.github.bgalek.llm.OllamaLlmProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.github.bgalek.llm.DgxSparkLlmProvider;
import com.github.bgalek.admin.LevelGateService;
import org.springframework.context.annotation.Profile;
import com.github.bgalek.database.UserRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties({MerlinConfiguration.MerlinConfigurationProperties.class})
class MerlinConfiguration {
    @Bean
    com.github.bgalek.admin.AdminLeaderboardService adminLeaderboardService(JdbcClient jdbcClient,
                                                                           LevelDefinitionService levelDefinitionService,
                                                                           com.github.bgalek.session.GameSessionService gameSessions) {
        return new com.github.bgalek.admin.AdminLeaderboardService(
                jdbcClient, levelDefinitionService.count(), gameSessions::activeId);
    }

    /**
     * Brings the database up to the game-session model and reconstructs the level timeline for
     * players who were already mid-game when it shipped.
     * <p>
     * One runner rather than two: the timeline backfill writes rows that need a session id to be
     * stamped with, and two runners have no defined relative order between them. There is no
     * migration tool in this project, so this stands in for one - runners execute after the
     * context refresh, hence after schema.sql.
     */
    @Bean
    org.springframework.boot.ApplicationRunner gameSessionBootstrap(
            JdbcClient jdbcClient,
            com.github.bgalek.database.SettingsRepository settings,
            com.github.bgalek.session.GameSessionService gameSessions) {
        MerlinLevelProgressRepository levelProgressRepository = new MerlinLevelProgressRepository(jdbcClient);
        return args -> new com.github.bgalek.session.GameSessionBootstrap(
                jdbcClient, settings, gameSessions,
                levelProgressRepository::backfillFromLogsIfEmpty).run();
    }

    /**
     * Restores the admin's last backend choice and starts health probing.
     * <p>
     * A runner rather than the router's constructor: prod sets lazy-initialization, and runners are
     * the project's existing stand-in for migrations because they execute after schema.sql.
     */
    @Bean
    org.springframework.boot.ApplicationRunner llmBackendRestore(
            com.github.bgalek.admin.LlmBackendAdminService backendAdminService,
            com.github.bgalek.admin.BackendHealthMonitor healthMonitor,
            com.github.bgalek.tv.TvService tvService,
            com.github.bgalek.auth.EventAccessCodeService eventAccessCodeService,
            MerlinConfigurationProperties properties) {
        return args -> {
            backendAdminService.restorePersistedChoice(properties.llm.forceBackendOrDefault());
            healthMonitor.start();
            tvService.loadFeedSetting();
            eventAccessCodeService.loadFromSettings();
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The one provider every collaborator holds. Each declared backend carries its own throttle, so
     * the judge call at levels 6 and 7 is bounded too, and a four-slot llama.cpp box and a
     * continuously-batching vLLM box can each have the limit that actually fits them.
     */
    @Bean
    @Primary
    LlmBackendRouter llmProvider(MerlinConfigurationProperties properties) {
        return new LlmBackendRouter(backendSpecs(properties), properties.llm.activeBackendOrDefault(),
                this::rawLlmProvider);
    }

    /**
     * The declared backends, or a single one synthesised from the legacy flat {@code merlin.llm.*}
     * block when none are declared - an existing deployment's .env must keep working untouched.
     */
    private static List<LlmBackendRouter.BackendSpec> backendSpecs(MerlinConfigurationProperties properties) {
        var llm = properties.llm;
        if (llm.backends() == null || llm.backends().isEmpty()) {
            return List.of(new LlmBackendRouter.BackendSpec(
                    "default", "Configured backend", llm.provider, llm.baseUrl, llm.apiKey,
                    llm.defaultModel, llm.maxConcurrentOrDefault(), llm.maxTokensOrDefault(),
                    llm.disableThinkingOrDefault(), llm.mergeSystemMessagesOrDefault(), 45));
        }
        List<LlmBackendRouter.BackendSpec> specs = new java.util.ArrayList<>();
        llm.backends().forEach((id, b) -> specs.add(new LlmBackendRouter.BackendSpec(
                id,
                b.label() == null || b.label().isBlank() ? id : b.label(),
                b.kind() == null || b.kind().isBlank() ? llm.provider : b.kind(),
                b.baseUrl(),
                b.apiKey() == null ? llm.apiKey : b.apiKey(),
                b.model(),
                b.maxConcurrent() == null || b.maxConcurrent() <= 0 ? llm.maxConcurrentOrDefault() : b.maxConcurrent(),
                b.maxTokens() == null || b.maxTokens() <= 0 ? llm.maxTokensOrDefault() : b.maxTokens(),
                b.disableThinking() == null ? llm.disableThinkingOrDefault() : b.disableThinking(),
                b.mergeSystemMessages() == null ? llm.mergeSystemMessagesOrDefault() : b.mergeSystemMessages(),
                b.waitSeconds() == null || b.waitSeconds() <= 0 ? 45 : b.waitSeconds())));
        return specs;
    }

    /** Unchanged provider selection, now per backend rather than once for the whole application. */
    private LlmProvider rawLlmProvider(LlmBackendRouter.BackendSpec spec) {
        return switch (spec.kind()) {
            case "azure" -> new AzureOpenAiLlmProvider(azureOpenAiClient(spec.apiKey(), spec.baseUrl()));
            case "gemini" -> new GeminiLlmProvider(spec.apiKey(), spec.model());
            case "ollama" -> new OllamaLlmProvider(spec.baseUrl() != null ? spec.baseUrl() : "http://localhost:11434");
            case "dgxspark" -> new DgxSparkLlmProvider(
                    spec.baseUrl(), spec.apiKey(), spec.model(),
                    spec.disableThinking(), spec.maxTokens(), spec.mergeSystemMessages());
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + spec.kind());
        };
    }

    private OpenAIClient azureOpenAiClient(String apiKey, String baseUrl) {
        return new OpenAIClientBuilder()
                .credential(new AzureKeyCredential(apiKey))
                .endpoint(baseUrl)
                .buildClient();
    }

    @Bean
    LevelDefinitionService levelDefinitionService(MerlinConfigurationProperties properties, LlmProvider llmProvider) {
        if (properties.levels() == null || properties.levels().isEmpty()) {
            throw new IllegalStateException("No levels configured - check that levels.yml is on the classpath "
                    + "and imported via spring.config.import");
        }
        return new LevelDefinitionService(properties.levels(), llmProvider);
    }

    @Bean
    MerlinService merlinService(LevelDefinitionService levelDefinitionService,
                                MerlinConfigurationProperties properties,
                                LlmBackendRouter llmProvider,
                                JdbcClient jdbcClient,
                                com.github.bgalek.admin.LevelGateService levelGateService,
                                UserRepository userRepository,
                                com.github.bgalek.session.GameSessionService gameSessions
    ) {
        MerlinLevelRepository merlinLevelRepository = new MerlinLevelRepository(levelDefinitionService);
        MerlinLeaderboardRepository merlinLeaderboardRepository = new MerlinLeaderboardRepository(jdbcClient);
        MerlinLogger merlinLogger = new MerlinLogger(jdbcClient);
        MerlinLevelProgressRepository levelProgressRepository = new MerlinLevelProgressRepository(jdbcClient);
        MerlinService merlinService = new MerlinService(
                llmProvider,
                merlinLevelRepository,
                merlinLeaderboardRepository,
                merlinLogger,
                levelProgressRepository,
                properties.passwords,
                llmProvider::activeId,
                levelGateService,
                userRepository,
                gameSessions
        );
        // The cached answer came from a particular box. Once a different one is answering, the
        // cache is stale by definition.
        llmProvider.onSwitch(backend -> merlinService.invalidateCache());
        return merlinService;
    }

    /**
     * Loopback and private LAN ranges. Patterns rather than exact origins because the machine's IP
     * is not known in advance, and an event is usually reached over the LAN by address rather than
     * by "localhost" - an allowlist of only localhost locks out every other machine in the room
     * with an opaque 403 "Invalid CORS request".
     * <p>
     * Still far narrower than the "*" this replaced, which let any website on the internet make
     * credentialed calls with a logged-in player's cookie. Add public hostnames via
     * merlin.cors.allowedOrigins, which replaces this list entirely when set.
     */
    private static final List<String> DEFAULT_CORS_PATTERNS = List.of(
            "http://localhost:[*]",
            "http://127.0.0.1:[*]",
            "http://192.168.*.*:[*]",
            "http://10.*.*.*:[*]",
            "http://172.16.*.*:[*]",
            "http://[::1]:[*]");

    /**
     * `allowedOriginPatterns("*")` together with `allowCredentials(true)` let any website on the
     * internet make authenticated calls with a logged-in player's cookie. Origins are now an
     * explicit allowlist (merlin.cors.allowedOrigins), defaulting to local dev only.
     */
    @Bean
    WebMvcConfigurer corsConfigurer(MerlinConfigurationProperties properties) {
        List<String> origins = (properties.cors() == null || properties.cors().allowedOrigins() == null
                || properties.cors().allowedOrigins().isEmpty())
                ? DEFAULT_CORS_PATTERNS
                : properties.cors().allowedOrigins();
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry corsRegistry) {
                corsRegistry.addMapping("/api/**")
                        .allowedOriginPatterns(origins.toArray(new String[0]))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }


    @ConfigurationProperties(prefix = "merlin")
    record MerlinConfigurationProperties(MerlinLlmConfigurationProperties llm, List<String> passwords,
                                        MerlinCorsConfigurationProperties cors,
                                        List<LevelDefinition> levels) {
        record MerlinCorsConfigurationProperties(List<String> allowedOrigins) {
        }

        record MerlinLlmConfigurationProperties(
                String provider,
                String apiKey,
                String baseUrl,
                String defaultModel,
                String advancedModel,
                /** Suppress reasoning-model chain-of-thought so `content` is never empty. */
                Boolean disableThinking,
                /** Hard cap on generated tokens; keeps replies short and the queue moving. */
                Integer maxTokens,
                /** Max simultaneous in-flight LLM calls. Match this to llama.cpp's --parallel. */
                Integer maxConcurrent,
                /**
                 * Collapse the levels' several system messages into one before sending.
                 * <p>
                 * vLLM serving Qwen3.6 answers HTTP 400 for a second system message, and every
                 * level sends between two and five. Defaults to true: required by that server,
                 * and harmless on the ones that accept the list form.
                 */
                Boolean mergeSystemMessages,
                /** Which entry of {@link #backends} to start on; the persisted admin choice wins. */
                String activeBackend,
                /** True ignores and clears any stored choice, booting on {@link #activeBackend}. */
                Boolean forceBackend,
                /** Empty means single-backend mode, synthesised from the flat fields above. */
                Map<String, MerlinLlmBackendProperties> backends
        ) {
            /** One declared box. Anything left null falls back to the flat block's value. */
            record MerlinLlmBackendProperties(
                    String label,
                    /** Provider kind: dgxspark, azure, gemini, ollama. */
                    String kind,
                    String baseUrl,
                    String apiKey,
                    String model,
                    Integer maxConcurrent,
                    Integer maxTokens,
                    Boolean disableThinking,
                    Boolean mergeSystemMessages,
                    Integer waitSeconds
            ) {}

            public boolean disableThinkingOrDefault() {
                return disableThinking == null || disableThinking;
            }

            public int maxTokensOrDefault() {
                return maxTokens == null || maxTokens <= 0 ? 320 : maxTokens;
            }

            public int maxConcurrentOrDefault() {
                return maxConcurrent == null || maxConcurrent <= 0 ? 4 : maxConcurrent;
            }

            public boolean mergeSystemMessagesOrDefault() {
                return mergeSystemMessages == null || mergeSystemMessages;
            }

            /** Discard a stored choice at boot. The way back when a saved backend has died. */
            public boolean forceBackendOrDefault() {
                return forceBackend != null && forceBackend;
            }

            public String activeBackendOrDefault() {
                return activeBackend == null || activeBackend.isBlank() ? "default" : activeBackend;
            }
        }
    }
}

