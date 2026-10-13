#pragma once

#include "chess/board/Types.h"
#include "chess/pieces/Piece.h"

namespace chess {

/**
 * @brief Flyweight registry providing access to the 12 immutable chess piece instances.
 * 
 * @details Avoids dynamic piece allocations by keeping singletons for each of the
 * 6 piece types across both player colors.
 */
class PieceFactory {
public:
    /**
     * @brief Retrieves the immutable flyweight Piece instance for a color and type.
     * 
     * @details Provides O(1) access to pre-instantiated piece singletons.
     * 
     * @param color The player color of the requested piece.
     * @param type The classification of the requested piece.
     * @return Const reference to the immutable Piece.
     */
    static const Piece& get(Color color, PieceType type);

    /**
     * @brief Retrieves a piece instance matching a standard FEN symbol.
     * 
     * @details Maps uppercase characters to White ('P', 'N', 'B', 'R', 'Q', 'K')
     * and lowercase characters to Black ('p', 'n', 'b', 'r', 'q', 'k').
     * 
     * @param symbol The FEN character symbol.
     * @return Const reference to the corresponding Piece flyweight.
     * @throws std::invalid_argument If the character does not correspond to a valid piece symbol.
     */
    static const Piece& fromSymbol(char symbol);
};

} // namespace chess
