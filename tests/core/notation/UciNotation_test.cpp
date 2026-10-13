#include "chess/board/Square.h"
#include "chess/notation/Fen.h"
#include "chess/notation/UciNotation.h"

#include <gtest/gtest.h>

using namespace chess;

TEST(UciNotation, SerializeStandardMoves) {
    UciNotation uci;
    Position start = Fen::standard();

    Move m1(Square::fromString("e2"), Square::fromString("e4"), MoveKind::DoublePawnPush);
    EXPECT_EQ(uci.serialize(start, m1), "e2e4");

    Move promo(Square::fromString("e7"), Square::fromString("e8"), MoveKind::Promotion, PieceType::Queen);
    EXPECT_EQ(uci.serialize(start, promo), "e7e8q");

    Move castle(Square::fromString("e1"), Square::fromString("g1"), MoveKind::Castle);
    EXPECT_EQ(uci.serialize(start, castle), "e1g1");
}

TEST(UciNotation, ParseValidLegalMove) {
    UciNotation uci;
    Position start = Fen::standard();

    auto moveOpt = uci.parse(start, "e2e4");
    ASSERT_TRUE(moveOpt.has_value());
    EXPECT_EQ(moveOpt->from(), Square::fromString("e2"));
    EXPECT_EQ(moveOpt->to(), Square::fromString("e4"));
    EXPECT_EQ(moveOpt->kind(), MoveKind::DoublePawnPush);
}

TEST(UciNotation, ParsePromotionMove) {
    UciNotation uci;
    Position pos = Fen::parse("k7/4P3/8/8/8/8/8/4K3 w - - 0 1");

    auto promoOpt = uci.parse(pos, "e7e8q");
    ASSERT_TRUE(promoOpt.has_value());
    EXPECT_EQ(promoOpt->kind(), MoveKind::Promotion);
    EXPECT_EQ(promoOpt->promotion(), PieceType::Queen);
}

TEST(UciNotation, ParseIllegalOrInvalidMoves) {
    UciNotation uci;
    Position start = Fen::standard();

    // Illegal piece movement
    EXPECT_EQ(uci.parse(start, "e1e8"), std::nullopt);

    // Bad syntax
    EXPECT_EQ(uci.parse(start, "e2"), std::nullopt);
    EXPECT_EQ(uci.parse(start, "e2e4qq"), std::nullopt);
    EXPECT_EQ(uci.parse(start, "zzzz"), std::nullopt);
    EXPECT_EQ(uci.parse(start, "e7e8k"), std::nullopt); // illegal promotion to King
}

TEST(UciNotation, PolymorphicUsageThroughBasePointer) {
    std::unique_ptr<MoveNotation> notation = std::make_unique<UciNotation>();
    Position start = Fen::standard();

    auto m = notation->parse(start, "g1f3");
    ASSERT_TRUE(m.has_value());
    EXPECT_EQ(notation->serialize(start, *m), "g1f3");
}
