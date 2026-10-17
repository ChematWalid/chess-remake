#include "chess/pieces/SlidingPiece.h"
#include "chess/board/Board.h"
#include "chess/board/Position.h"

namespace chess {

SlidingPiece::SlidingPiece(Color color, PieceType type)
    : Piece(color, type) {}

void SlidingPiece::generateMoves(const Position& position, Square from, std::vector<Move>& out) const {
    const Board& b = position.board();
    for (const auto& dir : directions()) {
        auto current = Square::tryOffset(from, dir.deltaFile, dir.deltaRank);
        while (current.has_value()) {
            const Piece* destPiece = b.pieceAt(*current);
            if (destPiece == nullptr) {
                out.emplace_back(from, *current, MoveKind::Normal);
            } else {
                if (destPiece->color() != color()) {
                    out.emplace_back(from, *current, MoveKind::Normal);
                }
                break;
            }
            current = Square::tryOffset(*current, dir.deltaFile, dir.deltaRank);
        }
    }
}

bool SlidingPiece::attacks(const Board& board, Square from, Square target) const {
    for (const auto& dir : directions()) {
        auto current = Square::tryOffset(from, dir.deltaFile, dir.deltaRank);
        while (current.has_value()) {
            if (*current == target) {
                return true;
            }
            if (board.pieceAt(*current) != nullptr) {
                break;
            }
            current = Square::tryOffset(*current, dir.deltaFile, dir.deltaRank);
        }
    }
    return false;
}

} 
