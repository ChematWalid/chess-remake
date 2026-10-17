#pragma once

#include <vector>

#include "chess/board/Types.h"
#include "chess/pieces/SlidingPiece.h"

namespace chess {

/**
 * @brief Represents a Rook sliding piece.
 * 
 * @details Moves orthogonally along ranks and files (4 directions: North, South, East, West).
 */
class Rook : public SlidingPiece {
protected:
    const std::vector<Direction>& directions() const override;

public:
    /**
     * @brief Constructs a Rook of the specified color.
     * 
     * @param color The piece color.
     */
    explicit Rook(Color color);
};

} 
