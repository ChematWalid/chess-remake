#pragma once

#include <vector>

#include "chess/board/Types.h"
#include "chess/pieces/StepPiece.h"

namespace chess {

/**
 * @brief Represents a Knight piece.
 * 
 * @details Moves in an "L-shape" (2 squares in one dimension, 1 square in the other).
 * Knights can leap over intervening pieces.
 */
class Knight : public StepPiece {
protected:
    const std::vector<Direction>& offsets() const override;

public:
    /**
     * @brief Constructs a Knight of the specified color.
     * 
     * @param color The piece color.
     */
    explicit Knight(Color color);
};

} 
