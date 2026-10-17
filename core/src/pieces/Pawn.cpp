#include "chess/pieces/Pawn.h"
#include "chess/board/Board.h"
#include "chess/board/Position.h"

#include <cstdlib>

namespace chess {

Pawn::Pawn(Color color)
    : Piece(color, PieceType::Pawn) {}

void Pawn::generateMoves(const Position& position, Square from, std::vector<Move>& out) const {
    const Board& b = position.board();
    int forward = (color() == Color::White) ? 1 : -1;
    int startRank = (color() == Color::White) ? 1 : 6;
    int promoRank = (color() == Color::White) ? 7 : 0;

    auto addPawnMove = [&](Square to, MoveKind kind) {
        if (to.rank() == promoRank) {
            out.emplace_back(from, to, MoveKind::Promotion, PieceType::Queen);
            out.emplace_back(from, to, MoveKind::Promotion, PieceType::Rook);
            out.emplace_back(from, to, MoveKind::Promotion, PieceType::Bishop);
            out.emplace_back(from, to, MoveKind::Promotion, PieceType::Knight);
        } else {
            out.emplace_back(from, to, kind);
        }
    };

    auto oneStep = Square::tryOffset(from, 0, forward);
    if (oneStep.has_value() && b.pieceAt(*oneStep) == nullptr) {
        addPawnMove(*oneStep, MoveKind::Normal);

        if (from.rank() == startRank) {
            auto twoStep = Square::tryOffset(from, 0, 2 * forward);
            if (twoStep.has_value() && b.pieceAt(*twoStep) == nullptr) {
                out.emplace_back(from, *twoStep, MoveKind::DoublePawnPush);
            }
        }
    }

    for (int df : {-1, 1}) {
        auto capSq = Square::tryOffset(from, df, forward);
        if (capSq.has_value()) {
            const Piece* destPiece = b.pieceAt(*capSq);
            if (destPiece != nullptr && destPiece->color() != color()) {
                addPawnMove(*capSq, MoveKind::Normal);
            } else if (position.enPassant().has_value() && *capSq == *position.enPassant()) {
                out.emplace_back(from, *capSq, MoveKind::EnPassant);
            }
        }
    }
}

bool Pawn::attacks(const Board& board, Square from, Square target) const {
    (void)board;
    int forward = (color() == Color::White) ? 1 : -1;
    return (target.rank() == from.rank() + forward) && (std::abs(target.file() - from.file()) == 1);
}

} 
