package com.chess.backend.features.social.repository;

import com.chess.backend.features.social.domain.FriendInvite;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface FriendInviteRepository extends JpaRepository<FriendInvite, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from FriendInvite invite join fetch invite.owner where invite.tokenHash = :hash and invite.consumedAt is null")
    Optional<FriendInvite> lockActiveByHash(@Param("hash") String hash);
    @Modifying
    void deleteByOwner_IdAndConsumedAtIsNull(long ownerId);
}
