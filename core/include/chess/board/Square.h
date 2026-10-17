#pragma once

#include <cstdint>
#include <optional>
#include <string>

namespace chess {

/**
 * @brief Represents a single coordinate square on the 8x8 chessboard.
 * 
 * @details Implemented as a lightweight value type wrapping an internal 0-63 0-indexed integer.
 * Files are columns (0 = 'a', 7 = 'h'), ranks are rows (0 = '1', 7 = '8').
 */
class Square {
private:
    int8_t idx;

public:
    /**
     * @brief Constructs a Square from 0-based file (column) and rank (row) coordinates.
     * 
     * @param file The horizontal column index [0, 7].
     * @param rank The vertical row index [0, 7].
     * @throws std::out_of_range If file or rank is outside the valid [0, 7] interval.
     */
    Square(int file, int rank);

    /**
     * @brief Parses standard two-character algebraic coordinate notation (e.g., "e4").
     * 
     * @param s The coordinate string.
     * @return The corresponding Square instance.
     * @throws std::invalid_argument If the string is not exactly two characters or outside valid range.
     */
    static Square fromString(const std::string& s);

    /**
     * @brief Computes a new square offset by deltaFile and deltaRank.
     * 
     * @param s The starting origin square.
     * @param df Horizontal displacement (-7 to +7).
     * @param dr Vertical displacement (-7 to +7).
     * @return std::optional containing the target Square, or std::nullopt if off the board.
     */
    static std::optional<Square> tryOffset(Square s, int df, int dr);

    /**
     * @brief Returns the 0-based horizontal file index (0 for 'a', 7 for 'h').
     * 
     * @return File coordinate in [0, 7].
     */
    int file() const;

    /**
     * @brief Returns the 0-based vertical rank index (0 for '1', 7 for '8').
     * 
     * @return Rank coordinate in [0, 7].
     */
    int rank() const;

    /**
     * @brief Returns the 0-63 flattened grid index (rank * 8 + file).
     * 
     * @return Flat index in [0, 63].
     */
    int index() const;

    /**
     * @brief Formats the square into standard two-character algebraic notation (e.g. "e4").
     * 
     * @return Algebraic coordinate string.
     */
    std::string toString() const;

    /**
     * @brief Compares two Square instances for equality.
     * 
     * @param other The Square to compare against.
     * @return True if both squares designate the exact same board coordinate, false otherwise.
     */
    bool operator==(const Square& other) const;
};

} 
