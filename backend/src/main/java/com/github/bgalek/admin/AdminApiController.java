package com.github.bgalek.admin;

import com.github.bgalek.database.LoginHistoryRepository;
import com.github.bgalek.llm.LlmProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final AdminUserService adminUserService;
    private final AdminLlmService adminLlmService;
    private final AdminLeaderboardService adminLeaderboardService;
    private final AdminLoginHistoryService adminLoginHistoryService;
    private final AnalyticsService analyticsService;
    private final AttackDetectionService attackDetectionService;

    public AdminApiController(LlmProvider llmProvider, LoginHistoryRepository loginHistoryRepository) {
        this.adminUserService = new AdminUserService();
        this.adminLlmService = new AdminLlmService(llmProvider);
        this.adminLeaderboardService = new AdminLeaderboardService();
        this.adminLoginHistoryService = new AdminLoginHistoryService(loginHistoryRepository);
        this.analyticsService = new AnalyticsService(List.of(), List.of(), List.of(), List.of());
        this.attackDetectionService = new AttackDetectionService();
    }

    @GetMapping("/users")
    ResponseEntity<List<AdminUserService.AdminUserResponse>> getAllUsers(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.ok(adminUserService.getAllUsers());
    }

    @GetMapping("/llm/config")
    ResponseEntity<AdminLlmService.AdminLlmConfigResponse> getLlmConfig(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.ok(adminLlmService.getCurrentConfig());
    }

    @PostMapping("/llm/config")
    ResponseEntity<AdminLlmService.AdminLlmConfigResponse> updateLlmConfig(
            HttpSession session,
            @RequestBody AdminLlmService.AdminLlmConfigRequest request) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.ok(adminLlmService.updateConfig(request));
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

    @GetMapping("/leaderboard")
    ResponseEntity<List<AdminLeaderboardService.AdminLeaderboardResponse>> getLeaderboard(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.ok(adminLeaderboardService.getLeaderboard());
    }

    @GetMapping("/leaderboard/stats")
    ResponseEntity<AdminLeaderboardService.AdminLeaderboardStatsResponse> getLeaderboardStats(HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.ok(adminLeaderboardService.getStats());
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

    private boolean isAdmin(HttpSession session) {
        Object adminAttr = session.getAttribute("isAdmin");
        return adminAttr != null && (Boolean) adminAttr;
    }
}
