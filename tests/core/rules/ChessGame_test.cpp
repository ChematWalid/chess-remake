#include "chess/board/Square.h"
#include "chess/notation/Fen.h"
#include "chess/rules/ChessGame.h"

#include <gtest/gtest.h>

using namespace chess;

TEST(ChessGame, InitialStandardGame) {
    ChessGame game;

    EXPECT_FALSE(game.isOver());
    EXPECT_EQ(game.result().outcome, Outcome::Ongoing);
    EXPECT_EQ(game.result().reason, Reason::None);
    EXPECT_EQ(game.movesHistory().size(), 0);
    EXPECT_EQ(game.positionHistory().size(), 1);
    EXPECT_EQ(game.legalMoves().size(), 20);
    EXPECT_EQ(game.position().sideToMove(), Color::White);
    EXPECT_FALSE(game.pendingDrawOffer().has_value());
}

TEST(ChessGame, PlayLegalMovesAndTrackHistory) {
    ChessGame game;

    auto move1 = game.play(Color::White, Square::fromString("e2"), Square::fromString("e4"));
    ASSERT_TRUE(move1.has_value());
    EXPECT_EQ(move1->from(), Square::fromString("e2"));
    EXPECT_EQ(move1->to(), Square::fromString("e4"));
    EXPECT_EQ(game.position().sideToMove(), Color::Black);
    EXPECT_EQ(game.movesHistory().size(), 1);
    EXPECT_EQ(game.positionHistory().size(), 2);

    auto move2 = game.play(Color::Black, Square::fromString("e7"), Square::fromString("e5"));
    ASSERT_TRUE(move2.has_value());
    EXPECT_EQ(game.position().sideToMove(), Color::White);
    EXPECT_EQ(game.movesHistory().size(), 2);
    EXPECT_EQ(game.positionHistory().size(), 3);
}

TEST(ChessGame, PlayOutOfTurnReturnsNotYourTurn) {
    ChessGame game;

    // Black attempts to move first
    auto result = game.play(Color::Black, Square::fromString("e7"), Square::fromString("e5"));
    ASSERT_FALSE(result.has_value());
    EXPECT_EQ(result.error(), MoveError::NotYourTurn);

    // Position and history remain untouched
    EXPECT_EQ(game.movesHistory().size(), 0);
    EXPECT_EQ(game.positionHistory().size(), 1);
    EXPECT_EQ(game.position().sideToMove(), Color::White);
}

TEST(ChessGame, PlayIllegalMoveReturnsIllegalMove) {
    ChessGame game;

    // White Queen cannot jump over pawns
    auto result = game.play(Color::White, Square::fromString("d1"), Square::fromString("d5"));
    ASSERT_FALSE(result.has_value());
    EXPECT_EQ(result.error(), MoveError::IllegalMove);

    // State and history remain untouched
    EXPECT_EQ(game.movesHistory().size(), 0);
    EXPECT_EQ(game.positionHistory().size(), 1);
    EXPECT_EQ(game.position().sideToMove(), Color::White);
}

TEST(ChessGame, FoolsMateResultsInBlackWinCheckmate) {
    ChessGame game;

    // 1. f3 e5
    ASSERT_TRUE(game.play(Color::White, Square::fromString("f2"), Square::fromString("f3")).has_value());
    ASSERT_TRUE(game.play(Color::Black, Square::fromString("e7"), Square::fromString("e5")).has_value());

    // 2. g4 Qh4#
    ASSERT_TRUE(game.play(Color::White, Square::fromString("g2"), Square::fromString("g4")).has_value());
    ASSERT_TRUE(game.play(Color::Black, Square::fromString("d8"), Square::fromString("h4")).has_value());

    EXPECT_TRUE(game.isOver());
    EXPECT_EQ(game.result().outcome, Outcome::BlackWins);
    EXPECT_EQ(game.result().reason, Reason::Checkmate);
    EXPECT_TRUE(game.legalMoves().empty());

    // Attempting to move after game over returns GameOver error
    auto postMove = game.play(Color::White, Square::fromString("a2"), Square::fromString("a3"));
    ASSERT_FALSE(postMove.has_value());
    EXPECT_EQ(postMove.error(), MoveError::GameOver);
}

TEST(ChessGame, ResignationEndsGame) {
    ChessGame game;

    game.resign(Color::White);

    EXPECT_TRUE(game.isOver());
    EXPECT_EQ(game.result().outcome, Outcome::BlackWins);
    EXPECT_EQ(game.result().reason, Reason::Resignation);
    EXPECT_TRUE(game.legalMoves().empty());
}

