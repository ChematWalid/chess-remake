#include "chess/board/Board.h"
#include "chess/board/Square.h"
#include "chess/board/Types.h"
#include "chess/pieces/Piece.h"

#include <gtest/gtest.h>

using namespace chess;

class MockPiece : public Piece {
public:
    MockPiece(Color color, PieceType type) : Piece(color, type) {}

    void generateMoves(const Position&, Square, std::vector<Move>&) const override {}
    bool attacks(const Board&, Square, Square) const override { return false; }
};

TEST(Board, EmptyBoard) {
    Board b;
    Square e4(4, 3);

    EXPECT_TRUE(b.isEmpty(e4));
    EXPECT_EQ(b.pieceAt(e4), nullptr);
    EXPECT_FALSE(b.findKing(Color::White).has_value());
    EXPECT_FALSE(b.findKing(Color::Black).has_value());
    EXPECT_TRUE(b.squaresOccupiedBy(Color::White).empty());
    EXPECT_TRUE(b.squaresOccupiedBy(Color::Black).empty());
}

TEST(Board, PlaceAndRemove) {
    Board b;
    Square e4(4, 3);
    MockPiece p(Color::White, PieceType::Pawn);

    b.place(p, e4);
    EXPECT_FALSE(b.isEmpty(e4));
    EXPECT_EQ(b.pieceAt(e4), &p);

    b.remove(e4);
    EXPECT_TRUE(b.isEmpty(e4));
    EXPECT_EQ(b.pieceAt(e4), nullptr);
}

TEST(Board, FindKing) {
    Board b;
    Square e1(4, 0);
    Square e8(4, 7);
    MockPiece whiteKing(Color::White, PieceType::King);
    MockPiece blackKing(Color::Black, PieceType::King);

    b.place(whiteKing, e1);
    b.place(blackKing, e8);

    auto wk = b.findKing(Color::White);
    ASSERT_TRUE(wk.has_value());
    EXPECT_EQ(*wk, e1);

    auto bk = b.findKing(Color::Black);
    ASSERT_TRUE(bk.has_value());
    EXPECT_EQ(*bk, e8);
}

TEST(Board, SquaresOccupiedBy) {
    Board b;
    Square a1(0, 0);
    Square h1(7, 0);
    Square a8(0, 7);

    MockPiece w1(Color::White, PieceType::Rook);
    MockPiece w2(Color::White, PieceType::Rook);
    MockPiece b1(Color::Black, PieceType::Rook);

    b.place(w1, a1);
    b.place(w2, h1);
    b.place(b1, a8);

    auto whiteSquares = b.squaresOccupiedBy(Color::White);
    EXPECT_EQ(whiteSquares.size(), 2);

    auto blackSquares = b.squaresOccupiedBy(Color::Black);
    EXPECT_EQ(blackSquares.size(), 1);
    EXPECT_EQ(blackSquares[0], a8);
}

TEST(Board, Equality) {
    Board b1;
    Board b2;
    EXPECT_EQ(b1, b2);

    MockPiece p(Color::White, PieceType::Knight);
    Square b1Sq(1, 0);

    b1.place(p, b1Sq);
    EXPECT_NE(b1, b2);

    b2.place(p, b1Sq);
    EXPECT_EQ(b1, b2);
}
