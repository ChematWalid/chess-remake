#include "chess/board/Square.h"
#include "chess/notation/Fen.h"
#include "chess/notation/SanNotation.h"
#include "chess/rules/MoveGenerator.h"

#include <gtest/gtest.h>

using namespace chess;

TEST(SanNotation, PawnPushesAndCaptures) {
    SanNotation san;
    Position start = Fen::standard();

    // 1. e4
    auto e4 = MoveGenerator::resolve(start, Square::fromString("e2"), Square::fromString("e4"));
    ASSERT_TRUE(e4.has_value());
    EXPECT_EQ(san.serialize(start, *e4), "e4");

    // 1... d5
    Position afterE4 = start.after(*e4);
    auto d5 = MoveGenerator::resolve(afterE4, Square::fromString("d7"), Square::fromString("d5"));
    ASSERT_TRUE(d5.has_value());
    EXPECT_EQ(san.serialize(afterE4, *d5), "d5");

    // 2. exd5
    Position afterD5 = afterE4.after(*d5);
    auto exd5 = MoveGenerator::resolve(afterD5, Square::fromString("e4"), Square::fromString("d5"));
    ASSERT_TRUE(exd5.has_value());
    EXPECT_EQ(san.serialize(afterD5, *exd5), "exd5");
}

TEST(SanNotation, PieceMovesAndCaptures) {
    SanNotation san;
    Position start = Fen::standard();

    // 1. Nf3
    auto nf3 = MoveGenerator::resolve(start, Square::fromString("g1"), Square::fromString("f3"));
    ASSERT_TRUE(nf3.has_value());
    EXPECT_EQ(san.serialize(start, *nf3), "Nf3");

    // Position with capture: White Queen takes black Pawn on d5
    Position pos = Fen::parse("rnbqkbnr/ppp1pppp/8/3p4/4Q3/8/PPPPPPPP/RNB1KBNR w KQkq - 0 2");
    auto qxd5 = MoveGenerator::resolve(pos, Square::fromString("e4"), Square::fromString("d5"));
    ASSERT_TRUE(qxd5.has_value());
    EXPECT_EQ(san.serialize(pos, *qxd5), "Qxd5");
}

TEST(SanNotation, CastlingMoves) {
    SanNotation san;
    // White can castle kingside and queenside
    Position pos = Fen::parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");

    auto castleK = MoveGenerator::resolve(pos, Square::fromString("e1"), Square::fromString("g1"));
    ASSERT_TRUE(castleK.has_value());
    EXPECT_EQ(san.serialize(pos, *castleK), "O-O");

    auto castleQ = MoveGenerator::resolve(pos, Square::fromString("e1"), Square::fromString("c1"));
    ASSERT_TRUE(castleQ.has_value());
    EXPECT_EQ(san.serialize(pos, *castleQ), "O-O-O");
}

TEST(SanNotation, PromotionNotation) {
    SanNotation san;
    Position pos = Fen::parse("k7/4P3/8/8/8/8/8/4K3 w - - 0 1");

    auto promoQ = MoveGenerator::resolve(pos, Square::fromString("e7"), Square::fromString("e8"), PieceType::Queen);
    ASSERT_TRUE(promoQ.has_value());
    EXPECT_EQ(san.serialize(pos, *promoQ), "e8=Q+");

    auto promoN = MoveGenerator::resolve(pos, Square::fromString("e7"), Square::fromString("e8"), PieceType::Knight);
    ASSERT_TRUE(promoN.has_value());
    EXPECT_EQ(san.serialize(pos, *promoN), "e8=N");
}

