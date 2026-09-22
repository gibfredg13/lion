package com.github.bgalek.database;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    private String id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String displayName;
    
    @Column(nullable = false)
    private String passwordHash;
    
    @Column(nullable = false)
    private boolean isAdmin = false;

    /** Fix 3: Persist game progress so users resume where they left off after logout/restart */
    @Column(nullable = false)
    private int currentLevel = 1;
    
    private Instant createdAt;
    private Instant lastLoginAt;

    public User() {}

    public User(String id, String email, String displayName, String passwordHash, Instant createdAt, Instant lastLoginAt) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
        this.isAdmin = false;
        this.currentLevel = 1;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { this.isAdmin = admin; }
    public int getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(int currentLevel) { this.currentLevel = currentLevel; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
