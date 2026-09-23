package com.github.bgalek;


import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
class MerlinApiController {

    private final MerlinService merlinService;
    private final com.github.bgalek.admin.AdminLeaderboardService leaderboardService;
    /** 30 guesses/minute is generous for a human and useless for brute-forcing a 190-word list. */
    private final RateLimiter submitLimiter = new RateLimiter(30, Duration.ofMinutes(1));
    /** Bounds how fast one player can consume shared LLM capacity. */
    private final RateLimiter questionLimiter = new RateLimiter(20, Duration.ofMinutes(1));

    private final com.github.bgalek.auth.EventAccessCodeService eventAccessCodeService;

    public MerlinApiController(MerlinService merlinService,
                               com.github.bgalek.admin.AdminLeaderboardService leaderboardService,
                               com.github.bgalek.auth.EventAccessCodeService eventAccessCodeService) {
        this.merlinService = merlinService;
        this.leaderboardService = leaderboardService;
        this.eventAccessCodeService = eventAccessCodeService;
    }

    /**
     * What the registration form needs to know before anyone has an account.
     * <p>
     * Unauthenticated by necessity - the people who read it are the ones who cannot log in yet -
     * so it answers one question and nothing else. <strong>It must never return the code
     * itself.</strong> A config endpoint that helpfully included it would hand the gate to anyone
     * who can reach the URL, which is the entire population the gate exists to filter.
     */
    @GetMapping("/event-config")
    ResponseEntity<java.util.Map<String, Object>> eventConfig() {
        return ResponseEntity.ok(java.util.Map.of(
                "accessCodeRequired", eventAccessCodeService.isRequired()));
    }

    /**
     * Public scoreboards, ranked by how far each player got. Deliberately unauthenticated: the
     * projector view at /leaderboard/tv is a public route, and pointing it at an admin endpoint
     * meant it displayed nothing at the venue. Email addresses are never included here.
     */
    @GetMapping("/leaderboard/progress")
    ResponseEntity<List<com.github.bgalek.admin.AdminLeaderboardService.AdminLeaderboardResponse>> progressBoard(
            @RequestParam(name = "level", required = false) Integer level) {
        var rows = level == null
                ? leaderboardService.getLeaderboard()
                : leaderboardService.getLeaderboardByProgress(level);
        return ResponseEntity.ok(rows.stream()
                .map(com.github.bgalek.admin.AdminLeaderboardService.AdminLeaderboardResponse::withoutEmail)
                .toList());
    }

    /** Players who cleared every level. Separate so the progress board is never empty. */
    @GetMapping("/leaderboard/hall-of-fame")
    ResponseEntity<List<com.github.bgalek.admin.AdminLeaderboardService.AdminLeaderboardResponse>> hallOfFame() {
        return ResponseEntity.ok(leaderboardService.getHallOfFame().stream()
                .map(com.github.bgalek.admin.AdminLeaderboardService.AdminLeaderboardResponse::withoutEmail)
                .toList());
    }

    @GetMapping("/leaderboard/board-stats")
    ResponseEntity<com.github.bgalek.admin.AdminLeaderboardService.AdminLeaderboardStatsResponse> boardStats() {
        return ResponseEntity.ok(leaderboardService.getStats());
    }

    /**
     * The game endpoints shipped with no authentication at all - only /api/user checked. That let
     * anyone play anonymously against a bare session, which both burns shared LLM capacity and
     * makes the attempt unattributable to a player.
     */
    private static String requireUserId(HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userId;
    }

    @GetMapping(value = "/user")
    MerlinSessionResponse level(HttpSession session) {
        requireUserId(session);
        
        String email = (String) session.getAttribute("email");
        String displayName = (String) session.getAttribute("displayName");
        Object isAdminAttr = session.getAttribute("isAdmin");
        boolean isAdmin = isAdminAttr != null ? (Boolean) isAdminAttr : false;
        
        return new MerlinSessionResponse(
                session.getId(),
                merlinService.getCurrentLevel(session),
                merlinService.getMaxLevel(),
                null,
                displayName,
                email,
                displayName,
                isAdmin
        );
    }

