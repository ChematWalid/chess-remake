#include "chess/pieces/PieceFactory.h"
#include "chess/pieces/Bishop.h"
#include "chess/pieces/King.h"
#include "chess/pieces/Knight.h"
#include "chess/pieces/Pawn.h"
#include "chess/pieces/Queen.h"
#include "chess/pieces/Rook.h"

#include <cctype>
#include <stdexcept>
#include <string>

namespace chess {

namespace {

const Pawn whitePawn(Color::White);
const Knight whiteKnight(Color::White);
const Bishop whiteBishop(Color::White);
const Rook whiteRook(Color::White);
const Queen whiteQueen(Color::White);
const King whiteKing(Color::White);

const Pawn blackPawn(Color::Black);
const Knight blackKnight(Color::Black);
const Bishop blackBishop(Color::Black);
const Rook blackRook(Color::Black);
const Queen blackQueen(Color::Black);
const King blackKing(Color::Black);

} // namespace

const Piece& PieceFactory::get(Color color, PieceType type) {
    if (color == Color::White) {
        switch (type) {
            case PieceType::Pawn:   return whitePawn;
            case PieceType::Knight: return whiteKnight;
            case PieceType::Bishop: return whiteBishop;
            case PieceType::Rook:   return whiteRook;
            case PieceType::Queen:  return whiteQueen;
            case PieceType::King:   return whiteKing;
        }
    } else {
        switch (type) {
            case PieceType::Pawn:   return blackPawn;
            case PieceType::Knight: return blackKnight;
            case PieceType::Bishop: return blackBishop;
            case PieceType::Rook:   return blackRook;
            case PieceType::Queen:  return blackQueen;
            case PieceType::King:   return blackKing;
        }
    }
    throw std::invalid_argument("PieceFactory: unknown piece type");
}

const Piece& PieceFactory::fromSymbol(char symbol) {
    Color color = std::isupper(static_cast<unsigned char>(symbol)) ? Color::White : Color::Black;
    char lower = static_cast<char>(std::tolower(static_cast<unsigned char>(symbol)));

    switch (lower) {
        case 'p': return get(color, PieceType::Pawn);
        case 'n': return get(color, PieceType::Knight);
        case 'b': return get(color, PieceType::Bishop);
        case 'r': return get(color, PieceType::Rook);
        case 'q': return get(color, PieceType::Queen);
        case 'k': return get(color, PieceType::King);
        default:
            throw std::invalid_argument(std::string("PieceFactory: unknown piece symbol '") + symbol + "'");
    }
}

} // namespace chess
