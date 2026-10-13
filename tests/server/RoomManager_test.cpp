#include <gtest/gtest.h>
#include "net/RoomManager.h"
#include "net/Protocol.h"
#include "net/WebSocketServer.h"
#include "net/JwtToken.h"
#include <nlohmann/json.hpp>
#include <vector>
#include <mutex>

namespace {

/**
 * @brief In-memory mock client session for capturing sent messages during integration tests.
 */
class MockSession : public net::IClientSession {
public:
    explicit MockSession(std::string id) : id_(std::move(id)) {}

    [[nodiscard]] std::string getId() const override {
        return id_;
    }

    void send(const std::string& message) override {
        std::lock_guard<std::mutex> lock(mutex_);
        messages_.push_back(message);
    }

    void close() override {
        closed_ = true;
    }

    std::vector<std::string> messages() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return messages_;
    }

    std::string lastMessage() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return messages_.empty() ? "" : messages_.back();
    }

    nlohmann::json lastJson() const {
        std::string msg = lastMessage();
        if (msg.empty()) return nlohmann::json{};
        return nlohmann::json::parse(msg);
    }

    void clear() {
        std::lock_guard<std::mutex> lock(mutex_);
        messages_.clear();
    }

private:
    std::string id_;
    mutable std::mutex mutex_;
    std::vector<std::string> messages_;
    bool closed_{false};
};

}

/**
 * @brief Tests room creation, invite code generation, and opponent join lifecycle.
 */
TEST(RoomManagerTest, CreateRoomAndJoinFlow) {
    net::RoomManager manager;
    auto host = std::make_shared<MockSession>("session-host");
    auto friendSession = std::make_shared<MockSession>("session-friend");

    manager.routeMessage(host, R"({
        "action": "create_room",
        "time_control": "unlimited",
        "preferred_color": "white"
    })");

    ASSERT_EQ(manager.activeRoomCount(), 1u);
    auto hostJson = host->lastJson();
    EXPECT_EQ(hostJson["event"], "room_created");
    EXPECT_EQ(hostJson["color"], "white");
    std::string roomCode = hostJson["room_code"].get<std::string>();
    EXPECT_EQ(roomCode.length(), 6u);

    manager.routeMessage(friendSession, nlohmann::json{
        {"action", "join_room"},
        {"room_code", roomCode}
    }.dump());

    auto friendJson = friendSession->lastJson();
    EXPECT_EQ(friendJson["event"], "game_started");
    EXPECT_EQ(friendJson["time_control"], "unlimited");

    auto room = manager.getRoom(roomCode);
    ASSERT_NE(room, nullptr);
    EXPECT_EQ(room->getState(), net::RoomState::InProgress);
}

/**
 * @brief Tests untimed move execution, turn alternation, and illegal turn detection.
 */
TEST(RoomManagerTest, UntimedGameMoveExecution) {
    net::RoomManager manager;
    auto white = std::make_shared<MockSession>("white-player");
    auto black = std::make_shared<MockSession>("black-player");

    manager.routeMessage(white, R"({"action": "create_room", "time_control": "unlimited", "preferred_color": "white"})");
    std::string code = white->lastJson()["room_code"].get<std::string>();
    manager.routeMessage(black, nlohmann::json{{"action", "join_room"}, {"room_code", code}}.dump());

    white->clear();
    black->clear();

    manager.routeMessage(white, R"({"action": "move", "san": "e4"})");

    auto wEvent = white->lastJson();
    EXPECT_EQ(wEvent["event"], "move_made");
    EXPECT_EQ(wEvent["san"], "e4");
    EXPECT_EQ(wEvent["side_to_move"], "black");

    auto bEvent = black->lastJson();
    EXPECT_EQ(bEvent["event"], "move_made");
    EXPECT_EQ(bEvent["san"], "e4");

    white->clear();
    manager.routeMessage(white, R"({"action": "move", "san": "e5"})");
    EXPECT_EQ(white->lastJson()["event"], "error");
    EXPECT_EQ(white->lastJson()["message"], "Not your turn");

    black->clear();
    manager.routeMessage(black, R"({"action": "move", "uci": "e7e5"})");
    EXPECT_EQ(black->lastJson()["event"], "move_made");
    EXPECT_EQ(black->lastJson()["san"], "e5");
    EXPECT_EQ(black->lastJson()["uci"], "e7e5");
}

