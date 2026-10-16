package com.chess.backend.features.challenge.repository;

import com.chess.backend.features.challenge.domain.GameChallenge;
import com.chess.backend.features.challenge.domain.enums.ChallengeStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface GameChallengeRepository extends JpaRepository<GameChallenge, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from GameChallenge challenge join fetch challenge.challenger join fetch challenge.challenged where challenge.id = :id")
    Optional<GameChallenge> lockById(@Param("id") long id);
    Optional<GameChallenge> findByRoomCode(String roomCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from GameChallenge challenge join fetch challenge.challenger join fetch challenge.challenged where challenge.roomCode = :roomCode")
    Optional<GameChallenge> lockByRoomCode(@Param("roomCode") String roomCode);
    boolean existsByRoomCode(String roomCode);
    @Query("select count(challenge) > 0 from GameChallenge challenge where challenge.status = :status "
            + "and challenge.expiresAt > :now and ((challenge.challenger.id = :first and challenge.challenged.id = :second) "
            + "or (challenge.challenger.id = :second and challenge.challenged.id = :first))")
    boolean hasPendingBetween(@Param("first") long firstId, @Param("second") long secondId,
                              @Param("now") Instant now, @Param("status") ChallengeStatus status);
    @EntityGraph(attributePaths = {"challenger", "challenged"})
    Page<GameChallenge> findByChallenged_IdOrderByCreatedAtDesc(long challengedId, Pageable pageable);

    @EntityGraph(attributePaths = {"challenger", "challenged"})
    Page<GameChallenge> findByChallenger_IdOrderByCreatedAtDesc(long challengerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from GameChallenge challenge join fetch challenge.challenger join fetch challenge.challenged "
            + "where challenge.status = :pending and challenge.expiresAt <= :now order by challenge.expiresAt")
    List<GameChallenge> lockExpired(@Param("now") Instant now, @Param("pending") ChallengeStatus pending, Pageable pageable);
}
