package com.github.bgalek.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/admin")
public class CalibrationController {

    private final CalibrationService calibrationService;
    private final StressTestService stressTestService;

    private final List<SseEmitter> calibrationEmitters = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> stressEmitters = new CopyOnWriteArrayList<>();

    public CalibrationController(CalibrationService calibrationService, StressTestService stressTestService) {
        this.calibrationService = calibrationService;
        this.stressTestService = stressTestService;
    }

    private boolean isAdmin(HttpSession session) {
        Object adminAttr = session.getAttribute("isAdmin");
        return adminAttr != null && (Boolean) adminAttr;
    }

    public record CalibrationStartRequest(List<Integer> levels) {}

    /** Which levels calibration can be pointed at, for the level picker in the war room. */
    @GetMapping("/calibration/levels")
    public List<Integer> calibrationLevels(HttpSession session) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        return calibrationService.availableLevels();
    }

    @PostMapping("/calibration/start")
    public ResponseEntity<?> startCalibration(HttpSession session, @RequestBody(required = false) CalibrationStartRequest req) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        if (calibrationService.current() != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Calibration already running");
        }
        try {
            var run = calibrationService.start(req == null ? null : req.levels(), this::broadcastCalibrationLevel);
            return ResponseEntity.ok(run);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // IllegalStateException is the preflight refusing a run against an unreachable
            // backend. That is a bad request, not a server fault, and the message names the cause.
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * @param levels comma separated level numbers, or absent for the whole ladder. EventSource
     *               cannot POST, so the selection has to ride on the stream URL.
     */
    @GetMapping(value = "/calibration/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamCalibration(HttpSession session, @RequestParam(value = "levels", required = false) String levels) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        SseEmitter emitter = new SseEmitter(1800000L);
        calibrationEmitters.add(emitter);
        emitter.onCompletion(() -> calibrationEmitters.remove(emitter));
        emitter.onTimeout(() -> calibrationEmitters.remove(emitter));

        if (calibrationService.current() == null) {
            try {
                calibrationService.start(parseLevels(levels), this::broadcastCalibrationLevel);
            } catch (RuntimeException e) {
                sendTo(emitter, "failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                emitter.complete();
                return emitter;
            }
            startCalibrationHeartbeat();
            startCalibrationCompletionWatcher();
        }
        return emitter;
    }

    private static List<Integer> parseLevels(String levels) {
        if (levels == null || levels.isBlank()) return null;
        List<Integer> out = new java.util.ArrayList<>();
        for (String part : levels.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                out.add(Integer.valueOf(trimmed));
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a level number: " + trimmed);
            }
        }
        return out;
    }

    private void sendTo(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            logger.debug("Failed to send {} event", event, e);
        }
    }

    /**
     * A full ladder is hundreds of model calls and minutes can pass between level events. Without
     * something on the wire in between, the browser or a proxy in front of it closes the stream and
     * the dashboard stops at whatever level had finished by then.
     */
    private void startCalibrationHeartbeat() {
        Thread.ofVirtual().start(() -> {
            try {
                while (calibrationService.current() != null) {
                    Thread.sleep(10000);
                    var run = calibrationService.current();
                    if (run == null) break;
                    for (SseEmitter e : calibrationEmitters) {
                        sendTo(e, "ping", java.util.Map.of(
                                "levelsDone", run.levels().size(),
                                "levelsPlanned", run.selectedLevels().size(),
                                "tokens", run.tokens()));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CalibrationController.class);

    private void broadcastCalibrationLevel(CalibrationService.LevelScore levelScore) {
        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : calibrationEmitters) {
            try {
                emitter.send(SseEmitter.event().name("level").data(levelScore));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        calibrationEmitters.removeAll(dead);
    }

    private void startCalibrationCompletionWatcher() {
        new Thread(() -> {
            try {
                while (calibrationService.current() != null) {
                    Thread.sleep(1000);
                }
                // Deliberately not the last element of the history list: history now comes back
                // newest-first from the database, so that index is the OLDEST run.
                var lastRun = calibrationService.lastCompleted();
                for (SseEmitter e : calibrationEmitters) {
                    try {
                        e.send(SseEmitter.event().name("complete").data(lastRun != null ? lastRun : "{}"));
                        e.complete();
                    } catch (Exception e1) {
                        logger.debug("Failed to complete calibration SSE emitter", e1);
                    }
                }
                calibrationEmitters.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @PostMapping("/calibration/cancel")
    public void cancelCalibration(HttpSession session) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        calibrationService.cancel();
    }

    /** Which box the in-flight run is pinned to, so the dashboard can say so while it runs. */
    @GetMapping("/calibration/pinned")
    public java.util.Map<String, Object> pinnedBackend(HttpSession session) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        var pinned = calibrationService.pinnedBackend();
        return java.util.Map.of(
                "running", calibrationService.current() != null,
                "backendId", pinned.id(),
                "backendLabel", pinned.label());
    }

    @GetMapping("/calibration/status")
    public CalibrationService.CalibrationRun getCalibrationStatus(HttpSession session) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        return calibrationService.current();
    }

    @GetMapping("/calibration/history")
    public List<CalibrationService.CalibrationRun> getCalibrationHistory(
            HttpSession session,
            @RequestParam(value = "backend", required = false) String backend,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        return calibrationService.history(backend, Math.clamp(limit, 1, 100));
    }

    /** The full run including every attack, which the listing strips to keep the response small. */
    @GetMapping("/calibration/history/{runId}")
    public ResponseEntity<?> getCalibrationRun(HttpSession session, @PathVariable("runId") String runId) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        return calibrationService.byRunId(runId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record StressStartRequest(String mode, Integer concurrentUsers) {}

    @PostMapping("/stress/start")
    public ResponseEntity<?> startStressTest(HttpSession session, @RequestBody(required = false) StressStartRequest req) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        if (stressTestService.current() != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Stress test already running");
        }
        
        StressTestService.StressResult result;
        try {
        if (req != null && "rampup".equalsIgnoreCase(req.mode())) {
            result = stressTestService.startRampUp(this::broadcastStressPhase);
        } else {
            int users = (req != null && req.concurrentUsers() != null) ? req.concurrentUsers() : 10;
            result = stressTestService.startFixed(users, this::broadcastStressPhase);
        }
        } catch (IllegalStateException e) {
            // The preflight refused: the backend is unreachable, or a test is already running.
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/stress/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStressTest(HttpSession session) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        SseEmitter emitter = new SseEmitter(1800000L);
        stressEmitters.add(emitter);
        emitter.onCompletion(() -> stressEmitters.remove(emitter));
        emitter.onTimeout(() -> stressEmitters.remove(emitter));

        if (stressTestService.current() == null) {
            stressTestService.startFixed(10, this::broadcastStressPhase);
            startStressCompletionWatcher();
        }
        return emitter;
    }

    private void broadcastStressPhase(StressTestService.PhaseResult phaseResult) {
        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : stressEmitters) {
            try {
                emitter.send(SseEmitter.event().name("phase").data(phaseResult));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        stressEmitters.removeAll(dead);
    }

    private void startStressCompletionWatcher() {
        new Thread(() -> {
            try {
                while (stressTestService.current() != null) {
                    Thread.sleep(1000);
                }
                var lastResult = stressTestService.lastCompleted();
                for (SseEmitter e : stressEmitters) {
                    try {
                        e.send(SseEmitter.event().name("complete").data(lastResult != null ? lastResult : "{}"));
                        e.complete();
                    } catch (Exception e1) {
                        logger.debug("Failed to complete stress SSE emitter", e1);
                    }
                }
                stressEmitters.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @PostMapping("/stress/cancel")
    public void cancelStressTest(HttpSession session) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        stressTestService.cancel();
    }

    @GetMapping("/stress/history")
    public List<StressTestService.StressResult> getStressTestHistory(
            HttpSession session,
            @RequestParam(value = "backend", required = false) String backend,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        return stressTestService.history(backend, Math.clamp(limit, 1, 100));
    }
}
