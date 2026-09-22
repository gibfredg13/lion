package com.github.bgalek;

import com.github.bgalek.levels.LevelDefinitionService;
import com.github.bgalek.levels.MerlinLevel;

/**
 * Reads levels through {@link LevelDefinitionService} rather than holding its own map, so a level
 * edited at runtime takes effect on the next request instead of the next restart.
 */
class MerlinLevelRepository {
    private final LevelDefinitionService levelDefinitionService;

    MerlinLevelRepository(LevelDefinitionService levelDefinitionService) {
        this.levelDefinitionService = levelDefinitionService;
    }

    MerlinLevel getLevel(int level) {
        return levelDefinitionService.get(level);
    }

    int count() {
        return levelDefinitionService.count();
    }
}
