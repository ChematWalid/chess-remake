#pragma once

#include <optional>
#include <string>

#include "chess/notation/MoveNotation.h"

namespace chess {

/**
 * @brief Universal Chess Interface (UCI) notation serializer and parser.
 * 
 * @details Handles 4-to-5 character coordinate strings such as "e2e4", "e7e8q", and "e1g1".
 */
class UciNotation : public MoveNotation {
public:
    UciNotation() = default;
    ~UciNotation() override = default;

    /**
     * @brief Formats a move into coordinate UCI notation.
     * 
     * @param position The position before the move (unused in UCI coordinate format).
     * @param move The move to format.
     * @return Coordinate string (e.g. "e2e4", "e7e8q").
     */
    std::string serialize(const Position& position, const Move& move) const override;

    /**
     * @brief Resolves a UCI coordinate string into a legal Move in the given position.
     * 
     * @param position The position to validate against.
     * @param text The UCI string (e.g. "e2e4", "e7e8q").
     * @return The legal Move, or std::nullopt if invalid or illegal.
     */
    std::optional<Move> parse(const Position& position, const std::string& text) const override;
};

} // namespace chess
