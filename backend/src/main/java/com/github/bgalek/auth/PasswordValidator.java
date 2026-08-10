package com.github.bgalek.auth;

public class PasswordValidator {
    private static final int MIN_LENGTH = 12;
    private static final String UPPERCASE_PATTERN = ".*[A-Z].*";
    private static final String LOWERCASE_PATTERN = ".*[a-z].*";
    private static final String DIGIT_PATTERN = ".*\\d.*";
    private static final String SPECIAL_CHAR_PATTERN = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*";

    public static boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }
        if (!password.matches(UPPERCASE_PATTERN)) {
            return false;
        }
        if (!password.matches(LOWERCASE_PATTERN)) {
            return false;
        }
        if (!password.matches(DIGIT_PATTERN)) {
            return false;
        }
        return password.matches(SPECIAL_CHAR_PATTERN);
    }

    public static String getValidationMessage() {
        return "Password must be at least 12 characters with uppercase, lowercase, number, and special character";
    }
}
