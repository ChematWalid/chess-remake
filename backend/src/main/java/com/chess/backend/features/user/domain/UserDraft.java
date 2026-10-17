package com.chess.backend.features.user.domain;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_drafts")
public class UserDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "feature_key", nullable = false, length = 64)
    private String featureKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserDraft() { }

    public UserDraft(User user, String featureKey, JsonNode payload) {
        this.user = user;
        this.featureKey = featureKey;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getFeatureKey() { return featureKey; }
    public JsonNode getPayload() { return payload; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(JsonNode value) {
        payload = value;
    }
}
