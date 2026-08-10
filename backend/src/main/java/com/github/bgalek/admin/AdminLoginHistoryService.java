package com.github.bgalek.admin;

import com.github.bgalek.database.LoginHistory;
import com.github.bgalek.database.LoginHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class AdminLoginHistoryService {
    private final LoginHistoryRepository loginHistoryRepository;

    public AdminLoginHistoryService(LoginHistoryRepository loginHistoryRepository) {
        this.loginHistoryRepository = loginHistoryRepository;
    }

    public List<LoginHistoryResponse> getRecentLogins(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return loginHistoryRepository.findAllByOrderByLoginTimeDesc(pageable)
            .stream()
            .map(h -> new LoginHistoryResponse(
                h.getId(),
                h.getUserId(),
                h.getEmail(),
                h.getLoginTime(),
                h.getLogoutTime(),
                h.getIpAddress(),
                h.getUserAgent()
            ))
            .collect(Collectors.toList());
    }

    public List<LoginHistoryResponse> getUserLoginHistory(String userId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return loginHistoryRepository.findByUserIdOrderByLoginTimeDesc(userId, pageable)
            .stream()
            .map(h -> new LoginHistoryResponse(
                h.getId(),
                h.getUserId(),
                h.getEmail(),
                h.getLoginTime(),
                h.getLogoutTime(),
                h.getIpAddress(),
                h.getUserAgent()
            ))
            .collect(Collectors.toList());
    }

    public LoginStatsResponse getLoginStats() {
        long loginsToday = loginHistoryRepository.countLoginsAfter(Instant.now().minusSeconds(86400));
        long loginsThisWeek = loginHistoryRepository.countLoginsAfter(Instant.now().minusSeconds(604800));
        long loginsThisMonth = loginHistoryRepository.countLoginsAfter(Instant.now().minusSeconds(2592000));
        
        return new LoginStatsResponse(loginsToday, loginsThisWeek, loginsThisMonth);
    }

    public record LoginHistoryResponse(
        Long id,
        String userId,
        String email,
        Instant loginTime,
        Instant logoutTime,
        String ipAddress,
        String userAgent
    ) {}

    public record LoginStatsResponse(
        long loginsToday,
        long loginsThisWeek,
        long loginsThisMonth
    ) {}
}
