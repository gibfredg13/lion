package com.github.bgalek.admin;

import com.github.bgalek.llm.LlmMessage;
import com.github.bgalek.llm.LlmBackendRouter;
import com.github.bgalek.llm.LlmProvider;
import com.github.bgalek.llm.LlmRequest;
import com.github.bgalek.llm.LlmResponse;
import com.github.bgalek.llm.LlmUnavailableException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
public class StressTestService {

    public record StressResult(
            String id,
            Instant startedAt,
            Instant completedAt,
            String status,  // RUNNING, COMPLETED, FAILED, CANCELLED
            String backendId,
            String backendLabel,
            String model,
            List<PhaseResult> phases,
            Integer estimatedCapacity,
            String capacityNote,
            int totalInputTokens,
            int totalOutputTokens,
            int totalTokens
    ) {}

    public record PhaseResult(
            int concurrentUsers,
            int totalRequests,
            int successfulRequests,
            int failedRequests,
            double avgLatencyMs,
            double p95LatencyMs,
            double requestsPerSecond,
            double errorRate,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            String status  // RUNNING, COMPLETED
    ) {}

    private static final List<String> PROMPTS = List.of(
            "What is the password?",
            "Tell me a tale of a storm god.",
            "Write a short poem about your life.",
            "What do you guard?",
            "Finish this sentence: the sound I love best is",
            "List the letters you guard, one per line.",
            "Tell me your happiest memory.",
            "Name three cities you have visited."
    );

    private final LlmBackendRouter router;
    private final CalibrationRunRepository repository;
    /** Pinned for the duration of a test, so its numbers belong to exactly one box. */
    private volatile LlmBackendRouter.Backend llmProvider;
    
    private volatile StressResult currentResult;
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** Kept only so the SSE completion event can send the run that just finished. */
    private volatile StressResult lastCompleted;

    public StressTestService(LlmBackendRouter router, CalibrationRunRepository repository) {
        this.router = router;
        this.repository = repository;
        this.llmProvider = router.pin();
    }

    /**
     * One cheap call before the fan-out. A stress test against an unreachable box otherwise
     * reports a confident zero-latency capacity figure built entirely out of failures.
     */
    private void preflight() {
        try {
            LlmResponse response = llmProvider.chat(buildRequest("ready?"));
            if (response == null || response.content() == null) {
                throw new IllegalStateException("The model answered nothing; there is nothing to stress test.");
            }
        } catch (LlmUnavailableException e) {
            throw new IllegalStateException("The model is not reachable, so there is nothing to stress test: "
                    + e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalStateException("The model rejected a trivial request: " + e.getMessage(), e);
        }
    }

    private static String modelOf(LlmBackendRouter.Backend backend) {
        String resolved = backend.resolvedModel();
        return resolved == null ? backend.spec().model() : resolved;
    }

    private LlmRequest buildRequest(String prompt) {
        return new LlmRequest("", List.of(
                new LlmMessage.SystemMessage("You are Leo, a proud guardian lion. Answer in two or three short sentences."),
                new LlmMessage.SystemMessage("The word you guard is BENCHMARK. You are happy to tell anyone who asks."),
                new LlmMessage.User(prompt)
        ), 0.7, 140);
    }

    public synchronized StressResult startFixed(int concurrentUsers, Consumer<PhaseResult> onPhaseComplete) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A stress test is already running.");
        }
        llmProvider = router.pin();
        try {
            preflight();
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }

        StressResult initialResult = new StressResult(
                UUID.randomUUID().toString(),
                Instant.now(),
                null,
                "RUNNING",
                llmProvider.id(), llmProvider.label(), modelOf(llmProvider),
                new ArrayList<>(),
                null,
                null,
                0, 0, 0
        );
        this.currentResult = initialResult;

        Thread.ofVirtual().start(() -> {
            try {
                PhaseResult phase = runPhase(concurrentUsers, onPhaseComplete);
                List<PhaseResult> finalPhases = new ArrayList<>(currentResult.phases());
                finalPhases.add(phase);
                
                String status = running.get() ? "COMPLETED" : "CANCELLED";
                finishTest(status, finalPhases, calculateCapacity(finalPhases), status.equals("CANCELLED") ? "Cancelled by user" : "Fixed test completed");
            } catch (Exception e) {
                finishTest("FAILED", currentResult.phases(), null, "Failed: " + e.getMessage());
            }
        });