/**
 * @brief Tests mutual draw offer and acceptance protocol between players.
 */
TEST(RoomManagerTest, MutualDrawProtocol) {
    net::RoomManager manager;
    auto white = std::make_shared<MockSession>("white-p");
    auto black = std::make_shared<MockSession>("black-p");

    manager.routeMessage(white, R"({"action": "create_room", "time_control": "unlimited", "preferred_color": "white"})");
    std::string code = white->lastJson()["room_code"].get<std::string>();
    manager.routeMessage(black, nlohmann::json{{"action", "join_room"}, {"room_code", code}}.dump());

    white->clear();
    black->clear();

    manager.routeMessage(white, R"({"action": "offer_draw"})");
    EXPECT_EQ(black->lastJson()["event"], "draw_offered");
    EXPECT_EQ(black->lastJson()["color"], "white");

    manager.routeMessage(black, R"({"action": "accept_draw"})");
    EXPECT_EQ(white->lastJson()["event"], "game_over");
    EXPECT_EQ(white->lastJson()["result"], "Draw");
    EXPECT_EQ(black->lastJson()["event"], "game_over");
    EXPECT_EQ(black->lastJson()["result"], "Draw");

    auto room = manager.getRoom(code);
    EXPECT_EQ(room->getState(), net::RoomState::Finished);
}

/**
 * @brief Tests instant game termination when a player resigns.
 */
TEST(RoomManagerTest, ResignationEndsGameImmediately) {
    net::RoomManager manager;
    auto white = std::make_shared<MockSession>("white-p");
    auto black = std::make_shared<MockSession>("black-p");

    manager.routeMessage(white, R"({"action": "create_room", "time_control": "unlimited", "preferred_color": "white"})");
    std::string code = white->lastJson()["room_code"].get<std::string>();
    manager.routeMessage(black, nlohmann::json{{"action", "join_room"}, {"room_code", code}}.dump());

    white->clear();
    black->clear();

    manager.routeMessage(black, R"({"action": "resign"})");

    EXPECT_EQ(white->lastJson()["event"], "game_over");
    EXPECT_EQ(white->lastJson()["result"], "WhiteWins");
    EXPECT_EQ(white->lastJson()["winner"], "white");
    EXPECT_EQ(white->lastJson()["reason"], "Resignation");

    EXPECT_EQ(black->lastJson()["event"], "game_over");
    EXPECT_EQ(black->lastJson()["result"], "WhiteWins");
    EXPECT_EQ(black->lastJson()["winner"], "white");
}

/**
 * @brief Tests that spectators receive game updates but cannot submit moves.
 */
TEST(RoomManagerTest, SpectatorReceivesGameStateAndCannotMove) {
    net::RoomManager manager;
    auto white = std::make_shared<MockSession>("white-p");
    auto black = std::make_shared<MockSession>("black-p");
    auto spec = std::make_shared<MockSession>("spectator-p");

    manager.routeMessage(white, R"({"action": "create_room", "time_control": "unlimited", "preferred_color": "white"})");
    std::string code = white->lastJson()["room_code"].get<std::string>();
    manager.routeMessage(black, nlohmann::json{{"action", "join_room"}, {"room_code", code}}.dump());

    manager.routeMessage(spec, nlohmann::json{{"action", "join_room"}, {"room_code", code}}.dump());
    EXPECT_EQ(spec->lastJson()["event"], "game_started");

    spec->clear();
    manager.routeMessage(spec, R"({"action": "move", "san": "e4"})");
    EXPECT_TRUE(spec->messages().empty());
}

/**
 * @brief Tests concurrent isolation between separate game rooms.
 */
