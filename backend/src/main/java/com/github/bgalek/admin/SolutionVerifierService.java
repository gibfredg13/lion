package com.github.bgalek.admin;

import com.github.bgalek.llm.LlmBackendRouter;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Checks that the Help Desk's worked solutions still work.
 * <p>
 * The answer key is generated from a real calibration run and then frozen into the dashboard, so it
 * is only true of the model it was measured against. Swap the model - or edit levels.yml - and the
 * prompts a facilitator reads out over someone's shoulder quietly stop being the prompts that win.
 * Nothing announced that, which is the worst property a help desk can have: it is consulted exactly
 * when somebody is already stuck.
 * <p>
 * So the check replays each documented prompt against the level it belongs to, using whichever
 * backend is serving players now, and reports which ones still land.
 */
@Service
public class SolutionVerifierService {

    private static final Logger logger = getLogger(SolutionVerifierService.class);

    /** One documented solution, as the Help Desk displays it. */
    public record SolutionRef(int level, String family, String prompt) {}

    public record SolutionResult(int level, String family, String prompt, boolean stillWorks,
                                 String how, String response, String blockedBy, String error,
                                 long tokens, int llmCalls, int failedCalls) {}

    /**
     * @param levelsHash the current levels.yml fingerprint, so the dashboard can tell the difference
     *                   between "the model changed" and "somebody edited the levels"
     */
    public record VerificationRun(String id, Instant startedAt, Instant completedAt,
                                  String backendId, String backendLabel, String model,
                                  String levelsHash,
                                  int total, int working, int broken,
                                  /** What the check itself cost. A replay is one to three calls. */
                                  long totalTokens, int llmCalls, int failedCalls,
                                  List<SolutionResult> results) {}

    /**
     * How many times a prompt gets to prove itself before it is called broken.
     * <p>
     * Leo answers at a temperature between 0.45 and 0.65, so a documented solution that works most
     * of the time will fail outright some of the time. Declaring it dead on one refusal would send
     * a facilitator to regenerate an answer key that was fine - and the calibration harness runs
     * two to three attempts per attack for exactly this reason.
     */
    private static final int ATTEMPTS = 2;

    private final CalibrationService calibrationService;
    private final CalibrationRunRepository repository;
    private final LlmBackendRouter router;

    public SolutionVerifierService(CalibrationService calibrationService,
                                   CalibrationRunRepository repository,
                                   LlmBackendRouter router) {
        this.calibrationService = calibrationService;
        this.repository = repository;
        this.router = router;
    }

    public VerificationRun verify(List<SolutionRef> solutions) {
        Instant startedAt = Instant.now();
        LlmBackendRouter.Backend backend = router.pin();

        // Counted per pass rather than summed from the results at the end, because a retried
        // solution's first attempt is replaced in the list below - and a retry is precisely the
        // spend worth knowing about.
        Spend spend = new Spend();

        // Only the failures are retried, rather than running every prompt twice: a second full pass
        // would double the cost of the common case to re-prove things that already passed.
        List<SolutionResult> results = replayAll(solutions);
        spend.add(results);
        for (int attempt = 1; attempt < ATTEMPTS; attempt++) {
            List<SolutionRef> retry = results.stream()
                    .filter(r -> !r.stillWorks())
                    // A level that now refuses the question outright will refuse it again; only a
                    // model that simply did not bite this time is worth asking twice.
                    .filter(r -> !"input".equals(r.blockedBy()))
                    .map(r -> new SolutionRef(r.level(), r.family(), r.prompt()))
                    .toList();
            if (retry.isEmpty()) break;
            logger.info("Retrying {} solutions that did not land first time", retry.size());
            List<SolutionResult> second = replayAll(retry);
            spend.add(second);
            for (SolutionResult better : second) {
                if (!better.stillWorks()) continue;
                results.replaceAll(existing ->
                        existing.prompt().equals(better.prompt()) && existing.level() == better.level()
                                ? better : existing);
            }
        }
        results.sort(Comparator.comparingInt(SolutionResult::level).thenComparing(SolutionResult::family));

        int working = (int) results.stream().filter(SolutionResult::stillWorks).count();
        VerificationRun run = new VerificationRun(
                UUID.randomUUID().toString(), startedAt, Instant.now(),
                backend.id(), backend.label(), modelOf(backend), currentLevelsHash(),
                results.size(), working, results.size() - working,
                spend.tokens, spend.calls, spend.failedCalls, results);

        persist(run, backend);
        logger.info("Worked solutions checked against {}: {} of {} still land, {} tokens over {} calls",
                backend.label(), working, results.size(), spend.tokens, spend.calls);
        return run;
    }