TEST(SanNotation, CheckAndCheckmateSuffixes) {
    SanNotation san;
    // Fool's mate setup: 1. f3 e5 2. g4 Qh4#
    Position pos = Fen::parse("rnbqkbnr/pppp1ppp/4p3/8/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2");
    auto qh4 = MoveGenerator::resolve(pos, Square::fromString("d8"), Square::fromString("h4"));
    ASSERT_TRUE(qh4.has_value());
    EXPECT_EQ(san.serialize(pos, *qh4), "Qh4#");

    // Normal check: Scholar's mate delivery before mate or bishop check
    Position checkPos = Fen::parse("rnbqkbnr/pppp1ppp/8/4p3/2B1P3/8/PPPP1PPP/RNBQK1NR w KQkq - 0 2");
    auto bxf7 = MoveGenerator::resolve(checkPos, Square::fromString("c4"), Square::fromString("f7"));
    ASSERT_TRUE(bxf7.has_value());
    EXPECT_EQ(san.serialize(checkPos, *bxf7), "Bxf7+");
}

TEST(SanNotation, DisambiguationByFileAndRank) {
    SanNotation san;

    // Two Rooks on a1 and f1 with unobstructed rank 1 (King on e2), both can move to d1 -> Rad1, Rfd1
    Position filePos = Fen::parse("4k3/8/8/8/8/8/4K3/R4R2 w - - 0 1");
    auto rad1 = MoveGenerator::resolve(filePos, Square::fromString("a1"), Square::fromString("d1"));
    ASSERT_TRUE(rad1.has_value());
    EXPECT_EQ(san.serialize(filePos, *rad1), "Rad1");

    auto rfd1 = MoveGenerator::resolve(filePos, Square::fromString("f1"), Square::fromString("d1"));
    ASSERT_TRUE(rfd1.has_value());
    EXPECT_EQ(san.serialize(filePos, *rfd1), "Rfd1");

    // Two Rooks on a1 and a8, both can move to a4 -> R1a4, R8a4
    Position rankPos = Fen::parse("R7/6k1/8/8/8/8/4K3/R7 w - - 0 1");
    auto r1a4 = MoveGenerator::resolve(rankPos, Square::fromString("a1"), Square::fromString("a4"));
    ASSERT_TRUE(r1a4.has_value());
    EXPECT_EQ(san.serialize(rankPos, *r1a4), "R1a4");

    auto r8a4 = MoveGenerator::resolve(rankPos, Square::fromString("a8"), Square::fromString("a4"));
    ASSERT_TRUE(r8a4.has_value());
    EXPECT_EQ(san.serialize(rankPos, *r8a4), "R8a4");
}

TEST(SanNotation, ParseSanStrings) {
    SanNotation san;
    Position start = Fen::standard();

    auto e4 = san.parse(start, "e4");
    ASSERT_TRUE(e4.has_value());
    EXPECT_EQ(e4->from(), Square::fromString("e2"));
    EXPECT_EQ(e4->to(), Square::fromString("e4"));

    auto nf3 = san.parse(start, "Nf3");
    ASSERT_TRUE(nf3.has_value());
    EXPECT_EQ(nf3->from(), Square::fromString("g1"));
    EXPECT_EQ(nf3->to(), Square::fromString("f3"));

    // Castling with 'O-O' and '0-0'
    Position castlePos = Fen::parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
    auto castle1 = san.parse(castlePos, "O-O");
    ASSERT_TRUE(castle1.has_value());
    EXPECT_EQ(castle1->kind(), MoveKind::Castle);

    auto castle2 = san.parse(castlePos, "0-0");
    ASSERT_TRUE(castle2.has_value());
    EXPECT_EQ(castle2->kind(), MoveKind::Castle);

    // Invalid string
    EXPECT_EQ(san.parse(start, "Nxd5"), std::nullopt);
    EXPECT_EQ(san.parse(start, "invalid"), std::nullopt);
}

TEST(SanNotation, PolymorphicBaseUsage) {
    std::unique_ptr<MoveNotation> notation = std::make_unique<SanNotation>();
    Position start = Fen::standard();

    auto m = notation->parse(start, "e4");
    ASSERT_TRUE(m.has_value());
    EXPECT_EQ(notation->serialize(start, *m), "e4");
}
