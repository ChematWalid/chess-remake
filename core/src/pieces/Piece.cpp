#include "chess/pieces/Piece.h"

#include <cctype>

namespace chess {

Piece::Piece(Color color, PieceType type)
    : pieceColor(color), pieceType(type) {}

Color Piece::color() const {
    return pieceColor;
}

PieceType Piece::type() const {
    return pieceType;
}

char Piece::symbol() const {
    char c = '?';
    switch (pieceType) {
        case PieceType::Pawn:   c = 'P'; break;
        case PieceType::Knight: c = 'N'; break;
        case PieceType::Bishop: c = 'B'; break;
        case PieceType::Rook:   c = 'R'; break;
        case PieceType::Queen:  c = 'Q'; break;
        case PieceType::King:   c = 'K'; break;
    }

    if (pieceColor == Color::Black) {
        c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    }

    return c;
}

} // namespace chess
