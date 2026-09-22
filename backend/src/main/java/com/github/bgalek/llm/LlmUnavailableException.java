package com.github.bgalek.llm;

/**
 * The inference server could not be reached, or answered with a server error.
 * <p>
 * Distinct from a model that answered and simply refused: this one means nothing was measured.
 * Calibration relies on the distinction - a box that is switched off used to produce a scorecard
 * indistinguishable from a level that held against every attack, because a failed call and a
 * successful refusal both ended up as "no leak".
 */
public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmUnavailableException(String message) {
        super(message);
    }
}
