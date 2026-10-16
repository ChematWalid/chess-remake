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

@Entity
@Table(name = "friend_invites")
public class FriendInvite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false) private User owner;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "consumed_by") private User consumedBy;
    @Column(name = "consumed_at") private Instant consumedAt;
    protected FriendInvite() { }
    public FriendInvite(User owner, String tokenHash) { this.owner = owner; this.tokenHash = tokenHash; }
    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public boolean isConsumed() { return consumedAt != null; }
    public void consume(User user, Instant instant) { consumedBy = user; consumedAt = instant; }
}
