#pragma once

#include <optional>
#include <string>

#include "chess/board/Square.h"
#include "chess/board/Types.h"

namespace chess {

/**
 * @brief Represents a chess move between two squares.
 * 
 * @details Stores the origin square, destination square, movement category (such as
 * normal step, castle, or double pawn push), and any chosen promotion piece type.
 */
class Move {
private:
    Square fromSquare;
    Square toSquare;
    MoveKind moveKind;
    std::optional<PieceType> promotionPiece;

public:
    /**
     * @brief Constructs a move with source, destination, kind, and optional promotion.
     * 
     * @details Encapsulates all details required to validate, execute, and record a chess move.
     * 
     * @param from The origin square of the moving piece.
     * @param to The target destination square.
     * @param kind The category of move (Normal, DoublePawnPush, EnPassant, Castle, Promotion).
     * @param promotion The piece type to promote to, if applicable (defaults to std::nullopt).
     */
    Move(Square from, Square to, MoveKind kind, std::optional<PieceType> promotion = std::nullopt);

    /**
     * @brief Gets the originating source square.
     * 
     * @details Returns the square from which the piece started its move.
     * 
     * @return The starting Square.
     */
    Square from() const;

    /**
     * @brief Gets the destination square.
     * 
     * @details Returns the square to which the piece moved.
     * 
     * @return The destination Square.
     */
    Square to() const;

    /**
     * @brief Gets the classification of the move.
     * 
     * @details Indicates whether the move is normal, a double pawn push, en passant, castling, or promotion.
     * 
     * @return The MoveKind enum value.
     */
    MoveKind kind() const;

    /**
     * @brief Gets the promotion piece type, if the move is a pawn promotion.
     * 
     * @details Holds the piece type (Queen, Rook, Bishop, Knight) if this move promoted a pawn.
     * 
     * @return An optional containing the PieceType, or std::nullopt if not a promotion.
     */
    std::optional<PieceType> promotion() const;

    /**
     * @brief Formats the move in Universal Chess Interface (UCI) coordinate notation.
     * 
     * @details Produces standard UCI move strings such as "e2e4", "e7e8q", or "e1g1".
     * 
     * @return The formatted UCI string.
     */
    std::string toUci() const;

    /**
     * @brief Compares two moves for structural equality.
     * 
     * @details Evaluates whether origin, destination, kind, and promotion piece match.
     * 
     * @param other The move to compare against.
     * @return True if all move properties match, false otherwise.
     */
    bool operator==(const Move& other) const;
};

} // namespace chess
