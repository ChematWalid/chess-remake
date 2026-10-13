#include "chess/board/Square.h"
#include "chess/board/Types.h"
#include "chess/rules/Move.h"

#include <gtest/gtest.h>

using namespace chess;

TEST(Move, NormalMoveGetters) {
    Square from(4, 1); // e2
    Square to(4, 3);   // e4
    Move m(from, to, MoveKind::DoublePawnPush);

    EXPECT_EQ(m.from(), from);
    EXPECT_EQ(m.to(), to);
    EXPECT_EQ(m.kind(), MoveKind::DoublePawnPush);
    EXPECT_FALSE(m.promotion().has_value());
    EXPECT_EQ(m.toUci(), "e2e4");
}

TEST(Move, PromotionToQueen) {
    Square from(4, 6); // e7
    Square to(4, 7);   // e8
    Move m(from, to, MoveKind::Promotion, PieceType::Queen);

    EXPECT_EQ(m.from(), from);
    EXPECT_EQ(m.to(), to);
    EXPECT_EQ(m.kind(), MoveKind::Promotion);
    ASSERT_TRUE(m.promotion().has_value());
    EXPECT_EQ(*m.promotion(), PieceType::Queen);
    EXPECT_EQ(m.toUci(), "e7e8q");
}

TEST(Move, PromotionOtherPieces) {
    Square from(0, 6); // a7
    Square to(0, 7);   // a8

    Move r(from, to, MoveKind::Promotion, PieceType::Rook);
    EXPECT_EQ(r.toUci(), "a7a8r");

    Move b(from, to, MoveKind::Promotion, PieceType::Bishop);
    EXPECT_EQ(b.toUci(), "a7a8b");

    Move n(from, to, MoveKind::Promotion, PieceType::Knight);
    EXPECT_EQ(n.toUci(), "a7a8n");
}

TEST(Move, Equality) {
    Square e2(4, 1);
    Square e4(4, 3);
    Square e3(4, 2);

    Move m1(e2, e4, MoveKind::DoublePawnPush);
    Move m2(e2, e4, MoveKind::DoublePawnPush);
    Move mDifferentTo(e2, e3, MoveKind::Normal);
    Move mDifferentKind(e2, e4, MoveKind::Normal);

    EXPECT_EQ(m1, m2);
    EXPECT_NE(m1, mDifferentTo);
    EXPECT_NE(m1, mDifferentKind);
}

TEST(Move, EqualityWithPromotion) {
    Square e7(4, 6);
    Square e8(4, 7);

    Move q1(e7, e8, MoveKind::Promotion, PieceType::Queen);
    Move q2(e7, e8, MoveKind::Promotion, PieceType::Queen);
    Move r(e7, e8, MoveKind::Promotion, PieceType::Rook);

    EXPECT_EQ(q1, q2);
    EXPECT_NE(q1, r);
}
