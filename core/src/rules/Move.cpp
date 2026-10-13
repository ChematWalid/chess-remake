#include "chess/rules/Move.h"

namespace chess {

Move::Move(Square from, Square to, MoveKind kind, std::optional<PieceType> promotion)
    : fromSquare(from), toSquare(to), moveKind(kind), promotionPiece(promotion) {}

Square Move::from() const {
    return fromSquare;
}

Square Move::to() const {
    return toSquare;
}

MoveKind Move::kind() const {
    return moveKind;
}

std::optional<PieceType> Move::promotion() const {
    return promotionPiece;
}

std::string Move::toUci() const {
    std::string uci = fromSquare.toString() + toSquare.toString();
    if (promotionPiece.has_value()) {
        switch (*promotionPiece) {
            case PieceType::Queen:  uci += 'q'; break;
            case PieceType::Rook:   uci += 'r'; break;
            case PieceType::Bishop: uci += 'b'; break;
            case PieceType::Knight: uci += 'n'; break;
            default: break;
        }
    }
    return uci;
}

bool Move::operator==(const Move& other) const {
    return fromSquare == other.fromSquare &&
           toSquare == other.toSquare &&
           moveKind == other.moveKind &&
           promotionPiece == other.promotionPiece;
}

} // namespace chess
