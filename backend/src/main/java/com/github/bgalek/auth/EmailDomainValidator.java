package com.github.bgalek.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Restricts self-registration to company email addresses. The event access code already gates who
 * can sign up, but a code spreads by word of mouth; the domain check is what actually ties an
 * account to an employee.
 * <p>
 * Configured by `merlin.event.allowedEmailDomains` (comma-separated) so a second country domain can
 * be added without a redeploy, the same way `merlin.event.accessCode` is configurable. Registration
 * is the only place this is enforced - see the class comment on {@link AuthenticationService}.
 */
@Service
public class EmailDomainValidator {

    private final List<String> allowedDomains;

    public EmailDomainValidator(@Value("${merlin.event.allowedEmailDomains:ing.com}") String domains) {
        this.allowedDomains = Arrays.stream(domains.split(","))
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isBlank())
                .toList();
    }

    public boolean isAllowed(String email) {
        if (email == null) return false;
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        int at = trimmed.lastIndexOf('@');
        // Reject a missing @, an empty local part, and an empty domain.
        if (at <= 0 || at == trimmed.length() - 1) return false;
        // Compare the domain for equality rather than testing endsWith on the whole address:
        // endsWith("ing.com") would also accept "someone@notting.com", and equality additionally
        // rejects subdomains such as "someone@ing.com.example.net".
        return allowedDomains.contains(trimmed.substring(at + 1));
    }

    /** Phrased for the player, since it is returned straight to the registration form. */
    public String getValidationMessage() {
        String domains = String.join(" or ", allowedDomains.stream().map(d -> "@" + d).toList());
        return "Registration is limited to " + domains + " email addresses";
    }

    public List<String> getAllowedDomains() {
        return allowedDomains;
    }
}
