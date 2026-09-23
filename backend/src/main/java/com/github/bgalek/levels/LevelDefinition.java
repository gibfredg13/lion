package com.github.bgalek.levels;

import java.util.List;

/**
 * One level, as data rather than a Java class.
 * <p>
 * Levels used to be seven hardcoded classes in a subclass chain, so retuning the difficulty meant
 * editing Java and rebuilding the Docker image - impossible mid-event, which is exactly when you
 * learn the curve is wrong. Defined here they can be edited in levels.yml or through the admin
 * dashboard and reloaded live.
 *
 * @param systemMessages each may contain the {secret} placeholder, substituted per player
 * @param outputFilter   each mode runs everything the mode before it runs, and one thing more.
 *                       Note that the ladder as a whole is <em>staged</em> rather than cumulative:
 *                       the input filter is not inherited upwards, because the top level's intended
 *                       solution needs probes the level below it blocks.
 */
public record LevelDefinition(
        int order,
        String name,
        String description,
        List<String> systemMessages,
        double temperature,
        int maxTokens,
        List<String> inputFilterKeywords,
        OutputFilterMode outputFilter,
        /** Overrides the default judge instruction. Only read when outputFilter is JUDGE. */
        String judgePrompt,
        String inputFilterResponse,
        String outputFilterResponse,
        String finishedResponse
) {
    public enum OutputFilterMode {
        /** Anything Leo says reaches the player. */
        NONE,
        /**
         * Blocks the secret written out, or spelled letter by letter.
         * <p>
         * It does <em>not</em> block acrostics, whatever earlier documentation claimed. That is
         * deliberate rather than an omission: the acrostic is the intended way through this level,
         * and it closes one rung later at {@link #NORMALISED}.
         */
        PLAIN,
        /** Also blocks the secret written backwards. */
        REVERSED,
        /**
         * Also blocks the secret in disguise: lookalike letters, accents and leetspeak; base64,
         * hex, binary, A1Z26, Morse, NATO phonetic, rotation ciphers and atbash; and the acrostic.
         * Everything a deterministic check can enumerate.
         */
        NORMALISED,
        /**
         * The judge alone: blocks a reply that gives away the word's <em>meaning</em>, and nothing
         * else.
         * <p>
         * Not a rung on the same ladder as the four above - it is a different axis. Those all read
         * the text for the word in some costume; this one reads it for the idea and does not care
         * about the letters at all. That is what makes it usable at a level whose own solution is
         * written-channel: a reply spelling T. H. U. N. D. E. R. describes nothing, so it passes,
         * while "the sound that follows lightning" does not.
         * <p>
         * It exists because levels with only string matching had no defence against being asked to
         * describe rather than name, so one prompt in that shape ran from level 1 until it met the
         * judge at the top. Measured over thirteen calibration runs, closing those prompts one at a
         * time by keyword just promoted the next prompt in the same family.
         * <p>
         * Costs a second model call per turn, like {@link #JUDGE}.
         */
        SEMANTIC,
        /**
         * {@link #PLAIN} and {@link #SEMANTIC} together: the word must not be written out, and its
         * meaning must not be given away either.
         * <p>
         * Spelled out as its own value rather than layered, because these two are on different axes
         * and the ladder above is not. A level that wants both is not one rung further up; it is a
         * level guarding the word and the idea at once.
         */
        PLAIN_SEMANTIC,
        /** Also asks a second model whether the response gives anything away. */
        JUDGE
    }

    public LevelDefinition {
        systemMessages = systemMessages == null ? List.of() : List.copyOf(systemMessages);
        inputFilterKeywords = inputFilterKeywords == null ? List.of() : List.copyOf(inputFilterKeywords);
        outputFilter = outputFilter == null ? OutputFilterMode.NONE : outputFilter;
        if (maxTokens <= 0) maxTokens = 200;
    }
}
