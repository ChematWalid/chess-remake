package com.chess.backend.repository;

import com.chess.backend.domain.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByEmailIgnoreCase(String email);

    @Query(value = """
            SELECT u.* FROM users u
            WHERE u.searchable = TRUE AND u.email_verified = TRUE AND u.id <> :viewer
              AND (u.username LIKE :prefix OR lower(u.display_name) LIKE :displayQuery)
            ORDER BY CASE WHEN u.username LIKE :prefix THEN 0 ELSE 1 END, u.username
            """, countQuery = """
            SELECT count(*) FROM users u
            WHERE u.searchable = TRUE AND u.email_verified = TRUE AND u.id <> :viewer
              AND (u.username LIKE :prefix OR lower(u.display_name) LIKE :displayQuery)
            """, nativeQuery = true)
    Page<User> search(@Param("viewer") long viewerId, @Param("prefix") String usernamePrefix,
                      @Param("displayQuery") String displayQuery, Pageable pageable);
}
