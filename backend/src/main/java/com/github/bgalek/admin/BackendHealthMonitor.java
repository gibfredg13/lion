package com.github.bgalek.admin;

import com.github.bgalek.llm.LlmBackendRouter;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Keeps a recent health reading for every backend, refreshed in the background.
 * <p>
 * Probing on demand does not work here: the health check is a blocking HTTP GET with a five second
 * timeout, the dashboard polls every five seconds, and a switched-off box always spends the full
 * timeout. Two backends probed serially on the request thread would stall the dashboard for ten
 * seconds a poll. So the endpoints read a snapshot, and the snapshot is refreshed off to one side
 * with all backends probed in parallel.
 */
@Service
public class BackendHealthMonitor {

    private static final Logger logger = getLogger(BackendHealthMonitor.class);
    private static final long REFRESH_INTERVAL_MS = 10_000;

    public record HealthSnapshot(
            String backendId,
            boolean healthy,
            long avgLatencyMs,
            String resolvedModel,
            List<String> availableModels,
            Instant checkedAt,
            String error
    ) {}

    private final LlmBackendRouter router;
    private final Map<String, HealthSnapshot> snapshots = new ConcurrentHashMap<>();
    private volatile boolean started;

    public BackendHealthMonitor(LlmBackendRouter router) {
        this.router = router;
    }

    /** Started from an ApplicationRunner; this project has no scheduling enabled. */
    public synchronized void start() {
        if (started) return;
        started = true;
        Thread.ofVirtual().name("backend-health").start(() -> {
            while (true) {
                try {
                    refresh();
                    Thread.sleep(REFRESH_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (RuntimeException e) {
                    logger.warn("Backend health refresh failed", e);
                }
            }
        });
    }

    public void refresh() {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (LlmBackendRouter.Backend backend : router.all()) {
                pool.submit(() -> snapshots.put(backend.id(), probe(backend)));
            }
        }
    }

    private HealthSnapshot probe(LlmBackendRouter.Backend backend) {
        try {
            boolean healthy = backend.probeHealthy();
            return new HealthSnapshot(backend.id(), healthy, backend.averageLatencyMs(),
                    backend.resolvedModel(), healthy ? backend.availableModels() : List.of(),
                    Instant.now(), healthy ? null : "No answer from " + backend.spec().baseUrl());
        } catch (RuntimeException e) {
            return new HealthSnapshot(backend.id(), false, 0, backend.spec().model(), List.of(),
                    Instant.now(), e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    /** Never blocks. An unprobed backend reports unhealthy with a null timestamp, not a stall. */
    public HealthSnapshot snapshot(String backendId) {
        return snapshots.getOrDefault(backendId, new HealthSnapshot(
                backendId, false, 0, null, List.of(), null, "Not probed yet"));
    }
}
