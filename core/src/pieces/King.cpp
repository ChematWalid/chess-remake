#include "chess/pieces/King.h"

namespace chess {

King::King(Color color)
    : StepPiece(color, PieceType::King) {}

const std::vector<Direction>& King::offsets() const {
    static const std::vector<Direction> offs = {
        {0, 1}, {0, -1}, {1, 0}, {-1, 0},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    return offs;
}

} 
