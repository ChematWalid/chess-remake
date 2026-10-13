#include "chess/board/Board.h"
#include "chess/board/Square.h"
#include "chess/board/Types.h"
#include "chess/pieces/PieceFactory.h"

#include <gtest/gtest.h>
#include <stdexcept>

using namespace chess;

TEST(PieceFactory, FlyweightIdentity) {
    const Piece& p1 = PieceFactory::get(Color::White, PieceType::Pawn);
    const Piece& p2 = PieceFactory::get(Color::White, PieceType::Pawn);
    EXPECT_EQ(&p1, &p2);

    const Piece& k1 = PieceFactory::get(Color::Black, PieceType::Knight);
    const Piece& k2 = PieceFactory::get(Color::Black, PieceType::Knight);
    EXPECT_EQ(&k1, &k2);
}

TEST(PieceFactory, FromSymbolValid) {
    EXPECT_EQ(&PieceFactory::fromSymbol('P'), &PieceFactory::get(Color::White, PieceType::Pawn));
    EXPECT_EQ(&PieceFactory::fromSymbol('p'), &PieceFactory::get(Color::Black, PieceType::Pawn));
    EXPECT_EQ(&PieceFactory::fromSymbol('N'), &PieceFactory::get(Color::White, PieceType::Knight));
    EXPECT_EQ(&PieceFactory::fromSymbol('n'), &PieceFactory::get(Color::Black, PieceType::Knight));
    EXPECT_EQ(&PieceFactory::fromSymbol('B'), &PieceFactory::get(Color::White, PieceType::Bishop));
    EXPECT_EQ(&PieceFactory::fromSymbol('b'), &PieceFactory::get(Color::Black, PieceType::Bishop));
    EXPECT_EQ(&PieceFactory::fromSymbol('R'), &PieceFactory::get(Color::White, PieceType::Rook));
    EXPECT_EQ(&PieceFactory::fromSymbol('r'), &PieceFactory::get(Color::Black, PieceType::Rook));
    EXPECT_EQ(&PieceFactory::fromSymbol('Q'), &PieceFactory::get(Color::White, PieceType::Queen));
    EXPECT_EQ(&PieceFactory::fromSymbol('q'), &PieceFactory::get(Color::Black, PieceType::Queen));
    EXPECT_EQ(&PieceFactory::fromSymbol('K'), &PieceFactory::get(Color::White, PieceType::King));
    EXPECT_EQ(&PieceFactory::fromSymbol('k'), &PieceFactory::get(Color::Black, PieceType::King));
}

TEST(PieceFactory, FromSymbolInvalid) {
    EXPECT_THROW(PieceFactory::fromSymbol('x'), std::invalid_argument);
    EXPECT_THROW(PieceFactory::fromSymbol('1'), std::invalid_argument);
}

TEST(PieceAttacks, KnightAttacks) {
    Board b;
    const Piece& knight = PieceFactory::get(Color::White, PieceType::Knight);
    Square d4(3, 3);

    EXPECT_TRUE(knight.attacks(b, d4, Square(4, 5))); // e6 (+1, +2)
    EXPECT_TRUE(knight.attacks(b, d4, Square(5, 4))); // f5 (+2, +1)
    EXPECT_FALSE(knight.attacks(b, d4, Square(3, 5))); // d6 (straight)
    EXPECT_FALSE(knight.attacks(b, d4, Square(5, 5))); // f6 (diagonal)
}

TEST(PieceAttacks, RookAttacksAndBlockers) {
    Board b;
    const Piece& rook = PieceFactory::get(Color::White, PieceType::Rook);
    Square d4(3, 3);
    Square d7(3, 6);

    EXPECT_TRUE(rook.attacks(b, d4, d7));

    // Place an obstacle at d5
    const Piece& obstacle = PieceFactory::get(Color::Black, PieceType::Pawn);
    b.place(obstacle, Square(3, 4));

    EXPECT_FALSE(rook.attacks(b, d4, d7));
    EXPECT_TRUE(rook.attacks(b, d4, Square(3, 4))); // attacks the obstacle itself
}

TEST(PieceAttacks, BishopAttacksAndBlockers) {
    Board b;
    const Piece& bishop = PieceFactory::get(Color::White, PieceType::Bishop);
    Square c1(2, 0);
    Square f4(5, 3);

    EXPECT_TRUE(bishop.attacks(b, c1, f4));

    // Place an obstacle at d2
    const Piece& obstacle = PieceFactory::get(Color::White, PieceType::Pawn);
    b.place(obstacle, Square(3, 1));

    EXPECT_FALSE(bishop.attacks(b, c1, f4));
}

TEST(PieceAttacks, PawnAttacks) {
    Board b;
    const Piece& whitePawn = PieceFactory::get(Color::White, PieceType::Pawn);
    Square e4(4, 3);

    EXPECT_TRUE(whitePawn.attacks(b, e4, Square(3, 4)));  // d5
    EXPECT_TRUE(whitePawn.attacks(b, e4, Square(5, 4)));  // f5
    EXPECT_FALSE(whitePawn.attacks(b, e4, Square(4, 4))); // e5 (straight is not attack!)
    EXPECT_FALSE(whitePawn.attacks(b, e4, Square(4, 2))); // e3 (backward)

    const Piece& blackPawn = PieceFactory::get(Color::Black, PieceType::Pawn);
    Square e5(4, 4);
    EXPECT_TRUE(blackPawn.attacks(b, e5, Square(3, 3)));  // d4
    EXPECT_TRUE(blackPawn.attacks(b, e5, Square(5, 3)));  // f4
}
