package com.github.bgalek.admin;

import com.github.bgalek.database.DetectedAttack;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public class AttackDetectionService {

    // Common prompt injection patterns
    private static final Map<String, AttackPattern> ATTACK_PATTERNS = new LinkedHashMap<>();

    static {
        // Classic jailbreak attempts
        ATTACK_PATTERNS.put("ignore_instructions", new AttackPattern(
                "ignore.*instruction|forget.*previous|disregard.*rule|override.*prompt",
                "Ignore Previous Instructions Attempt",
                "HIGH"
        ));

        // Role manipulation
        ATTACK_PATTERNS.put("role_change", new AttackPattern(
                "you are now|pretend.*you are|act as|play.*role|be.*admin|become.*hacker",
                "Role/Identity Change Attempt",
                "HIGH"
        ));

        // Output format manipulation
        ATTACK_PATTERNS.put("output_format", new AttackPattern(
                "output.*plain text|show.*code|print.*secret|reveal.*system|expose.*password|dump.*database",
                "Output Format Manipulation",
                "CRITICAL"
        ));

        // System prompt extraction
        ATTACK_PATTERNS.put("prompt_injection", new AttackPattern(
                "what is your prompt|show your instructions|reveal your system prompt|what is your goal",
                "System Prompt Extraction",
                "HIGH"
        ));

        // SQL injection patterns
        ATTACK_PATTERNS.put("sql_injection", new AttackPattern(
                "'; drop|union select|or 1=1|insert into|delete from|update.*set|create table",
                "SQL Injection Pattern",
                "CRITICAL"
        ));

        // Path traversal
        ATTACK_PATTERNS.put("path_traversal", new AttackPattern(
                "\\.\\./|\\.\\.\\\\|/etc/passwd|/etc/shadow|windows/system32",
                "Path Traversal Attempt",
                "HIGH"
        ));

        // Command injection
        ATTACK_PATTERNS.put("command_injection", new AttackPattern(
                "`;|&&|\\||\\$\\(|`.*`|bash.*-c|sh.*-c|exec.*\\(|system\\(",
                "Command Injection Attempt",
                "CRITICAL"
        ));

        // Token/API key exposure attempts
        ATTACK_PATTERNS.put("credential_extraction", new AttackPattern(
                "api.*key|secret.*key|password|token|auth.*key|bearer.*token",
                "Credential Extraction Attempt",
                "CRITICAL"
        ));

        // Logic bypasses
        ATTACK_PATTERNS.put("logic_bypass", new AttackPattern(
                "if.*else|bypass|check.*false|skip.*validation|disable.*check",
                "Logic Bypass Attempt",
                "MEDIUM"
        ));

        // Encoding attacks
        ATTACK_PATTERNS.put("encoding_attack", new AttackPattern(
                "base64|hex.*decode|url.*encode|unicode|ascii|rot13|\\\\x[0-9a-f]",
                "Encoding-based Attack",
                "MEDIUM"
        ));

        // XML/XXE attacks
        ATTACK_PATTERNS.put("xxe_injection", new AttackPattern(
                "<\\?xml|<!DOCTYPE|<!ENTITY|SYSTEM.*file|ENTITIES.*file",
                "XXE Injection Attempt",
                "HIGH"
        ));

        // LDAP injection
        ATTACK_PATTERNS.put("ldap_injection", new AttackPattern(
                "\\*|\\)|\\(|&|\\||ldap.*filter",
                "LDAP Injection Pattern",
                "MEDIUM"
        ));
    }

    public List<DetectedAttack> scanPrompt(String userId, String promptId, String promptText) {
        List<DetectedAttack> attacks = new ArrayList<>();
        String lowerPrompt = promptText.toLowerCase();

        for (Map.Entry<String, AttackPattern> entry : ATTACK_PATTERNS.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getValue().regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            if (pattern.matcher(promptText).find()) {
                DetectedAttack attack = new DetectedAttack(
                        UUID.randomUUID().toString(),
                        userId,
                        promptId,
                        entry.getKey(),
                        entry.getValue().severity,
                        entry.getValue().description,
                        Instant.now()
                );
                attacks.add(attack);
            }
        }

        return attacks;
    }

    public String getSeverityColor(String severity) {
        return switch (severity) {
            case "CRITICAL" -> "#ff0000"; // Red
            case "HIGH" -> "#ff6600"; // Orange
            case "MEDIUM" -> "#ffaa00"; // Yellow-Orange
            case "LOW" -> "#ffdd00"; // Yellow
            default -> "#cccccc"; // Gray
        };
    }

    public static class AttackPattern {
        public String regex;
        public String description;
        public String severity;

        public AttackPattern(String regex, String description, String severity) {
            this.regex = regex;
            this.description = description;
            this.severity = severity;
        }
    }
}
