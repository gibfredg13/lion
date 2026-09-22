package com.github.bgalek.admin;

import com.github.bgalek.database.SettingsRepository;
import com.github.bgalek.llm.LlmBackendRouter;
import com.github.bgalek.llm.LlmMessage;
import com.github.bgalek.llm.LlmRequest;
import com.github.bgalek.llm.LlmResponse;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/** Reads, switches and tests the LLM backends on behalf of the admin dashboard. */
@Service
public class LlmBackendAdminService {

    private static final Logger logger = getLogger(LlmBackendAdminService.class);
    static final String ACTIVE_BACKEND_SETTING = "llm.activeBackend";

    private final LlmBackendRouter router;
    private final BackendHealthMonitor healthMonitor;
    private final SettingsRepository settings;

    public LlmBackendAdminService(LlmBackendRouter router, BackendHealthMonitor healthMonitor,
                                  SettingsRepository settings) {
        this.router = router;
        this.healthMonitor = healthMonitor;
        this.settings = settings;
    }

    public record BackendView(
            String id, String label, String kind, String baseUrl, String model,
            int maxConcurrent, int maxTokens, int waitSeconds, boolean mergeSystemMessages,
            boolean active, boolean healthy, long avgLatencyMs, int inFlight, int availableSlots,
            List<String> availableModels, Instant checkedAt, String error
    ) {}

    public record ActivateResult(String activeBackendId, boolean persisted, String note,
                                 List<BackendView> backends) {}

    public record TestResult(String backendId, boolean success, String response,
                             int inputTokens, int outputTokens, long latencyMs, String error) {}

    public List<BackendView> backends() {
        List<BackendView> out = new ArrayList<>();
        for (LlmBackendRouter.Backend backend : router.all()) {
            var spec = backend.spec();
            var health = healthMonitor.snapshot(backend.id());
            out.add(new BackendView(
                    backend.id(), backend.label(), spec.kind(), spec.baseUrl(),
                    backend.resolvedModel() == null ? spec.model() : backend.resolvedModel(),
                    spec.maxConcurrent(), spec.maxTokens(), spec.waitSeconds(), spec.mergeSystemMessages(),
                    backend.id().equals(router.activeId()), health.healthy(), health.avgLatencyMs(),
                    backend.inFlight(), backend.availableSlots(), health.availableModels(),
                    health.checkedAt(), health.error()));
        }
        return out;
    }

    /**
     * Switches, then records the choice.
     * <p>
     * That order on purpose: a database hiccup must not stop an operator moving the room off a
     * failing box mid-event. A failed write costs the choice on next restart, and says so.
     */
    public ActivateResult activate(String id) {
        LlmBackendRouter.Backend backend = router.activate(id);
        boolean persisted = true;
        String note = null;
        try {
            settings.put(ACTIVE_BACKEND_SETTING, id);
        } catch (RuntimeException e) {
            persisted = false;
            note = "Switched, but the choice could not be saved, so a restart will revert it: " + e.getMessage();
            logger.error("Could not persist the active LLM backend", e);
        }
        healthMonitor.refresh();
        return new ActivateResult(backend.id(), persisted, note, backends());
    }

    /** One real call against a named backend, without moving production traffic onto it. */
    public TestResult test(String id, String prompt) {
        LlmBackendRouter.Backend backend = router.byId(id)
                .orElseThrow(() -> new IllegalArgumentException("No LLM backend declared under id '" + id + "'"));
        long start = System.currentTimeMillis();
        try {
            LlmResponse response = backend.chat(new LlmRequest("", List.of(
                    new LlmMessage.SystemMessage("You are Leo, a proud guardian lion. Answer in one short sentence."),
                    new LlmMessage.User(prompt == null || prompt.isBlank() ? "Say hello." : prompt)), 0.7, 80));
            return new TestResult(id, true, response.content(), response.inputTokens(),
                    response.outputTokens(), System.currentTimeMillis() - start, null);
        } catch (RuntimeException e) {
            return new TestResult(id, false, null, 0, 0, System.currentTimeMillis() - start,
                    e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    /**
     * Restores the operator's last choice at boot.
     * <p>
     * Configuration seeds the default; the stored choice overrides it, but only when it still names
     * a declared backend - otherwise renaming one in YAML would boot the app onto nothing.
     */
    public void restorePersistedChoice(boolean force) {
        String persisted = settings.get(ACTIVE_BACKEND_SETTING).orElse(null);
        if (force) {
            if (persisted != null) {
                settings.delete(ACTIVE_BACKEND_SETTING);
                logger.warn("LLM backend: forced to '{}' by configuration; discarded the stored choice '{}'",
                        router.activeId(), persisted);
            }
            return;
        }
        if (persisted == null) {
            logger.info("LLM backend: {} (from configuration; no stored choice)", router.activeId());
        } else if (router.byId(persisted).isEmpty()) {
            logger.warn("Stored LLM backend '{}' is not declared; staying on '{}'. Fix the id in "
                    + "configuration to honour it, or set merlin.llm.forceBackend=true to discard it.",
                    persisted, router.activeId());
        } else {
            router.activate(persisted);
            logger.info("LLM backend: {} (restored from database)", persisted);
        }
    }
}
