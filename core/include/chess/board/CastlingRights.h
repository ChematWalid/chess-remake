#pragma once

#include <cstdint>
#include <string>

#include "chess/board/Types.h"

namespace chess {

/**
 * @brief Manages castling availability flags for both players.
 * 
 * @details Compact bitmask representation of White King-side (K), White Queen-side (Q),
 * Black King-side (k), and Black Queen-side (q) castling rights.
 */
class CastlingRights {
private:
    uint8_t mask;

    explicit CastlingRights(uint8_t m);

public:
    /**
     * @brief Constructs a CastlingRights instance with no rights enabled.
     * 
     * @details Equivalent to CastlingRights::none(), representing "-" in FEN notation.
     */
    CastlingRights();

    /**
     * @brief Factory creating a CastlingRights instance with all 4 rights enabled.
     * 
     * @details Used at the beginning of a standard chess match ("KQkq").
     * 
     * @return CastlingRights with White and Black KingSide and QueenSide rights.
     */
    static CastlingRights all();

    /**
     * @brief Factory creating a CastlingRights instance with no rights available.
     * 
     * @details Represents games where all castling rights have been lost or revoked.
     * 
     * @return CastlingRights with 0 flags active ("-").
     */
    static CastlingRights none();

    /**
     * @brief Checks if a specific color and side has castling privilege.
     * 
     * @details Evaluates the bitmask for the requested color and board flank.
     * 
     * @param color The player color (White or Black).
     * @param side The flank (KingSide or QueenSide).
     * @return True if the right is still active, false otherwise.
     */
    bool has(Color color, Side side) const;

    /**
     * @brief Revokes castling privilege for a specific color and side.
     * 
     * @details Clears the corresponding bit (e.g. when a rook moves or is captured).
     * 
     * @param color The player color.
     * @param side The side whose privilege is revoked.
     */
    void remove(Color color, Side side);

    /**
     * @brief Revokes both KingSide and QueenSide castling rights for a player.
     * 
     * @details Called when a player's King moves, permanently losing all castling rights.
     * 
     * @param color The player color whose castling rights are removed.
     */
    void removeAll(Color color);

    /**
     * @brief Serializes castling availability into standard FEN notation.
     * 
     * @details Returns combinations of "KQkq", or "-" if no rights remain.
     * 
     * @return The FEN castling substring.
     */
    std::string toString() const;

    /**
     * @brief Parses standard FEN castling characters into a CastlingRights instance.
     * 
     * @details Parses characters 'K', 'Q', 'k', 'q', or "-" for none.
     * 
     * @param str The FEN castling substring.
     * @return The parsed CastlingRights object.
     * @throws std::invalid_argument If the string contains unrecognized characters.
     */
    static CastlingRights fromString(const std::string& str);

    /**
     * @brief Compares two CastlingRights objects for bitmask equality.
     * 
     * @details Checks whether both instances hold identical castling permissions.
     * 
     * @param other The other CastlingRights object to compare with.
     * @return True if all active flags match, false otherwise.
     */
    bool operator==(const CastlingRights& other) const;
};

} 
