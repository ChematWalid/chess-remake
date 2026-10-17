#pragma once

#include <vector>

#include "chess/board/Types.h"
#include "chess/pieces/SlidingPiece.h"

namespace chess {

/**
 * @brief Represents a Queen sliding piece.
 * 
 * @details Combines the power of a Rook and Bishop (all 8 orthogonal and diagonal directions).
 */
class Queen : public SlidingPiece {
protected:
    const std::vector<Direction>& directions() const override;

public:
    /**
     * @brief Constructs a Queen of the specified color.
     * 
     * @param color The piece color.
     */
    explicit Queen(Color color);
};

} 
