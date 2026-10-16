package com.chess.backend.features.auth.repository;

import com.chess.backend.features.auth.domain.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token join fetch token.user where token.tokenHash = :hash")
    Optional<RefreshToken> lockByHash(@Param("hash") String hash);
    void deleteByTokenHash(String tokenHash);
    void deleteByUser_Id(long userId);
}
