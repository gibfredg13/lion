package com.github.bgalek.auth;

import com.github.bgalek.database.User;
import com.github.bgalek.database.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String email, String displayName, String password) throws AuthenticationException {
        if (!PasswordValidator.isValid(password)) {
            throw new AuthenticationException(PasswordValidator.getValidationMessage());
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new AuthenticationException("Email already registered");
        }

        User user = new User(
                UUID.randomUUID().toString(),
                email,
                displayName,
                passwordEncoder.encode(password),
                Instant.now(),
                Instant.now()
        );

        // Make the first user an admin
        if (userRepository.count() == 0) {
            user.setAdmin(true);
        }

        return userRepository.save(user);
    }

    public User login(String email, String password) throws AuthenticationException {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            throw new AuthenticationException("Invalid email or password");
        }

        User user = optionalUser.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid email or password");
        }

        user.setLastLoginAt(Instant.now());
        return userRepository.save(user);
    }

    public static class AuthenticationException extends Exception {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}
