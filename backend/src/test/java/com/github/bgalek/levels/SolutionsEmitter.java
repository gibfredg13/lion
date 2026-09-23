package com.github.bgalek.levels;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes the facilitator answer key from what the calibration harness actually measured.
 * <p>
 * Both artefacts it produces have carried the line "GENERATED - do not edit by hand" since before
 * there was a generator, and both were hand-maintained and wrong: they documented a difficulty curve
 * the game no longer had. A guide that drifts from the game is worse than no guide, because the
 * person reading it is standing next to a stuck player.
 * <p>
 * The split is deliberate. <b>The prose is authored</b>, in
 * {@code src/test/resources/calibration/guide.yml} - a generator that writes its own explanations
 * writes bad ones. <b>The evidence is measured</b> - the prompts, the replies, and which of them
 * actually won. This class only joins the two.
 * <p>
 * Opt-in: set {@code CALIBRATE_EMIT=<repo root>} alongside {@code CALIBRATE=true}, so an ordinary
 * calibration run stays read-only.
 */
final class SolutionsEmitter {

    /** Only the per-level sections are generated; the preamble and taxonomy are hand-written. */
    private static final String BEGIN = "<!-- BEGIN GENERATED -->";
    private static final String END = "<!-- END GENERATED -->";

    /** One measured attack that beat a level, with the reply it beat it with. */
    record Win(int level, String family, String prompt, String reply, String how) {
    }

    private final Map<String, Object> guide;

    SolutionsEmitter() {
        try (InputStream in = SolutionsEmitter.class.getResourceAsStream("/calibration/guide.yml")) {
            this.guide = new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read calibration/guide.yml", e);
        }
    }

    static Path requestedOutputDirectory() {
        String dir = System.getenv("CALIBRATE_EMIT");
        return dir == null || dir.isBlank() ? null : Path.of(dir);
    }

    /**
     * @param wins     one worked example per family per level - the answer key's per-level sections
     * @param everyWin every measured leak, which is what the reuse matrix needs: a family's second
     *                 prompt is exactly where a route that beats every rung hides from a taxonomy
     *                 recording only the first
     */
    void emit(Path repoRoot, List<LevelDefinition> definitions, List<Win> wins, List<Win> everyWin)
            throws Exception {
        List<String> empty = new ArrayList<>();
        for (LevelDefinition definition : definitions) {
            if (wins.stream().noneMatch(w -> w.level() == definition.order())) {
                empty.add(String.valueOf(definition.order()));
            }
        }
        if (!empty.isEmpty()) {
            // An empty section reads as "this level is unbeatable" to whoever is helping a player.
            throw new IllegalStateException("refusing to write the answer key: no measured solution for level(s) "
                    + String.join(", ", empty) + ". Fix the level, then re-run.");
        }
        String generatedAt = LocalDate.now().toString();
        String levelsHash = shortHash(repoRoot.resolve("backend/src/main/resources/levels.yml"));
        String stamp = "Generated %s from levels.yml sha256:%s".formatted(generatedAt, levelsHash);
        writeTypeScript(repoRoot.resolve("admin-dashboard/src/data/solutions.ts"), definitions, wins,
                stamp, generatedAt, levelsHash);
        writeMarkdown(repoRoot.resolve("docs/SOLUTIONS.md"), definitions, wins, everyWin, stamp);
    }

    // -- admin dashboard -------------------------------------------------------------------------

