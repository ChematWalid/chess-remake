#include "chess/board/Types.h"
#include "chess/pieces/Piece.h"

#include <gtest/gtest.h>

using namespace chess;

class ConcretePiece : public Piece {
public:
    ConcretePiece(Color color, PieceType type) : Piece(color, type) {}

    void generateMoves(const Position&, Square, std::vector<Move>&) const override {}
    bool attacks(const Board&, Square, Square) const override { return false; }
};

TEST(Piece, Getters) {
    ConcretePiece p(Color::White, PieceType::Knight);
    EXPECT_EQ(p.color(), Color::White);
    EXPECT_EQ(p.type(), PieceType::Knight);
}

TEST(Piece, WhiteSymbols) {
    EXPECT_EQ(ConcretePiece(Color::White, PieceType::Pawn).symbol(), 'P');
    EXPECT_EQ(ConcretePiece(Color::White, PieceType::Knight).symbol(), 'N');
    EXPECT_EQ(ConcretePiece(Color::White, PieceType::Bishop).symbol(), 'B');
    EXPECT_EQ(ConcretePiece(Color::White, PieceType::Rook).symbol(), 'R');
    EXPECT_EQ(ConcretePiece(Color::White, PieceType::Queen).symbol(), 'Q');
    EXPECT_EQ(ConcretePiece(Color::White, PieceType::King).symbol(), 'K');
}

TEST(Piece, BlackSymbols) {
    EXPECT_EQ(ConcretePiece(Color::Black, PieceType::Pawn).symbol(), 'p');
    EXPECT_EQ(ConcretePiece(Color::Black, PieceType::Knight).symbol(), 'n');
    EXPECT_EQ(ConcretePiece(Color::Black, PieceType::Bishop).symbol(), 'b');
    EXPECT_EQ(ConcretePiece(Color::Black, PieceType::Rook).symbol(), 'r');
    EXPECT_EQ(ConcretePiece(Color::Black, PieceType::Queen).symbol(), 'q');
    EXPECT_EQ(ConcretePiece(Color::Black, PieceType::King).symbol(), 'k');
}
