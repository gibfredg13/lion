package com.github.bgalek.session;

import java.time.Instant;

/**
 * One run of the event.
 *
 * @param resetLevels whether this run began by sending every player back to level 1
 * @param players     how many distinct people have attempted anything in this run
 * @param attempts    how many attempts in total
 */
public record GameSession(
        String id,
        String label,
        Instant startedAt,
        Instant endedAt,
        boolean resetLevels,
        String createdBy,
        int players,
        long attempts,
        boolean active
) {}
