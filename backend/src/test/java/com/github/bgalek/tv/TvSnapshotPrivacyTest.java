package com.github.bgalek.tv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The projector payload is served to anyone who can reach the URL, with no login. This walks
 * everything that can appear in it and fails if a field arrives that should not be on a wall.
 * <p>
 * A test rather than a code review note because the failure is silent: adding a field to a record
 * that is already being serialised looks like a one-line change and produces no error anywhere.
 */
class TvSnapshotPrivacyTest {

    /**
     * Substrings that have no business in an unauthenticated response.
     * <p>
     * "url" is here because the backend's base URL is an internal LAN address; publishing it on a
     * screen, and through a public tunnel, hands out the network map. "id" is deliberately absent -
     * the feed needs a row id for React keys - but any per-<em>player</em> identifier is caught by
     * the account terms below.
     */
    private static final List<String> FORBIDDEN = List.of(
            "email", "mail", "password", "hash", "userid", "accountid", "ipaddress", "useragent",
            "baseurl", "endpoint", "apikey", "token_", "sessionid");

    /** Names that are fine despite matching loosely above. */
    private static final Set<String> ALLOWED = Set.of("tokens", "tokensTotal", "totalTokens");

    @Test
    @DisplayName("nothing in the projector payload identifies a player or the network")
    void payloadCarriesNothingSensitive() {
        List<String> offenders = new ArrayList<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        List<Class<?>> seen = new ArrayList<>();
        queue.add(TvSnapshot.class);

        while (!queue.isEmpty()) {
            Class<?> type = queue.poll();
            if (!type.isRecord() || seen.contains(type)) continue;
            seen.add(type);
            for (RecordComponent component : type.getRecordComponents()) {
                String name = component.getName();
                if (!ALLOWED.contains(name)) {
                    String lowered = name.toLowerCase(Locale.ROOT);
                    for (String banned : FORBIDDEN) {
                        if (lowered.contains(banned)) {
                            offenders.add(type.getSimpleName() + "." + name);
                        }
                    }
                }
                queue.add(component.getType());
                // List<Something> - reach the element type too.
                if (component.getGenericType() instanceof java.lang.reflect.ParameterizedType p) {
                    for (var arg : p.getActualTypeArguments()) {
                        if (arg instanceof Class<?> c) queue.add(c);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "These fields would be served without a login: " + offenders
                        + ". The projector shows a display name and a position, nothing else.");
    }

    @Test
    @DisplayName("a player is a display name, not an account")
    void boardRowIsAnonymous() {
        List<String> names = new ArrayList<>();
        for (RecordComponent c : TvSnapshot.TvBoardRow.class.getRecordComponents()) names.add(c.getName());
        assertTrue(names.contains("name"), "the board needs a display name");
        assertTrue(names.stream().noneMatch(n -> n.toLowerCase(Locale.ROOT).contains("id")),
                "a stable per-player id on a public endpoint is a correlation handle: polled every "
                        + "few seconds it reconstructs who cleared what and when. Found: " + names);
    }
}
