#pragma once

#include <vector>

#include "chess/board/Board.h"
#include "chess/board/Position.h"
#include "chess/board/Types.h"

namespace chess {

/**
 * @brief Stateless utility class providing explicit chess rule and draw queries.
 * 
 * @details Evaluates game termination conditions such as checkmate, stalemate,
 * fifty-move rule, insufficient mating material, and threefold repetition.
 */
class GameRules {
public:
    /**
     * @brief Determines whether the current player is in checkmate.
     * 
     * @details Returns true if the active player is in check and has zero legal moves.
     * 
     * @param position The current game position snapshot.
     * @return True if the player is checkmated, false otherwise.
     */
    static bool isCheckmate(const Position& position);

    /**
     * @brief Determines whether the current position is a stalemate draw.
     * 
     * @details Returns true if the active player is NOT in check but has zero legal moves.
     * 
     * @param position The current game position snapshot.
     * @return True if the position is stalemate, false otherwise.
     */
    static bool isStalemate(const Position& position);

    /**
     * @brief Evaluates whether the game can be declared a draw under the fifty-move rule.
     * 
     * @details Checks if at least 100 halfmoves (50 full moves) have elapsed without
     * a pawn move or piece capture.
     * 
     * @param position The current game position snapshot.
     * @return True if the halfmove clock is 100 or greater, false otherwise.
     */
    static bool isFiftyMoveDraw(const Position& position);

    /**
     * @brief Determines whether neither player has sufficient material to force checkmate.
     * 
     * @details Detects king vs king, king + bishop vs king, king + knight vs king,
     * and king + bishop vs king + bishop where bishops are on identical color squares.
     * 
     * @param board The piece configuration on the board.
     * @return True if checkmate is impossible due to insufficient material, false otherwise.
     */
    static bool hasInsufficientMaterial(const Board& board);

    /**
     * @brief Determines whether the current position has occurred at least three times.
     * 
     * @details Counts occurrences of the current board layout, side to move, castling rights,
     * and en passant status across the recorded position history.
     * 
     * @param history The chronological sequence of positions played in the game.
     * @return True if any position appears 3 or more times, false otherwise.
     */
    static bool isThreefoldRepetition(const std::vector<Position>& history);
};

} // namespace chess