TEST(ChessGame, MutualDrawAgreementProtocol) {
    ChessGame game;

    // Cannot accept without an offer
    EXPECT_FALSE(game.acceptDraw(Color::Black));
    EXPECT_FALSE(game.isOver());

    // White offers draw
    game.offerDraw(Color::White);
    EXPECT_EQ(game.pendingDrawOffer(), Color::White);

    // White cannot accept their own draw offer
    EXPECT_FALSE(game.acceptDraw(Color::White));
    EXPECT_FALSE(game.isOver());

    // Black accepts White's offer -> Draw agreed
    EXPECT_TRUE(game.acceptDraw(Color::Black));
    EXPECT_TRUE(game.isOver());
    EXPECT_EQ(game.result().outcome, Outcome::Draw);
    EXPECT_EQ(game.result().reason, Reason::DrawAgreement);
    EXPECT_TRUE(game.legalMoves().empty());
}

TEST(ChessGame, MoveCancelsPendingDrawOffer) {
    ChessGame game;

    game.offerDraw(Color::White);
    EXPECT_EQ(game.pendingDrawOffer(), Color::White);

    // White makes a move instead -> draw offer expires
    ASSERT_TRUE(game.play(Color::White, Square::fromString("e2"), Square::fromString("e4")).has_value());
    EXPECT_FALSE(game.pendingDrawOffer().has_value());

    // Black can no longer accept it
    EXPECT_FALSE(game.acceptDraw(Color::Black));
    EXPECT_FALSE(game.isOver());
}

TEST(ChessGame, TimeoutEndsGameWithNormalWinner) {
    ChessGame game;

    game.declareTimeout(Color::Black);

    EXPECT_TRUE(game.isOver());
    EXPECT_EQ(game.result().outcome, Outcome::WhiteWins);
    EXPECT_EQ(game.result().reason, Reason::Timeout);
    EXPECT_TRUE(game.legalMoves().empty());
}

TEST(ChessGame, TimeoutWithInsufficientMatingMaterialIsDraw) {
    // White has a Queen + King, Black has ONLY a King
    // If White runs out of time, Black has insufficient material to mate -> Draw!
    Position pos = Fen::parse("4k3/8/8/8/8/8/4Q3/4K3 w - - 0 1");
    ChessGame game(pos);

    game.declareTimeout(Color::White);

    EXPECT_TRUE(game.isOver());
    EXPECT_EQ(game.result().outcome, Outcome::Draw);
    EXPECT_EQ(game.result().reason, Reason::Timeout);
}

TEST(ChessGame, CustomPositionWithImmediateStalemate) {
    Position stale = Fen::parse("7k/5Q2/8/8/8/8/8/7K b - - 0 1");
    ChessGame game(stale);

    EXPECT_TRUE(game.isOver());
    EXPECT_EQ(game.result().outcome, Outcome::Draw);
    EXPECT_EQ(game.result().reason, Reason::Stalemate);
    EXPECT_TRUE(game.legalMoves().empty());
}

TEST(ChessGame, ThreefoldRepetitionInRealPlay) {
    ChessGame game;
    // 1. Nf3 Nf6
    ASSERT_TRUE(game.play(Color::White, Square::fromString("g1"), Square::fromString("f3")).has_value());
    ASSERT_TRUE(game.play(Color::Black, Square::fromString("g8"), Square::fromString("f6")).has_value());
    // 2. Ng1 Ng8 (Initial position reached 2nd time)
    ASSERT_TRUE(game.play(Color::White, Square::fromString("f3"), Square::fromString("g1")).has_value());
    ASSERT_TRUE(game.play(Color::Black, Square::fromString("f6"), Square::fromString("g8")).has_value());
    EXPECT_FALSE(game.isOver());

    // 3. Nf3 Nf6
    ASSERT_TRUE(game.play(Color::White, Square::fromString("g1"), Square::fromString("f3")).has_value());
    ASSERT_TRUE(game.play(Color::Black, Square::fromString("g8"), Square::fromString("f6")).has_value());
    EXPECT_FALSE(game.isOver());

    // 4. Ng1 Ng8 (Initial position reached 3rd time -> Threefold Repetition Draw!)
    ASSERT_TRUE(game.play(Color::White, Square::fromString("f3"), Square::fromString("g1")).has_value());
    ASSERT_TRUE(game.play(Color::Black, Square::fromString("f6"), Square::fromString("g8")).has_value());

    EXPECT_TRUE(game.isOver());
    EXPECT_EQ(game.result().outcome, Outcome::Draw);
    EXPECT_EQ(game.result().reason, Reason::ThreefoldRepetition);
}
