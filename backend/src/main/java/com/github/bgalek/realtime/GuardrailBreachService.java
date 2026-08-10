package com.github.bgalek.realtime;

import com.github.bgalek.levels.MerlinLevel;
import com.github.bgalek.levels.MerlinLevelRepository;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class GuardrailBreachService {

    private final List<GuardrailBreach> breaches = new CopyOnWriteArrayList<>();
    private final List<BreachListener> listeners = new CopyOnWriteArrayList<>();
    private final MerlinLevelRepository levelRepository;

    public GuardrailBreachService(MerlinLevelRepository levelRepository) {
        this.levelRepository = levelRepository;
    }

    /**
     * Detect and record when a guardrail is breached
     */
    public void recordBreach(String sessionId, String email, String displayName, int level, 
                            String prompt, String response, GuardrailType breachType, String description) {
        GuardrailBreach breach = new GuardrailBreach(
                UUID.randomUUID().toString(),
                sessionId,
                email,
                displayName,
                level,
                prompt,
                response,
                breachType,
                description,
                Instant.now()
        );
        
        breaches.add(breach);
        
        // Notify all real-time listeners
        notifyListeners(breach);
    }

    /**
     * Check for input filter breach (Level 1-2: No input filter, but Level 3+ might have)
     */
    public boolean checkInputFilterBreach(int level, String prompt, String email, String displayName, String sessionId) {
        MerlinLevel merlinLevel = levelRepository.getLevel(level);
        if (merlinLevel.inputFilter(prompt)) {
            recordBreach(sessionId, email, displayName, level, prompt, "", 
                    GuardrailType.INPUT_FILTER, "Attempted to bypass input filter");
            return true;
        }
        return false;
    }

    /**
     * Check for output filter breach (Level 3+: Output filter to detect password leakage)
     */
    public boolean checkOutputFilterBreach(int level, String prompt, String response, 
                                          String secret, String email, String displayName, String sessionId) {
        MerlinLevel merlinLevel = levelRepository.getLevel(level);
        if (merlinLevel.outputFilter(response, secret)) {
            recordBreach(sessionId, email, displayName, level, prompt, response,
                    GuardrailType.OUTPUT_FILTER, "Response triggered output filter (password detected)");
            return true;
        }
        return false;
    }

    /**
     * Check for prompt injection attempt (detects common injection patterns)
     */
    public boolean checkPromptInjectionBreach(String prompt, String email, String displayName, String sessionId, int level) {
        if (isPromptInjectionAttempt(prompt)) {
            recordBreach(sessionId, email, displayName, level, prompt, "",
                    GuardrailType.PROMPT_INJECTION, "Detected prompt injection attempt");
            return true;
        }
        return false;
    }

    /**
     * Get all breaches for real-time display
     */
    public List<GuardrailBreach> getAllBreaches() {
        return new ArrayList<>(breaches);
    }

    /**
     * Get breaches for a specific user
     */
    public List<GuardrailBreach> getBreachesForUser(String email) {
        return breaches.stream()
                .filter(b -> b.email().equals(email))
                .toList();
    }

    /**
     * Get breaches for a specific level
     */
    public List<GuardrailBreach> getBreachesForLevel(int level) {
        return breaches.stream()
                .filter(b -> b.level() == level)
                .toList();
    }

    /**
     * Get breach statistics
     */
    public BreachStatistics getStatistics() {
        Map<GuardrailType, Integer> typeCount = new HashMap<>();
        Map<Integer, Integer> levelCount = new HashMap<>();
        Map<String, Integer> userCount = new HashMap<>();

        for (GuardrailBreach breach : breaches) {
            typeCount.merge(breach.breachType(), 1, Integer::sum);
            levelCount.merge(breach.level(), 1, Integer::sum);
            userCount.merge(breach.email(), 1, Integer::sum);
        }

        return new BreachStatistics(
                breaches.size(),
                typeCount,
                levelCount,
                userCount,
                Instant.now()
        );
    }

    /**
     * Get recent breaches (last N)
     */
    public List<GuardrailBreach> getRecentBreaches(int limit) {
        return breaches.stream()
                .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
                .limit(limit)
                .toList();
    }

    /**
     * Register listener for real-time breach notifications
     */
    public void addListener(BreachListener listener) {
        listeners.add(listener);
    }

    public void removeListener(BreachListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(GuardrailBreach breach) {
        for (BreachListener listener : listeners) {
            try {
                listener.onBreachDetected(breach);
            } catch (Exception e) {
                // Log and continue
            }
        }
    }

    private boolean isPromptInjectionAttempt(String prompt) {
        String lower = prompt.toLowerCase();
        return lower.contains("ignore") && lower.contains("instruction") ||
               lower.contains("forget") && lower.contains("previous") ||
               lower.contains("what is your") && lower.contains("prompt") ||
               lower.contains("show me") && lower.contains("instruction") ||
               lower.contains("act as") ||
               lower.contains("pretend") && lower.contains("you are");
    }

    // Records
    public record GuardrailBreach(
            String id,
            String sessionId,
            String email,
            String displayName,
            int level,
            String prompt,
            String response,
            GuardrailType breachType,
            String description,
            Instant timestamp
    ) {}

    public record BreachStatistics(
            int totalBreaches,
            Map<GuardrailType, Integer> byType,
            Map<Integer, Integer> byLevel,
            Map<String, Integer> byUser,
            Instant timestamp
    ) {}

    public enum GuardrailType {
        INPUT_FILTER("Input Filter Breach"),
        OUTPUT_FILTER("Output Filter Breach"),
        PROMPT_INJECTION("Prompt Injection Attempt"),
        CONTENT_POLICY("Content Policy Violation"),
        RATE_LIMIT("Rate Limit Exceeded"),
        UNKNOWN("Unknown Breach");

        public final String description;

        GuardrailType(String description) {
            this.description = description;
        }
    }

    @FunctionalInterface
    public interface BreachListener {
        void onBreachDetected(GuardrailBreach breach);
    }
}
