#include <gtest/gtest.h>
#include "chess/notation/Fen.h"
#include "chess/notation/PgnReader.h"
#include "chess/notation/SanNotation.h"
#include "chess/rules/ChessGame.h"
#include "net/RoomManager.h"
#include <chrono>
#include <filesystem>
#include <iostream>
#include <string>
#include <vector>

namespace {

/**
 * @brief Resolves path to a benchmark PGN file in test data directories.
 */
std::filesystem::path getGamePath(const std::string& filename) {
    std::vector<std::filesystem::path> candidates = {
        std::filesystem::path("tests/data/games") / filename,
        std::filesystem::path("../tests/data/games") / filename,
        std::filesystem::path("../../tests/data/games") / filename
    };
    for (const auto& path : candidates) {
        if (std::filesystem::exists(path)) {
            return path;
        }
    }
    return std::filesystem::path("tests/data/games") / filename;
}

/**
 * @brief Metrics and verification results from replaying a PGN game.
 */
struct ReplayResult {
    bool allMovesLegal{false};
    size_t movesPlayed{0};
    chess::Outcome outcome{chess::Outcome::Ongoing};
    chess::Reason reason{chess::Reason::None};
    std::string finalFen;
    int64_t durationUs{0};
};

/**
 * @brief Replays all moves of a parsed PGN record through ChessGame.
 */
ReplayResult replayPgnGame(const chess::PgnGame& pgn) {
    ReplayResult res;
    chess::ChessGame game;
    chess::SanNotation sanParser;

    auto start = std::chrono::high_resolution_clock::now();

    for (const auto& moveStr : pgn.moves) {
        if (game.isOver()) {
            break;
        }

        chess::Color toMove = game.position().sideToMove();
        auto parsedMove = sanParser.parse(game.position(), moveStr);
        if (!parsedMove.has_value()) {
            std::cerr << "Failed to parse move '" << moveStr << "' at turn "
                      << res.movesPlayed + 1 << " (FEN: " << chess::Fen::serialize(game.position()) << ")\n";
            res.allMovesLegal = false;
            return res;
        }

        auto playResult = game.play(toMove, parsedMove->from(), parsedMove->to(), parsedMove->promotion());
        if (!playResult.has_value()) {
            std::cerr << "Illegal move rejected: " << moveStr << "\n";
            res.allMovesLegal = false;
            return res;
        }

        res.movesPlayed++;
    }

    auto end = std::chrono::high_resolution_clock::now();
    res.durationUs = std::chrono::duration_cast<std::chrono::microseconds>(end - start).count();

    res.allMovesLegal = (res.movesPlayed == pgn.moves.size());
    res.outcome = game.result().outcome;
    res.reason = game.result().reason;
    res.finalFen = chess::Fen::serialize(game.position());
    return res;
}

}

/**
 * @brief Carlsen vs Nepomniachtchi (2021 World Championship Game 6): 136 full moves (271 plies).
 */
TEST(PgnFileBenchmark, CarlsenVsNepomniachtchi2021_Game6_136Moves) {
    auto path = getGamePath("carlsen_nepomniachtchi_2021_game6.pgn");
    ASSERT_TRUE(std::filesystem::exists(path)) << "PGN file not found at " << path;

    auto games = chess::PgnReader::loadFromFile(path);
    ASSERT_EQ(games.size(), 1u);

    const auto& game = games[0];
    EXPECT_EQ(game.getHeader("White"), "Carlsen, Magnus");
    EXPECT_EQ(game.getHeader("Black"), "Nepomniachtchi, Ian");
    EXPECT_EQ(game.getHeader("Result"), "1-0");
    EXPECT_EQ(game.moves.size(), 271u);

    auto result = replayPgnGame(game);
    EXPECT_TRUE(result.allMovesLegal) << "Expected all 271 moves to be valid and legal";
    EXPECT_EQ(result.movesPlayed, 271u);
    EXPECT_FALSE(result.finalFen.empty());

    double movesPerSec = (result.durationUs > 0)
        ? (static_cast<double>(result.movesPlayed) / (result.durationUs / 1000000.0))
        : 0.0;

    std::cout << "[PGN Benchmark] Carlsen vs Nepo 2021 Game 6 (136 moves / 271 plies): "
              << result.durationUs / 1000 << " ms ("
              << static_cast<int>(movesPerSec) << " moves/sec)\n";
}

/**
 * @brief Kasparov vs Topalov (Wijk aan Zee 1999) - "Kasparov's Immortal": 44 full moves (87 plies).
 */
TEST(PgnFileBenchmark, KasparovVsTopalov1999_KasparovsImmortal_44Moves) {
    auto path = getGamePath("kasparov_topalov_1999.pgn");
    ASSERT_TRUE(std::filesystem::exists(path)) << "PGN file not found at " << path;

    auto games = chess::PgnReader::loadFromFile(path);
    ASSERT_EQ(games.size(), 1u);

    const auto& game = games[0];
    EXPECT_EQ(game.getHeader("White"), "Kasparov, Garry");
    EXPECT_EQ(game.getHeader("Black"), "Topalov, Veselin");
    EXPECT_EQ(game.getHeader("Result"), "1-0");
    EXPECT_EQ(game.moves.size(), 87u);

    auto result = replayPgnGame(game);
    EXPECT_TRUE(result.allMovesLegal);
    EXPECT_EQ(result.movesPlayed, 87u);

    std::cout << "[PGN Benchmark] Kasparov vs Topalov 1999 (44 moves / 87 plies): "
              << result.durationUs / 1000 << " ms\n";
}

