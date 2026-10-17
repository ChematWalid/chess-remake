#pragma once

#include <array>
#include <optional>
#include <vector>

#include "chess/board/Square.h"
#include "chess/board/Types.h"

namespace chess {

class Piece;

/**
 * @brief Represents the physical 8x8 chessboard and the pieces placed on it.
 * 
 * @details Responsible strictly for piece placement, lookup, and spatial queries.
 * Does not implement move validation, turns, or rule checks (Single Responsibility Principle).
 */
class Board {
private:
    std::array<const Piece*, 64> squares;

public:
    /**
     * @brief Constructs an empty 8x8 chessboard with all 64 squares set to nullptr.
     */
    Board();

    /**
     * @brief Retrieves the piece occupying the specified square.
     * 
     * @param sq The target Square.
     * @return Pointer to the Piece on that square, or nullptr if unoccupied.
     */
    const Piece* pieceAt(Square sq) const;

    /**
     * @brief Places a piece on the specified square, replacing any occupant.
     * 
     * @param piece Const reference to the flyweight piece instance.
     * @param sq Destination Square.
     */
    void place(const Piece& piece, Square sq);

    /**
     * @brief Clears the occupant of the specified square.
     * 
     * @param sq Target Square to empty.
     */
    void remove(Square sq);

    /**
     * @brief Checks if a square is unoccupied.
     * 
     * @param sq Target Square.
     * @return True if nullptr, false if occupied by a piece.
     */
    bool isEmpty(Square sq) const;

    /**
     * @brief Locates the square containing the King of the specified color.
     * 
     * @param color The King's Color (White or Black).
     * @return std::optional containing the King's Square, or std::nullopt if absent.
     */
    std::optional<Square> findKing(Color color) const;

    /**
     * @brief Returns all squares currently occupied by pieces of a given color.
     * 
     * @param color The Color of the player's pieces.
     * @return std::vector containing all matching Squares.
     */
    std::vector<Square> squaresOccupiedBy(Color color) const;

    /**
     * @brief Compares two Board configurations for piece-by-piece equality.
     * 
     * @param other The board to compare against.
     * @return True if all 64 squares hold identical piece pointers, false otherwise.
     */
    bool operator==(const Board& other) const;
};

} 
