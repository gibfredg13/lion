package com.github.bgalek.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Manages the event access code for self-registration control.
 * The access code prevents unauthorized users from joining the hackathon event.
 * It can be changed at runtime via the admin API.
 */
@Service
public class EventAccessCodeService {
    private volatile String currentCode;
    
    public EventAccessCodeService(@Value("${merlin.event.accessCode:LIONHACK2026}") String initialCode) {
        this.currentCode = initialCode.toUpperCase();
    }
    
    public boolean validate(String code) {
        if (code == null || code.isBlank()) return false;
        return currentCode.equalsIgnoreCase(code.trim());
    }
    
    public void updateCode(String newCode) {
        if (newCode == null || newCode.isBlank()) {
            throw new IllegalArgumentException("Access code cannot be empty");
        }
        this.currentCode = newCode.toUpperCase().trim();
    }
    
    public String getCurrentCode() {
        return currentCode;
    }
}
