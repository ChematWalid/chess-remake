#include "chess/pieces/Knight.h"

namespace chess {

Knight::Knight(Color color)
    : StepPiece(color, PieceType::Knight) {}

const std::vector<Direction>& Knight::offsets() const {
    static const std::vector<Direction> offs = {
        {1, 2}, {2, 1}, {2, -1}, {1, -2},
        {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
    };
    return offs;
}

} // namespace chess
