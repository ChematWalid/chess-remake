package com.chess.backend.features.user.repository;

import com.chess.backend.features.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByPublicId(UUID publicId);
    Optional<User> findByEmailIgnoreCase(String email);

    @Modifying
    @Query("update User user set user.totalGames = user.totalGames + 1, "
            + "user.wins = user.wins + :wins, user.losses = user.losses + :losses, "
            + "user.draws = user.draws + :draws where user.id = :userId")
    int recordGameResult(@Param("userId") long userId, @Param("wins") int wins,
                         @Param("losses") int losses, @Param("draws") int draws);

    @Query("""
            select user from User user
            where user.searchable = true and user.emailVerified = true and user.id <> :viewer
              and (lower(user.username) like :prefix or lower(user.displayName) like :displayQuery)
            order by case when lower(user.username) like :prefix then 0 else 1 end, user.username
            """)
    Page<User> search(@Param("viewer") long viewerId, @Param("prefix") String usernamePrefix,
                      @Param("displayQuery") String displayQuery, Pageable pageable);
}
