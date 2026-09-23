package com.github.bgalek.admin;

import com.github.bgalek.levels.LevelDefinition;
import com.github.bgalek.levels.LevelDefinitionService;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Turns a finished calibration run into the Help Desk's worked solutions.
 * <p>
 * The bundled key in {@code admin-dashboard/src/data/solutions.ts} is written by an offline
 * emitter and frozen into the dashboard bundle, so it is only ever as current as the last time
 * someone ran the generator and rebuilt two images. A calibration started from the war room
 * measures exactly the same thing - which prompts beat which level, and what Leo replied - and
 * threw it away. This keeps it.
 * <p>
 * The split the emitter established is kept: <b>the prose is authored</b>, in
 * {@code calibration/guide.yml}, and <b>the evidence is measured</b>. This only joins the two, the
 * same way {@code SolutionsEmitter} does, so the two keys cannot describe different games.
 */
@Service
public class AnswerKeyService {

    private static final Logger logger = getLogger(AnswerKeyService.class);

    /** Matches the emitter, so a reply reads the same in both keys. */
    private static final int MAX_REPLY = 400;

    private final AnswerKeyRepository repository;
    private final LevelDefinitionService levelDefinitionService;
    private final Map<String, Object> guide;

    public AnswerKeyService(AnswerKeyRepository repository, LevelDefinitionService levelDefinitionService) {
        this.repository = repository;
        this.levelDefinitionService = levelDefinitionService;
        this.guide = loadGuide();
    }

    private static Map<String, Object> loadGuide() {
        try (InputStream in = AnswerKeyService.class.getResourceAsStream("/calibration/guide.yml")) {
            if (in == null) return Map.of();
            return new Yaml().load(in);
        } catch (Exception e) {
            // Prose missing is a cosmetic loss - the prompts and replies are the part a facilitator
            // cannot reconstruct - so it must not stop the key being saved.
            logger.warn("Could not read calibration/guide.yml; the answer key will have no prose", e);
            return Map.of();
        }
    }

    /** One worked example, with how reliably it landed. */
    public record Attack(String family, String prompt, String reply, String why, String how,
                         int wins, int runs) {}

    public record LevelGuide(int level, String name, String summary, String defence,
                             List<String> blocked, List<String> hints, List<Attack> attacks) {}

    public record MeasuredKey(List<LevelGuide> levels, String runId, String backendLabel,
                              String model, Instant measuredAt, List<Integer> measuredLevels) {
        /** Nothing has been measured yet, so the dashboard should keep showing the bundled key. */
        public boolean empty() {
            return levels.stream().allMatch(l -> l.attacks().isEmpty());
        }
    }

    /**
     * Records what a finished run proved, for the levels it actually covered.
     * <p>
     * Levels the run skipped keep the rows they had. A one-level run is a normal thing to do while
     * tuning, and it must top up one section rather than emptying the other six.
     */
    public void record(CalibrationService.CalibrationRun run) {
        if (run == null || run.levels() == null || run.levels().isEmpty()) return;
        List<AnswerKeyRepository.Row> rows = new ArrayList<>();
        Set<Integer> levels = run.levels().stream()
                // A level that never reached the model proves nothing about its solutions, and
                // wiping its section on the strength of a switched-off box is the worst outcome
                // here: the Help Desk would say "no known way through" during an event.
                .filter(score -> !score.unreachable())
                .map(CalibrationService.LevelScore::level)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Instant measuredAt = run.completedAt() == null ? Instant.now() : run.completedAt();

        for (CalibrationService.LevelScore score : run.levels()) {
            if (!levels.contains(score.level())) continue;
            Map<String, Integer> runsByFamily = new LinkedHashMap<>();
            Map<String, Integer> winsByFamily = new LinkedHashMap<>();
            for (var r : score.reliability() == null ? List.<CalibrationService.FamilyReliability>of() : score.reliability()) {
                runsByFamily.put(r.family(), r.totalRuns());
                winsByFamily.put(r.family(), r.wonRuns());
            }
            for (var attack : bestPerFamily(score)) {
                rows.add(new AnswerKeyRepository.Row(
                        score.level(), attack.family(), attack.prompt(), truncate(attack.response()),
                        attack.how(),
                        winsByFamily.getOrDefault(attack.family(), 1),
                        runsByFamily.getOrDefault(attack.family(), Math.max(1, run.repeats())),
                        run.id(), run.backendLabel(), run.model(), measuredAt));
            }
        }
        repository.replaceLevels(levels, rows);
        logger.info("Answer key updated from calibration {}: {} worked solutions across levels {}",
                run.id(), rows.size(), levels);
    }

