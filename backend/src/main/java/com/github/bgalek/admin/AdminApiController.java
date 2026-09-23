package com.github.bgalek.admin;

import com.github.bgalek.database.LoginHistoryRepository;
import com.github.bgalek.database.UserRepository;
import com.github.bgalek.levels.LevelDefinition;
import com.github.bgalek.levels.LevelDefinitionService;
import com.github.bgalek.llm.LlmProvider;
import com.github.bgalek.llm.DgxSparkLlmProvider;
import com.github.bgalek.auth.EventAccessCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final AdminUserService adminUserService;
    private final AdminLlmService adminLlmService;
    private final AdminLeaderboardService adminLeaderboardService;
    private final AdminLoginHistoryService adminLoginHistoryService;
    private final AnalyticsService analyticsService;
    private final AttackDetectionService attackDetectionService;

    private final EventAccessCodeService eventAccessCodeService;
    private final com.github.bgalek.llm.LlmBackendRouter router;
    private final com.github.bgalek.session.GameSessionService gameSessions;
    private final com.github.bgalek.tv.TvService tvService;
    private final SolutionVerifierService solutionVerifier;
    private final LlmBackendAdminService backendAdminService;
    private final JdbcClient jdbcClient;
    private final LevelGateService levelGateService;
    private final UserRepository userRepository;
    private final int levelCount;
    private final LevelDefinitionService levelDefinitionService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AdminApiController.class);

    public AdminApiController(
            LlmProvider llmProvider,
            LoginHistoryRepository loginHistoryRepository,
            EventAccessCodeService eventAccessCodeService,
            com.github.bgalek.llm.LlmBackendRouter router,
            com.github.bgalek.session.GameSessionService gameSessions,
            com.github.bgalek.tv.TvService tvService,
            SolutionVerifierService solutionVerifier,
            LlmBackendAdminService backendAdminService,
            JdbcClient jdbcClient,
            LevelGateService levelGateService,
            UserRepository userRepository,
            LevelDefinitionService levelDefinitionService,
            AdminLeaderboardService adminLeaderboardService,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.adminUserService = new AdminUserService();
        this.adminLlmService = new AdminLlmService(llmProvider);
        this.levelDefinitionService = levelDefinitionService;
        this.adminLeaderboardService = adminLeaderboardService;
        this.passwordEncoder = passwordEncoder;
        this.adminLoginHistoryService = new AdminLoginHistoryService(loginHistoryRepository);
        this.analyticsService = new AnalyticsService(List.of(), List.of(), List.of(), List.of());
        this.attackDetectionService = new AttackDetectionService();
        this.eventAccessCodeService = eventAccessCodeService;
        this.router = router;
        this.gameSessions = gameSessions;
        this.tvService = tvService;
        this.solutionVerifier = solutionVerifier;
        this.backendAdminService = backendAdminService;
        this.jdbcClient = jdbcClient;
        this.levelGateService = levelGateService;
        this.userRepository = userRepository;
        this.levelCount = levelDefinitionService.count();
    }

    @GetMapping("/users")
    ResponseEntity<List<Map<String, Object>>> getAllUsers(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        // Fix 8: Return real user data from the database
        try {
            return ResponseEntity.ok(jdbcClient.sql("""
                    SELECT u.id AS "id", u.email AS "email",
                           u.display_name AS "displayName", u.is_admin AS "isAdmin",
                           u.current_level AS "currentLevel", u.created_at AS "createdAt",
                           u.last_login_at AS "lastLoginAt",
                           COUNT(l.id) AS "attempts",
                           COALESCE(SUM(l.input_tokens + l.output_tokens), 0) AS "tokens",
                           MAX(l.created_at) AS "lastActivity"
                    FROM users u LEFT JOIN logs l ON l.user_id = u.id
                                                 AND l.game_session_id = :gameSessionId
                    GROUP BY u.id, u.email, u.display_name, u.is_admin, u.current_level,
                             u.created_at, u.last_login_at
                    ORDER BY u.created_at DESC
                    """).param("gameSessionId", gameSessions.activeId()).query().listOfRows());
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    /** Reports the backend actually serving traffic. It used to report two hardcoded strings. */
    @GetMapping("/llm/config")
    ResponseEntity<AdminLlmService.AdminLlmConfigResponse> getLlmConfig(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        var backend = router.active();
        return ResponseEntity.ok(new AdminLlmService.AdminLlmConfigResponse(
                backend.spec().kind(),
                backend.resolvedModel() == null ? backend.spec().model() : backend.resolvedModel(),
                "***hidden***"));
    }

    /**
     * Retired rather than wired up.
     * <p>
     * This wrote a provider name and a model into two display-only fields and discarded the rest,
     * so nothing it accepted ever took effect and no UI ever called it. Pointing it at the real
     * router would silently turn a dormant endpoint into a live production switch, so it says
     * where the switch lives instead.
     */
    @PostMapping("/llm/config")
    ResponseEntity<String> updateLlmConfig(HttpSession session,
                                           @RequestBody(required = false) AdminLlmService.AdminLlmConfigRequest request) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.status(HttpStatus.GONE)
                .body("Backends are declared in configuration. Switch with "
                        + "POST /api/admin/llm/backends/{id}/activate; list them with GET /api/admin/llm/backends.");
    }

    @PostMapping("/llm/test")
    ResponseEntity<AdminLlmService.LlmTestResponse> testLlmPrompt(
            HttpSession session,
            @RequestBody AdminLlmService.AdminLlmTestRequest request) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt is required");
        }
        return ResponseEntity.ok(adminLlmService.testPrompt(request));
    }

    @GetMapping("/llm/test-history")
    ResponseEntity<List<AdminLlmService.LlmTestResult>> getLlmTestHistory(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.ok(adminLlmService.getTestHistory());
    }

    /**
     * @param sessionId a past run to browse instead of the live one. Admin-only: history is not
     *                  something an ordinary player needs, and the picker lives in the dashboard.
     */
    @GetMapping("/leaderboard")
    ResponseEntity<List<AdminLeaderboardService.AdminLeaderboardResponse>> getLeaderboard(
            HttpSession session,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        // Fix 4: Public leaderboard — any authenticated user can see rankings
        if (session.getAttribute("userId") == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        return ResponseEntity.ok(visibleTo(session,
                adminLeaderboardService.getLeaderboard(sessionOrActive(session, sessionId))));
    }

    /**
     * Resolves a requested run, falling back to the live one.
     * <p>
     * A non-admin is always given the live run whatever they ask for: the history picker is an
     * admin tool, and this endpoint is reachable by any logged-in player.
     */
    private String sessionOrActive(HttpSession session, String requested) {
        if (requested == null || requested.isBlank() || !isAdmin(session)) return gameSessions.activeId();
        return requested;
    }

    /** Players who cleared every level. Kept separate so the progress board is never empty. */
    @GetMapping("/leaderboard/hall-of-fame")
    ResponseEntity<List<AdminLeaderboardService.AdminLeaderboardResponse>> getHallOfFame(HttpSession session) {
        if (session.getAttribute("userId") == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        return ResponseEntity.ok(visibleTo(session, adminLeaderboardService.getHallOfFame()));
    }

    /**
     * Email addresses are admin-only. They were previously rendered for every logged-in player, and
     * the TV board at /leaderboard/tv is an unauthenticated route - so a projector at the venue was
     * one login away from displaying everyone's address.
     */
    private List<AdminLeaderboardService.AdminLeaderboardResponse> visibleTo(
            HttpSession session, List<AdminLeaderboardService.AdminLeaderboardResponse> rows) {
        if (isAdmin(session)) return rows;
        return rows.stream().map(AdminLeaderboardService.AdminLeaderboardResponse::withoutEmail).toList();
    }

    @GetMapping("/leaderboard/stats")
    ResponseEntity<Map<String, Object>> getLeaderboardStats(HttpSession session) {
        // Fix 4: Public stats — any authenticated user can see
        if (session.getAttribute("userId") == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        var stats = adminLeaderboardService.getStats();
        return ResponseEntity.ok(Map.of(
                "totalParticipants", stats.totalParticipants(),
                "completedLevel7", stats.completedLevel7(),
                "averageTimeSeconds", stats.averageTimeSeconds(),
                "currentlyActive", stats.currentlyActive()
        ));
    }

    @PostMapping("/leaderboard/reset")
    ResponseEntity<Void> resetLeaderboard(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        adminLeaderboardService.resetLeaderboard();
        return ResponseEntity.ok().build();
    }

    // ============ ANALYTICS & EXPORT ENDPOINTS ============

    @GetMapping("/analytics/attack-heatmap")
    ResponseEntity<AnalyticsService.AttackHeatmapResponse> getAttackHeatmap(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.ok(analyticsService.getAttackHeatmap());
    }

    @GetMapping("/analytics/token-usage-by-user")
    ResponseEntity<List<AnalyticsService.TokenUsageByUserResponse>> getTokenUsageByUser(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.ok(analyticsService.getTokenUsageByUser());
    }

    @GetMapping("/analytics/token-usage-timeseries")
    ResponseEntity<List<AnalyticsService.TimeSeriesTokensResponse>> getTokenUsageTimeSeries(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.ok(analyticsService.getTokenUsageTimeSeries());
    }

    @GetMapping("/analytics/top-tricks")
    ResponseEntity<List<AnalyticsService.TopTricksResponse>> getTopTricks(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.ok(analyticsService.getTopTricks());
    }

    @GetMapping("/export/csv")
    ResponseEntity<byte[]> exportCSV(HttpSession session) throws IOException {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        byte[] csvData = analyticsService.exportAsCSV();
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header("Content-Disposition", "attachment; filename=ctf-data.csv")
                .body(csvData);
    }

    @GetMapping("/export/json")
    ResponseEntity<String> exportJSON(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        String jsonData = analyticsService.exportAsJSON();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Content-Disposition", "attachment; filename=ctf-data.json")
                .body(jsonData);
    }

    @GetMapping("/login-history")
    ResponseEntity<List<AdminLoginHistoryService.LoginHistoryResponse>> getLoginHistory(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.ok(adminLoginHistoryService.getRecentLogins(50));
    }

    @GetMapping("/login-stats")
    ResponseEntity<AdminLoginHistoryService.LoginStatsResponse> getLoginStats(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.ok(adminLoginHistoryService.getLoginStats());
    }

    // ============ HACKATHON LION'S DEN ENDPOINTS ============

    // ============ WORKED SOLUTIONS ============

    public record VerifySolutionsRequest(List<SolutionVerifierService.SolutionRef> solutions) {}

    /**
     * Replays the Help Desk's worked solutions against the live model.
     * <p>
     * The prompts are posted by the dashboard rather than read from a file here, so that what is
     * checked is exactly what is on screen. A copy kept server-side would be a second answer key,
     * and the one that drifted would be the one nobody was looking at.
     */
    @PostMapping("/solutions/verify")
    public ResponseEntity<?> verifySolutions(HttpSession session, @RequestBody VerifySolutionsRequest request) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        if (request == null || request.solutions() == null || request.solutions().isEmpty()) {
            return ResponseEntity.badRequest().body("No solutions to check");
        }
        return ResponseEntity.ok(solutionVerifier.verify(request.solutions()));
    }

    /** The last check, so the Help Desk can show its age without spending a run to find out. */
    @GetMapping("/solutions/verify/latest")
    public ResponseEntity<?> latestSolutionCheck(HttpSession session) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        return solutionVerifier.latest()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of("neverRun", true)));
    }

    // ============ TV DISPLAY ============

    @GetMapping("/tv/settings")
    public Map<String, Object> tvSettings(HttpSession session) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        return Map.of(
                "feedEnabled", tvService.feedEnabled(),
                "sessionLabel", gameSessions.activeLabel(),
                "activeSessionId", gameSessions.activeId() == null ? "" : gameSessions.activeId());
    }

    public record TvFeedRequest(boolean enabled) {}

    /** The kill switch. Takes effect on the projector's next poll, a few seconds later. */
    @PutMapping("/tv/feed-enabled")
    public Map<String, Object> setTvFeed(HttpSession session, @RequestBody TvFeedRequest req) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        tvService.setFeedEnabled(req.enabled());
        return Map.of("feedEnabled", tvService.feedEnabled());
    }

    // ============ GAME SESSIONS ============

    /**
     * The runs of the event, newest first. A run is not deleted when the next one starts, so this
     * is also the history the dashboard's session picker browses.
     */
    @GetMapping("/game-sessions")
    public List<com.github.bgalek.session.GameSession> gameSessions(HttpSession session) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        return gameSessions.list();
    }

    public record NewSessionRequest(String label, boolean resetLevels) {}

    /**
     * Starts a new run. Deletes nothing - the previous run's attempts, timings and rankings stay
     * exactly where they are and remain browsable.
     */
    @PostMapping("/game-sessions")
    public ResponseEntity<?> startGameSession(HttpSession session, @RequestBody(required = false) NewSessionRequest req) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        String by = (String) session.getAttribute("email");
        var started = gameSessions.startNewSession(
                req == null ? null : req.label(),
                req != null && req.resetLevels(),
                by);
        return ResponseEntity.ok(started);
    }

    /** Discards a botched test round along with its attempts. Refuses the run that is live. */
    @DeleteMapping("/game-sessions/{id}")
    public ResponseEntity<?> deleteGameSession(HttpSession session, @PathVariable("id") String id) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        try {
            gameSessions.delete(id);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    // ============ LLM BACKENDS ============

    @GetMapping("/llm/backends")
    public List<LlmBackendAdminService.BackendView> llmBackends(HttpSession session) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        return backendAdminService.backends();
    }

    @PostMapping("/llm/backends/{id}/activate")
    public ResponseEntity<?> activateLlmBackend(HttpSession session, @PathVariable("id") String id) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        try {
            return ResponseEntity.ok(backendAdminService.activate(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    public record BackendTestRequest(String prompt) {}

    @PostMapping("/llm/backends/{id}/test")
    public ResponseEntity<?> testLlmBackend(HttpSession session, @PathVariable("id") String id,
                                            @RequestBody(required = false) BackendTestRequest req) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        try {
            return ResponseEntity.ok(backendAdminService.test(id, req == null ? null : req.prompt()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    public record AccessCodeRequest(String code) {}

    @GetMapping("/access-code")
    public Map<String, Object> getAccessCode(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return Map.of(
                "code", eventAccessCodeService.getCurrentCode(),
                "required", eventAccessCodeService.isRequired());
    }

    @PutMapping("/access-code")
    public void updateAccessCode(HttpSession session, @RequestBody AccessCodeRequest request) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        eventAccessCodeService.updateCode(request.code());
    }

    public record AccessCodeRequiredRequest(boolean required) {}

    /**
     * Turns the registration gate on and off. With it off, who may register is decided by the
     * allowed email domains alone.
     */
    @PutMapping("/access-code/required")
    public Map<String, Object> setAccessCodeRequired(HttpSession session,
                                                     @RequestBody AccessCodeRequiredRequest request) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        eventAccessCodeService.setRequired(request.required());
        return Map.of("required", eventAccessCodeService.isRequired());
    }

    @GetMapping("/dgx-health")
    public Map<String, Object> getDgxHealth(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        var backend = router.active();
        var health = backendAdminService.backends().stream()
                .filter(b -> b.id().equals(backend.id())).findFirst().orElse(null);
        boolean healthy = health != null && health.healthy();
        Map<String, Object> resp = new HashMap<>();
        resp.put("healthy", healthy);
        // The game frontend renders `status` and has always shown "Unknown" because nothing ever
        // returned it. Every existing key is kept; this is purely additive.
        resp.put("status", healthy ? "Online" : "Offline");
        resp.put("avgLatencyMs", health == null ? 0 : health.avgLatencyMs());
        resp.put("availableModels", health == null ? List.of() : health.availableModels());
        resp.put("provider", backend.spec().kind());
        resp.put("backendId", backend.id());
        resp.put("label", backend.label());
        resp.put("endpoint", backend.spec().baseUrl());
        resp.put("resolvedModel", health == null ? backend.spec().model() : health.model());
        return resp;
    }

    @GetMapping("/level-stats")
    public List<Map<String, Object>> getLevelStats(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (int level = 1; level <= levelCount; level++) {
            long attempts = 0;
            long players = 0;
            long completions = 0;
            try {
                attempts = jdbcClient.sql("SELECT COUNT(*) FROM logs WHERE level = :level "
                                + "AND game_session_id = :gameSessionId")
                        .param("gameSessionId", gameSessions.activeId())
                        .param("level", level).query(Long.class).single();
                players = jdbcClient.sql("SELECT COUNT(DISTINCT user_id) FROM logs WHERE level = :level "
                                + "AND game_session_id = :gameSessionId")
                        .param("gameSessionId", gameSessions.activeId())
                        .param("level", level).query(Long.class).single();
                // Anyone whose current level is past this one has cleared it.
                completions = jdbcClient.sql("SELECT COUNT(*) FROM users WHERE current_level > :level")
                        .param("level", level).query(Long.class).single();
            } catch (Exception e) {
                logger.warn("level-stats query failed for level {}", level, e);
            }
            out.add(Map.of(
                    "level", level,
                    "enabled", levelGateService.isLevelEnabled(level),
                    "attempts", attempts,
                    "players", players,
                    "completions", completions));
        }
        return out;
    }

    public record LevelEnableRequest(boolean enabled) {}

    @PutMapping("/level/{level}/enabled")
    public void setLevelEnabled(HttpSession session, @PathVariable("level") int level, @RequestBody LevelEnableRequest request) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        levelGateService.setLevelEnabled(level, request.enabled());
    }

    @GetMapping("/active-users")
    public List<Map<String, Object>> getActiveUsers(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        try {
            return jdbcClient.sql("""
                    SELECT u.display_name   AS "displayName",
                           u.email          AS "email",
                           u.current_level  AS "currentLevel",
                           MAX(l.created_at) AS "lastActivity",
                           COUNT(l.id)      AS "attempts"
                    FROM users u
                    JOIN logs l ON l.user_id = u.id
                    WHERE l.created_at > :cutoff AND l.game_session_id = :gameSessionId
                    GROUP BY u.id, u.display_name, u.email, u.current_level
                    ORDER BY MAX(l.created_at) DESC
                    """)
                    .param("cutoff", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(600)))
                    .param("gameSessionId", gameSessions.activeId())
                    .query().listOfRows();
        } catch (Exception e) {
            logger.warn("active-users query failed", e);
            return List.of();
        }
    }

    @GetMapping("/token-stats")
    public Map<String, Object> getTokenStats(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        try {
            var totals = jdbcClient.sql("""
                    -- Quoted for the same reason as the queries below, and here it was worse than
                    -- a wrong JSON key: singleRow() keys the map by column label, so on HSQLDB the
                    -- gets returned null and this threw NPE rather than reporting any tokens.
                    SELECT COALESCE(SUM(input_tokens), 0)  AS "in_tokens",
                           COALESCE(SUM(output_tokens), 0) AS "out_tokens",
                           COUNT(*)                        AS "attempts"
                    FROM logs WHERE game_session_id = :gameSessionId
                    """).param("gameSessionId", gameSessions.activeId()).query().singleRow();
            long in = ((Number) totals.get("in_tokens")).longValue();
            long out = ((Number) totals.get("out_tokens")).longValue();
            long lastHour = jdbcClient.sql(
                    "SELECT COALESCE(SUM(input_tokens + output_tokens), 0) FROM logs "
                            + "WHERE created_at > :cutoff AND game_session_id = :gameSessionId")
                    .param("cutoff", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3600)))
                    .param("gameSessionId", gameSessions.activeId())
                    .query(Long.class).single();
            // Unscoped on purpose: the totals above answer "how is this run going", but the
            // question "what has this box cost us" spans every run there has ever been.
            long allTime = jdbcClient.sql(
                    "SELECT COALESCE(SUM(input_tokens + output_tokens), 0) FROM logs")
                    .query(Long.class).single();
            return Map.of(
                    "totalTokens", in + out,
                    "totalInputTokens", in,
                    "totalOutputTokens", out,
                    "totalAttempts", ((Number) totals.get("attempts")).longValue(),
                    "tokensLastHour", lastHour,
                    "allTimeTokens", allTime
            );
        } catch (Exception e) {
            logger.warn("token-stats query failed", e);
            return Map.of("totalTokens", 0, "totalInputTokens", 0, "totalOutputTokens", 0,
                    "totalAttempts", 0, "tokensLastHour", 0);
        }
    }


    // ============ USER ADMINISTRATION ============

    public record PasswordResetRequest(String newPassword) {}
    public record AdminFlagRequest(boolean isAdmin) {}

    /**
     * Every prompt a player has sent, newest first, with what happened to it.
     * Attempts are recorded per user in `logs`, which is what makes this possible at all.
     */
    @GetMapping("/users/{id}/prompts")
    ResponseEntity<List<Map<String, Object>>> getUserPrompts(HttpSession session, @PathVariable("id") String id,
                                                             @RequestParam(name = "limit", defaultValue = "200") int limit) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        try {
            return ResponseEntity.ok(jdbcClient.sql("""
                    -- Aliases quoted for the reason spelled out on the recent-prompts query below:
                    -- an unquoted label is lower-cased by Postgres and upper-cased by HSQLDB, and
                    -- listOfRows() serves the label straight out as the JSON key.
                    SELECT id AS "id", level AS "level", prompt AS "prompt",
                           response AS "response", blocked AS "blocked",
                           blocked_by AS "blockedBy",
                           input_tokens + output_tokens AS "tokens",
                           created_at AS "createdAt"
                    FROM logs WHERE user_id = :id AND game_session_id = :gameSessionId
                     ORDER BY created_at DESC LIMIT :limit
                    """).param("id", id).param("limit", Math.min(Math.max(limit, 1), 1000))
                    .param("gameSessionId", gameSessions.activeId())
                    .query().listOfRows());
        } catch (Exception e) {
            logger.warn("user prompt history failed for {}", id, e);
            return ResponseEntity.ok(List.of());
        }
    }

    /** Live feed of the most recent prompts across all players. */
    @GetMapping("/analytics/recent-prompts")
    ResponseEntity<List<Map<String, Object>>> getRecentPrompts(HttpSession session,
                                                               @RequestParam(name = "limit", defaultValue = "50") int limit) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        try {
            return ResponseEntity.ok(jdbcClient.sql("""
                    -- Every alias is quoted, including the ones that look redundant. An unquoted
                    -- alias is folded to lower case by Postgres and upper case by HSQLDB, and
                    -- listOfRows() hands those labels straight out as JSON keys - so the same
                    -- endpoint returned "prompt" in production and "PROMPT" in local dev.
                    SELECT l.id AS "id", l.level AS "level", l.prompt AS "prompt",
                           l.response AS "response", l.blocked AS "blocked",
                           l.blocked_by AS "blockedBy",
                           l.input_tokens + l.output_tokens AS "tokens",
                           l.created_at AS "createdAt",
                           u.display_name AS "displayName", u.email AS "email"
                    FROM logs l JOIN users u ON u.id = l.user_id
                     WHERE l.game_session_id = :gameSessionId
                    ORDER BY l.created_at DESC LIMIT :limit
                    """).param("limit", Math.min(Math.max(limit, 1), 500))
                    .param("gameSessionId", gameSessions.activeId())
                    .query().listOfRows());
        } catch (Exception e) {
            logger.warn("recent-prompts failed", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Sets a new password directly.
     * <p>
     * Deliberately skips the registration policy: this is for handing someone a temporary password
     * at an event, where a 12-character password with a symbol in it is read out wrong every time.
     */
    @PutMapping("/users/{id}/password")
    ResponseEntity<Map<String, String>> resetPassword(HttpSession session, @PathVariable("id") String id,
                                                      @RequestBody PasswordResetRequest request) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        if (request.newPassword() == null || request.newPassword().length() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 4 characters");
        }
        var user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such user"));
        jdbcClient.sql("UPDATE users SET password_hash = :hash WHERE id = :id")
                .param("hash", passwordEncoder.encode(request.newPassword()))
                .param("id", id).update();
        logger.warn("Admin reset the password for {}", user.getEmail());
        return ResponseEntity.ok(Map.of("status", "ok", "email", user.getEmail()));
    }

    /** Grants or revokes admin. Refuses to remove the last admin, which would lock everyone out. */
    @PutMapping("/users/{id}/admin")
    ResponseEntity<Map<String, Object>> setAdminFlag(HttpSession session, @PathVariable("id") String id,
                                                     @RequestBody AdminFlagRequest request) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        if (id.equals(session.getAttribute("userId")) && !request.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot remove your own admin rights");
        }
        if (!request.isAdmin() && countAdmins() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That is the only admin left");
        }
        var user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such user"));
        user.setAdmin(request.isAdmin());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("status", "ok", "isAdmin", request.isAdmin()));
    }

    /** Sends a player back to level 1 without deleting their recorded attempts. */
    @PostMapping("/users/{id}/reset-progress")
    ResponseEntity<Map<String, String>> resetProgress(HttpSession session, @PathVariable("id") String id) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        var user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such user"));
        user.setCurrentLevel(1);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * Removes a player and everything recorded about them. Irreversible, so the caller is told the
     * attempt count up front via GET /users - deleting an account also destroys its prompt history.
     */
    @DeleteMapping("/users/{id}")
    ResponseEntity<Map<String, Object>> deleteUser(HttpSession session, @PathVariable("id") String id) {
        if (!isAdmin(session)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        if (id.equals(session.getAttribute("userId"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own account");
        }
        var user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such user"));
        if (user.isAdmin() && countAdmins() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That is the only admin left");
        }
        int prompts = jdbcClient.sql("DELETE FROM logs WHERE user_id = :id").param("id", id).update();
        jdbcClient.sql("DELETE FROM leaderboard WHERE user_id = :id").param("id", id).update();
        userRepository.deleteById(id);
        logger.warn("Admin deleted account {} along with {} recorded prompts", user.getEmail(), prompts);
        return ResponseEntity.ok(Map.of("status", "ok", "deletedPrompts", prompts, "email", user.getEmail()));
    }

    private long countAdmins() {
        try {
            return jdbcClient.sql("SELECT COUNT(*) FROM users WHERE is_admin = true").query(Long.class).single();
        } catch (Exception e) {
            return 2;   // fail safe: never let a query error be the reason the last admin is removed
        }
    }

    // ============ LEVEL EDITOR ============

    /** The current level definitions, so the admin UI can show and edit them. */
    @GetMapping("/levels")
    public List<LevelDefinition> getLevels(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return levelDefinitionService.definitions();
    }

    /**
     * Redefines one level at runtime. Takes effect on the next request - no restart, no rebuild,
     * which is the point: the curve usually needs adjusting once players are actually on it.
     */
    @PutMapping("/levels/{order}")
    public LevelDefinition updateLevel(HttpSession session, @PathVariable("order") int order, @RequestBody LevelDefinition definition) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        if (definition.order() != order) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Level order in body must match the path");
        }
        levelDefinitionService.update(definition);
        return levelDefinitionService.definition(order);
    }

    private boolean isAdmin(HttpSession session) {
        Object adminAttr = session.getAttribute("isAdmin");
        return adminAttr != null && (Boolean) adminAttr;
    }
}
