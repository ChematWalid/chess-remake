#pragma once

#include <vector>

#include "chess/board/Types.h"
#include "chess/pieces/SlidingPiece.h"

namespace chess {

/**
 * @brief Represents a Bishop sliding piece.
 * 
 * @details Moves diagonally across squares of the same color (4 diagonal directions).
 */
class Bishop : public SlidingPiece {
protected:
    const std::vector<Direction>& directions() const override;

public:
    /**
     * @brief Constructs a Bishop of the specified color.
     * 
     * @param color The piece color.
     */
    explicit Bishop(Color color);
};

} 
