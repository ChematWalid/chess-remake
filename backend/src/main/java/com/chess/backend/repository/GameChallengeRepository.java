package com.chess.backend.repository;

import com.chess.backend.domain.entity.GameChallenge;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface GameChallengeRepository extends JpaRepository<GameChallenge, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from GameChallenge challenge join fetch challenge.challenger join fetch challenge.challenged where challenge.id = :id")
    Optional<GameChallenge> lockById(@Param("id") long id);
    Optional<GameChallenge> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);
    @Query("select count(challenge) > 0 from GameChallenge challenge where challenge.status = 'PENDING' "
            + "and challenge.expiresAt > :now and ((challenge.challenger.id = :first and challenge.challenged.id = :second) "
            + "or (challenge.challenger.id = :second and challenge.challenged.id = :first))")
    boolean hasPendingBetween(@Param("first") long firstId, @Param("second") long secondId, @Param("now") Instant now);
    @Modifying
    @Query("update GameChallenge challenge set challenge.status = 'EXPIRED' where challenge.status = 'PENDING' and challenge.expiresAt <= :now")
    int expirePending(@Param("now") Instant now);
}
