#pragma once

#include <optional>
#include <string>

#include "chess/board/Board.h"
#include "chess/board/CastlingRights.h"
#include "chess/board/Square.h"
#include "chess/board/Types.h"
#include "chess/rules/Move.h"

namespace chess {

/**
 * @brief Represents an immutable snapshot of an entire chess position.
 * 
 * @details Contains everything necessary to completely specify the game state at a given turn:
 * piece placement on the board, player turn, castling rights, en passant availability,
 * halfmove clock (for the 50-move rule), and fullmove counter.
 */
class Position {
private:
    Board currentBoard;
    Color side;
    CastlingRights castlingRights;
    std::optional<Square> enPassantSquare;
    int halfmoves;
    int fullmoves;

public:
    /**
     * @brief Constructs an empty default position.
     * 
     * @details Initializes an empty board, White to move, no castling rights,
     * halfmove clock set to 0, and fullmove number set to 1.
     */
    Position();

    /**
     * @brief Constructs a position with explicitly specified state attributes.
     * 
     * @param board The piece configuration on the 64 squares.
     * @param sideToMove The color of the player whose turn it is.
     * @param castling The active castling rights.
     * @param enPassant The target en passant capture square, if any.
     * @param halfmoveClock Count of halfmoves since last pawn push or capture.
     * @param fullmoveNumber The current fullmove turn count.
     */
    Position(Board board,
             Color sideToMove,
             CastlingRights castling,
             std::optional<Square> enPassant = std::nullopt,
             int halfmoveClock = 0,
             int fullmoveNumber = 1);

    /**
     * @brief Gets the underlying board layout.
     * 
     * @details Provides read-only access to the 64 board squares and piece locations.
     * 
     * @return Const reference to the current Board.
     */
    const Board& board() const;

    /**
     * @brief Gets the player color whose turn it is to move.
     * 
     * @return Color::White or Color::Black.
     */
    Color sideToMove() const;

    /**
     * @brief Gets the active castling privileges.
     * 
     * @return Const reference to the current CastlingRights.
     */
    const CastlingRights& castling() const;

    /**
     * @brief Gets the square subject to en passant capture, if available.
     * 
     * @details Present immediately following a double pawn push by the opponent.
     * 
     * @return Optional containing the en passant target Square, or std::nullopt.
     */
    std::optional<Square> enPassant() const;

    /**
     * @brief Gets the halfmove clock for the fifty-move draw rule.
     * 
     * @details Number of halfmoves (plies) elapsed since the last capture or pawn push.
     * 
     * @return The halfmove count as an integer.
     */
    int halfmoveClock() const;

    /**
     * @brief Gets the number of full turns played.
     * 
     * @details Starts at 1 and increments after every move completed by Black.
     * 
     * @return The 1-based fullmove number.
     */
    int fullmoveNumber() const;

    /**
     * @brief Determines whether a given board square is attacked by an opponent.
     * 
     * @details Checks whether any piece belonging to byColor threatens the square.
     * 
     * @param sq The square being checked for incoming attacks.
     * @param byColor The attacking player's color.
     * @return True if at least one piece of byColor attacks sq, false otherwise.
     */
    bool isAttacked(Square sq, Color byColor) const;

    /**
     * @brief Determines whether the specified player's King is currently in check.
     * 
     * @details Finds the King of the specified color and checks if its square is attacked.
     * 
     * @param color The player whose King safety is being evaluated.
     * @return True if the King is attacked by an enemy piece, false otherwise.
     */
    bool isInCheck(Color color) const;

    /**
     * @brief Returns a new Position resulting from playing a move on this position.
     * 
     * @details Computes the state transition by applying piece movement, captures,
     * en passant pawns, castling rook shifts, pawn promotions, castling revocations,
     * clock resets, and switching sideToMove.
     * 
     * @param move The move to execute.
     * @return The new immutable Position following the move.
     */
    Position after(const Move& move) const;

    /**
     * @brief Compares two positions for full state equality.
     * 
     * @details Compares board layout, active player, castling rights, en passant,
     * halfmove clock, and fullmove number.
     * 
     * @param other The position to compare against.
     * @return True if all state attributes match identically, false otherwise.
     */
    bool operator==(const Position& other) const;
};

} // namespace chess
