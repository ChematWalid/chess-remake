#include "chess/board/Position.h"
#include "chess/notation/Fen.h"
#include "chess/rules/GameRules.h"

#include <gtest/gtest.h>

using namespace chess;

TEST(GameRules, FoolsMateIsCheckmate) {
    // Fool's mate position: Black Queen delivers checkmate on h4
    std::string fen = "rnb1kbnr/pppp1ppp/4p3/8/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 3";
    Position pos = Fen::parse(fen);

    EXPECT_TRUE(GameRules::isCheckmate(pos));
    EXPECT_FALSE(GameRules::isStalemate(pos));
}

TEST(GameRules, StalematePosition) {
    // Black King on h8 has no legal moves, but is NOT in check
    std::string fen = "7k/5Q2/8/8/8/8/8/7K b - - 0 1";
    Position pos = Fen::parse(fen);

    EXPECT_FALSE(GameRules::isCheckmate(pos));
    EXPECT_TRUE(GameRules::isStalemate(pos));
}

TEST(GameRules, FiftyMoveDrawThreshold) {
    std::string fen99 = "8/8/8/4k3/8/8/8/4K3 w - - 99 50";
    EXPECT_FALSE(GameRules::isFiftyMoveDraw(Fen::parse(fen99)));

    std::string fen100 = "8/8/8/4k3/8/8/8/4K3 w - - 100 51";
    EXPECT_TRUE(GameRules::isFiftyMoveDraw(Fen::parse(fen100)));
}

TEST(GameRules, InsufficientMaterialKingVsKing) {
    std::string fen = "8/8/8/4k3/8/8/8/4K3 w - - 0 1";
    EXPECT_TRUE(GameRules::hasInsufficientMaterial(Fen::parse(fen).board()));
}

TEST(GameRules, InsufficientMaterialKingAndMinorVsKing) {
    // King + Knight vs King
    std::string fenKnight = "8/8/8/4k3/8/8/5N2/4K3 w - - 0 1";
    EXPECT_TRUE(GameRules::hasInsufficientMaterial(Fen::parse(fenKnight).board()));

    // King + Bishop vs King
    std::string fenBishop = "8/8/8/4k3/8/8/5B2/4K3 w - - 0 1";
    EXPECT_TRUE(GameRules::hasInsufficientMaterial(Fen::parse(fenBishop).board()));
}

TEST(GameRules, InsufficientMaterialSameColorBishops) {
    // Both bishops on light squares: c1 (2,0=2) and e3 (4,2=6)
    std::string fen = "8/8/8/4k3/8/4b3/8/2B1K3 w - - 0 1";
    EXPECT_TRUE(GameRules::hasInsufficientMaterial(Fen::parse(fen).board()));
}

TEST(GameRules, SufficientMaterialWhenPawnsExist) {
    std::string fen = "8/8/8/4k3/8/8/4P3/4K3 w - - 0 1";
    EXPECT_FALSE(GameRules::hasInsufficientMaterial(Fen::parse(fen).board()));
}

TEST(GameRules, ThreefoldRepetition) {
    Position pos1 = Fen::standard();
    Position pos2 = pos1.after(Move(Square::fromString("g1"), Square::fromString("f3"), MoveKind::Normal));
    Position pos3 = pos2.after(Move(Square::fromString("g8"), Square::fromString("f6"), MoveKind::Normal));

    std::vector<Position> history = {pos1, pos2, pos3, pos1, pos2, pos3, pos1};
    EXPECT_TRUE(GameRules::isThreefoldRepetition(history));

    std::vector<Position> historyTwo = {pos1, pos2, pos3, pos1};
    EXPECT_FALSE(GameRules::isThreefoldRepetition(historyTwo));
}

/**
 * Regression test for Item 1: En passant square ignored when no legal en passant capture exists.
 */
TEST(GameRules, ThreefoldRepetitionIgnoresEpWithoutLegalCapture) {
    Position pos = Fen::standard();
    std::vector<Position> history = {pos};
    std::vector<std::string> seq = {"e2e4",
                                    "g8f6", "b1c3", "f6g8", "c3b1",
                                    "g8f6", "b1c3", "f6g8", "c3b1"};
    for (const auto& m : seq) {
        Square from = Square::fromString(m.substr(0, 2));
        Square to = Square::fromString(m.substr(2, 2));
        pos = pos.after(Move(from, to, MoveKind::Normal));
        history.push_back(pos);
    }
    EXPECT_TRUE(GameRules::isThreefoldRepetition(history));
}