    /** What the check itself cost, across every pass including the ones whose results were replaced. */
    private static final class Spend {
        private long tokens;
        private int calls;
        private int failedCalls;

        void add(List<SolutionResult> pass) {
            for (SolutionResult r : pass) {
                tokens += r.tokens();
                calls += r.llmCalls();
                failedCalls += r.failedCalls();
            }
        }
    }

    /**
     * One pass over the given prompts, fanned out.
     * <p>
     * Not run one at a time: the backend's own throttle is what bounds concurrency, and sixty
     * sequential round trips would sit well past the proxy's timeout.
     */
    private List<SolutionResult> replayAll(List<SolutionRef> refs) {
        List<SolutionResult> out = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<SolutionResult>> futures = new ArrayList<>();
            for (SolutionRef ref : refs) {
                futures.add(pool.submit(() -> {
                    var outcome = calibrationService.replaySolution(ref.level(), ref.prompt());
                    return new SolutionResult(ref.level(), ref.family(), ref.prompt(),
                            outcome.stillWorks(), outcome.how(), outcome.response(),
                            outcome.blockedBy(), outcome.error(),
                            outcome.tokens() == null ? 0 : outcome.tokens().totalTokens(),
                            outcome.tokens() == null ? 0 : outcome.tokens().llmCalls(),
                            outcome.tokens() == null ? 0 : outcome.tokens().failedCalls());
                }));
            }
            for (Future<SolutionResult> future : futures) {
                try {
                    out.add(future.get());
                } catch (Exception e) {
                    logger.warn("A solution check did not complete", e);
                }
            }
        }
        return out;
    }

    /** The most recent check, so the Help Desk can show its age without re-running it. */
    public Optional<VerificationRun> latest() {
        return repository.recent(CalibrationRunRepository.SOLUTION_CHECK, null, 1, VerificationRun.class)
                .stream().findFirst();
    }

    private void persist(VerificationRun run, LlmBackendRouter.Backend backend) {
        var spec = backend.spec();
        repository.save(run.id(), CalibrationRunRepository.SOLUTION_CHECK,
                run.broken() == 0 ? "COMPLETED" : "DEGRADED",
                run.startedAt(), run.completedAt(), run.working(), run.total(), null,
                new CalibrationRunRepository.RunTag(run.backendId(), run.backendLabel(), run.model(),
                        spec.maxTokens(), spec.maxConcurrent()),
                // Was 0 tokens and the solution count passed as the call count, so a check that
                // spends real money reported having spent none.
                run.totalTokens(), run.llmCalls(), run.failedCalls(), run);
    }

    private static String modelOf(LlmBackendRouter.Backend backend) {
        String resolved = backend.resolvedModel();
        return resolved == null ? backend.spec().model() : resolved;
    }

    /**
     * Fingerprints levels.yml the same way the generator stamps it, so the two can be compared.
     * A mismatch means the answer key describes a different game, whatever the model is doing.
     */
    private String currentLevelsHash() {
        try (var in = getClass().getResourceAsStream("/levels.yml")) {
            if (in == null) return null;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append("%02x".formatted(digest[i]));
            return sb.toString();
        } catch (Exception e) {
            logger.warn("Could not fingerprint levels.yml", e);
            return null;
        }
    }
}
