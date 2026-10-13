#include "chess/pieces/StepPiece.h"
#include "chess/board/Board.h"
#include "chess/board/Position.h"

namespace chess {

StepPiece::StepPiece(Color color, PieceType type)
    : Piece(color, type) {}

void StepPiece::generateMoves(const Position& position, Square from, std::vector<Move>& out) const {
    const Board& b = position.board();
    for (const auto& offset : offsets()) {
        auto to = Square::tryOffset(from, offset.deltaFile, offset.deltaRank);
        if (to.has_value()) {
            const Piece* dest = b.pieceAt(*to);
            if (dest == nullptr || dest->color() != color()) {
                out.emplace_back(from, *to, MoveKind::Normal);
            }
        }
    }
}

bool StepPiece::attacks(const Board& board, Square from, Square target) const {
    (void)board;
    for (const auto& offset : offsets()) {
        auto to = Square::tryOffset(from, offset.deltaFile, offset.deltaRank);
        if (to.has_value() && *to == target) {
            return true;
        }
    }
    return false;
}

} // namespace chess
