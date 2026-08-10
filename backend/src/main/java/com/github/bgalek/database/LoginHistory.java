package com.github.bgalek.database;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "login_history")
public class LoginHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String email;
    
    @Column(nullable = false)
    private Instant loginTime;
    
    @Column
    private Instant logoutTime;
    
    @Column
    private String ipAddress;
    
    @Column
    private String userAgent;
    
    public LoginHistory() {}
    
    public LoginHistory(String userId, String email, Instant loginTime, String ipAddress, String userAgent) {
        this.userId = userId;
        this.email = email;
        this.loginTime = loginTime;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
    
    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public Instant getLoginTime() { return loginTime; }
    public Instant getLogoutTime() { return logoutTime; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    
    public void setLogoutTime(Instant logoutTime) { this.logoutTime = logoutTime; }
}