TEST(RoomManagerTest, MultiRoomIsolation) {
    net::RoomManager manager;

    auto w1 = std::make_shared<MockSession>("w1");
    auto b1 = std::make_shared<MockSession>("b1");
    manager.routeMessage(w1, R"({"action": "create_room", "preferred_color": "white"})");
    std::string code1 = w1->lastJson()["room_code"].get<std::string>();
    manager.routeMessage(b1, nlohmann::json{{"action", "join_room"}, {"room_code", code1}}.dump());

    auto w2 = std::make_shared<MockSession>("w2");
    auto b2 = std::make_shared<MockSession>("b2");
    manager.routeMessage(w2, R"({"action": "create_room", "preferred_color": "white"})");
    std::string code2 = w2->lastJson()["room_code"].get<std::string>();
    manager.routeMessage(b2, nlohmann::json{{"action", "join_room"}, {"room_code", code2}}.dump());

    EXPECT_EQ(manager.activeRoomCount(), 2u);
    EXPECT_NE(code1, code2);

    w1->clear(); b1->clear(); w2->clear(); b2->clear();
    manager.routeMessage(w1, R"({"action": "move", "san": "e4"})");

    EXPECT_EQ(w1->lastJson()["event"], "move_made");
    EXPECT_EQ(b1->lastJson()["event"], "move_made");

    EXPECT_TRUE(w2->messages().empty());
    EXPECT_TRUE(b2->messages().empty());
}

/**
 * Regression test for Item 4: Sub-minute time control parsing and invalid time control error rejection.
 */
TEST(RoomManagerTest, TimeControlParsingAndValidation) {
    net::RoomManager manager;
    auto alice = std::make_shared<MockSession>("alice-tc");

    manager.routeMessage(alice, R"({"action":"create_room","time_control":{"seconds":30}})");
    auto room = manager.getRoomBySession("alice-tc");
    ASSERT_TRUE(room != nullptr);
    EXPECT_FALSE(room->getTimeControl().isUnlimited);
    EXPECT_EQ(room->getTimeControl().initialTime.count(), 30000);

    auto bob = std::make_shared<MockSession>("bob-tc");
    manager.routeMessage(bob, R"({"action":"create_room","time_control":{"minutes":-5}})");
    EXPECT_EQ(bob->lastJson()["event"], "error");
    EXPECT_TRUE(manager.getRoomBySession("bob-tc") == nullptr);

    auto charlie = std::make_shared<MockSession>("charlie-tc");
    manager.routeMessage(charlie, R"({"action":"create_room","time_control":"lightning"})");
    EXPECT_EQ(charlie->lastJson()["event"], "error");
    EXPECT_TRUE(manager.getRoomBySession("charlie-tc") == nullptr);
}

/**
 * Regression test for Item 5: Duplicate session join rejection.
 */
TEST(RoomManagerTest, DuplicateSessionJoinRejection) {
    auto host = std::make_shared<MockSession>("dup-host");
    net::GameRoom room("DUP001", host, chess::ChessClock::TimeControl::unlimited(), chess::Color::White);

    EXPECT_FALSE(room.join(host));
    EXPECT_EQ(host->lastJson()["event"], "error");
    EXPECT_EQ(room.getState(), net::RoomState::WaitingForOpponent);

    auto guest = std::make_shared<MockSession>("dup-guest");
    EXPECT_TRUE(room.join(guest));
    EXPECT_FALSE(room.join(guest));
    EXPECT_EQ(guest->lastJson()["event"], "error");

    auto spec = std::make_shared<MockSession>("dup-spec");
    EXPECT_TRUE(room.join(spec));
    EXPECT_FALSE(room.join(spec));
    EXPECT_EQ(spec->lastJson()["event"], "error");
}

/**
 * Regression test for Item 6: Stranger cannot take disconnected seat and reconnect preserves clock.
 */
TEST(RoomManagerTest, ReconnectPreservesClockAndStrangerCannotTakeSeat) {
    auto alice = std::make_shared<MockSession>("bob-host");
    auto bob = std::make_shared<MockSession>("bob");
    auto mallory = std::make_shared<MockSession>("mallory");
    auto bob2 = std::make_shared<MockSession>("bob2");

    net::GameRoom room("REC001", alice, chess::ChessClock::TimeControl::standard(1, 0), chess::Color::White);
    room.join(bob);
    room.handleMove("bob-host", "e4");

    std::this_thread::sleep_for(std::chrono::milliseconds(50));
    room.handleDisconnect("bob");

    room.join(mallory);
    EXPECT_EQ(room.getPlayerColor("mallory"), std::nullopt);

    room.join(bob2);
    EXPECT_EQ(room.getPlayerColor("bob2"), chess::Color::Black);
    room.handleMove("bob2", "e5");

    auto lastMsg = nlohmann::json::parse(alice->lastMessage());
    EXPECT_EQ(lastMsg["event"], "move_made");
    int64_t bTime = lastMsg["black_time_ms"].get<int64_t>();
    EXPECT_LE(bTime, 60000);
}

