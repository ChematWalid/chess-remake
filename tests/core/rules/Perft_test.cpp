#include "chess/board/Position.h"
#include "chess/notation/Fen.h"
#include "chess/rules/MoveGenerator.h"

#include <gtest/gtest.h>
#include <cstdint>

using namespace chess;

namespace {

uint64_t perft(const Position& position, int depth) {
    if (depth == 0) {
        return 1;
    }

    auto moves = MoveGenerator::legalMoves(position);
    if (depth == 1) {
        return moves.size();
    }

    uint64_t nodes = 0;
    for (const auto& move : moves) {
        nodes += perft(position.after(move), depth - 1);
    }
    return nodes;
}

}

TEST(Perft, StartingPositionDepth1) {
    Position pos = Fen::standard();
    EXPECT_EQ(perft(pos, 1), 20ULL);
}

TEST(Perft, StartingPositionDepth2) {
    Position pos = Fen::standard();
    EXPECT_EQ(perft(pos, 2), 400ULL);
}

TEST(Perft, StartingPositionDepth3) {
    Position pos = Fen::standard();
    EXPECT_EQ(perft(pos, 3), 8902ULL);
}

TEST(Perft, StartingPositionDepth4) {
    Position pos = Fen::standard();
    EXPECT_EQ(perft(pos, 4), 197281ULL);
}

/** @brief Kiwipete position - famous test position designed by Peter McKenzie to stress-test */
/** @brief all edge cases: pins, en passant, castling through check, promotions. */
TEST(Perft, KiwipetePositionDepth1) {
    /** @brief "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1" */
    std::string fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    Position pos = Fen::parse(fen);
    EXPECT_EQ(perft(pos, 1), 48ULL);
}

TEST(Perft, KiwipetePositionDepth2) {
    std::string fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    Position pos = Fen::parse(fen);
    EXPECT_EQ(perft(pos, 2), 2039ULL);
}
