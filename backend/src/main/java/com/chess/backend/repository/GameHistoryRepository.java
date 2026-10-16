package com.chess.backend.repository;

import com.chess.backend.domain.entity.GameHistory;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameHistoryRepository extends JpaRepository<GameHistory, Long> {
    Optional<GameHistory> findByRoomCode(String roomCode);
    Page<GameHistory> findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc(long whiteId, long blackId, Pageable pageable);
}
