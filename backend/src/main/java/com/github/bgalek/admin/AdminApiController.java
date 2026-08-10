package com.github.bgalek.admin;

import com.github.bgalek.llm.LlmProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final AdminUserService adminUserService;
    private final AdminLlmService adminLlmService;
    private final AdminLeaderboardService adminLeaderboardService;

    public AdminApiController(LlmProvider llmProvider) {
        this.adminUserService = new AdminUserService();
        this.adminLlmService = new AdminLlmService(llmProvider);
        this.adminLeaderboardService = new AdminLeaderboardService();
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

    private boolean isAdmin(HttpSession session) {
        Object adminAttr = session.getAttribute("isAdmin");
        return adminAttr != null && (Boolean) adminAttr;
    }
}
