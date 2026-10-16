package com.chess.backend.features.social.domain;


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
import com.chess.backend.features.social.domain.enums.FriendshipStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "friendships")
public class Friendship {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requester_id", nullable = false) private User requester;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "addressee_id", nullable = false) private User addressee;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private FriendshipStatus status;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    protected Friendship() { }
    public Friendship(User requester, User addressee, FriendshipStatus status) { this.requester = requester; this.addressee = addressee; this.status = status; }
    public Long getId() { return id; }
    public User getRequester() { return requester; }
    public User getAddressee() { return addressee; }
    public FriendshipStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(FriendshipStatus status) { this.status = status; }
}
