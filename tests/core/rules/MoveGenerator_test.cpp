#include "chess/board/Square.h"
#include "chess/board/Types.h"
#include "chess/notation/Fen.h"
#include "chess/rules/MoveGenerator.h"

#include <gtest/gtest.h>

using namespace chess;

TEST(MoveGenerator, OpeningPositionMoves) {
    Position pos = Fen::standard();
    auto moves = MoveGenerator::legalMoves(pos);

    /** @brief Standard chess opening has exactly 20 legal moves: */
    /** @brief 16 pawn moves (8 pawns x 2) + 4 knight moves (2 knights x 2) */
    EXPECT_EQ(moves.size(), 20);

    /** @brief Check specific knight moves */
    Square b1 = Square::fromString("b1");
    auto knightMoves = MoveGenerator::legalMovesFrom(pos, b1);
    EXPECT_EQ(knightMoves.size(), 2);
}

TEST(MoveGenerator, PinnedPieceCannotMove) {
    /** @brief White King on e1, White Bishop on e2, Black Rook on e8 (pinning the Bishop) */
    std::string fen = "4r3/8/8/8/8/8/4B3/4K3 w - - 0 1";
    Position pos = Fen::parse(fen);

    Square e2 = Square::fromString("e2");
    auto bishopMoves = MoveGenerator::legalMovesFrom(pos, e2);

    /** @brief Bishop cannot move off the e-file because doing so exposes King to check */
    EXPECT_EQ(bishopMoves.size(), 0);
}

TEST(MoveGenerator, CheckEvasionRequired) {
    /** @brief White King on e1 in check by Black Queen on e2, with no way to capture or block */
    /** @brief King can only escape to d1 or f1 */
    std::string fen = "4k3/8/8/8/8/8/4q3/4K3 w - - 0 1";
    Position pos = Fen::parse(fen);

    EXPECT_TRUE(pos.isInCheck(Color::White));

    auto legal = MoveGenerator::legalMoves(pos);
    for (const auto& m : legal) {
        Position after = pos.after(m);
        EXPECT_FALSE(after.isInCheck(Color::White));
    }
}

TEST(MoveGenerator, ResolveMoveIntent) {
    Position pos = Fen::standard();
    Square e2 = Square::fromString("e2");
    Square e4 = Square::fromString("e4");
    Square e5 = Square::fromString("e5");

    auto resolved = MoveGenerator::resolve(pos, e2, e4);
    ASSERT_TRUE(resolved.has_value());
    EXPECT_EQ(resolved->kind(), MoveKind::DoublePawnPush);
    EXPECT_EQ(resolved->toUci(), "e2e4");

    /** @brief Illegal move: e2 to e5 */
    auto illegal = MoveGenerator::resolve(pos, e2, e5);
    EXPECT_FALSE(illegal.has_value());
}
