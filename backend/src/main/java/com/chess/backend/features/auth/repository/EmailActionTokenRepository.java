package com.chess.backend.features.auth.repository;

import com.chess.backend.features.auth.domain.EmailActionToken;
import com.chess.backend.features.auth.domain.enums.EmailAction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;

public interface EmailActionTokenRepository extends JpaRepository<EmailActionToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from EmailActionToken token join fetch token.user "
            + "where token.tokenHash = :hash and token.action = :action and token.consumedAt is null")
    Optional<EmailActionToken> lockActive(@Param("hash") String tokenHash, @Param("action") EmailAction action);
    @Modifying
    void deleteByUser_IdAndActionAndConsumedAtIsNull(long userId, EmailAction action);
}
