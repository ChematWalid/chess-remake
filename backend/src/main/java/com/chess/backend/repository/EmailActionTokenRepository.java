package com.chess.backend.repository;

import com.chess.backend.domain.entity.EmailActionToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface EmailActionTokenRepository extends JpaRepository<EmailActionToken, Long> {
    Optional<EmailActionToken> findByTokenHashAndActionAndConsumedAtIsNull(String tokenHash, String action);
    @Modifying
    void deleteByUser_IdAndActionAndConsumedAtIsNull(long userId, String action);
}
