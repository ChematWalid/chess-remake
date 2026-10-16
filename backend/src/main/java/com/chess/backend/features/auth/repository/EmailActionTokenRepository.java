package com.chess.backend.features.auth.repository;

import com.chess.backend.features.auth.domain.EmailActionToken;
import com.chess.backend.features.auth.domain.enums.EmailAction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface EmailActionTokenRepository extends JpaRepository<EmailActionToken, Long> {
    Optional<EmailActionToken> findByTokenHashAndActionAndConsumedAtIsNull(String tokenHash, EmailAction action);
    @Modifying
    void deleteByUser_IdAndActionAndConsumedAtIsNull(long userId, EmailAction action);
}
