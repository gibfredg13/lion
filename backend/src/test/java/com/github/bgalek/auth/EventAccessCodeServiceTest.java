package com.github.bgalek.auth;

import com.github.bgalek.database.SettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the branch that decides who may register. It is two lines of code and the only thing
 * standing between a closed event and an open one, so it is worth pinning down.
 */
class EventAccessCodeServiceTest {

    /** Stands in for the database without needing one. */
    private static class InMemorySettings extends SettingsRepository {
        private final Map<String, String> values = new HashMap<>();

        InMemorySettings() {
            super(null);
        }

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }
    }

    private EventAccessCodeService service() {
        return new EventAccessCodeService("LIONHACK2026", new InMemorySettings());
    }

    @Test
    @DisplayName("with the gate on, only the code gets in")
    void requiredByDefault() {
        EventAccessCodeService service = service();
        assertTrue(service.isRequired(), "the gate must be on until somebody deliberately opens it");
        assertTrue(service.validate("LIONHACK2026"));
        assertTrue(service.validate(" lionhack2026 "), "case and stray spaces are the user's, not a wrong code");
        assertFalse(service.validate("NOPE"));
        assertFalse(service.validate(""));
        assertFalse(service.validate(null));
    }

    @Test
    @DisplayName("with the gate off, anything gets in - including nothing")
    void notRequired() {
        EventAccessCodeService service = service();
        service.setRequired(false);

        // The point of the switch: the register form stops sending a code at all.
        assertTrue(service.validate(null));
        assertTrue(service.validate(""));
        assertTrue(service.validate("whatever someone happens to type"));
    }

    @Test
    @DisplayName("both settings survive a restart")
    void persists() {
        InMemorySettings shared = new InMemorySettings();
        EventAccessCodeService before = new EventAccessCodeService("LIONHACK2026", shared);
        before.updateCode("newcode2027");
        before.setRequired(false);

        // A second instance reading the same store is what a container restart looks like.
        EventAccessCodeService after = new EventAccessCodeService("LIONHACK2026", shared);
        after.loadFromSettings();
        assertFalse(after.isRequired());
        assertTrue(after.validate("anything"));

        after.setRequired(true);
        assertTrue(after.validate("NEWCODE2027"), "the code set in the dashboard must outlive the restart");
        assertFalse(after.validate("LIONHACK2026"), "not the value from .env");
    }
}