/**
 * Regression test for Item 7: Disconnect grace period expires causing abandonment in untimed game.
 */
TEST(RoomManagerTest, DisconnectGracePeriodAbandonmentInUntimedGame) {
    auto white = std::make_shared<MockSession>("w-aban");
    auto black = std::make_shared<MockSession>("b-aban");

    net::GameRoom room("ABA001", white, chess::ChessClock::TimeControl::unlimited(), chess::Color::White);
    room.join(black);
    room.setDisconnectGracePeriod(std::chrono::seconds(0));

    room.handleDisconnect("w-aban");
    room.tickClock();

    EXPECT_EQ(room.getState(), net::RoomState::Finished);
    EXPECT_TRUE(room.isDead());
    auto lastMsg = nlohmann::json::parse(black->lastMessage());
    EXPECT_EQ(lastMsg["event"], "game_over");
    EXPECT_EQ(lastMsg["result"], "BlackWins");
    EXPECT_EQ(lastMsg["reason"], "Abandonment");
}

/**
 * Regression test for Item 8: Prevent orphan seats when session is active in in-progress game.
 */
TEST(RoomManagerTest, PreventOrphanSeatsWhenSessionInActiveGame) {
    net::RoomManager manager;
    auto alice = std::make_shared<MockSession>("alice-orphan");
    auto bob = std::make_shared<MockSession>("bob-orphan");

    auto room1 = manager.createRoom(alice);
    manager.joinRoom(room1->getRoomCode(), bob);

    auto room2 = manager.createRoom(alice);
    EXPECT_EQ(room2, nullptr);
    EXPECT_EQ(alice->lastJson()["event"], "error");
    EXPECT_EQ(manager.activeRoomCount(), 1u);

    bob->clear();
    manager.onDisconnect("alice-orphan");
    EXPECT_FALSE(bob->messages().empty());
    EXPECT_TRUE(room1->isDead());
}

/**
 * Regression test for Item 9: HandleMove checks mover clock flag before applying.
 */
TEST(RoomManagerTest, HandleMoveChecksClockFlagBeforeApplying) {
    auto white = std::make_shared<MockSession>("w-flag");
    auto black = std::make_shared<MockSession>("b-flag");

    net::GameRoom room("FLG001", white, chess::ChessClock::TimeControl::fromSeconds(5, 0), chess::Color::White);
    room.join(black);

    std::this_thread::sleep_for(std::chrono::milliseconds(20));
    room.handleMove("w-flag", "e4");

    auto lastMsg = nlohmann::json::parse(white->lastMessage());
    EXPECT_EQ(lastMsg["event"], "move_made");
}

/**
 * Regression test for Item 10: GetGame returns snapshot copy and getPlayerColor is thread-safe.
 */
TEST(RoomManagerTest, GetGameReturnsSnapshotCopy) {
    auto white = std::make_shared<MockSession>("w-snap");
    auto black = std::make_shared<MockSession>("b-snap");

    net::GameRoom room("SNP001", white, chess::ChessClock::TimeControl::unlimited(), chess::Color::White);
    room.join(black);

    chess::ChessGame snap1 = room.getGame();
    EXPECT_EQ(snap1.position().sideToMove(), chess::Color::White);

    room.handleMove("w-snap", "e4");

    EXPECT_EQ(snap1.position().sideToMove(), chess::Color::White);
    EXPECT_EQ(room.getGame().position().sideToMove(), chess::Color::Black);
}

/**
 * Regression test for Item 11: SweepDeadRooms purges stale sessionToRoom mappings and standardized reasons.
 */
TEST(RoomManagerTest, SweepDeadRoomsPurgesStaleSessionsAndStandardizedReasons) {
    net::RoomManager manager;
    auto white = std::make_shared<MockSession>("w-purge");
    auto black = std::make_shared<MockSession>("b-purge");

    auto room = manager.createRoom(white);
    manager.joinRoom(room->getRoomCode(), black);

    manager.routeMessage(white, R"({"action":"offer_draw"})");
    manager.routeMessage(black, R"({"action":"accept_draw"})");

    EXPECT_EQ(white->lastJson()["reason"], "Mutual agreement");
    EXPECT_EQ(room->getState(), net::RoomState::Finished);

    room->markDead();
    manager.sweepDeadRooms();

    EXPECT_EQ(manager.activeRoomCount(), 0u);
    EXPECT_EQ(manager.getRoomBySession("w-purge"), nullptr);
    EXPECT_EQ(manager.getRoomBySession("b-purge"), nullptr);
}

