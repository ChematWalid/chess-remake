#include "chess/notation/Fen.h"
#include "chess/notation/SanNotation.h"
#include "chess/notation/UciNotation.h"
#include "chess/render/BoardRenderer.h"
#include "chess/rules/ChessGame.h"

#include <iostream>
#include <string>

using namespace chess;
using namespace chess::cli;

int main() {
    ChessGame game;
    SanNotation san;
    UciNotation uci;

    std::cout << "\n=========================================\n";
    std::cout << "          C++ CHESS ENGINE (CLI)         \n";
    std::cout << "=========================================\n";
    std::cout << "• Enter moves in SAN (e.g. e4, Nf3, O-O)\n";
    std::cout << "  or UCI coordinates (e.g. e2e4, g1f3).\n";
    std::cout << "• Commands: moves, fen, resign, draw, quit\n\n";

    while (!game.isOver()) {
        std::cout << BoardRenderer::render(game.position().board()) << "\n";

        Color turn = game.position().sideToMove();
        std::string turnStr = (turn == Color::White) ? "White" : "Black";

        if (game.position().isInCheck(turn)) {
            std::cout << "⚠️  " << turnStr << " is in CHECK!\n";
        }

        std::cout << turnStr << " to move > ";

        std::string input;
        if (!std::getline(std::cin, input)) {
            break;
        }

        // Trim leading and trailing whitespace
        input.erase(0, input.find_first_not_of(" \t\r\n"));
        input.erase(input.find_last_not_of(" \t\r\n") + 1);

        if (input.empty()) {
            continue;
        }

        if (input == "quit" || input == "exit") {
            std::cout << "Game exited.\n";
            return 0;
        }

        if (input == "fen") {
            std::cout << "FEN: " << Fen::serialize(game.position()) << "\n\n";
            continue;
        }

        if (input == "moves" || input == "legal") {
            std::cout << "Legal moves (" << game.legalMoves().size() << "): ";
            for (const auto& m : game.legalMoves()) {
                std::cout << san.serialize(game.position(), m) << " ";
            }
            std::cout << "\n\n";
            continue;
        }

        if (input == "resign") {
            game.resign(turn);
            std::cout << "\n" << turnStr << " resigned.\n\n";
            break;
        }

        if (input == "draw") {
            if (game.pendingDrawOffer().has_value() && game.pendingDrawOffer().value() != turn) {
                game.acceptDraw(turn);
                std::cout << "\nDraw offer accepted. Game drawn by mutual agreement.\n\n";
            } else {
                game.offerDraw(turn);
                std::cout << "\n" << turnStr << " offered a draw to the opponent.\n\n";
            }
            continue;
        }

        // 1. Resolve move: try SAN first, then UCI
        auto parsedMove = san.parse(game.position(), input);
        if (!parsedMove.has_value()) {
            parsedMove = uci.parse(game.position(), input);
        }

        if (!parsedMove.has_value()) {
            std::cout << ">> You can't do that: Unrecognized or illegal move!\n\n";
            continue;
        }

        // 2. Authoritative backend execution & rule verification
        auto result = game.play(turn, parsedMove->from(), parsedMove->to(), parsedMove->promotion());
        if (!result.has_value()) {
            switch (result.error()) {
                case MoveError::NotYourTurn:
                    std::cout << ">> You can't do that: It's not your turn!\n\n";
                    break;
                case MoveError::IllegalMove:
                    std::cout << ">> You can't do that: Illegal move!\n\n";
                    break;
                case MoveError::GameOver:
                    std::cout << ">> You can't do that: The game is already over!\n\n";
                    break;
            }
            continue;
        }

        const auto& hist = game.positionHistory();
        std::string moveSan = san.serialize(hist[hist.size() - 2], *result);
        std::cout << ">> " << turnStr << " played: " << moveSan << "\n\n";
    }

    // Final board display
    std::cout << BoardRenderer::render(game.position().board()) << "\n";
    std::cout << "=========================================\n";
    std::cout << "                GAME OVER                \n";
    std::cout << "=========================================\n";

    switch (game.result().outcome) {
        case Outcome::WhiteWins:
            std::cout << "Outcome: 1-0 (White Wins!)\n";
            break;
        case Outcome::BlackWins:
            std::cout << "Outcome: 0-1 (Black Wins!)\n";
            break;
        case Outcome::Draw:
            std::cout << "Outcome: 1/2-1/2 (Draw)\n";
            break;
        case Outcome::Ongoing:
            break;
    }

    switch (game.result().reason) {
        case Reason::Checkmate:
            std::cout << "Reason : Checkmate!\n";
            break;
        case Reason::Stalemate:
            std::cout << "Reason : Stalemate.\n";
            break;
        case Reason::FiftyMoveRule:
            std::cout << "Reason : 50-move rule threshold reached.\n";
            break;
        case Reason::ThreefoldRepetition:
            std::cout << "Reason : Threefold repetition.\n";
            break;
        case Reason::InsufficientMaterial:
            std::cout << "Reason : Insufficient mating material.\n";
            break;
        case Reason::Resignation:
            std::cout << "Reason : Player resignation.\n";
            break;
        case Reason::DrawAgreement:
            std::cout << "Reason : Mutual draw agreement.\n";
            break;
        case Reason::Timeout:
            std::cout << "Reason : Clock timeout.\n";
            break;
        case Reason::Abandonment:
            std::cout << "Reason : Player abandonment.\n";
            break;
        case Reason::None:
            break;
    }
    std::cout << "\n";

    return 0;
}