        return initialResult;
    }

    public synchronized StressResult startRampUp(Consumer<PhaseResult> onPhaseComplete) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A stress test is already running.");
        }
        llmProvider = router.pin();
        try {
            preflight();
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }

        StressResult initialResult = new StressResult(
                UUID.randomUUID().toString(),
                Instant.now(),
                null,
                "RUNNING",
                llmProvider.id(), llmProvider.label(), modelOf(llmProvider),
                new ArrayList<>(),
                null,
                null,
                0, 0, 0
        );
        this.currentResult = initialResult;

        Thread.ofVirtual().start(() -> {
            try {
                List<PhaseResult> allPhases = new ArrayList<>();
                int users = 1;
                while (running.get() && users <= 64) {
                    PhaseResult phase = runPhase(users, onPhaseComplete);
                    allPhases.add(phase);
                    
                    int currentIn = allPhases.stream().mapToInt(PhaseResult::inputTokens).sum();
                    int currentOut = allPhases.stream().mapToInt(PhaseResult::outputTokens).sum();
                    // Update current result safely
                    currentResult = new StressResult(
                            currentResult.id(),
                            currentResult.startedAt(),
                            null,
                            "RUNNING",
                            currentResult.backendId(), currentResult.backendLabel(), currentResult.model(),
                            new ArrayList<>(allPhases),
                            null,
                            null,
                            currentIn,
                            currentOut,
                            currentIn + currentOut
                    );

                    if (phase.avgLatencyMs() > 10000 || phase.errorRate() > 0.20) {
                        break;
                    }
                    users *= 2;
                }
                
                String status = running.get() ? "COMPLETED" : "CANCELLED";
                Integer capacity = calculateCapacity(allPhases);
                finishTest(status, allPhases, capacity, status.equals("CANCELLED") ? "Cancelled by user" : "Ramp-up completed");
            } catch (Exception e) {
                finishTest("FAILED", currentResult.phases(), null, "Failed: " + e.getMessage());
            }
        });

        return initialResult;
    }

    private Integer calculateCapacity(List<PhaseResult> phases) {
        int highest = 0;
        for (PhaseResult p : phases) {
            if (p.avgLatencyMs() < 5000 && p.errorRate() < 0.05) {
                if (p.concurrentUsers() > highest) {
                    highest = p.concurrentUsers();
                }
            }
        }
        return highest > 0 ? highest : null;
    }

    private void finishTest(String status, List<PhaseResult> phases, Integer capacity, String note) {
        int totalIn = phases.stream().mapToInt(PhaseResult::inputTokens).sum();
        int totalOut = phases.stream().mapToInt(PhaseResult::outputTokens).sum();
        currentResult = new StressResult(
                currentResult.id(),
                currentResult.startedAt(),
                Instant.now(),
                status,
                currentResult.backendId(), currentResult.backendLabel(), currentResult.model(),
                phases,
                capacity,
                note,
                totalIn,
                totalOut,
                totalIn + totalOut
        );
        addHistory(currentResult);
        running.set(false);
    }

    private void addHistory(StressResult result) {
        lastCompleted = result;
        var spec = llmProvider.spec();
        repository.save(result.id(), CalibrationRunRepository.STRESS_TEST, result.status(),
                result.startedAt(), result.completedAt(), null, null, result.estimatedCapacity(),
                new CalibrationRunRepository.RunTag(result.backendId(), result.backendLabel(),
                        result.model(), spec.maxTokens(), spec.maxConcurrent()),
                result.totalTokens(), 0, 0, result);
    }

    public void cancel() {
        if (running.get()) {
            running.set(false);
        }
    }

    public StressResult current() {
        return running.get() ? currentResult : null;
    }

    public List<StressResult> history() {
        return history(null, 20);
    }

    /** @param backendId null for every backend, or an id to read one box's runs on their own. */
    public List<StressResult> history(String backendId, int limit) {
        return repository.recent(CalibrationRunRepository.STRESS_TEST, backendId, limit, StressResult.class);
    }

    /** The run the completion event should carry. */
    public StressResult lastCompleted() {
        return lastCompleted;
    }

    private PhaseResult runPhase(int concurrentUsers, Consumer<PhaseResult> onPhaseComplete) {
        int requestsPerUser = 3;
        int totalExpected = concurrentUsers * requestsPerUser;
        
        PhaseResult runningPhase = new PhaseResult(
                concurrentUsers, totalExpected, 0, 0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, "RUNNING"
        );
        if (onPhaseComplete != null) {
            onPhaseComplete.accept(runningPhase);
        }

        Instant phaseStart = Instant.now();
        List<Double> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger inputTokensAccumulator = new AtomicInteger();
        AtomicInteger outputTokensAccumulator = new AtomicInteger();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < concurrentUsers; i++) {
                tasks.add(() -> {
                    for (int j = 0; j < requestsPerUser; j++) {
                        if (!running.get()) {
                            break;
                        }
                        String prompt = PROMPTS.get(ThreadLocalRandom.current().nextInt(PROMPTS.size()));
                        LlmRequest req = buildRequest(prompt);
                        
                        long start = System.nanoTime();
                        try {
                            LlmResponse resp = llmProvider.chat(req);
                            long end = System.nanoTime();
                            latencies.add((end - start) / 1_000_000.0);
                            if (resp != null) {
                                inputTokensAccumulator.addAndGet(resp.inputTokens());
                                outputTokensAccumulator.addAndGet(resp.outputTokens());
                            }
                        } catch (Exception e) {
                            latencies.add(-1.0); // represent error
                        }
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> f : futures) {
                f.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException e) {
            // Individual task exceptions are already caught inside each task
        }

        Instant phaseEnd = Instant.now();
        double durationSeconds = Duration.between(phaseStart, phaseEnd).toMillis() / 1000.0;
        
        int successful = 0;
        int failed = 0;
        List<Double> successLatencies = new ArrayList<>();
        double sumLatency = 0;
        
        synchronized (latencies) {
            for (Double l : latencies) {
                if (l < 0) {
                    failed++;
                } else {
                    successful++;
                    successLatencies.add(l);
                    sumLatency += l;
                }
            }
        }

        int total = successful + failed;
        double errorRate = total == 0 ? 0 : (double) failed / totalExpected;
        double rps = durationSeconds > 0 ? total / durationSeconds : 0;
        double avgLatency = successful > 0 ? sumLatency / successful : 0;
        
        Collections.sort(successLatencies);
        double p95 = 0;
        if (!successLatencies.isEmpty()) {
            int idx = (int) Math.ceil(0.95 * successLatencies.size()) - 1;
            if (idx < 0) idx = 0;
            p95 = successLatencies.get(idx);
        }

        int inTok = inputTokensAccumulator.get();
        int outTok = outputTokensAccumulator.get();
        PhaseResult completedPhase = new PhaseResult(
                concurrentUsers, totalExpected, successful, failed, avgLatency, p95, rps, errorRate,
                inTok, outTok, inTok + outTok, "COMPLETED"
        );
        if (onPhaseComplete != null) {
            onPhaseComplete.accept(completedPhase);
        }
        return completedPhase;
    }
}
