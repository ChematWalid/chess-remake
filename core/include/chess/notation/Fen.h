#pragma once

#include <string>

#include "chess/board/Position.h"

namespace chess {

/**
 * @brief Utility class for parsing and serializing Forsyth–Edwards Notation (FEN).
 * 
 * @details Translates between standard FEN strings and immutable Position instances.
 */
class Fen {
public:
    /**
     * @brief The standard chess opening starting position FEN string.
     */
    static constexpr const char* kStartingFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * @brief Parses a FEN string into a Position object.
     * 
     * @details Parses all 6 space-separated FEN fields:
     * 1. Piece placement across 8 ranks
     * 2. Active color ('w' or 'b')
     * 3. Castling availability ("KQkq" or "-")
     * 4. En passant target square (e.g. "e3" or "-")
     * 5. Halfmove clock (e.g. "0")
     * 6. Fullmove number (e.g. "1")
     * 
     * @param fen The full FEN string.
     * @return The parsed Position object.
     * @throws std::invalid_argument If the FEN string format is malformed.
     */
    static Position parse(const std::string& fen);

    /**
     * @brief Serializes a Position into a standard 6-field FEN string.
     * 
     * @param position The position to format.
     * @return The serialized FEN string.
     */
    static std::string serialize(const Position& position);

    /**
     * @brief Convenience factory returning the standard chess opening position.
     * 
     * @return Position initialized to the standard start of a game.
     */
    static Position standard();
};

} // namespace chess
