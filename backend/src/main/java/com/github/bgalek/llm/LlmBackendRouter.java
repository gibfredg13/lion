package com.github.bgalek.llm;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Routes every LLM call to whichever box is currently selected.
 * <p>
 * The app has several inference backends available - a llama.cpp box and a vLLM box, at the time of
 * writing - and the difficulty curve is strongly model-specific, so being able to move between them
 * during an event matters more than picking one at deploy time.
 * <p>
 * Everything that talks to a model stores its provider in a {@code final} field for the object's
 * lifetime: {@code MerlinService}, {@code LevelDefinitionService}, {@code ConfigurableLevel} (one
 * instance per level), {@code CalibrationService}, {@code StressTestService}, {@code AdminLlmService}.
 * Rebuilding the bean would reach none of them. So the bean they all hold is this router, and the
 * choice lives behind one volatile field instead.
 */
public final class LlmBackendRouter implements LlmProvider {

    private static final Logger logger = getLogger(LlmBackendRouter.class);

    /** One declared box, as configured. */
    public record BackendSpec(
            String id,
            String label,
            String kind,
            String baseUrl,
            String apiKey,
            String model,
            int maxConcurrent,
            int maxTokens,
            boolean disableThinking,
            boolean mergeSystemMessages,
            int waitSeconds
    ) {}

    /**
     * A box plus its own throttle.
     * <p>
     * The throttle is per backend rather than one in front of the router, because the limit is a
     * property of the server: llama.cpp serves a fixed slot count and queues everything else, while
     * vLLM batches continuously and is happy with far more. A single shared semaphore would pin
     * every backend to the smallest one's limit, and would carry permits across a switch.
     */
    public static final class Backend implements LlmProvider {
        private final BackendSpec spec;
        private final LlmProvider raw;
        private final ThrottledLlmProvider throttled;

        Backend(BackendSpec spec, LlmProvider raw) {
            this.spec = spec;
            this.raw = raw;
            this.throttled = new ThrottledLlmProvider(raw, spec.maxConcurrent(), spec.waitSeconds());
        }

        @Override
        public LlmResponse chat(LlmRequest request) {
            return throttled.chat(request);
        }

        public String id() {
            return spec.id();
        }

        public String label() {
            return spec.label();
        }

        public BackendSpec spec() {
            return spec;
        }

        /** Callers queued or in flight against this box. */
        public int inFlight() {
            return throttled.getWaitingCount();
        }

        public int availableSlots() {
            return throttled.getAvailableSlots();
        }

        public String resolvedModel() {
            return raw instanceof DgxSparkLlmProvider d ? d.getResolvedModel() : spec.model();
        }

        public long averageLatencyMs() {
            return raw instanceof DgxSparkLlmProvider d ? d.getAverageLatencyMs() : 0;
        }

        public List<String> availableModels() {
            return raw instanceof DgxSparkLlmProvider d ? d.getAvailableModels() : List.of();
        }

        /** Blocking: hits the box. Callers on a request path should read a cached snapshot instead. */
        public boolean probeHealthy() {
            return !(raw instanceof DgxSparkLlmProvider d) || d.isHealthy();
        }
    }

    private final Map<String, Backend> backends = new LinkedHashMap<>();
    private final String configuredDefaultId;
    private volatile String activeId;
    private final List<Consumer<Backend>> switchListeners = new CopyOnWriteArrayList<>();

    public LlmBackendRouter(List<BackendSpec> specs, String configuredDefaultId, ProviderFactory factory) {
        if (specs == null || specs.isEmpty()) {
            throw new IllegalStateException("No LLM backends configured");
        }
        for (BackendSpec spec : specs) {
            // Constructing a backend costs no network I/O, so building them all eagerly is free
            // even for boxes that are switched off.
            backends.put(spec.id(), new Backend(spec, factory.create(spec)));
        }
        this.configuredDefaultId = backends.containsKey(configuredDefaultId)
                ? configuredDefaultId
                : backends.keySet().iterator().next();
        if (!this.configuredDefaultId.equals(configuredDefaultId)) {
            logger.warn("Configured active LLM backend '{}' is not declared; falling back to '{}'",
                    configuredDefaultId, this.configuredDefaultId);
        }
        this.activeId = this.configuredDefaultId;
        logger.info("LLM backends declared: {}; starting on '{}'", backends.keySet(), this.activeId);
    }

    /** Builds the concrete provider for a spec. Keeps the provider {@code switch} out of this class. */
    @FunctionalInterface
    public interface ProviderFactory {
        LlmProvider create(BackendSpec spec);
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        return active().chat(request);
    }

    public Backend active() {
        Backend backend = backends.get(activeId);
        if (backend == null) throw new IllegalStateException("Active LLM backend '" + activeId + "' vanished");
        return backend;
    }

    public String activeId() {
        return activeId;
    }

    public String configuredDefaultId() {
        return configuredDefaultId;
    }

    public Collection<Backend> all() {
        return new ArrayList<>(backends.values());
    }

    public Optional<Backend> byId(String id) {
        return Optional.ofNullable(backends.get(id));
    }

    /**
     * Freezes the current choice for the caller.
     * <p>
     * A calibration run is several hundred calls over several minutes, and it resolves the active
     * backend per call. Switching halfway through would split one run across two boxes and make the
     * backend recorded against it a lie, so a run takes a handle here and keeps it.
     */
    public Backend pin() {
        return active();
    }

    /** @throws IllegalArgumentException if no backend is declared under that id. */
    public Backend activate(String id) {
        Backend backend = backends.get(id);
        if (backend == null) {
            throw new IllegalArgumentException("No LLM backend declared under id '" + id + "'");
        }
        String previous = activeId;
        activeId = id;
        if (!id.equals(previous)) {
            logger.info("LLM backend switched from '{}' to '{}' ({})", previous, id, backend.label());
            // In-flight calls finish against the old box on purpose: cancelling them would cost a
            // player their turn to an operator action they cannot see.
            for (Consumer<Backend> listener : switchListeners) {
                try {
                    listener.accept(backend);
                } catch (RuntimeException e) {
                    logger.warn("An LLM backend switch listener failed", e);
                }
            }
        }
        return backend;
    }

    /** Notified after the active backend changes. Used to drop caches keyed on the old box's answers. */
    public void onSwitch(Consumer<Backend> listener) {
        switchListeners.add(listener);
    }
}