    private void writeTypeScript(Path target, List<LevelDefinition> definitions, List<Win> wins,
                                 String stamp, String generatedAt, String levelsHash)
            throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("// GENERATED - do not edit by hand.\n")
                .append("// ").append(stamp).append('\n')
                .append("// Every prompt below actually beat that level against the live model, and the replies are\n")
                .append("// real. Regenerate with CALIBRATE=true CALIBRATE_EMIT=<repo root> ./gradlew :backend:test\n")
                .append("// --tests '*LevelCalibrationTest*'\n\n")
                // The same provenance as the comment above, but readable by the dashboard: it uses
                // these to say how old the key is and whether the levels have changed underneath it.
                .append("export const GENERATED_AT = \"").append(generatedAt).append("\";\n")
                .append("export const GENERATED_FROM_LEVELS_HASH = \"").append(levelsHash).append("\";\n\n")
                .append("export interface Attack { family: string; prompt: string; reply: string; why: string; how: string; }\n")
                .append("export interface LevelGuide {\n")
                .append("  level: number; name: string; summary: string; defence: string;\n")
                .append("  blocked: string[]; hints: string[]; attacks: Attack[];\n")
                .append("}\n\n")
                .append("export const LEVEL_GUIDES: LevelGuide[] = [\n");
        for (LevelDefinition definition : definitions) {
            sb.append("  {\n")
                    .append("    \"level\": ").append(definition.order()).append(",\n")
                    .append("    \"name\": ").append(json(definition.name())).append(",\n")
                    .append("    \"summary\": ").append(json(definition.description())).append(",\n")
                    .append("    \"defence\": ").append(json(defenceOf(definition))).append(",\n")
                    .append("    \"blocked\": [").append(String.join(", ",
                            definition.inputFilterKeywords().stream().map(SolutionsEmitter::json).toList())).append("],\n")
                    .append("    \"hints\": [\n");
            List<String> hints = hintsFor(definition.order());
            for (int i = 0; i < hints.size(); i++) {
                sb.append("      ").append(json(hints.get(i))).append(i < hints.size() - 1 ? ",\n" : "\n");
            }
            sb.append("    ],\n    \"attacks\": [\n");
            List<Win> forLevel = winsFor(wins, definition.order());
            for (int i = 0; i < forLevel.size(); i++) {
                Win w = forLevel.get(i);
                sb.append("      {\n")
                        .append("        \"family\": ").append(json(w.family())).append(",\n")
                        .append("        \"prompt\": ").append(json(w.prompt())).append(",\n")
                        .append("        \"reply\": ").append(json(truncate(w.reply()))).append(",\n")
                        .append("        \"why\": ").append(json(whyOf(w.family()))).append(",\n")
                        .append("        \"how\": ").append(json(w.how())).append('\n')
                        .append("      }").append(i < forLevel.size() - 1 ? ",\n" : "\n");
            }
            sb.append("    ]\n  },\n");
        }
        sb.append("];\n");
        Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
    }

    // -- facilitator answer key ------------------------------------------------------------------

    private void writeMarkdown(Path target, List<LevelDefinition> definitions, List<Win> wins,
                               List<Win> everyWin, String stamp) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(BEGIN).append("\n\n*").append(stamp).append(".*\n");
        sb.append(reuseMatrix(definitions, wins, everyWin));
        for (LevelDefinition definition : definitions) {
            List<Win> forLevel = winsFor(wins, definition.order());
            sb.append("\n---\n\n## Level ").append(definition.order()).append(" — ").append(definition.name())
                    .append("\n\n*").append(definition.description()).append("*\n\n")
                    .append("**What defends it.** ").append(defenceOf(definition)).append('\n');
            if (!definition.inputFilterKeywords().isEmpty()) {
                sb.append("\nQuestions containing any of these phrases never reach Leo at all: ")
                        .append(String.join(", ", definition.inputFilterKeywords().stream()
                                .map(k -> "`" + k + "`").toList()))
                        .append(".\n");
            }
            sb.append("\n**Verified ways through** — ").append(distinctFamilies(forLevel)).append(" families:\n");
            for (Win w : forLevel) {
                sb.append("\n### ").append(w.family()).append("\n\n> ").append(w.prompt())
                        .append("\n\nLeo replied:\n\n```\n").append(truncate(w.reply())).append("\n```\n\n")
                        .append("*Why it works:* ").append(whyOf(w.family()))
                        .append(" **(").append(w.how()).append(")**\n");
            }
            sb.append("\n**Hint ladder**\n\n");
            List<String> hints = hintsFor(definition.order());
            for (int i = 0; i < hints.size(); i++) {
                sb.append(i + 1).append(". ").append(hints.get(i)).append('\n');
            }
        }
        sb.append('\n').append(END).append('\n');

        String existing = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : "";
        int begin = existing.indexOf(BEGIN);
        int end = existing.indexOf(END);
        String merged = begin >= 0 && end > begin
                ? existing.substring(0, begin) + sb + existing.substring(end + END.length() + 1)
                : existing + "\n" + sb;
        Files.writeString(target, merged, StandardCharsets.UTF_8);
    }

    /**
     * Which family beat which level, and which single sentences beat several.
     * <p>
     * Generated because it is the one view that shows whether the ladder is a staircase, and the
     * per-level sections below structurally cannot: each reads as a list of ways through one level,
     * so a route that solves all seven appears in seven separate sections and looks like seven
     * routes. Written flat, four prompts clearing six rungs apiece is the first thing on the page.
     * <p>
     * It is also what a facilitator needs at the venue. "Which of these have you tried" is a better
     * question when you can see that the thing the player has been doing since level 2 will keep
     * working until level 6, and that they have not learned anything since.
     */
    private static String reuseMatrix(List<LevelDefinition> definitions, List<Win> wins, List<Win> everyWin) {
        List<Integer> orders = definitions.stream().map(LevelDefinition::order).sorted().toList();
        List<String> families = wins.stream().map(Win::family).distinct().sorted().toList();

        StringBuilder sb = new StringBuilder("\n### Which route beats which level\n\n| Family |");
        orders.forEach(o -> sb.append(' ').append(o).append(" |"));
        sb.append("\n|---|").append("---|".repeat(orders.size())).append('\n');
        for (String family : families) {
            sb.append("| **").append(family).append("** |");
            for (Integer order : orders) {
                boolean won = wins.stream().anyMatch(w -> w.level() == order && w.family().equals(family));
                sb.append(won ? " X |" : " · |");
            }
            sb.append('\n');
        }

        Map<String, List<Integer>> byPrompt = new java.util.LinkedHashMap<>();
        for (Win w : everyWin) {
            List<Integer> levels = byPrompt.computeIfAbsent(w.prompt(), k -> new ArrayList<>());
            if (!levels.contains(w.level())) levels.add(w.level());
        }
        List<Map.Entry<String, List<Integer>>> reused = byPrompt.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .toList();
        if (!reused.isEmpty()) {
            sb.append("\n### Prompts that beat more than one level\n\n")
                    .append("A sentence that clears several rungs unchanged is a rung the player skipped: the ")
                    .append("answer never had to change, so the level in between taught nothing.\n\n")
                    .append("| Levels | Prompt |\n|---|---|\n");
            for (Map.Entry<String, List<Integer>> entry : reused) {
                sb.append("| ").append(entry.getValue().size()).append(" — ")
                        .append(entry.getValue().stream().sorted().map(String::valueOf).toList())
                        .append(" | ").append(entry.getKey().replace("|", "\\|")).append(" |\n");
            }
        }
        return sb.toString();
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static List<Win> winsFor(List<Win> wins, int level) {
        return wins.stream().filter(w -> w.level() == level).toList();
    }

    private static long distinctFamilies(List<Win> wins) {
        return wins.stream().map(Win::family).distinct().count();
    }

    @SuppressWarnings("unchecked")
    private String defenceOf(LevelDefinition definition) {
        Map<String, String> defences = (Map<String, String>) guide.get("defences");
        return defences.getOrDefault(definition.outputFilter().name(), "");
    }

    @SuppressWarnings("unchecked")
    private String whyOf(String family) {
        Map<String, Map<String, String>> families = (Map<String, Map<String, String>>) guide.get("families");
        Map<String, String> entry = families.get(family);
        return entry == null ? "" : entry.getOrDefault("why", "");
    }

    @SuppressWarnings("unchecked")
    private List<String> hintsFor(int order) {
        Map<Integer, Map<String, Object>> levels = (Map<Integer, Map<String, Object>>) guide.get("levels");
        Map<String, Object> entry = levels.get(order);
        return entry == null ? List.of() : (List<String>) entry.get("hints");
    }

    /** Long enough to show the shape of the answer, short enough to scan in a help desk tab. */
    private static String truncate(String reply) {
        String cleaned = reply == null ? "" : reply.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 300 ? cleaned : cleaned.substring(0, 300) + " …";
    }

    private static String json(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : (value == null ? "" : value).toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String shortHash(Path file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append("%02x".formatted(digest[i]));
        return sb.toString();
    }
}
