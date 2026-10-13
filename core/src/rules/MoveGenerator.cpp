#include "chess/rules/MoveGenerator.h"
#include "chess/pieces/Piece.h"

namespace chess {

namespace {

void generatePieceMoves(const Position& position, Color side, std::vector<Move>& pseudoMoves) {
    const Board& board = position.board();
    auto friendlySquares = board.squaresOccupiedBy(side);
    for (Square from : friendlySquares) {
        const Piece* piece = board.pieceAt(from);
        if (piece != nullptr) {
            piece->generateMoves(position, from, pseudoMoves);
        }
    }
}

void generateCastlingMoves(const Position& position, Color side, std::vector<Move>& pseudoMoves) {
    if (position.isInCheck(side)) {
        return;
    }

    const Board& board = position.board();
    Color opponent = (side == Color::White) ? Color::Black : Color::White;
    int r = (side == Color::White) ? 0 : 7;
    Square kingSq(4, r);
    const Piece* king = board.pieceAt(kingSq);

    if (king == nullptr || king->type() != PieceType::King || king->color() != side) {
        return;
    }

    // King-side castling (O-O)
    if (position.castling().has(side, Side::KingSide)) {
        if (board.isEmpty(Square(5, r)) && board.isEmpty(Square(6, r))) {
            const Piece* rook = board.pieceAt(Square(7, r));
            if (rook != nullptr && rook->type() == PieceType::Rook && rook->color() == side) {
                if (!position.isAttacked(Square(5, r), opponent) &&
                    !position.isAttacked(Square(6, r), opponent)) {
                    pseudoMoves.emplace_back(kingSq, Square(6, r), MoveKind::Castle);
                }
            }
        }
    }

    // Queen-side castling (O-O-O)
    if (position.castling().has(side, Side::QueenSide)) {
        if (board.isEmpty(Square(1, r)) && board.isEmpty(Square(2, r)) && board.isEmpty(Square(3, r))) {
            const Piece* rook = board.pieceAt(Square(0, r));
            if (rook != nullptr && rook->type() == PieceType::Rook && rook->color() == side) {
                if (!position.isAttacked(Square(3, r), opponent) &&
                    !position.isAttacked(Square(2, r), opponent)) {
                    pseudoMoves.emplace_back(kingSq, Square(2, r), MoveKind::Castle);
                }
            }
        }
    }
}

void generatePseudoLegalMoves(const Position& position, std::vector<Move>& pseudoMoves) {
    Color side = position.sideToMove();
    generatePieceMoves(position, side, pseudoMoves);
    generateCastlingMoves(position, side, pseudoMoves);
}

} // namespace

std::vector<Move> MoveGenerator::legalMoves(const Position& position) {
    std::vector<Move> pseudoMoves;
    pseudoMoves.reserve(64);
    generatePseudoLegalMoves(position, pseudoMoves);

    Color side = position.sideToMove();
    std::vector<Move> legal;
    legal.reserve(pseudoMoves.size());

    for (const auto& move : pseudoMoves) {
        Position after = position.after(move);
        if (!after.isInCheck(side)) {
            legal.push_back(move);
        }
    }

    return legal;
}

std::vector<Move> MoveGenerator::legalMovesFrom(const Position& position, Square from) {
    auto all = legalMoves(position);
    std::vector<Move> filtered;
    for (const auto& m : all) {
        if (m.from() == from) {
            filtered.push_back(m);
        }
    }
    return filtered;
}

std::optional<Move> MoveGenerator::resolve(const Position& position,
                                           Square from,
                                           Square to,
                                           std::optional<PieceType> promo) {
    auto moves = legalMoves(position);
    for (const auto& m : moves) {
        if (m.from() == from && m.to() == to && m.promotion() == promo) {
            return m;
        }
    }
    return std::nullopt;
}

} // namespace chess
