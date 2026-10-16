package com.chess.backend.features.game.domain;


import com.chess.backend.features.challenge.domain.GameChallenge;
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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import com.chess.backend.features.game.domain.enums.TerminationReason;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "game_histories")
public class GameHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "challenge_id", nullable = false, unique = true) private GameChallenge challenge;
    @Column(name = "room_code", nullable = false, unique = true, length = 10) private String roomCode;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "white_player_id") private User whitePlayer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "black_player_id") private User blackPlayer;
    @Column(name = "white_username_snapshot", nullable = false, length = 30) private String whiteUsernameSnapshot;
    @Column(name = "black_username_snapshot", nullable = false, length = 30) private String blackUsernameSnapshot;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "winner_player_id") private User winner;
    @Column(name = "is_draw", nullable = false) private boolean draw;
    @Enumerated(EnumType.STRING) @Column(name = "termination_reason", nullable = false, length = 40) private TerminationReason terminationReason;
    @Column(name = "initial_time_seconds") private Integer initialTimeSeconds;
    @Column(name = "increment_seconds") private Integer incrementSeconds;
    @Column(name = "total_moves", nullable = false) private int totalMoves;
    @Column(nullable = false, columnDefinition = "text") private String pgn;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "moves_history", nullable = false, columnDefinition = "jsonb") private List<String> moves;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "fens_history", nullable = false, columnDefinition = "jsonb") private List<String> fens;
    @Column(name = "played_at", nullable = false) private Instant playedAt;
    @Column(name = "duration_seconds", nullable = false) private int durationSeconds;

    protected GameHistory() { }
    public GameHistory(GameChallenge challenge, String roomCode, User whitePlayer, User blackPlayer,
                       String whiteUsernameSnapshot, String blackUsernameSnapshot, User winner, boolean draw,
                       TerminationReason terminationReason, Integer initialTimeSeconds, Integer incrementSeconds,
                       int totalMoves, String pgn, List<String> moves, List<String> fens, Instant playedAt, int durationSeconds) {
        this.challenge = challenge; this.roomCode = roomCode; this.whitePlayer = whitePlayer; this.blackPlayer = blackPlayer;
        this.whiteUsernameSnapshot = whiteUsernameSnapshot; this.blackUsernameSnapshot = blackUsernameSnapshot;
        this.winner = winner; this.draw = draw; this.terminationReason = terminationReason;
        this.initialTimeSeconds = initialTimeSeconds; this.incrementSeconds = incrementSeconds;
        this.totalMoves = totalMoves; this.pgn = pgn; this.moves = moves; this.fens = fens;
        this.playedAt = playedAt; this.durationSeconds = durationSeconds;
    }
    public Long getId() { return id; }
    public GameChallenge getChallenge() { return challenge; }
    public String getRoomCode() { return roomCode; }
    public User getWhitePlayer() { return whitePlayer; }
    public User getBlackPlayer() { return blackPlayer; }
    public String getWhiteUsernameSnapshot() { return whiteUsernameSnapshot; }
    public String getBlackUsernameSnapshot() { return blackUsernameSnapshot; }
    public User getWinner() { return winner; }
    public boolean isDraw() { return draw; }
    public TerminationReason getTerminationReason() { return terminationReason; }
    public Integer getInitialTimeSeconds() { return initialTimeSeconds; }
    public Integer getIncrementSeconds() { return incrementSeconds; }
    public int getTotalMoves() { return totalMoves; }
    public String getPgn() { return pgn; }
    public List<String> getMoves() { return moves; }
    public List<String> getFens() { return fens; }
    public Instant getPlayedAt() { return playedAt; }
    public int getDurationSeconds() { return durationSeconds; }
}
