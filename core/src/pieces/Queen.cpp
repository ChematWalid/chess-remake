#include "chess/pieces/Queen.h"

namespace chess {

Queen::Queen(Color color)
    : SlidingPiece(color, PieceType::Queen) {}

const std::vector<Direction>& Queen::directions() const {
    static const std::vector<Direction> dirs = {
        {0, 1}, {0, -1}, {1, 0}, {-1, 0},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    return dirs;
}

} 
