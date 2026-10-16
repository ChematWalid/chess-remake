package com.chess.backend.repository;

import com.chess.backend.domain.entity.OAuthExchangeCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface OAuthExchangeCodeRepository extends JpaRepository<OAuthExchangeCode, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select code from OAuthExchangeCode code join fetch code.user where code.tokenHash = :hash and code.consumedAt is null")
    Optional<OAuthExchangeCode> lockActive(@Param("hash") String hash);
}
