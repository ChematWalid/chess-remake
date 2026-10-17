#include "chess/pieces/Bishop.h"

namespace chess {

Bishop::Bishop(Color color)
    : SlidingPiece(color, PieceType::Bishop) {}

const std::vector<Direction>& Bishop::directions() const {
    static const std::vector<Direction> dirs = {
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    return dirs;
}

} 
