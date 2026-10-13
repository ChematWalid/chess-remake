#pragma once

#include <optional>
#include <vector>

#include "chess/board/Position.h"
#include "chess/board/Square.h"
#include "chess/board/Types.h"
#include "chess/rules/Move.h"

namespace chess {

/**
 * @brief Stateless service generating and validating legal chess moves.
 * 
 * @details Implements pseudo-legal generation, king-safety legality filtering,
 * castling path checks, and client move-intent resolution.
 */
class MoveGenerator {
public:
    /**
     * @brief Generates all strictly legal moves for the active player in the given position.
     * 
     * @details Generates candidate pseudo-legal moves for all friendly pieces, adds valid
     * castling moves, and filters out any move that leaves or puts the player's own King in check.
     * 
     * @param position The position to evaluate.
     * @return Vector of all strictly legal Move objects.
     */
    static std::vector<Move> legalMoves(const Position& position);

    /**
     * @brief Generates all strictly legal moves originating from a specific square.
     * 
     * @details Filters the legal moves list to only those where move.from() matches the input square.
     * 
     * @param position The position to evaluate.
     * @param from The origin square.
     * @return Vector of legal moves starting at from.
     */
    static std::vector<Move> legalMovesFrom(const Position& position, Square from);

    /**
     * @brief Resolves raw client input (from, to, optional promotion) into a fully flagged Move.
     * 
     * @details Matches the client's intent against the verified legal move list. Prevents
     * clients from forging flags like en passant or castling.
     * 
     * @param position The current game position.
     * @param from The source square.
     * @param to The target square.
     * @param promo The requested promotion piece type, if any.
     * @return The complete Move with appropriate MoveKind, or std::nullopt if illegal.
     */
    static std::optional<Move> resolve(const Position& position,
                                       Square from,
                                       Square to,
                                       std::optional<PieceType> promo = std::nullopt);
};

} // namespace chess
