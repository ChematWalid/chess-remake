#pragma once

#include <vector>

#include "chess/board/Types.h"
#include "chess/pieces/Piece.h"

namespace chess {

/**
 * @brief Abstract intermediate base class for pieces making single fixed-offset steps or jumps.
 * 
 * @details Implements single-step move generation and attack checking for Knights and Kings.
 * Derived subclasses supply their jump or step offset vectors.
 */
class StepPiece : public Piece {
protected:
    /**
     * @brief Constructs a step piece with color and piece classification.
     * 
     * @param color The piece color.
     * @param type The piece type (Knight or King).
     */
    StepPiece(Color color, PieceType type);

    /**
     * @brief Pure virtual method providing single-step coordinate offsets for this piece.
     * 
     * @details Subclasses override this to return their fixed offset list.
     * 
     * @return Const reference to a static vector of Direction offsets.
     */
    virtual const std::vector<Direction>& offsets() const = 0;

public:
    /**
     * @brief Generates pseudo-legal moves by checking each offset exactly once.
     * 
     * @details Steps directly to destination squares. Can move to empty squares
     * or capture enemy pieces. Ignores intermediate squares.
     * 
     * @param position The current game position.
     * @param from The square where this piece is positioned.
     * @param out Vector to receive generated Move objects.
     */
    void generateMoves(const Position& position, Square from, std::vector<Move>& out) const override;

    /**
     * @brief Checks if this step piece attacks a target square.
     * 
     * @details Tests whether the target square matches any of the piece's fixed jump offsets.
     * Intermediate board obstacles are ignored.
     * 
     * @param board The board layout (unused by step pieces as jumps are non-sliding).
     * @param from The origin square of this piece.
     * @param target The square being tested for attack.
     * @return True if target is within the piece's step pattern, false otherwise.
     */
    bool attacks(const Board& board, Square from, Square target) const override;
};

} // namespace chess

#include "chess/pieces/King.h"
#include "chess/pieces/Knight.h"
