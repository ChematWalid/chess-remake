#include "net/GameRoom.h"
#include "net/IClientSession.h"

#include <iostream>
#include <memory>
#include <string>
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

void dump(const std::string& label, const MockSession& s) {
    std::cout << "  [" << label << "] received " << s.inbox.size() << " msg(s)\n";
    for (const auto& m : s.inbox) {
        std::string shown = m.size() > 110 ? m.substr(0, 110) + "..." : m;
        std::cout << "     " << shown << "\n";
    }
}

}

int main() {
    std::cout << "== Probe A: host joins its own room ==\n";
    {
        auto host = std::make_shared<MockSession>("host");
        GameRoom room("ABC234", host, chess::ChessClock::TimeControl::unlimited(), chess::Color::White);
        bool joined = room.join(host);
        std::cout << "  join() returned " << (joined ? "true" : "false")
                  << ", room state = " << (room.getState() == RoomState::InProgress ? "InProgress" : "other") << "\n";
        auto color = room.getPlayerColor("host");
        std::cout << "  host color lookup: " << (color ? (*color == chess::Color::White ? "white" : "black") : "none") << "\n";
        host->inbox.clear();
        room.handleMove("host", "e2e4");
        room.handleMove("host", "e7e5");
        std::cout << "  host sent e2e4 then e7e5 alone:\n";
        dump("host", *host);
    }

    std::cout << "\n== Probe B: spectator sends a move before the game starts ==\n";
    {
        auto host = std::make_shared<MockSession>("host");
        auto spectator = std::make_shared<MockSession>("spec");
        GameRoom room("ABC234", host, chess::ChessClock::TimeControl::unlimited(), chess::Color::White);
        host->inbox.clear();
        room.handleMove("spec", "e2e4");
        dump("host", *host);
        dump("spec", *spectator);
    }

    std::cout << "\n== Probe C: player disconnects mid-game, stranger takes the seat ==\n";
    {
        auto alice = std::make_shared<MockSession>("alice");
        auto bob = std::make_shared<MockSession>("bob");
        auto mallory = std::make_shared<MockSession>("mallory");
        GameRoom room("ABC234", alice, chess::ChessClock::TimeControl::unlimited(), chess::Color::White);
        room.join(bob);
        room.handleMove("alice", "e4");
        room.handleDisconnect("bob");
        room.join(mallory);
        auto color = room.getPlayerColor("mallory");
        std::cout << "  mallory (any connection knowing the room code) now plays as: "
                  << (color ? (*color == chess::Color::White ? "white" : "black") : "spectator/none") << "\n";
        room.handleMove("mallory", "e5");
        dump("alice", *alice);
    }

    std::cout << "\n== Probe E: draw offer state and game_over reason strings ==\n";
    {
        auto alice = std::make_shared<MockSession>("alice");
        auto bob = std::make_shared<MockSession>("bob");
        GameRoom room("ABC234", alice, chess::ChessClock::TimeControl::unlimited(), chess::Color::White);
        room.join(bob);
        room.handleOfferDraw("alice");
        room.handleAcceptDraw("bob");
        std::string last = bob->inbox.back();
        auto j = nlohmann::json::parse(last);
        std::cout << "  game_over via accepted draw: result=" << j["result"] << " reason=" << j["reason"] << "\n";
    }
    {
        auto alice = std::make_shared<MockSession>("alice");
        auto bob = std::make_shared<MockSession>("bob");
        GameRoom room("ABC234", alice, chess::ChessClock::TimeControl::unlimited(), chess::Color::White);
        room.join(bob);
        const char* seq[] = {"f3", "e5", "g4", "Qh4"};
        const char* who[] = {"alice", "bob", "alice", "bob"};
        for (int i = 0; i < 4; ++i) room.handleMove(who[i], seq[i]);
        auto j = nlohmann::json::parse(alice->inbox.back());
        std::cout << "  fool's mate game_over: result=" << j["result"] << " reason=" << j["reason"]
                  << " winner=" << j["winner"] << "\n  pgn:\n" << j["pgn"].get<std::string>() << "\n";
        std::cout << "  fens count=" << j["fens"].size() << " moves count=" << j["moves"].size() << "\n";
    }
    return 0;
}
