#pragma once

#include <optional>
#include <string>

#include "chess/board/Position.h"
#include "chess/rules/Move.h"

namespace chess {

/**
 * @brief Abstract base class defining the contract for chess move notations.
 * 
 * @details Polymorphic interface enabling serialization of internal Move objects into
 * text strings and parsing of text notation back into verified legal Move objects.
 */
class MoveNotation {
public:
    virtual ~MoveNotation() = default;

    /**
     * @brief Serializes a move into its textual representation relative to a position.
     * 
     * @param position The position before the move is played.
     * @param move The move to serialize.
     * @return Formatted notation string.
     */
    virtual std::string serialize(const Position& position, const Move& move) const = 0;

    /**
     * @brief Parses a textual move string into a verified legal move.
     * 
     * @param position The current position to evaluate against.
     * @param text The textual notation string.
     * @return The resolved legal Move, or std::nullopt if syntax or move is illegal.
     */
    virtual std::optional<Move> parse(const Position& position, const std::string& text) const = 0;
};

} 
