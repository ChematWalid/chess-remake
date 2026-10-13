#include "chess/board/CastlingRights.h"
#include "chess/board/Types.h"

#include <gtest/gtest.h>
#include <stdexcept>

using namespace chess;

TEST(CastlingRights, DefaultIsNone) {
    CastlingRights cr;
    EXPECT_FALSE(cr.has(Color::White, Side::KingSide));
    EXPECT_FALSE(cr.has(Color::White, Side::QueenSide));
    EXPECT_FALSE(cr.has(Color::Black, Side::KingSide));
    EXPECT_FALSE(cr.has(Color::Black, Side::QueenSide));
    EXPECT_EQ(cr.toString(), "-");
}

TEST(CastlingRights, AllRights) {
    CastlingRights cr = CastlingRights::all();
    EXPECT_TRUE(cr.has(Color::White, Side::KingSide));
    EXPECT_TRUE(cr.has(Color::White, Side::QueenSide));
    EXPECT_TRUE(cr.has(Color::Black, Side::KingSide));
    EXPECT_TRUE(cr.has(Color::Black, Side::QueenSide));
    EXPECT_EQ(cr.toString(), "KQkq");
}

TEST(CastlingRights, RemoveIndividualRight) {
    CastlingRights cr = CastlingRights::all();

    cr.remove(Color::White, Side::KingSide);
    EXPECT_FALSE(cr.has(Color::White, Side::KingSide));
    EXPECT_TRUE(cr.has(Color::White, Side::QueenSide));
    EXPECT_EQ(cr.toString(), "Qkq");

    cr.remove(Color::Black, Side::QueenSide);
    EXPECT_FALSE(cr.has(Color::Black, Side::QueenSide));
    EXPECT_EQ(cr.toString(), "Qk");
}

TEST(CastlingRights, RemoveAllForColor) {
    CastlingRights cr = CastlingRights::all();

    cr.removeAll(Color::White);
    EXPECT_FALSE(cr.has(Color::White, Side::KingSide));
    EXPECT_FALSE(cr.has(Color::White, Side::QueenSide));
    EXPECT_TRUE(cr.has(Color::Black, Side::KingSide));
    EXPECT_TRUE(cr.has(Color::Black, Side::QueenSide));
    EXPECT_EQ(cr.toString(), "kq");

    cr.removeAll(Color::Black);
    EXPECT_EQ(cr.toString(), "-");
    EXPECT_EQ(cr, CastlingRights::none());
}

TEST(CastlingRights, FromStringValid) {
    EXPECT_EQ(CastlingRights::fromString("-"), CastlingRights::none());
    EXPECT_EQ(CastlingRights::fromString("KQkq"), CastlingRights::all());

    CastlingRights custom = CastlingRights::fromString("Kq");
    EXPECT_TRUE(custom.has(Color::White, Side::KingSide));
    EXPECT_FALSE(custom.has(Color::White, Side::QueenSide));
    EXPECT_FALSE(custom.has(Color::Black, Side::KingSide));
    EXPECT_TRUE(custom.has(Color::Black, Side::QueenSide));
    EXPECT_EQ(custom.toString(), "Kq");
}

TEST(CastlingRights, FromStringInvalid) {
    EXPECT_THROW(CastlingRights::fromString("X"), std::invalid_argument);
    EXPECT_THROW(CastlingRights::fromString("Kx"), std::invalid_argument);
}

TEST(CastlingRights, Equality) {
    CastlingRights a = CastlingRights::all();
    CastlingRights b = CastlingRights::all();
    EXPECT_EQ(a, b);

    a.remove(Color::White, Side::KingSide);
    EXPECT_NE(a, b);
}
