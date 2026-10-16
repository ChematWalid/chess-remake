package com.chess.backend.features.challenge.domain;


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
import com.chess.backend.features.challenge.domain.enums.ChallengeStatus;
import com.chess.backend.features.challenge.domain.enums.GameSide;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "game_challenges")
public class GameChallenge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "challenger_id", nullable = false) private User challenger;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "challenged_id", nullable = false) private User challenged;
    @Column(name = "initial_time_seconds") private Integer initialTimeSeconds;
    @Column(name = "increment_seconds") private Integer incrementSeconds;
    @Enumerated(EnumType.STRING) @Column(name = "challenger_side", nullable = false, length = 10) private GameSide challengerSide;
    @Enumerated(EnumType.STRING) @Column(name = "challenger_color", length = 5) private GameSide challengerColor;
    @Column(name = "room_code", unique = true, length = 10) private String roomCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ChallengeStatus status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    protected GameChallenge() { }
    public GameChallenge(User challenger, User challenged, Integer initialTimeSeconds, Integer incrementSeconds,
                         GameSide challengerSide, Instant expiresAt) {
        this.challenger = challenger; this.challenged = challenged; this.initialTimeSeconds = initialTimeSeconds;
        this.incrementSeconds = incrementSeconds; this.challengerSide = challengerSide;
        this.expiresAt = expiresAt; this.status = ChallengeStatus.PENDING;
    }
    public Long getId() { return id; }
    public User getChallenger() { return challenger; }
    public User getChallenged() { return challenged; }
    public Integer getInitialTimeSeconds() { return initialTimeSeconds; }
    public Integer getIncrementSeconds() { return incrementSeconds; }
    public GameSide getChallengerSide() { return challengerSide; }
    public GameSide getChallengerColor() { return challengerColor; }
    public String getRoomCode() { return roomCode; }
    public ChallengeStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public void accept(String roomCode, GameSide challengerColor) { this.roomCode = roomCode; this.challengerColor = challengerColor; this.status = ChallengeStatus.ACCEPTED; }
    public void setStatus(ChallengeStatus status) { this.status = status; }
}
