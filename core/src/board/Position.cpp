#include "chess/board/Position.h"
#include "chess/pieces/Piece.h"
#include "chess/pieces/PieceFactory.h"

namespace chess {

namespace {

void applyMoveToBoard(Board& board, const Move& move, Color side, const Piece* movingPiece) {
    board.remove(move.from());

    switch (move.kind()) {
        case MoveKind::EnPassant: {
            Square pawnSq(move.to().file(), move.from().rank());
            board.remove(pawnSq);
            if (movingPiece != nullptr) {
                board.place(*movingPiece, move.to());
            }
            break;
        }
        case MoveKind::Castle: {
            if (movingPiece != nullptr) {
                board.place(*movingPiece, move.to());
            }
            /** @brief Move corresponding rook */
            if (move.to().file() == 6) {
                Square rookFrom(7, move.from().rank());
                Square rookTo(5, move.from().rank());
                const Piece* rook = board.pieceAt(rookFrom);
                board.remove(rookFrom);
                if (rook != nullptr) {
                    board.place(*rook, rookTo);
                }
            } else if (move.to().file() == 2) {
                Square rookFrom(0, move.from().rank());
                Square rookTo(3, move.from().rank());
                const Piece* rook = board.pieceAt(rookFrom);
                board.remove(rookFrom);
                if (rook != nullptr) {
                    board.place(*rook, rookTo);
                }
            }
            break;
        }
        case MoveKind::Promotion: {
            if (move.promotion().has_value()) {
                const Piece& promoted = PieceFactory::get(side, *move.promotion());
                board.place(promoted, move.to());
            } else if (movingPiece != nullptr) {
                board.place(*movingPiece, move.to());
            }
            break;
        }
        case MoveKind::Normal:
        case MoveKind::DoublePawnPush:
        default: {
            if (movingPiece != nullptr) {
                board.place(*movingPiece, move.to());
            }
            break;
        }
    }
}

CastlingRights updateCastlingRights(CastlingRights rights,
                                     const Move& move,
                                     const Piece* movingPiece,
                                     Color side) {
    if (movingPiece != nullptr && movingPiece->type() == PieceType::King) {
        rights.removeAll(side);
    }

    /** @brief Revoke rights if rooks move from corner origin squares */
    if (move.from() == Square(0, 0)) rights.remove(Color::White, Side::QueenSide);
    if (move.from() == Square(7, 0)) rights.remove(Color::White, Side::KingSide);
    if (move.from() == Square(0, 7)) rights.remove(Color::Black, Side::QueenSide);
    if (move.from() == Square(7, 7)) rights.remove(Color::Black, Side::KingSide);

    /** @brief Revoke rights if rooks are captured on corner destination squares */
    if (move.to() == Square(0, 0)) rights.remove(Color::White, Side::QueenSide);
    if (move.to() == Square(7, 0)) rights.remove(Color::White, Side::KingSide);
    if (move.to() == Square(0, 7)) rights.remove(Color::Black, Side::QueenSide);
    if (move.to() == Square(7, 7)) rights.remove(Color::Black, Side::KingSide);

    return rights;
}

}

Position::Position()
    : currentBoard(),
      side(Color::White),
      castlingRights(CastlingRights::none()),
      enPassantSquare(std::nullopt),
      halfmoves(0),
      fullmoves(1) {}

Position::Position(Board board,
                   Color sideToMove,
                   CastlingRights castling,
                   std::optional<Square> enPassant,
                   int halfmoveClock,
                   int fullmoveNumber)
    : currentBoard(board),
      side(sideToMove),
      castlingRights(castling),
      enPassantSquare(enPassant),
      halfmoves(halfmoveClock),
      fullmoves(fullmoveNumber) {}

const Board& Position::board() const {
    return currentBoard;
}

Color Position::sideToMove() const {
    return side;
}

const CastlingRights& Position::castling() const {
    return castlingRights;
}

std::optional<Square> Position::enPassant() const {
    return enPassantSquare;
}

int Position::halfmoveClock() const {
    return halfmoves;
}

int Position::fullmoveNumber() const {
    return fullmoves;
}

bool Position::isAttacked(Square sq, Color byColor) const {
    auto squares = currentBoard.squaresOccupiedBy(byColor);
    for (Square origin : squares) {
        const Piece* p = currentBoard.pieceAt(origin);
        if (p != nullptr && p->attacks(currentBoard, origin, sq)) {
            return true;
        }
    }
    return false;
}

bool Position::isInCheck(Color color) const {
    auto kingSq = currentBoard.findKing(color);
    if (!kingSq.has_value()) {
        return false;
    }
    Color opponent = (color == Color::White) ? Color::Black : Color::White;
    return isAttacked(*kingSq, opponent);
}

Position Position::after(const Move& move) const {
    Board nextBoard = currentBoard;
    const Piece* movingPiece = currentBoard.pieceAt(move.from());
    const Piece* capturedPiece = currentBoard.pieceAt(move.to());

    bool isPawn = (movingPiece != nullptr && movingPiece->type() == PieceType::Pawn);
    bool isCapture = (capturedPiece != nullptr) || (move.kind() == MoveKind::EnPassant);

    applyMoveToBoard(nextBoard, move, side, movingPiece);

    CastlingRights nextCastling = updateCastlingRights(castlingRights, move, movingPiece, side);

    std::optional<Square> nextEp = std::nullopt;
    if (move.kind() == MoveKind::DoublePawnPush) {
        int epRank = (side == Color::White) ? 2 : 5;
        nextEp = Square(move.from().file(), epRank);
    }

    int nextHalfmoves = (isPawn || isCapture) ? 0 : halfmoves + 1;
    int nextFullmoves = fullmoves + (side == Color::Black ? 1 : 0);
    Color nextSide = (side == Color::White) ? Color::Black : Color::White;

    return Position(nextBoard, nextSide, nextCastling, nextEp, nextHalfmoves, nextFullmoves);
}

bool Position::operator==(const Position& other) const {
    return currentBoard == other.currentBoard &&
           side == other.side &&
           castlingRights == other.castlingRights &&
           enPassantSquare == other.enPassantSquare &&
           halfmoves == other.halfmoves &&
           fullmoves == other.fullmoves;
}

} 
