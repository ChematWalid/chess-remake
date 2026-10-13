#include "chess/pieces/Rook.h"

namespace chess {

Rook::Rook(Color color)
    : SlidingPiece(color, PieceType::Rook) {}

const std::vector<Direction>& Rook::directions() const {
    static const std::vector<Direction> dirs = {
        {0, 1}, {0, -1}, {1, 0}, {-1, 0}
    };
    return dirs;
}

} // namespace chess
