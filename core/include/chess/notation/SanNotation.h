#pragma once

#include <optional>
#include <string>

#include "chess/notation/MoveNotation.h"

namespace chess {

/**
 * @brief Standard Algebraic Notation (SAN) serializer and parser.
 * 
 * @details Implements FIDE standard algebraic notation including piece letters (K, Q, R, B, N),
 * pawn captures (exd5), castling (O-O, O-O-O), disambiguation (Nbd7, R1a3), promotions (=Q),
 * and check/checkmate suffixes (+, #).
 */
class SanNotation : public MoveNotation {
public:
    SanNotation() = default;
    ~SanNotation() override = default;

    /**
     * @brief Serializes a move into Standard Algebraic Notation.
     * 
     * @param position The position before the move is played.
     * @param move The move to serialize.
     * @return Formatted SAN string (e.g. "Nf3", "exd5", "O-O", "Qh4#").
     */
    std::string serialize(const Position& position, const Move& move) const override;

    /**
     * @brief Parses a SAN string into a verified legal move.
     * 
     * @param position The position to evaluate against.
     * @param text The SAN string.
     * @return The legal Move, or std::nullopt if syntax is invalid or move is illegal.
     */
    std::optional<Move> parse(const Position& position, const std::string& text) const override;
};

} // namespace chess