/**
 * @brief Capablanca vs Tartakower (New York 1924) - Famous Endgame: 52 full moves (103 plies).
 */
TEST(PgnFileBenchmark, CapablancaVsTartakower1924_52Moves) {
    auto path = getGamePath("capablanca_tartakower_1924.pgn");
    ASSERT_TRUE(std::filesystem::exists(path)) << "PGN file not found at " << path;

    auto games = chess::PgnReader::loadFromFile(path);
    ASSERT_EQ(games.size(), 1u);

    const auto& game = games[0];
    EXPECT_EQ(game.getHeader("White"), "Capablanca, Jose Raul");
    EXPECT_EQ(game.getHeader("Black"), "Tartakower, Savielly");
    EXPECT_EQ(game.moves.size(), 103u);

    auto result = replayPgnGame(game);
    EXPECT_TRUE(result.allMovesLegal);
    EXPECT_EQ(result.movesPlayed, 103u);

    std::cout << "[PGN Benchmark] Capablanca vs Tartakower 1924 (52 moves / 103 plies): "
              << result.durationUs / 1000 << " ms\n";
}

/**
 * @brief Donald Byrne vs Bobby Fischer (1956) - "Game of the Century": 41 moves ending in checkmate.
 */
TEST(PgnFileBenchmark, ByrneVsFischer1956_GameOfCentury_Checkmate) {
    auto path = getGamePath("byrne_fischer_1956.pgn");
    ASSERT_TRUE(std::filesystem::exists(path)) << "PGN file not found at " << path;

    auto games = chess::PgnReader::loadFromFile(path);
    ASSERT_EQ(games.size(), 1u);

    const auto& game = games[0];
    EXPECT_EQ(game.getHeader("White"), "Byrne, Donald");
    EXPECT_EQ(game.getHeader("Black"), "Fischer, Robert James");
    EXPECT_EQ(game.getHeader("Result"), "0-1");

    auto result = replayPgnGame(game);
    EXPECT_TRUE(result.allMovesLegal);
    EXPECT_EQ(result.outcome, chess::Outcome::BlackWins);
    EXPECT_EQ(result.reason, chess::Reason::Checkmate);

    std::cout << "[PGN Benchmark] Byrne vs Fischer 1956 (Checkmate): "
              << result.durationUs / 1000 << " ms\n";
}

/**
 * @brief Paul Morphy vs Duke Karl / Count Isouard (1858) - "Opera Game": 17 moves ending in checkmate.
 */
TEST(PgnFileBenchmark, MorphyOperaGame1858_Checkmate) {
    auto path = getGamePath("opera_game_1858.pgn");
    ASSERT_TRUE(std::filesystem::exists(path)) << "PGN file not found at " << path;

    auto games = chess::PgnReader::loadFromFile(path);
    ASSERT_EQ(games.size(), 1u);

    const auto& game = games[0];
    auto result = replayPgnGame(game);
    EXPECT_TRUE(result.allMovesLegal);
    EXPECT_EQ(result.outcome, chess::Outcome::WhiteWins);
    EXPECT_EQ(result.reason, chess::Reason::Checkmate);
}

/**
 * @brief Measures high-volume replay performance directly from disk across multiple iterations.
 */
TEST(PgnFileBenchmark, AggregateThroughputFromDisk) {
    std::vector<std::string> pgnFiles = {
        "carlsen_nepomniachtchi_2021_game6.pgn",
        "kasparov_topalov_1999.pgn",
        "capablanca_tartakower_1924.pgn",
        "byrne_fischer_1956.pgn",
        "opera_game_1858.pgn"
    };

    std::vector<chess::PgnGame> loadedGames;
    for (const auto& f : pgnFiles) {
        auto gList = chess::PgnReader::loadFromFile(getGamePath(f));
        ASSERT_FALSE(gList.empty()) << "Failed to load " << f;
        loadedGames.push_back(gList[0]);
    }

    constexpr int ITERATIONS = 10;
    auto start = std::chrono::high_resolution_clock::now();

    size_t totalMovesProcessed = 0;
    for (int iter = 0; iter < ITERATIONS; ++iter) {
        for (const auto& game : loadedGames) {
            auto res = replayPgnGame(game);
            EXPECT_TRUE(res.allMovesLegal);
            totalMovesProcessed += res.movesPlayed;
        }
    }

    auto end = std::chrono::high_resolution_clock::now();
    auto totalMs = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();

    double movesPerSec = (totalMs > 0)
        ? (static_cast<double>(totalMovesProcessed) / (totalMs / 1000.0))
        : 0.0;

    std::cout << "\n========================================================\n"
              << "       REAL PGN FILE BENCHMARK RESULTS (DISK LOADED)    \n"
              << "========================================================\n"
              << " Total Games Replayed: " << loadedGames.size() * ITERATIONS << "\n"
              << " Total Moves Verified: " << totalMovesProcessed << "\n"
              << " Execution Time:       " << totalMs << " ms\n"
              << " Average Throughput:   " << static_cast<int>(movesPerSec) << " moves/sec\n"
              << " Longest Game Tested:  Carlsen vs Nepo (136 full moves)\n"
              << "========================================================\n\n";

    EXPECT_GT(totalMovesProcessed, 0u);
}
