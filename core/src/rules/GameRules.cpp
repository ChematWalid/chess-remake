#include "chess/rules/GameRules.h"
#include "chess/pieces/Piece.h"
#include "chess/rules/MoveGenerator.h"

namespace chess {

bool GameRules::isCheckmate(const Position& position) {
    if (!position.isInCheck(position.sideToMove())) {
        return false;
    }
    return MoveGenerator::legalMoves(position).empty();
}

bool GameRules::isStalemate(const Position& position) {
    if (position.isInCheck(position.sideToMove())) {
        return false;
    }
    return MoveGenerator::legalMoves(position).empty();
}

bool GameRules::isFiftyMoveDraw(const Position& position) {
    return position.halfmoveClock() >= 100;
}

bool GameRules::hasInsufficientMaterial(const Board& board) {
    int whitePawns = 0, blackPawns = 0;
    int whiteRooks = 0, blackRooks = 0;
    int whiteQueens = 0, blackQueens = 0;
    int whiteKnights = 0, blackKnights = 0;
    int whiteBishops = 0, blackBishops = 0;

    std::optional<Square> whiteBishopSq;
    std::optional<Square> blackBishopSq;

    for (int file = 0; file < 8; ++file) {
        for (int rank = 0; rank < 8; ++rank) {
            Square sq(file, rank);
            const Piece* p = board.pieceAt(sq);
            if (p == nullptr) continue;

            if (p->color() == Color::White) {
                switch (p->type()) {
                    case PieceType::Pawn:   whitePawns++; break;
                    case PieceType::Rook:   whiteRooks++; break;
                    case PieceType::Queen:  whiteQueens++; break;
                    case PieceType::Knight: whiteKnights++; break;
                    case PieceType::Bishop:
                        whiteBishops++;
                        whiteBishopSq = sq;
                        break;
                    case PieceType::King: break;
                }
            } else {
                switch (p->type()) {
                    case PieceType::Pawn:   blackPawns++; break;
                    case PieceType::Rook:   blackRooks++; break;
                    case PieceType::Queen:  blackQueens++; break;
                    case PieceType::Knight: blackKnights++; break;
                    case PieceType::Bishop:
                        blackBishops++;
                        blackBishopSq = sq;
                        break;
                    case PieceType::King: break;
                }
            }
        }
    }

    if (whitePawns > 0 || blackPawns > 0 ||
        whiteRooks > 0 || blackRooks > 0 ||
        whiteQueens > 0 || blackQueens > 0) {
        return false;
    }

    int whiteMinors = whiteKnights + whiteBishops;
    int blackMinors = blackKnights + blackBishops;

    if (whiteMinors == 0 && blackMinors == 0) {
        return true;
    }

    if ((whiteMinors == 1 && blackMinors == 0) || (whiteMinors == 0 && blackMinors == 1)) {
        return true;
    }

    if (whiteBishops == 1 && blackBishops == 1 && whiteKnights == 0 && blackKnights == 0) {
        int whiteColor = (whiteBishopSq->file() + whiteBishopSq->rank()) % 2;
        int blackColor = (blackBishopSq->file() + blackBishopSq->rank()) % 2;
        if (whiteColor == blackColor) {
            return true;
        }
    }

    return false;
}

namespace {

bool hasLegalEnPassant(const Position& pos) {
    if (!pos.enPassant().has_value()) {
        return false;
    }
    Square ep = *pos.enPassant();
    Color side = pos.sideToMove();
    int forward = (side == Color::White) ? 1 : -1;
    for (int df : {-1, 1}) {
        auto fromOpt = Square::tryOffset(ep, df, -forward);
        if (fromOpt.has_value()) {
            const Piece* pc = pos.board().pieceAt(*fromOpt);
            if (pc != nullptr && pc->type() == PieceType::Pawn && pc->color() == side) {
                Move m(*fromOpt, ep, MoveKind::EnPassant);
                Position after = pos.after(m);
                if (!after.isInCheck(side)) {
                    return true;
                }
            }
        }
    }
    return false;
}

std::optional<Square> effectiveEnPassant(const Position& pos) {
    if (hasLegalEnPassant(pos)) {
        return pos.enPassant();
    }
    return std::nullopt;
}

}

bool GameRules::isThreefoldRepetition(const std::vector<Position>& history) {
    if (history.empty()) {
        return false;
    }

    const Position& current = history.back();
    auto currentEp = effectiveEnPassant(current);
    int count = 0;
    for (const auto& pos : history) {
        if (pos.board() == current.board() &&
            pos.sideToMove() == current.sideToMove() &&
            pos.castling() == current.castling() &&
            effectiveEnPassant(pos) == currentEp) {
            count++;
        }
    }
    return count >= 3;
}

}
