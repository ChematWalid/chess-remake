package com.chess.backend.features.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import com.chess.backend.common.domain.enums.UserRole;
import com.chess.backend.features.game.domain.enums.GameResult;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 30) private String username;
    @Column(nullable = false, length = 254) private String email;
    @Column(name = "password_hash", length = 100) private String passwordHash;
    @Column(name = "display_name", nullable = false, length = 80) private String displayName;
    @Column(length = 500) private String bio;
    @Column(name = "avatar_key") private String avatarKey;
    @Column(name = "email_verified", nullable = false) private boolean emailVerified;
    @Column(nullable = false) private boolean searchable = true;
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false, columnDefinition = "user_role")
    private UserRole role = UserRole.ROLE_USER;
    @Column(name = "onboarding_complete", nullable = false) private boolean onboardingComplete = true;
    @Column(name = "total_games", nullable = false) private int totalGames;
    @Column(nullable = false) private int wins;
    @Column(nullable = false) private int losses;
    @Column(nullable = false) private int draws;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;

    protected User() { }
    public User(String username, String email, String passwordHash, String displayName) {
        this.username = username; this.email = email; this.passwordHash = passwordHash; this.displayName = displayName;
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public String getAvatarKey() { return avatarKey; }
    public boolean isEmailVerified() { return emailVerified; }
    public boolean isSearchable() { return searchable; }
    public UserRole getRole() { return role; }
    public boolean isOnboardingComplete() { return onboardingComplete; }
    public int getTotalGames() { return totalGames; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getDraws() { return draws; }
    public void setUsername(String value) { username = value; }
    public void setDisplayName(String value) { displayName = value; }
    public void setBio(String value) { bio = value; }
    public void setAvatarKey(String value) { avatarKey = value; }
    public void setEmailVerified(boolean value) { emailVerified = value; }
    public void setSearchable(boolean value) { searchable = value; }
    public void setPasswordHash(String value) { passwordHash = value; }
    public void requireOAuthOnboarding() { onboardingComplete = false; role = UserRole.ROLE_ONBOARDING; }
    public void completeOAuthOnboarding(String newUsername) { username = newUsername; onboardingComplete = true; role = UserRole.ROLE_USER; }
    public void recordResult(GameResult result) {
        totalGames++;
        switch (result) {
            case WIN -> wins++;
            case LOSS -> losses++;
            case DRAW -> draws++;
        }
    }
}