    /**
     * One worked example per family, which is the answer key's shape.
     * <p>
     * The longest reply wins the tie rather than the first. A facilitator reads this aloud, and
     * "THUNDER" teaches a stuck player less than the sentence Leo wrapped around it.
     */
    private static List<CalibrationService.AttackResult> bestPerFamily(CalibrationService.LevelScore score) {
        Map<String, CalibrationService.AttackResult> best = new LinkedHashMap<>();
        for (var attack : score.attacks() == null ? List.<CalibrationService.AttackResult>of() : score.attacks()) {
            if (!attack.leaked()) continue;
            best.merge(attack.family(), attack,
                    (a, b) -> length(b.response()) > length(a.response()) ? b : a);
        }
        return best.values().stream()
                .sorted(Comparator.comparing(CalibrationService.AttackResult::family))
                .toList();
    }

    private static int length(String s) {
        return s == null ? 0 : s.length();
    }

    private static String truncate(String reply) {
        if (reply == null) return null;
        String collapsed = reply.strip();
        return collapsed.length() <= MAX_REPLY ? collapsed : collapsed.substring(0, MAX_REPLY) + "...";
    }

    /** The measured key, joined to the authored prose, in the shape the Help Desk already renders. */
    public MeasuredKey current() {
        List<AnswerKeyRepository.Row> rows = repository.all();
        List<LevelGuide> guides = new ArrayList<>();
        for (LevelDefinition definition : levelDefinitionService.definitions()) {
            List<Attack> attacks = rows.stream()
                    .filter(r -> r.level() == definition.order())
                    .map(r -> new Attack(r.family(), r.prompt(), r.reply(), why(r.family()), r.how(),
                            r.wins(), r.runs()))
                    .toList();
            guides.add(new LevelGuide(definition.order(), definition.name(), definition.description(),
                    defence(definition), definition.inputFilterKeywords(), hints(definition.order()),
                    attacks));
        }
        AnswerKeyRepository.Row newest = rows.stream()
                .filter(r -> r.measuredAt() != null)
                .max(Comparator.comparing(AnswerKeyRepository.Row::measuredAt))
                .orElse(null);
        return new MeasuredKey(guides,
                newest == null ? null : newest.runId(),
                newest == null ? null : newest.backendLabel(),
                newest == null ? null : newest.model(),
                newest == null ? null : newest.measuredAt(),
                rows.stream().map(AnswerKeyRepository.Row::level).distinct().sorted().toList());
    }

    /**
     * Raw key type on purpose: guide.yml's {@code levels:} keys parse as Integers and its
     * {@code families:} keys as Strings, and typing this as one or the other only moves the cast.
     */
    @SuppressWarnings("unchecked")
    private Map<Object, Object> section(String name) {
        Object value = guide.get(name);
        return value instanceof Map ? (Map<Object, Object>) value : Map.of();
    }

    private String why(String family) {
        Object entry = section("families").get(family);
        return entry instanceof Map<?, ?> m ? String.valueOf(Optional.ofNullable(m.get("why")).orElse("")) : "";
    }

    private String defence(LevelDefinition definition) {
        Object text = section("defences").get(definition.outputFilter().name());
        return text == null ? "" : String.valueOf(text);
    }

    @SuppressWarnings("unchecked")
    private List<String> hints(int level) {
        Object entry = section("levels").get(Integer.valueOf(level));
        if (entry instanceof Map<?, ?> m && m.get("hints") instanceof List<?> hints) {
            return (List<String>) hints;
        }
        return List.of();
    }
}
