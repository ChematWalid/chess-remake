#include "chess/notation/SanNotation.h"
#include "chess/pieces/Piece.h"
#include "chess/rules/GameRules.h"
#include "chess/rules/MoveGenerator.h"

#include <algorithm>
#include <cctype>

namespace chess {

namespace {

char pieceSymbol(PieceType type) {
    switch (type) {
        case PieceType::King:   return 'K';
        case PieceType::Queen:  return 'Q';
        case PieceType::Rook:   return 'R';
        case PieceType::Bishop: return 'B';
        case PieceType::Knight: return 'N';
        case PieceType::Pawn:   return '\0';
    }
    return '\0';
}

std::string normalizeSan(std::string s) {
    if (s == "0-0") return "O-O";
    if (s == "0-0-0") return "O-O-O";

    while (!s.empty() && (s.back() == '+' || s.back() == '#' || s.back() == '!' || s.back() == '?')) {
        s.pop_back();
    }
    s.erase(std::remove(s.begin(), s.end(), '='), s.end());
    return s;
}

} // namespace

std::string SanNotation::serialize(const Position& position, const Move& move) const {
    const Board& board = position.board();
    const Piece* movingPiece = board.pieceAt(move.from());
    if (movingPiece == nullptr) {
        return "";
    }

    std::string san;

    if (move.kind() == MoveKind::Castle) {
        if (move.to().file() == 6) {
            san = "O-O";
        } else {
            san = "O-O-O";
        }
    } else if (movingPiece->type() == PieceType::Pawn) {
        bool isCapture = (move.from().file() != move.to().file());
        if (isCapture) {
            san += static_cast<char>('a' + move.from().file());
            san += 'x';
        }
        san += move.to().toString();

        if (move.kind() == MoveKind::Promotion && move.promotion().has_value()) {
            san += '=';
            san += pieceSymbol(*move.promotion());
        }
    } else {
        san += pieceSymbol(movingPiece->type());

        auto legalMoves = MoveGenerator::legalMoves(position);
        std::vector<Move> candidates;
        for (const auto& m : legalMoves) {
            if (m.to() == move.to() && m.from() != move.from()) {
                const Piece* candPiece = board.pieceAt(m.from());
                if (candPiece != nullptr && candPiece->type() == movingPiece->type()) {
                    candidates.push_back(m);
                }
            }
        }

        if (!candidates.empty()) {
            bool fileAmbiguous = false;
            bool rankAmbiguous = false;

            for (const auto& cand : candidates) {
                if (cand.from().file() == move.from().file()) {
                    fileAmbiguous = true;
                }
                if (cand.from().rank() == move.from().rank()) {
                    rankAmbiguous = true;
                }
            }

            if (!fileAmbiguous) {
                san += static_cast<char>('a' + move.from().file());
            } else if (!rankAmbiguous) {
                san += static_cast<char>('1' + move.from().rank());
            } else {
                san += move.from().toString();
            }
        }

        bool isCapture = (board.pieceAt(move.to()) != nullptr);
        if (isCapture) {
            san += 'x';
        }

        san += move.to().toString();
    }

    Position after = position.after(move);
    if (GameRules::isCheckmate(after)) {
        san += '#';
    } else if (after.isInCheck(after.sideToMove())) {
        san += '+';
    }

    return san;
}

std::optional<Move> SanNotation::parse(const Position& position, const std::string& text) const {
    if (text.empty()) {
        return std::nullopt;
    }

    auto legalMoves = MoveGenerator::legalMoves(position);

    for (const auto& move : legalMoves) {
        if (serialize(position, move) == text) {
            return move;
        }
    }

    std::string normalizedTarget = normalizeSan(text);
    for (const auto& move : legalMoves) {
        if (normalizeSan(serialize(position, move)) == normalizedTarget) {
            return move;
        }
    }

    return std::nullopt;
}

} // namespace chess
