#include "chess/notation/Fen.h"
#include "chess/notation/SanNotation.h"
#include "chess/notation/UciNotation.h"
#include "chess/rules/MoveGenerator.h"

#include <chrono>
#include <cstdint>
#include <iostream>
#include <string>
#include <vector>

using namespace chess;

namespace {

uint64_t perft(const Position& pos, int depth) {
    auto moves = MoveGenerator::legalMoves(pos);
    if (depth == 1) {
        return moves.size();
    }
    uint64_t nodes = 0;
    for (const auto& m : moves) {
        nodes += perft(pos.after(m), depth - 1);
    }
    return nodes;
}

int sanRoundTripFailures = 0;
int sanChecked = 0;
int uciFailures = 0;

void checkNotation(const Position& pos, int depth) {
    SanNotation san;
    UciNotation uci;
    auto moves = MoveGenerator::legalMoves(pos);
    for (const auto& m : moves) {
        ++sanChecked;
        std::string s = san.serialize(pos, m);
        auto parsed = san.parse(pos, s);
        if (!parsed || !(*parsed == m)) {
            ++sanRoundTripFailures;
            if (sanRoundTripFailures <= 10) {
                std::cout << "  SAN round-trip FAIL: " << Fen::serialize(pos) << " move " << m.toUci()
                          << " san=" << s << "\n";
            }
        }
        auto u = uci.parse(pos, m.toUci());
        if (!u || !(*u == m)) {
            ++uciFailures;
        }
        if (depth > 1) {
            checkNotation(pos.after(m), depth - 1);
        }
    }
}

struct Case {
    std::string name;
    std::string fen;
    std::vector<uint64_t> expected;
};

}

int main(int argc, char** argv) {
    int maxDepth = (argc > 1) ? std::stoi(argv[1]) : 4;

    std::vector<Case> cases = {
        {"startpos", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
         {20, 400, 8902, 197281, 4865609}},
        {"kiwipete", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
         {48, 2039, 97862, 4085603}},
        {"pos3", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
         {14, 191, 2812, 43238, 674624}},
        {"pos4", "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
         {6, 264, 9467, 422333}},
        {"pos5", "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
         {44, 1486, 62379, 2103487}},
        {"pos6", "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
         {46, 2079, 89890, 3894594}},
    };

    int failures = 0;
    for (const auto& c : cases) {
        Position pos = Fen::parse(c.fen);
        std::cout << c.name << "\n";
        for (int d = 1; d <= maxDepth && d <= static_cast<int>(c.expected.size()); ++d) {
            auto t0 = std::chrono::steady_clock::now();
            uint64_t got = perft(pos, d);
            auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                          std::chrono::steady_clock::now() - t0).count();
            bool ok = (got == c.expected[d - 1]);
            if (!ok) ++failures;
            std::cout << "  depth " << d << ": got " << got << " expected " << c.expected[d - 1]
                      << (ok ? "  OK" : "  MISMATCH") << "  (" << ms << " ms)\n";
        }
        checkNotation(pos, 2);
    }

    std::cout << "\nperft mismatches: " << failures << "\n";
    std::cout << "SAN/UCI checked moves: " << sanChecked
              << "  SAN round-trip failures: " << sanRoundTripFailures
              << "  UCI failures: " << uciFailures << "\n";
    return failures == 0 ? 0 : 1;
}
