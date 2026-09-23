package com.github.bgalek;

import com.github.bgalek.llm.LlmProvider;
import com.github.bgalek.llm.LlmRequest;
import com.github.bgalek.levels.MerlinLevel;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

class MerlinService {
    private static final Logger logger = getLogger(MerlinService.class);
    private final LlmProvider llmProvider;
    private final MerlinLevelRepository merlinLevelRepository;
    private final MerlinLeaderboardRepository merlinLeaderboardRepository;
    private final MerlinLogger merlinLogger;
    private final MerlinLevelProgressRepository levelProgressRepository;
    private final List<String> merlinPasswords;
    /**
     * Which box answers right now.
     * <p>
     * A supplier rather than the router itself, so this class stays decoupled from the routing.
     * It is only used to key the response cache - see {@link #getCacheKey}.
     */
    private final java.util.function.Supplier<String> activeBackendId;
    /** Controls which levels are enabled - admin can toggle these in real-time without restart */
    private final com.github.bgalek.admin.LevelGateService levelGateService;
    private final com.github.bgalek.database.UserRepository userRepository;
    private final com.github.bgalek.session.GameSessionService gameSessions;
    private final Cache<String, com.github.bgalek.llm.LlmResponse> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    MerlinService(LlmProvider llmProvider,
                  MerlinLevelRepository merlinLevelRepository,
                  MerlinLeaderboardRepository merlinLeaderboardRepository,
                  MerlinLogger merlinLogger,
                  MerlinLevelProgressRepository levelProgressRepository,
                  List<String> merlinPasswords,
                  java.util.function.Supplier<String> activeBackendId,
                  com.github.bgalek.admin.LevelGateService levelGateService,
                  com.github.bgalek.database.UserRepository userRepository,
                  com.github.bgalek.session.GameSessionService gameSessions) {
        this.llmProvider = llmProvider;
        this.merlinLevelRepository = merlinLevelRepository;
        this.merlinLeaderboardRepository = merlinLeaderboardRepository;
        this.merlinLogger = merlinLogger;
        this.levelProgressRepository = levelProgressRepository;
        this.merlinPasswords = merlinPasswords;
        this.activeBackendId = activeBackendId;
        this.levelGateService = levelGateService;
        this.userRepository = userRepository;
        this.gameSessions = gameSessions;
    }

    /**
     * One player turn. Carries enough detail for the attempt log: whether a guardrail fired and
     * what the turn cost, neither of which a bare response string could express.
     */
    record Answer(String text, boolean blocked, String blockedBy, int inputTokens, int outputTokens) {
        static Answer plain(String text, int in, int out) {
            return new Answer(text, false, null, in, out);
        }

        static Answer blocked(String text, String by) {
            return new Answer(text, true, by, 0, 0);
        }
    }

    Answer respond(HttpSession httpSession, int currentLevel, String prompt) {
        // Admin can disable individual levels at runtime via the admin war room dashboard
        if (!levelGateService.isLevelEnabled(currentLevel)) {
            return Answer.blocked("This level is switched off at the moment. Check with the organisers.", "level-disabled");
        }
        MerlinLevel level = merlinLevelRepository.getLevel(currentLevel);
        if (level.inputFilter(prompt)) return Answer.blocked(level.inputFilterResponse(), "input-filter");
        String currentSessionSecret = getCurrentSessionPassword(httpSession, currentLevel);
        // The model is the backend's business, not this class's. This used to override every
        // request with a model name captured at startup, which sends one box's model id to
        // whichever box is actually answering the moment there is more than one of them.
        LlmRequest request = level.prompt(prompt, currentSessionSecret);
        com.github.bgalek.llm.LlmResponse llm;
        try {
            llm = cache.get(getCacheKey(activeBackendId.get(), gameSessions.activeId(),
                    httpSession.getId(), currentLevel, prompt), key ->
                    llmProvider.chat(request));
        } catch (com.github.bgalek.llm.LlmBusyException e) {
            return Answer.blocked("Too many questions at once right now - wait a moment and send that again.", "llm-busy");
        } catch (Exception e) {
            logger.error("LLM call failed at level {}", currentLevel, e);
            return Answer.blocked("Something went wrong on our side, not yours - that was not a guardrail. Please try again.", "llm-error");
        }
        String response = llm == null ? null : llm.content();
        // If the output exposes the secret, return the canned guardrail response.
        //
        // Kept outside the call above because the JUDGE filter makes a second LLM call of its own,
        // and that one must be able to fail the turn rather than wave the answer through: it is the
        // later of the two calls, so it is the one that loses the race for a slot under load.
        try {
            if (response != null && level.outputFilter(response, currentSessionSecret)) {
                return new Answer(level.outputFilterResponse(), true, "output-filter", llm.inputTokens(), llm.outputTokens());
            }
        } catch (com.github.bgalek.llm.LlmBusyException e) {
            return Answer.blocked("Too many questions at once right now - wait a moment and send that again.", "judge-busy");
        }
        return Answer.plain(presentable(response), llm == null ? 0 : llm.inputTokens(), llm == null ? 0 : llm.outputTokens());
    }

