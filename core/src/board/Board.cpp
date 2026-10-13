#include "chess/board/Board.h"
#include "chess/pieces/Piece.h"

namespace chess {

Board::Board() {
    squares.fill(nullptr);
}

const Piece* Board::pieceAt(Square sq) const {
    return squares[sq.index()];
}

void Board::place(const Piece& piece, Square sq) {
    squares[sq.index()] = &piece;
}

void Board::remove(Square sq) {
    squares[sq.index()] = nullptr;
}

bool Board::isEmpty(Square sq) const {
    return squares[sq.index()] == nullptr;
}

std::optional<Square> Board::findKing(Color color) const {
    for (int i = 0; i < 64; ++i) {
        const Piece* p = squares[i];
        if (p != nullptr && p->type() == PieceType::King && p->color() == color) {
            return Square(i % 8, i / 8);
        }
    }
    return std::nullopt;
}

std::vector<Square> Board::squaresOccupiedBy(Color color) const {
    std::vector<Square> result;
    result.reserve(16);
    for (int i = 0; i < 64; ++i) {
        const Piece* p = squares[i];
        if (p != nullptr && p->color() == color) {
            result.push_back(Square(i % 8, i / 8));
        }
    }
    return result;
}

bool Board::operator==(const Board& other) const {
    return squares == other.squares;
}

} // namespace chess