/**
 * Regression test for Item 12: Token authentication, identity-based seating, and safe reconnect.
 */
TEST(RoomManagerTest, TokenAuthenticationAndIdentitySeating) {
    std::string secret = "test-secret-key-phase3";
    net::RoomManager manager(nullptr, secret);

    auto now = std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();

    net::PlayerToken aliceClaims;
    aliceClaims.roomCode = "TOK123";
    aliceClaims.userId = "usr_alice";
    aliceClaims.displayName = "Alice";
    aliceClaims.color = "white";
    aliceClaims.initialSeconds = 300;
    aliceClaims.incrementSeconds = 5;
    aliceClaims.exp = now + 3600;
    std::string aliceToken = net::JwtToken::create(aliceClaims, secret);

    net::PlayerToken bobClaims;
    bobClaims.roomCode = "TOK123";
    bobClaims.userId = "usr_bob";
    bobClaims.displayName = "Bob";
    bobClaims.color = "black";
    bobClaims.initialSeconds = 300;
    bobClaims.incrementSeconds = 5;
    bobClaims.exp = now + 3600;
    std::string bobToken = net::JwtToken::create(bobClaims, secret);

    auto aliceSession = std::make_shared<MockSession>("sess_alice_1");
    bool aliceOk = manager.handleTokenConnect(aliceSession, "TOK123", aliceToken);
    EXPECT_TRUE(aliceOk);
    EXPECT_EQ(aliceSession->lastJson()["event"], "room_created");

    auto bobSession = std::make_shared<MockSession>("sess_bob_1");
    bool bobOk = manager.handleTokenConnect(bobSession, "TOK123", bobToken);
    EXPECT_TRUE(bobOk);
    EXPECT_EQ(bobSession->lastJson()["event"], "game_started");

    auto room = manager.getRoom("TOK123");
    ASSERT_NE(room, nullptr);
    EXPECT_EQ(room->getState(), net::RoomState::InProgress);

    net::PlayerToken charlieClaims;
    charlieClaims.roomCode = "TOK123";
    charlieClaims.userId = "usr_charlie";
    charlieClaims.displayName = "Charlie";
    charlieClaims.color = "black";
    charlieClaims.exp = now + 3600;
    std::string charlieToken = net::JwtToken::create(charlieClaims, secret);

    auto charlieSession = std::make_shared<MockSession>("sess_charlie_1");
    bool charlieOk = manager.handleTokenConnect(charlieSession, "TOK123", charlieToken);
    EXPECT_TRUE(charlieOk);
    ASSERT_GE(charlieSession->messages().size(), 1u);
    EXPECT_EQ(nlohmann::json::parse(charlieSession->messages().front())["role"], "spectator");
    EXPECT_EQ(charlieSession->lastJson()["event"], "game_started");

    manager.onDisconnect("sess_bob_1");

    auto bobReconnectSession = std::make_shared<MockSession>("sess_bob_2");
    bool bobReconnected = manager.handleTokenConnect(bobReconnectSession, "TOK123", bobToken);
    EXPECT_TRUE(bobReconnected);
    ASSERT_GE(bobReconnectSession->messages().size(), 1u);
    EXPECT_EQ(nlohmann::json::parse(bobReconnectSession->messages().front())["role"], "black");
    EXPECT_EQ(bobReconnectSession->lastJson()["event"], "game_started");

    auto expiredClaims = bobClaims;
    expiredClaims.exp = now - 500;
    std::string expiredToken = net::JwtToken::create(expiredClaims, secret);
    auto badSession = std::make_shared<MockSession>("sess_bad");
    bool badOk = manager.handleTokenConnect(badSession, "TOK123", expiredToken);
    EXPECT_FALSE(badOk);
    EXPECT_EQ(badSession->lastJson()["event"], "error");
}

