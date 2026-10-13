#include <gtest/gtest.h>
#include "net/WebhookClient.h"
#include <filesystem>
#include <fstream>

/**
 * @brief Tests that game records are atomically serialized to the durable on-disk outbox.
 */
TEST(WebhookClientTest, EnqueuePersistsToOutbox) {
    std::filesystem::path testOutbox = ".agent-out/test_outbox_enqueue";
    std::error_code ec;
    std::filesystem::remove_all(testOutbox, ec);

    net::WebhookClient client("", "", testOutbox);
    EXPECT_EQ(client.pendingOutboxCount(), 0u);

    net::GameRecordPayload payload;
    payload.roomCode = "ROOM123";
    payload.roomToken = "ROOM123";
    payload.whitePlayerId = "usr_alice";
    payload.blackPlayerId = "usr_bob";
    payload.winnerPlayerId = "usr_alice";
    payload.isDraw = false;
    payload.terminationReason = "Checkmate";
    payload.reasonCode = "CHECKMATE";
    payload.totalMoves = 3;
    payload.durationSeconds = 45;
    payload.pgn = "[Event \"Casual Game\"]\n1. e4 e5 2. Qh5 1-0";
    payload.moves = {"e4", "e5", "Qh5"};
    payload.fens = {
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
        "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pppp1ppp/8/4p2Q/4P3/8/PPPP1PPP/RNB1KBNR b KQkq - 1 2"
    };

    client.enqueue(payload);
    EXPECT_EQ(client.pendingOutboxCount(), 1u);

    size_t inspectedFiles = 0;
    for (const auto& entry : std::filesystem::directory_iterator(testOutbox, ec)) {
        if (entry.is_regular_file() && entry.path().extension() == ".json") {
            ++inspectedFiles;
            std::ifstream ifs(entry.path());
            nlohmann::json j;
            ifs >> j;
            EXPECT_EQ(j["roomCode"], "ROOM123");
            EXPECT_EQ(j["whitePlayerId"], "usr_alice");
            EXPECT_EQ(j["blackPlayerId"], "usr_bob");
            EXPECT_EQ(j["winnerPlayerId"], "usr_alice");
            EXPECT_FALSE(j["isDraw"].get<bool>());
            EXPECT_EQ(j["reason_code"], "CHECKMATE");
            EXPECT_EQ(j["totalMoves"], 3);
            EXPECT_EQ(j["durationSeconds"], 45);
            EXPECT_EQ(j["moves"].size(), 3u);
            EXPECT_EQ(j["fens"].size(), 4u);
        }
    }
    EXPECT_EQ(inspectedFiles, 1u);

    std::filesystem::remove_all(testOutbox, ec);
}

/**
 * @brief Tests that empty endpoint does not crash and leaves outbox intact.
 */
TEST(WebhookClientTest, EmptyEndpointLeavesOutboxIntact) {
    std::filesystem::path testOutbox = ".agent-out/test_outbox_empty_ep";
    std::error_code ec;
    std::filesystem::remove_all(testOutbox, ec);

    net::WebhookClient client("", "", testOutbox);

    net::GameRecordPayload payload;
    payload.roomCode = "ROOM999";
    payload.whitePlayerId = "alice";
    payload.blackPlayerId = "bob";

    client.enqueue(payload);
    EXPECT_EQ(client.pendingOutboxCount(), 1u);

    size_t delivered = client.flushOnce();
    EXPECT_EQ(delivered, 0u);
    EXPECT_EQ(client.pendingOutboxCount(), 1u);

    std::filesystem::remove_all(testOutbox, ec);
}
