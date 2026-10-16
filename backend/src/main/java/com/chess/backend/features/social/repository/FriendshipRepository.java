package com.chess.backend.features.social.repository;

import com.chess.backend.features.social.domain.Friendship;
import com.chess.backend.features.social.domain.enums.FriendshipStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    @Query(value = "SELECT * FROM friendships WHERE LEAST(requester_id, addressee_id) = LEAST(:first, :second) "
            + "AND GREATEST(requester_id, addressee_id) = GREATEST(:first, :second)", nativeQuery = true)
    Optional<Friendship> findPair(@Param("first") long firstId, @Param("second") long secondId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT * FROM friendships WHERE LEAST(requester_id, addressee_id) = LEAST(:first, :second) "
            + "AND GREATEST(requester_id, addressee_id) = GREATEST(:first, :second) FOR UPDATE", nativeQuery = true)
    Optional<Friendship> lockPair(@Param("first") long firstId, @Param("second") long secondId);

    @Query("select f from Friendship f join fetch f.requester join fetch f.addressee "
            + "where (f.requester.id = :user or f.addressee.id = :user) and f.status = :status")
    List<Friendship> findForUser(@Param("user") long userId, @Param("status") FriendshipStatus status);

    @Query("select f from Friendship f join fetch f.requester join fetch f.addressee "
            + "where f.addressee.id = :user and f.status = :status order by f.createdAt desc")
    List<Friendship> incoming(@Param("user") long userId, @Param("status") FriendshipStatus status);

    @Query("select f from Friendship f join fetch f.requester join fetch f.addressee "
            + "where f.requester.id = :user and f.status = :status order by f.createdAt desc")
    List<Friendship> outgoing(@Param("user") long userId, @Param("status") FriendshipStatus status);

    @Query("select count(f) > 0 from Friendship f where f.status = :status "
            + "and ((f.requester.id = :first and f.addressee.id = :second) or (f.requester.id = :second and f.addressee.id = :first))")
    boolean areFriends(@Param("first") long firstId, @Param("second") long secondId, @Param("status") FriendshipStatus status);
}
