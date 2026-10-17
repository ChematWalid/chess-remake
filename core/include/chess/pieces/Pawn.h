#pragma once

#include "chess/pieces/Piece.h"

namespace chess {

/**
 * @brief Represents a Pawn piece with specialized movement rules.
 * 
 * @details Pawns advance forward, capture diagonally, can advance 2 squares
 * from their starting rank, execute en passant captures, and promote when
 * reaching the opposing edge of the board.
 */
class Pawn : public Piece {
public:
    /**
     * @brief Constructs a Pawn of the specified color.
     * 
     * @param color The piece color (White advances +1 rank, Black advances -1 rank).
     */
    explicit Pawn(Color color);

    /**
     * @brief Generates pseudo-legal moves for this pawn.
     * 
     * @details Generates 1-square forward advances into empty squares, optional
     * 2-square leaps from starting ranks, diagonal captures against enemy pieces,
     * en passant captures, and 4-way promotion choices when reaching the final rank.
     * 
     * @param position The current game position snapshot.
     * @param from The square where the pawn is currently positioned.
     * @param out Vector to receive generated Move objects.
     */
    void generateMoves(const Position& position, Square from, std::vector<Move>& out) const override;

    /**
     * @brief Checks if this pawn attacks a target square.
     * 
     * @details Evaluates whether the target square is located exactly 1 square
     * diagonally forward from the pawn's current location.
     * 
     * @param board The board layout (unused by pawn attack geometry).
     * @param from The square where this pawn is located.
     * @param target The square being tested for attack.
     * @return True if target is diagonally forward from the pawn, false otherwise.
     */
    bool attacks(const Board& board, Square from, Square target) const override;
};

} 
