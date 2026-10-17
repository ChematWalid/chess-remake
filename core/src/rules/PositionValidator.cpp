#include "chess/rules/PositionValidator.h"
#include "chess/notation/Fen.h"
#include "chess/notation/SanNotation.h"
#include "chess/pieces/Piece.h"
#include "chess/rules/ChessGame.h"
#include "chess/rules/MoveGenerator.h"

namespace chess {

bool PositionValidator::isPlayable(const Position& position) {
    int whiteKings = 0;
    int blackKings = 0;
    int whitePawns = 0;
    int blackPawns = 0;
    for (int file = 0; file < 8; ++file) {
        for (int rank = 0; rank < 8; ++rank) {
            const Piece* piece = position.board().pieceAt(Square(file, rank));
            if (!piece) continue;
            if (piece->type() == PieceType::King) {
                if (piece->color() == Color::White) ++whiteKings;
                else ++blackKings;
            }
            if (piece->type() == PieceType::Pawn) {
                if (rank == 0 || rank == 7) return false;
                if (piece->color() == Color::White) ++whitePawns;
                else ++blackPawns;
            }
        }
    }
    if (whiteKings != 1 || blackKings != 1 || whitePawns > 8 || blackPawns > 8) return false;
    const auto pieceAt = [&](int file, int rank, Color color, PieceType type) {
        const Piece* piece = position.board().pieceAt(Square(file, rank));
        return piece && piece->color() == color && piece->type() == type;
    };
    const auto& rights = position.castling();
    if (rights.has(Color::White, Side::KingSide)
            && (!pieceAt(4, 0, Color::White, PieceType::King) || !pieceAt(7, 0, Color::White, PieceType::Rook))) return false;
    if (rights.has(Color::White, Side::QueenSide)
            && (!pieceAt(4, 0, Color::White, PieceType::King) || !pieceAt(0, 0, Color::White, PieceType::Rook))) return false;
    if (rights.has(Color::Black, Side::KingSide)
            && (!pieceAt(4, 7, Color::Black, PieceType::King) || !pieceAt(7, 7, Color::Black, PieceType::Rook))) return false;
    if (rights.has(Color::Black, Side::QueenSide)
            && (!pieceAt(4, 7, Color::Black, PieceType::King) || !pieceAt(0, 7, Color::Black, PieceType::Rook))) return false;
    if (position.enPassant().has_value()) {
        const Square target = *position.enPassant();
        if (!position.board().isEmpty(target)) return false;
        if (position.sideToMove() == Color::White) {
            if (target.rank() != 5 || !pieceAt(target.file(), 4, Color::Black, PieceType::Pawn)
                    || !position.board().isEmpty(Square(target.file(), 6))) return false;
        } else if (target.rank() != 2 || !pieceAt(target.file(), 3, Color::White, PieceType::Pawn)
                || !position.board().isEmpty(Square(target.file(), 1))) return false;
    }
    if (position.isInCheck(Color::White) && position.isInCheck(Color::Black)) return false;
    if (position.sideToMove() == Color::White && position.isInCheck(Color::Black)) return false;
    if (position.sideToMove() == Color::Black && position.isInCheck(Color::White)) return false;
    ChessGame game(position);
    return !game.isOver() && !MoveGenerator::legalMoves(position).empty();
}

bool PositionValidator::replayMatches(
    const std::vector<std::string>& sanMoves,
    const std::vector<std::string>& fens,
    const std::string& finalFen
) {
    if (sanMoves.empty()) return fens.empty();
    if (fens.size() != sanMoves.size() + 1) return false;
    try {
        Position position = Fen::parse(fens.front());
        if (!isPlayable(position)) return false;
        SanNotation notation;
        for (std::size_t index = 0; index < sanMoves.size(); ++index) {
            const auto move = notation.parse(position, sanMoves[index]);
            if (!move.has_value()) return false;
            position = position.after(*move);
            if (!isPlayable(position)) return false;
            if (Fen::serialize(position) != Fen::serialize(Fen::parse(fens[index + 1]))) return false;
        }
        return Fen::serialize(position) == Fen::serialize(Fen::parse(finalFen));
    } catch (const std::exception&) {
        return false;
    }
}

}