/**
 * Regression test for Item 14: Stable machine termination codes emitted in reason_code field.
 */
TEST(RoomManagerTest, StableMachineTerminationCodesEmitted) {
    net::RoomManager manager;
    auto white = std::make_shared<MockSession>("w-codes");
    auto black = std::make_shared<MockSession>("b-codes");

    auto room = manager.createRoom(white);
    manager.joinRoom(room->getRoomCode(), black);

    manager.routeMessage(white, R"({"action":"offer_draw"})");
    manager.routeMessage(black, R"({"action":"accept_draw"})");

    auto drawJson = white->lastJson();
    EXPECT_EQ(drawJson["event"], "game_over");
    EXPECT_EQ(drawJson["reason"], "Mutual agreement");
    EXPECT_EQ(drawJson["reason_code"], "DRAW_AGREED");

    auto white2 = std::make_shared<MockSession>("w-resign");
    auto black2 = std::make_shared<MockSession>("b-resign");
    auto room2 = manager.createRoom(white2);
    manager.joinRoom(room2->getRoomCode(), black2);
    manager.routeMessage(white2, R"({"action":"resign"})");

    auto resignJson = white2->lastJson();
    EXPECT_EQ(resignJson["reason"], "Resignation");
    EXPECT_EQ(resignJson["reason_code"], "RESIGNATION");

    auto white3 = std::make_shared<MockSession>("w-mate");
    auto black3 = std::make_shared<MockSession>("b-mate");
    auto room3 = manager.createRoom(white3, net::RoomManager::TimeControl::unlimited(), chess::Color::White);
    manager.joinRoom(room3->getRoomCode(), black3);
    manager.routeMessage(white3, R"({"action":"move","san":"f3"})");
    manager.routeMessage(black3, R"({"action":"move","san":"e5"})");
    manager.routeMessage(white3, R"({"action":"move","san":"g4"})");
    manager.routeMessage(black3, R"({"action":"move","san":"Qh4#"})");

    auto mateJson = white3->lastJson();
    EXPECT_EQ(mateJson["reason"], "Checkmate");
    EXPECT_EQ(mateJson["reason_code"], "CHECKMATE");
}

/**
 * Regression test for Item 15: PGN 7-tag roster, TimeControl tag, and duration tracking.
 */
TEST(RoomManagerTest, PgnSevenTagRosterAndDurationTracking) {
    auto white = std::make_shared<MockSession>("w-pgn");
    auto black = std::make_shared<MockSession>("b-pgn");

    auto tc = chess::ChessClock::TimeControl::fromSeconds(300, 5);
    net::GameRoom room("PGN001", white, tc, chess::Color::White, "user_w", "AliceGrandmaster");
    room.joinWithIdentity(black, "user_b", "BobMaster", "black");

    EXPECT_EQ(room.getState(), net::RoomState::InProgress);

    std::this_thread::sleep_for(std::chrono::milliseconds(20));
    room.handleMove("w-pgn", "e4");
    room.handleMove("b-pgn", "e5");
    room.handleResign("w-pgn");

    auto lastMsg = nlohmann::json::parse(white->lastMessage());
    EXPECT_EQ(lastMsg["event"], "game_over");
    std::string pgn = lastMsg["pgn"].get<std::string>();

    EXPECT_NE(pgn.find("[Event \"Live Room\"]"), std::string::npos);
    EXPECT_NE(pgn.find("[Site \"Chess Server\"]"), std::string::npos);
    EXPECT_NE(pgn.find("[Date \""), std::string::npos);
    EXPECT_NE(pgn.find("[Round \"-\"]"), std::string::npos);
    EXPECT_NE(pgn.find("[White \"AliceGrandmaster\"]"), std::string::npos);
    EXPECT_NE(pgn.find("[Black \"BobMaster\"]"), std::string::npos);
    EXPECT_NE(pgn.find("[Result \"0-1\"]"), std::string::npos);
    EXPECT_NE(pgn.find("[TimeControl \"300+5\"]"), std::string::npos);
}

/**
 * Regression test for Item 16: Maximum incoming payload size cap.
 */
TEST(RoomManagerTest, PayloadSizeCapHandling) {
    EXPECT_EQ(net::WebSocketServer::kMaxIncomingMessageSize, 65536u);
}

