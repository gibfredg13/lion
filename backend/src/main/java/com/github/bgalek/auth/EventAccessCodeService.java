package com.github.bgalek.auth;

import com.github.bgalek.database.SettingsRepository;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * The code players need to register, and whether they need one at all.
 * <p>
 * Both are changeable at runtime from the dashboard and both are written through to
 * {@code app_settings}. They used to live only in memory, so changing the code and restarting the
 * container reverted it to whatever was in .env - which mid-event locks the room out with a code
 * nobody was given.
 */
@Service
public class EventAccessCodeService {

    private static final Logger logger = getLogger(EventAccessCodeService.class);

    static final String CODE_KEY = "event.accessCode";
    static final String REQUIRED_KEY = "event.accessCodeRequired";

    private final SettingsRepository settings;

    private volatile String currentCode;
    /**
     * Whether the code is enforced. Defaults to true everywhere - an unset setting, an unreadable
     * database, a fresh deployment - so the gate can only ever be opened deliberately.
     */
    private volatile boolean required = true;

    public EventAccessCodeService(@Value("${merlin.event.accessCode:LIONHACK2026}") String initialCode,
                                  SettingsRepository settings) {
        this.currentCode = initialCode.toUpperCase();
        this.settings = settings;
    }

    /**
     * Restores both settings at boot.
     * <p>
     * Called from an ApplicationRunner rather than the constructor: runners execute after the
     * context refresh and therefore after schema.sql, which is this project's stand-in for a
     * migration tool. Reading app_settings during construction would race that.
     */
    public void loadFromSettings() {
        settings.get(CODE_KEY).filter(c -> !c.isBlank()).ifPresent(c -> this.currentCode = c);
        this.required = settings.get(REQUIRED_KEY).map("true"::equals).orElse(true);
        logger.info("Event access code is {}", required ? "REQUIRED" : "NOT required for registration");
    }

    public boolean validate(String code) {
        // The gate is off: anything passes, including nothing. Registration is then controlled by
        // the allowed email domains alone - see EmailDomainValidator.
        if (!required) return true;
        if (code == null || code.isBlank()) return false;
        return currentCode.equalsIgnoreCase(code.trim());
    }

    public void updateCode(String newCode) {
        if (newCode == null || newCode.isBlank()) {
            throw new IllegalArgumentException("Access code cannot be empty");
        }
        this.currentCode = newCode.toUpperCase().trim();
        settings.put(CODE_KEY, this.currentCode);
    }

    public String getCurrentCode() {
        return currentCode;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
        settings.put(REQUIRED_KEY, Boolean.toString(required));
        logger.warn("Event access code is now {} for registration", required ? "REQUIRED" : "NOT required");
    }
}
