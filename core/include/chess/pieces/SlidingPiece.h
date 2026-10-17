#pragma once

#include <vector>

#include "chess/board/Types.h"
#include "chess/pieces/Piece.h"

namespace chess {

/**
 * @brief Abstract intermediate base class for pieces moving along continuous straight rays.
 * 
 * @details Implements ray marching algorithms once for all sliding pieces (Rook, Bishop, Queen).
 * Derived subclasses supply their directional vectors.
 */
class SlidingPiece : public Piece {
protected:
    /**
     * @brief Constructs a sliding piece with color and piece classification.
     * 
     * @param color The piece color.
     * @param type The piece type (Rook, Bishop, or Queen).
     */
    SlidingPiece(Color color, PieceType type);

    /**
     * @brief Pure virtual method providing ray vectors for this sliding piece.
     * 
     * @details Subclasses override this to return their movement directions.
     * 
     * @return Const reference to a static vector of Direction deltas.
     */
    virtual const std::vector<Direction>& directions() const = 0;

public:
    /**
     * @brief Generates all pseudo-legal moves by ray marching along each direction vector.
     * 
     * @details Continues advancing in each direction until hitting an obstacle piece
     * or the board perimeter. Captures opponent pieces and terminates ray upon collision.
     * 
     * @param position The current game position.
     * @param from The square currently occupied by this piece.
     * @param out Vector to receive the generated Move objects.
     */
    void generateMoves(const Position& position, Square from, std::vector<Move>& out) const override;

    /**
     * @brief Checks if this sliding piece attacks a target square.
     * 
     * @details Marches rays towards the target square. Returns true if the target lies
     * unobstructed along one of the rays.
     * 
     * @param board The board layout to check for blocking pieces.
     * @param from The origin square of this piece.
     * @param target The square being tested for attack.
     * @return True if target is attacked without blocking obstacles, false otherwise.
     */
    bool attacks(const Board& board, Square from, Square target) const override;
};

} 

#include "chess/pieces/Bishop.h"
#include "chess/pieces/Queen.h"
#include "chess/pieces/Rook.h"
