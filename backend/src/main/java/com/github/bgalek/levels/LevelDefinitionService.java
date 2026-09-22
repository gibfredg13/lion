package com.github.bgalek.levels;

import com.github.bgalek.llm.LlmProvider;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Holds the live level definitions and hands out {@link MerlinLevel} instances built from them.
 * <p>
 * Definitions are replaceable at runtime so the difficulty curve can be retuned during an event -
 * after watching where people actually stall - without rebuilding and redeploying.
 */
public class LevelDefinitionService {

    private static final Logger logger = getLogger(LevelDefinitionService.class);

    private final LlmProvider llmProvider;
    private volatile Map<Integer, ConfigurableLevel> levels = new LinkedHashMap<>();

    public LevelDefinitionService(List<LevelDefinition> definitions, LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
        replaceAll(definitions);
    }

    public final void replaceAll(List<LevelDefinition> definitions) {
        Map<Integer, ConfigurableLevel> next = new LinkedHashMap<>();
        definitions.stream()
                .sorted(java.util.Comparator.comparingInt(LevelDefinition::order))
                .forEach(d -> next.put(d.order(), new ConfigurableLevel(d, llmProvider)));
        this.levels = next;
        logger.info("Loaded {} level definitions", next.size());
    }

    /** Replaces one level and keeps the rest. Used by the admin level editor. */
    public void update(LevelDefinition definition) {
        Map<Integer, ConfigurableLevel> next = new LinkedHashMap<>(levels);
        next.put(definition.order(), new ConfigurableLevel(definition, llmProvider));
        this.levels = next;
        logger.info("Level {} redefined at runtime", definition.order());
    }

    public MerlinLevel get(int order) {
        MerlinLevel level = levels.get(order);
        if (level == null) {
            throw new IllegalArgumentException("Level %d does not exist".formatted(order));
        }
        return level;
    }

    public LevelDefinition definition(int order) {
        ConfigurableLevel level = levels.get(order);
        return level == null ? null : level.definition();
    }

    public List<LevelDefinition> definitions() {
        List<LevelDefinition> out = new ArrayList<>();
        levels.values().forEach(l -> out.add(l.definition()));
        return out;
    }

    public int count() {
        return levels.size();
    }
}
