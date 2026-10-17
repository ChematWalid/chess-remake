#include "chess/board/Square.h"

#include <gtest/gtest.h>
#include <stdexcept>

using namespace chess;

/** @name Constructor */

TEST(Square, ConstructorValidInput) {
    Square s(4, 3);
    EXPECT_EQ(s.file(), 4);
    EXPECT_EQ(s.rank(), 3);
    EXPECT_EQ(s.index(), 28);
}

TEST(Square, ConstructorCorners) {
    EXPECT_NO_THROW(Square(0, 0));
    EXPECT_NO_THROW(Square(7, 7));
    EXPECT_NO_THROW(Square(0, 7));
    EXPECT_NO_THROW(Square(7, 0));
}

TEST(Square, ConstructorInvalidFile) {
    EXPECT_THROW(Square(-1, 0), std::out_of_range);
    EXPECT_THROW(Square(8, 0),  std::out_of_range);
}

TEST(Square, ConstructorInvalidRank) {
    EXPECT_THROW(Square(0, -1), std::out_of_range);
    EXPECT_THROW(Square(0, 8),  std::out_of_range);
}

/** @name toString */

TEST(Square, ToStringCenter) {
    EXPECT_EQ(Square(4, 3).toString(), "e4");
}

TEST(Square, ToStringA1) {
    EXPECT_EQ(Square(0, 0).toString(), "a1");
}

TEST(Square, ToStringH8) {
    EXPECT_EQ(Square(7, 7).toString(), "h8");
}

/** @name fromString */

TEST(Square, FromStringValid) {
    Square s = Square::fromString("e4");
    EXPECT_EQ(s.file(), 4);
    EXPECT_EQ(s.rank(), 3);
}

TEST(Square, FromStringA1) {
    Square s = Square::fromString("a1");
    EXPECT_EQ(s.file(), 0);
    EXPECT_EQ(s.rank(), 0);
}

TEST(Square, FromStringH8) {
    Square s = Square::fromString("h8");
    EXPECT_EQ(s.file(), 7);
    EXPECT_EQ(s.rank(), 7);
}

TEST(Square, FromStringInvalidSquare) {
    EXPECT_THROW(Square::fromString("z9"), std::invalid_argument);
    EXPECT_THROW(Square::fromString("a0"), std::invalid_argument);
    EXPECT_THROW(Square::fromString("a9"), std::invalid_argument);
}

TEST(Square, FromStringInvalidLength) {
    EXPECT_THROW(Square::fromString("e"),   std::invalid_argument);
    EXPECT_THROW(Square::fromString("e44"), std::invalid_argument);
    EXPECT_THROW(Square::fromString(""),    std::invalid_argument);
}

/** @name tryOffset */

TEST(Square, TryOffsetValid) {
    auto result = Square::tryOffset(Square(4, 3), 1, 1);
    ASSERT_TRUE(result.has_value());
    EXPECT_EQ(result->toString(), "f5");
}

TEST(Square, TryOffsetOffBoardRight) {
    auto result = Square::tryOffset(Square(7, 7), 1, 0);
    EXPECT_FALSE(result.has_value());
}

TEST(Square, TryOffsetOffBoardLeft) {
    auto result = Square::tryOffset(Square(0, 0), -1, 0);
    EXPECT_FALSE(result.has_value());
}

TEST(Square, TryOffsetOffBoardTop) {
    auto result = Square::tryOffset(Square(0, 7), 0, 1);
    EXPECT_FALSE(result.has_value());
}

TEST(Square, TryOffsetOffBoardBottom) {
    auto result = Square::tryOffset(Square(0, 0), 0, -1);
    EXPECT_FALSE(result.has_value());
}

TEST(Square, TryOffsetZero) {
    auto result = Square::tryOffset(Square(4, 3), 0, 0);
    ASSERT_TRUE(result.has_value());
    EXPECT_EQ(result->toString(), "e4");
}

/** @name operator== */

TEST(Square, EqualityTrue) {
    EXPECT_EQ(Square(4, 3), Square(4, 3));
}

TEST(Square, EqualityFalse) {
    EXPECT_NE(Square(4, 3), Square(4, 4));
    EXPECT_NE(Square(4, 3), Square(5, 3));
}

TEST(Square, RoundTrip) {
    /** @brief fromString → toString should give back the original string */
    EXPECT_EQ(Square::fromString("e4").toString(), "e4");
    EXPECT_EQ(Square::fromString("a1").toString(), "a1");
    EXPECT_EQ(Square::fromString("h8").toString(), "h8");
}
