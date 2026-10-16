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

@Entity
@Table(name = "oauth_identities")
public class OAuthIdentity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, length = 30) private String provider;
    @Column(name = "provider_subject", nullable = false, length = 200) private String providerSubject;
    protected OAuthIdentity() { }
    public OAuthIdentity(User user, String provider, String providerSubject) {
        this.user = user; this.provider = provider; this.providerSubject = providerSubject;
    }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getProvider() { return provider; }
    public String getProviderSubject() { return providerSubject; }
}
