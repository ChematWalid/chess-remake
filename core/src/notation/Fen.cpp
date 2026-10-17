#include "chess/notation/Fen.h"
#include "chess/pieces/Piece.h"
#include "chess/pieces/PieceFactory.h"

#include <algorithm>
#include <charconv>
#include <cctype>
#include <sstream>
#include <stdexcept>
#include <vector>

namespace chess {

Position Fen::parse(const std::string& fen) {
    std::istringstream iss(fen);
    std::string piecesStr;
    std::string sideStr;
    std::string castlingStr;
    std::string epStr;
    std::string halfmoveStr;
    std::string fullmoveStr;
    std::string extraField;

    if (!(iss >> piecesStr >> sideStr >> castlingStr >> epStr >> halfmoveStr >> fullmoveStr)) {
        throw std::invalid_argument("Fen::parse: incomplete FEN string \"" + fen + "\"");
    }

    if (iss >> extraField) {
        throw std::invalid_argument("Fen::parse: unexpected trailing field in FEN string \"" + fen + "\"");
    }

    auto parseCounter = [&](const std::string& value, const char* name, bool mustBePositive) {
        if (value.empty() || !std::all_of(value.begin(), value.end(), [](unsigned char c) {
                return std::isdigit(c) != 0;
            })) {
            throw std::invalid_argument(std::string("Fen::parse: invalid ") + name + " \"" + value + "\"");
        }

        int counter = 0;
        const auto [end, error] = std::from_chars(value.data(), value.data() + value.size(), counter);
        if (error != std::errc{} || end != value.data() + value.size() || (mustBePositive && counter == 0)) {
            throw std::invalid_argument(std::string("Fen::parse: invalid ") + name + " \"" + value + "\"");
        }
        return counter;
    };

    const int halfmoveClock = parseCounter(halfmoveStr, "halfmove clock", false);
    const int fullmoveNumber = parseCounter(fullmoveStr, "fullmove number", true);

    Board board;
    int rank = 7;
    int file = 0;

    for (char c : piecesStr) {
        if (c == '/') {
            if (file != 8) {
                throw std::invalid_argument("Fen::parse: invalid rank length in \"" + piecesStr + "\"");
            }
            rank--;
            file = 0;
            if (rank < 0) {
                throw std::invalid_argument("Fen::parse: too many ranks in \"" + piecesStr + "\"");
            }
        } else if (std::isdigit(static_cast<unsigned char>(c))) {
            int emptyCount = c - '0';
            file += emptyCount;
            if (file > 8) {
                throw std::invalid_argument("Fen::parse: file overflow in rank in \"" + piecesStr + "\"");
            }
        } else {
            if (file >= 8 || rank < 0) {
                throw std::invalid_argument("Fen::parse: piece placement out of bounds in \"" + piecesStr + "\"");
            }
            const Piece& piece = PieceFactory::fromSymbol(c);
            board.place(piece, Square(file, rank));
            file++;
        }
    }

    if (rank != 0 || file != 8) {
        throw std::invalid_argument("Fen::parse: malformed piece placement string \"" + piecesStr + "\"");
    }

    Color sideToMove = Color::White;
    if (sideStr == "w") {
        sideToMove = Color::White;
    } else if (sideStr == "b") {
        sideToMove = Color::Black;
    } else {
        throw std::invalid_argument("Fen::parse: invalid active color \"" + sideStr + "\"");
    }

    CastlingRights castling = CastlingRights::fromString(castlingStr);

    std::optional<Square> enPassant = std::nullopt;
    if (epStr != "-") {
        enPassant = Square::fromString(epStr);
    }

    return Position(board, sideToMove, castling, enPassant, halfmoveClock, fullmoveNumber);
}

std::string Fen::serialize(const Position& position) {
    std::string result;

    for (int rank = 7; rank >= 0; --rank) {
        int emptyCount = 0;
        for (int file = 0; file < 8; ++file) {
            const Piece* p = position.board().pieceAt(Square(file, rank));
            if (p == nullptr) {
                emptyCount++;
            } else {
                if (emptyCount > 0) {
                    result += std::to_string(emptyCount);
                    emptyCount = 0;
                }
                result += p->symbol();
            }
        }
        if (emptyCount > 0) {
            result += std::to_string(emptyCount);
        }
        if (rank > 0) {
            result += '/';
        }
    }

    result += (position.sideToMove() == Color::White) ? " w " : " b ";
    result += position.castling().toString() + " ";
    result += (position.enPassant().has_value()) ? position.enPassant()->toString() : "-";
    result += " " + std::to_string(position.halfmoveClock());
    result += " " + std::to_string(position.fullmoveNumber());

    return result;
}

Position Fen::standard() {
    return parse(kStartingFen);
}

} 
