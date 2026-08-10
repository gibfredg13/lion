package com.github.bgalek.auth;

import com.github.bgalek.database.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest request,
            HttpSession session) {
        try {
            if (request.email == null || request.email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
            }
            if (request.displayName == null || request.displayName.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is required");
            }
            if (request.password == null || request.password.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
            }

            User user = authenticationService.register(request.email, request.displayName, request.password);
            
            session.setAttribute("userId", user.getId());
            session.setAttribute("email", user.getEmail());
            session.setAttribute("displayName", user.getDisplayName());
            session.setAttribute("isAdmin", user.isAdmin());
            
            return ResponseEntity.ok(new AuthResponse(user.getId(), user.getEmail(), user.getDisplayName(), true, user.isAdmin()));
        } catch (AuthenticationService.AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request,
            HttpSession session) {
        try {
            if (request.email == null || request.email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
            }
            if (request.password == null || request.password.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
            }

            User user = authenticationService.login(request.email, request.password);
            
            session.setAttribute("userId", user.getId());
            session.setAttribute("email", user.getEmail());
            session.setAttribute("displayName", user.getDisplayName());
            session.setAttribute("isAdmin", user.isAdmin());
            
            return ResponseEntity.ok(new AuthResponse(user.getId(), user.getEmail(), user.getDisplayName(), true, user.isAdmin()));
        } catch (AuthenticationService.AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
        
        String email = (String) session.getAttribute("email");
        String displayName = (String) session.getAttribute("displayName");
        Object isAdminAttr = session.getAttribute("isAdmin");
        boolean isAdmin = isAdminAttr != null ? (Boolean) isAdminAttr : false;
        
        return ResponseEntity.ok(new UserResponse(userId, email, displayName, isAdmin));
    }

    record LoginRequest(String email, String password) {}
    record RegisterRequest(String email, String displayName, String password) {}
    record AuthResponse(String userId, String email, String displayName, boolean authenticated, boolean isAdmin) {}
    record UserResponse(String userId, String email, String displayName, boolean isAdmin) {}
}