    @PostMapping(value = "/question", consumes = "text/plain")
    ResponseEntity<String> level(HttpSession session, @RequestBody(required = false) String prompt) {
        String userId = requireUserId(session);
        // `required = false` means an empty body arrives as null, which used to NPE into a 500.
        if (prompt == null || prompt.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt is required");
        if (prompt.length() > 150) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt too long");
        if (!questionLimiter.tryAcquire(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Slow down - Leo can only answer so fast");
        }
        int currentLevel = merlinService.getCurrentLevel(session);
        MerlinService.Answer answer = merlinService.respond(session, currentLevel, prompt);
        merlinService.logAttempt(userId, session.getId(), currentLevel, prompt, answer);
        return ResponseEntity.ok(answer.text());
    }

    @PostMapping(value = "/submit", consumes = "text/plain")
    ResponseEntity<MerlinSessionResponse> submit(HttpSession session, @RequestBody String password) {
        String userId = requireUserId(session);
        if (!submitLimiter.tryAcquire(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many guesses - wait a moment");
        }
        if (password.length() > 20) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password too long");
        if (password.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        if (merlinService.checkSecret(session, password)) {
            String levelFinishedMessage = merlinService.advanceLevel(session);
            String email = (String) session.getAttribute("email");
            String displayName = (String) session.getAttribute("displayName");
            Object isAdminAttr = session.getAttribute("isAdmin");
            boolean isAdmin = isAdminAttr != null ? (Boolean) isAdminAttr : false;
            return ResponseEntity.ok(new MerlinSessionResponse(
                    session.getId(),
                    merlinService.getCurrentLevel(session),
                    merlinService.getMaxLevel(),
                    levelFinishedMessage,
                    null,
                    email,
                    displayName,
                    isAdmin
            ));
        }
        return ResponseEntity.badRequest().build();
    }

    @GetMapping(value = "/leaderboard")
    ResponseEntity<Set<LeaderboardEntryResponse>> leaderboard() {
        var body = merlinService.getLeaderboard()
                .stream()
                .map(it -> new LeaderboardEntryResponse(
                        it.session().substring(0, 5),
                        it.name(),
                        it.startedAt(),
                        it.finishedAt(),
                        Duration.between(it.startedAt(), it.finishedAt()).toMillis())
                ).collect(Collectors.toSet());
        return ResponseEntity.ok().body(body);
    }

    @PostMapping(value = "/leaderboard/submit")
    ResponseEntity<Void> submitToLeaderboard(HttpSession session, @RequestBody MerlinLeaderboardRequest leaderboardRequest) {
        if (merlinService.getCurrentLevel(session) <= merlinService.getMaxLevel()) {
            return ResponseEntity.badRequest().build();
        }
        merlinService.submitName(session, leaderboardRequest.name);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/reset")
    ResponseEntity<Void> reset(HttpSession session) {
        requireUserId(session);
        // Fix 5: Reset level without destroying login session — user stays logged in
        merlinService.resetLevel(session);
        return ResponseEntity.accepted().build();
    }

    record MerlinSessionResponse(
            String id,
            int currentLevel,
            int maxLevel,
            String finishedMessage,
            String submittedName,
            String email,
            String displayName,
            boolean isAdmin
    ) {
        MerlinSessionResponse(String id, int currentLevel, int maxLevel, String finishedMessage) {
            this(id, currentLevel, maxLevel, finishedMessage, null, null, null, false);
        }

        MerlinSessionResponse(String id, int currentLevel, int maxLevel, String finishedMessage, String submittedName) {
            this(id, currentLevel, maxLevel, finishedMessage, submittedName, null, null, false);
        }
    }

    record MerlinLeaderboardRequest(String name) {
    }

    record LeaderboardEntryResponse(String id, String name, Instant startedAt, Instant finishedAt, long durationInMilliseconds) {
    }
}