    boolean checkSecret(HttpSession httpSession, String secret) {
        int currentLevel = getCurrentLevel(httpSession);
        return getCurrentSessionPassword(httpSession, currentLevel).equalsIgnoreCase(secret);
    }

    String advanceLevel(HttpSession httpSession) {
        int currentLevel = getCurrentLevel(httpSession);
        MerlinLevel merlinLevel = merlinLevelRepository.getLevel(currentLevel);
        int nextLevel = currentLevel + 1;
        httpSession.setAttribute("level", nextLevel);
        // Fix 3: Persist level progress to the database so it survives logout/restart
        String userId = (String) httpSession.getAttribute("userId");
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user -> {
                user.setCurrentLevel(nextLevel);
                userRepository.save(user);
            });
            // Level 1 is never advanced into, so seed it here or every timeline starts at 2.
            levelProgressRepository.ensureStartLevel(userId, gameSessions.activeId());
            levelProgressRepository.recordLevelReached(userId, nextLevel, Instant.now(), gameSessions.activeId());
        }
        if (currentLevel == getMaxLevel()) {
            saveLeaderboardEntry(httpSession);
        }
        return merlinLevel.getLevelFinishedResponse();
    }

    /**
     * Where a player is, and the only place that question is answered during play.
     * <p>
     * A player's level lives in an HttpSession attribute, so no UPDATE can reach someone who is
     * already logged in - and the session cookie lasts thirty days. Starting a new run therefore
     * re-reads from the database the first time each player is seen in it. Both modes fall out of
     * the same check: after a reset the row says 1, after a carry-on it says whatever they reached.
     * <p>
     * This works only while it stays the single reader. <strong>Do not read the "level" attribute
     * anywhere else</strong> - a second reader would keep serving the previous run's level, the
     * player would carry on from it, and the only visible symptom would be one odd projector row.
     */
    int getCurrentLevel(HttpSession session) {
        String active = gameSessions.activeId();
        if (active != null && !active.equals(session.getAttribute("gameSessionId"))) {
            rehydrate(session, active);
        }
        return Optional.ofNullable(session.getAttribute("level")).map(x -> (Integer) x).orElse(1);
    }

    /** Re-reads a player's level from the database because the run changed under their browser. */
    private void rehydrate(HttpSession session, String activeGameSessionId) {
        String userId = (String) session.getAttribute("userId");
        int level = userId == null ? 1
                : userRepository.findById(userId).map(com.github.bgalek.database.User::getCurrentLevel).orElse(1);
        session.setAttribute("level", level);
        session.setAttribute("gameSessionId", activeGameSessionId);
        logger.info("Player {} rehydrated to level {} for game session {}", userId, level, activeGameSessionId);
    }

    int getMaxLevel() {
        return merlinLevelRepository.count();
    }

    /** Fix 5: Reset progress to level 1 without destroying the login session */
    void resetLevel(HttpSession session) {
        session.setAttribute("level", 1);
        String userId = (String) session.getAttribute("userId");
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user -> {
                user.setCurrentLevel(1);
                userRepository.save(user);
            });
            // The old milestones describe a run that no longer exists. Start the timeline again
            // rather than leaving a level-7 timestamp beside a current level of 1.
            levelProgressRepository.clearForUser(userId, gameSessions.activeId());
            levelProgressRepository.recordLevelReached(userId, 1, Instant.now(), gameSessions.activeId());
        }
    }

    void saveLeaderboardEntry(HttpSession session) {
        try {
            merlinLeaderboardRepository.addEntry(
                    (String) session.getAttribute("userId"),
                    session.getId(),
                    (String) session.getAttribute("displayName"),
                    Instant.ofEpochMilli(session.getCreationTime()),
                    gameSessions.activeId());
        } catch (Exception e) {
            logger.error("Failed to save leaderboard entry", e);
        }
    }

    void submitName(HttpSession session, String name) {
        try {
            session.setAttribute("submittedName", name);
            merlinLeaderboardRepository.addName(session.getId(), name);
        } catch (Exception e) {
            logger.error("Failed to log attempt", e);
        }
    }

    void logAttempt(String userId, String session, int currentLevel, String prompt, Answer answer) {
        try {
            merlinLogger.logAttempt(userId, session, currentLevel, prompt, answer.text(),
                    answer.blocked(), answer.blockedBy(), answer.inputTokens(), answer.outputTokens(),
                    gameSessions.activeId());
        } catch (Exception e) {
            logger.error("Failed to log attempt", e);
        }
    }

    Set<LeaderboardEntry> getLeaderboard() {
        return merlinLeaderboardRepository.getLeaderboard(gameSessions.activeId());
    }

    /**
     * Guards the degenerate replies this model produces: an empty string, or output with no letters
     * at all (asking it to spell something out often yields ". . . . ."). Either reaches the player
     * as an apparently broken app, so answer in character instead.
     */
    private static String presentable(String response) {
        if (response == null || response.isBlank() || !response.matches("(?s).*[a-zA-Z].*")) {
            return "\uD83E\uDD81 Leo regards you in silence. Ask me something else.";
        }
        // The model wraps replies in roleplay stage directions and markdown emphasis, which render
        // as literal asterisks in the chat bubble. Only the asterisk characters are removed, never
        // the text between them: the model very often writes the password as *WORD* or **WORD**,
        // and stripping those spans would delete the answer the player just earned.
        String cleaned = response
                .replaceAll("\\*+", "")
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return cleaned.isBlank() ? response.trim() : cleaned;
    }

    private String getCurrentSessionPassword(HttpSession httpSession, int currentLevel) {
        // Seeded on the browser session AND the run, not the browser session alone. The session
        // cookie lasts thirty days, so under the old seed a player's level-N word was fixed for a
        // month - and restarting the event at level 1 handed everyone back the very words they had
        // just solved. At a jailbreaking competition, everyone writes them down.
        Random random = new Random((httpSession.getId() + "|" + gameSessions.activeId()).hashCode());
        for (int i = 1; i < currentLevel; i++) {
            random.nextInt(merlinPasswords.size());
        }
        int passwordIndex = random.nextInt(merlinPasswords.size());
        return merlinPasswords.get(passwordIndex).toUpperCase(Locale.ROOT);
    }

    /**
     * Keyed by backend as well as session, so switching boxes cannot serve the previous one's
     * answers for the remainder of the cache's minute - which is exactly what someone comparing
     * two backends does first.
     */
    private static String getCacheKey(String backendId, String gameSessionId, String httpSession,
                                      int currentLevel, String prompt) {
        return "%s-%s-%s-%d-%s".formatted(backendId, gameSessionId, httpSession, currentLevel, prompt);
    }

    /** Dropped when the active backend changes, so the switch is visible on the very next turn. */
    void invalidateCache() {
        cache.invalidateAll();
    }

    record LeaderboardEntry(String session, String name, Instant startedAt, Instant finishedAt) {
    }
}
