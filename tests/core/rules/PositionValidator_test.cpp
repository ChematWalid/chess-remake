#include "chess/notation/Fen.h"
#include "chess/notation/SanNotation.h"
#include "chess/rules/MoveGenerator.h"
#include "chess/rules/PositionValidator.h"
#include <gtest/gtest.h>

using namespace chess;

TEST(PositionValidator, AcceptsPlayableStandardPosition) {
    EXPECT_TRUE(PositionValidator::isPlayable(Fen::standard()));
}

TEST(PositionValidator, RejectsPositionWithoutBothKings) {
    const auto position = Fen::parse("8/8/8/8/8/8/4K3/8 w - - 0 1");
    EXPECT_FALSE(PositionValidator::isPlayable(position));
}

TEST(PositionValidator, ConfirmsLegalSetupReplayMatchesFinalFen) {
    const auto start = Fen::standard();
    const auto move = MoveGenerator::resolve(start, Square(4, 1), Square(4, 3));
    ASSERT_TRUE(move.has_value());
    SanNotation san;
    const auto notation = san.serialize(start, *move);
    const auto finalPosition = start.after(*move);
    const std::vector<std::string> fens{Fen::serialize(start), Fen::serialize(finalPosition)};
    EXPECT_TRUE(PositionValidator::replayMatches({notation}, fens, Fen::serialize(finalPosition)));
    EXPECT_FALSE(PositionValidator::replayMatches({notation}, fens, Fen::serialize(start)));
}
