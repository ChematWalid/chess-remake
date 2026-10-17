#include "chess/notation/UciNotation.h"
#include "chess/board/Square.h"
#include "chess/rules/MoveGenerator.h"

namespace chess {

std::string UciNotation::serialize(const Position& position, const Move& move) const {
    (void)position;
    return move.toUci();
}

std::optional<Move> UciNotation::parse(const Position& position, const std::string& text) const {
    if (text.length() != 4 && text.length() != 5) {
        return std::nullopt;
    }

    Square from(0, 0);
    Square to(0, 0);

    try {
        from = Square::fromString(text.substr(0, 2));
        to = Square::fromString(text.substr(2, 2));
    } catch (...) {
        return std::nullopt;
    }

    std::optional<PieceType> promo = std::nullopt;
    if (text.length() == 5) {
        switch (text[4]) {
            case 'q': promo = PieceType::Queen; break;
            case 'r': promo = PieceType::Rook; break;
            case 'b': promo = PieceType::Bishop; break;
            case 'n': promo = PieceType::Knight; break;
            default: return std::nullopt;
        }
    }

    return MoveGenerator::resolve(position, from, to, promo);
}

} 
