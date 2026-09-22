package com.github.bgalek.tv;

import com.github.bgalek.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * The projector feed behind /leaderboard/tv.
 * <p>
 * Unauthenticated by design: the screen at the venue has no keyboard and nobody to log it in. That
 * makes this the one controller in the application where a careless SELECT is a disclosure rather
 * than a bug, so it is kept deliberately small and separate from the endpoints that serve people
 * who have signed in.
 * <p>
 * Three rules:
 * <ol>
 *   <li>No email address, no user id, no account identifier of any kind leaves this class. A
 *       player is a display name and nothing else. A stable per-player token would also be a
 *       correlation handle: polled every few seconds it reconstructs who cleared what and when.</li>
 *   <li>Every row is scoped to the run in progress. Previous runs are history, and history is
 *       admin-only.</li>
 *   <li>The attempt feed can be switched off from the dashboard and goes dark on the next poll.
 *       A prompt is a player's own words, on a wall, in front of their colleagues.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/tv")
public class TvController {

    /**
     * Keyed on the caller, because this endpoint is unauthenticated and runs half a dozen
     * aggregates. Generous enough for several screens at a five-second poll, mean enough that a
     * loop cannot take the event down.
     */
    private final RateLimiter limiter = new RateLimiter(60, Duration.ofMinutes(1));

    private final TvService tvService;

    public TvController(TvService tvService) {
        this.tvService = tvService;
    }

    @GetMapping("/snapshot")
    public ResponseEntity<TvSnapshot> snapshot(HttpServletRequest request) {
        if (!limiter.tryAcquire(clientKey(request))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        return ResponseEntity.ok()
                // Collapses a second screen, and anyone scraping, onto one query every few seconds.
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(3)).cachePublic())
                .body(tvService.snapshot());
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
