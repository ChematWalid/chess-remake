package com.chess.backend.features.auth.domain;


import com.chess.backend.features.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import com.chess.backend.features.auth.domain.enums.EmailAction;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "email_action_tokens")
public class EmailActionToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EmailAction action;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    protected EmailActionToken() { }
    public EmailActionToken(User user, String tokenHash, EmailAction action, Instant expiresAt) {
        this.user = user; this.tokenHash = tokenHash; this.action = action; this.expiresAt = expiresAt;
    }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public EmailAction getAction() { return action; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isConsumed() { return consumedAt != null; }
    public void consume(Instant instant) { consumedAt = instant; }
}
