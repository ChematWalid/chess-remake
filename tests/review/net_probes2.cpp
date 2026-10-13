#include "net/GameRoom.h"
#include "net/IClientSession.h"
#include "net/Protocol.h"
#include "net/RoomManager.h"

#include <chrono>
#include <iostream>
#include <memory>
#include <string>
#include <thread>
#include <vector>

using namespace net;

namespace {

class MockSession : public IClientSession {
public:
    explicit MockSession(std::string id) : id_(std::move(id)) {}
    std::string getId() const override { return id_; }
    void send(const std::string& message) override { inbox.push_back(message); }
    void close() override {}
    std::vector<std::string> inbox;

private:
    std::string id_;
};

} // namespace

int main() {
    std::cout << "== Probe F: clock after a vacated seat is re-filled mid-game (1 min, no increment) ==\n";
    {
        auto alice = std::make_shared<MockSession>("alice");
        auto bob = std::make_shared<MockSession>("bob");
        auto bob2 = std::make_shared<MockSession>("bob2");
        GameRoom room("ABC234", alice, chess::ChessClock::TimeControl::standard(1, 0), chess::Color::White);
        room.join(bob);
        room.handleMove("alice", "e4");
        std::this_thread::sleep_for(std::chrono::milliseconds(1000));
        room.handleDisconnect("bob");
        room.join(bob2);
        std::this_thread::sleep_for(std::chrono::milliseconds(1000));
        room.handleMove("bob2", "e5");
        auto j = nlohmann::json::parse(alice->inbox.back());
        std::cout << "  after black thought ~2.0s total and white thought ~0s of the current turn:\n";
        std::cout << "  move_made white_time_ms=" << j["white_time_ms"] << " black_time_ms=" << j["black_time_ms"] << "\n";
        std::cout << "  expected white ~= 59000 (spent ~1s on 1.e4 move creation delay excluded), black ~= 58000\n";
    }

    std::cout << "\n== Probe G: RoomManager - one session creates a second room while seated in the first ==\n";
    {
        RoomManager manager;
        auto alice = std::make_shared<MockSession>("alice");
        auto bob = std::make_shared<MockSession>("bob");
        auto room1 = manager.createRoom(alice);
        manager.joinRoom(room1->getRoomCode(), bob);
        auto room2 = manager.createRoom(alice);
        std::cout << "  rooms active: " << manager.activeRoomCount() << "\n";
        std::cout << "  alice still seated in room1? "
                  << (room1->getPlayerColor("alice").has_value() ? "yes" : "no") << "\n";
        bob->inbox.clear();
        manager.onDisconnect("alice");
        std::cout << "  alice disconnects -> bob (room1) notified of disconnect? "
                  << (bob->inbox.empty() ? "NO" : "yes") << "\n";
        std::cout << "  room1 isDead after alice left? " << (room1->isDead() ? "yes" : "no") << "\n";
    }

    std::cout << "\n== Probe H: create_room with minutes=40000 via the router ==\n";
    {
        RoomManager manager;
        auto alice = std::make_shared<MockSession>("alice");
        manager.routeMessage(alice, R"({"action":"create_room","time_control":{"minutes":40000}})");
        auto room = manager.getRoomBySession("alice");
        std::cout << "  initial time ms stored: " << room->getTimeControl().initialTime.count() << "\n";
    }
    return 0;
}
