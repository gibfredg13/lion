package com.github.bgalek.admin;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

/**
 * Controls which game levels are currently enabled.
 * Levels can be toggled on/off in real-time via the admin dashboard
 * without requiring a restart.
 */
@Service
public class LevelGateService {

    /**
     * Only levels an admin has explicitly toggled appear here; everything else is enabled.
     * <p>
     * This used to be seeded with 1..7 in the constructor, which duplicated a level count that
     * every other class derives from {@code LevelDefinitionService.count()} - so the two would
     * silently disagree the moment a level was added or removed. The default below already covers
     * an unknown level, so the seed was never doing any work.
     */
    private final Map<Integer, Boolean> levelEnabled = new HashMap<>();

    public boolean isLevelEnabled(int level) {
        return levelEnabled.getOrDefault(level, true);
    }

    public void setLevelEnabled(int level, boolean enabled) {
        levelEnabled.put(level, enabled);
    }

    /** Only the levels that have been toggled. A level absent from this map is enabled. */
    public Map<Integer, Boolean> getAllLevelStates() {
        return Map.copyOf(levelEnabled);
    }
}
