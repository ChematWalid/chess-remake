#include "chess/notation/Fen.h"
#include "chess/rules/ChessClock.h"
#include "chess/rules/ChessGame.h"

#include <chrono>
#include <iostream>
#include <string>
#include <thread>
#include <vector>

using namespace chess;

namespace {

bool playUci(ChessGame& game, const std::string& uci) {
    Square from = Square::fromString(uci.substr(0, 2));
    Square to = Square::fromString(uci.substr(2, 2));
    auto r = game.play(game.position().sideToMove(), from, to);
    return r.has_value();
}

const char* reasonName(Reason r) {
    switch (r) {
        case Reason::None: return "None";
        case Reason::Checkmate: return "Checkmate";
        case Reason::Stalemate: return "Stalemate";
        case Reason::FiftyMoveRule: return "FiftyMoveRule";
        case Reason::ThreefoldRepetition: return "ThreefoldRepetition";
        case Reason::InsufficientMaterial: return "InsufficientMaterial";
        case Reason::Resignation: return "Resignation";
        case Reason::DrawAgreement: return "DrawAgreement";
        case Reason::Timeout: return "Timeout";
        case Reason::Abandonment: return "Abandonment";
    }
    return "?";
}

} // namespace

int main() {
    std::cout << "== Probe 1: threefold repetition when first occurrence carries an en passant square ==\n";
    {
        ChessGame game;
        std::vector<std::string> seq = {"e2e4",
                                        "g8f6", "b1c3", "f6g8", "c3b1",
                                        "g8f6", "b1c3", "f6g8", "c3b1"};
        for (const auto& m : seq) {
            if (!playUci(game, m)) {
                std::cout << "illegal in probe: " << m << "\n";
                return 2;
            }
        }
        std::cout << "after 1.e4 then two full knight shuffles (same placement 3 times, black to move)\n";
        std::cout << "FEN now: " << Fen::serialize(game.position()) << "\n";
        std::cout << "game over? " << (game.isOver() ? "yes" : "no")
                  << "  reason=" << reasonName(game.result().reason) << "\n";
        std::cout << "(FIDE: no black pawn can capture e3 en passant, so this is the 3rd occurrence -> draw)\n";
        std::cout << "history FENs of the three occurrences:\n";
        const auto& h = game.positionHistory();
        std::cout << "  " << Fen::serialize(h[1]) << "\n";
        std::cout << "  " << Fen::serialize(h[5]) << "\n";
        std::cout << "  " << Fen::serialize(h[9]) << "\n";
    }

    std::cout << "\n== Probe 2: timeout when winner has king + pawn only vs loser with pieces ==\n";
    {
        ChessGame game(Fen::parse("4k3/8/8/8/8/8/P7/R3K3 w - - 0 1"));
        game.declareTimeout(Color::Black);
        std::cout << "black flags, white has K+R+P: outcome="
                  << static_cast<int>(game.result().outcome) << " (1=WhiteWins) reason="
                  << reasonName(game.result().reason) << "\n";
    }
    {
        ChessGame game(Fen::parse("4k3/8/8/8/8/8/8/R3K3 w - - 0 1"));
        game.declareTimeout(Color::White);
        std::cout << "white flags, black has bare king: outcome="
                  << static_cast<int>(game.result().outcome) << " (3=Draw) reason="
                  << reasonName(game.result().reason) << "\n";
    }

    std::cout << "\n== Probe 3: ChessClock flags and Fischer increment ==\n";
    {
        ChessClock clock(ChessClock::TimeControl{std::chrono::milliseconds(300), std::chrono::milliseconds(100), false});
        clock.start(Color::White);
        std::this_thread::sleep_for(std::chrono::milliseconds(150));
        clock.press(Color::White);
        std::cout << "white after 150ms thinking + 100ms increment: "
                  << clock.timeRemaining(Color::White).count() << " ms (expect ~250)\n";
        std::this_thread::sleep_for(std::chrono::milliseconds(350));
        std::cout << "black flagged after 350ms: " << (clock.isFlagged(Color::Black) ? "yes" : "no") << "\n";
        std::cout << "black remaining reported: " << clock.timeRemaining(Color::Black).count() << " ms\n";
    }

    std::cout << "\n== Probe 4: TimeControl::standard overflow with a large minutes value ==\n";
    {
        auto tc = ChessClock::TimeControl::standard(40000, 0);
        std::cout << "standard(40000 min) initialTime ms = " << tc.initialTime.count()
                  << " (expected 2400000000)\n";
    }
    return 0;
}
