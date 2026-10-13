#pragma once

#include <vector>

#include "chess/board/Types.h"
#include "chess/pieces/StepPiece.h"

namespace chess {

/**
 * @brief Represents a King piece.
 * 
 * @details Moves 1 square in any direction (orthogonal or diagonal).
 */
class King : public StepPiece {
protected:
    const std::vector<Direction>& offsets() const override;

public:
    /**
     * @brief Constructs a King of the specified color.
     * 
     * @param color The piece color.
     */
    explicit King(Color color);
};

} // namespace chess
