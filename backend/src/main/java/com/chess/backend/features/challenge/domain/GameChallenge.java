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
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
    @Column(name = "initial_fen", columnDefinition = "text") private String initialFen;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "setup_moves", nullable = false, columnDefinition = "jsonb") private List<String> setupMoves = List.of();
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "setup_fens", nullable = false, columnDefinition = "jsonb") private List<String> setupFens = List.of();
    @Enumerated(EnumType.STRING) @Column(name = "challenger_side", nullable = false, length = 10) private GameSide challengerSide;
    @Enumerated(EnumType.STRING) @Column(name = "challenger_color", length = 5) private GameSide challengerColor;
    @Column(name = "room_code", unique = true, length = 10) private String roomCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ChallengeStatus status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "started_at") private Instant startedAt;
    protected GameChallenge() { }
    public GameChallenge(User challenger, User challenged, Integer initialTimeSeconds, Integer incrementSeconds,
                         GameSide challengerSide, Instant expiresAt) {
        this(challenger, challenged, initialTimeSeconds, incrementSeconds, challengerSide, null, List.of(), List.of(), expiresAt);
    }
    public GameChallenge(User challenger, User challenged, Integer initialTimeSeconds, Integer incrementSeconds,
                         GameSide challengerSide, String initialFen, List<String> setupMoves, List<String> setupFens, Instant expiresAt) {
        this.challenger = challenger; this.challenged = challenged; this.initialTimeSeconds = initialTimeSeconds;
        this.incrementSeconds = incrementSeconds; this.challengerSide = challengerSide;
        this.initialFen = initialFen; this.setupMoves = setupMoves == null ? List.of() : List.copyOf(setupMoves);
        this.setupFens = setupFens == null ? List.of() : List.copyOf(setupFens);
        this.expiresAt = expiresAt; this.status = ChallengeStatus.PENDING;
    }
    public Long getId() { return id; }
    public User getChallenger() { return challenger; }
    public User getChallenged() { return challenged; }
    public Integer getInitialTimeSeconds() { return initialTimeSeconds; }
    public Integer getIncrementSeconds() { return incrementSeconds; }
    public String getInitialFen() { return initialFen; }
    public List<String> getSetupMoves() { return setupMoves; }
    public List<String> getSetupFens() { return setupFens; }
    public GameSide getChallengerSide() { return challengerSide; }
    public GameSide getChallengerColor() { return challengerColor; }
    public String getRoomCode() { return roomCode; }
    public ChallengeStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void accept(String roomCode, GameSide challengerColor, Instant acceptedAt) {
        this.roomCode = roomCode;
        this.challengerColor = challengerColor;
        this.acceptedAt = acceptedAt;
        this.status = ChallengeStatus.ACCEPTED;
    }
    public void markStarted(Instant startedAt) { this.startedAt = startedAt; }
    public void setStatus(ChallengeStatus status) { this.status = status; }
}
