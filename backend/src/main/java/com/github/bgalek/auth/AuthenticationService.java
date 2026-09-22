package com.github.bgalek.auth;

import com.github.bgalek.database.User;
import com.github.bgalek.database.UserRepository;
import com.github.bgalek.database.LoginHistory;
import com.github.bgalek.database.LoginHistoryRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final EventAccessCodeService eventAccessCodeService;
    private final EmailDomainValidator emailDomainValidator;
    private final LoginHistoryRepository loginHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(UserRepository userRepository, 
                                 LoginHistoryRepository loginHistoryRepository, 
                                 PasswordEncoder passwordEncoder,
                                 EventAccessCodeService eventAccessCodeService,
                                 EmailDomainValidator emailDomainValidator) {
        this.userRepository = userRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventAccessCodeService = eventAccessCodeService;
        this.emailDomainValidator = emailDomainValidator;
    }

    public User register(String email, String displayName, String password, String accessCode) throws AuthenticationException {
        if (!eventAccessCodeService.validate(accessCode)) {
            throw new AuthenticationException("Invalid event access code");
        }

        if (!emailDomainValidator.isAllowed(email)) {
            throw new AuthenticationException(emailDomainValidator.getValidationMessage());
        }

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

    public User register(String email, String displayName, String password) throws AuthenticationException {
        return register(email, displayName, password, null);
    }

    /**
     * Note there is no email-domain check here, only on registration. The first account ever
     * created is made an admin (see {@code register}), so an admin account that predates the
     * domain restriction would be locked out of its own dashboard with no in-app recovery.
     * Restricting registration is already sufficient: no new non-company account can be created.
     */
    public User login(String email, String password, HttpServletRequest request) throws AuthenticationException {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            throw new AuthenticationException("Invalid email or password");
        }

        User user = optionalUser.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid email or password");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        
        // Log login history
        LoginHistory history = new LoginHistory(
            user.getId(),
            user.getEmail(),
            Instant.now(),
            getClientIp(request),
            request.getHeader("User-Agent")
        );
        loginHistoryRepository.save(history);
        
        return user;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public static class AuthenticationException extends Exception {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}
