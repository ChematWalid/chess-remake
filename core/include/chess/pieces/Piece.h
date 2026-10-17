#pragma once

#include <vector>

#include "chess/board/Square.h"
#include "chess/board/Types.h"
#include "chess/rules/Move.h"

namespace chess {

class Board;
class Position;

/**
 * @brief Abstract base class representing an immutable chess piece.
 * 
 * @details Encapsulates piece color, piece type, and polymorphic movement
 * and attack rules. Designed as an immutable flyweight managed by PieceFactory.
 */
class Piece {
private:
    Color pieceColor;
    PieceType pieceType;

protected:
    /**
     * @brief Constructs a piece with a specific color and type.
     * 
     * @details Protected constructor invoked by derived piece implementations.
     * 
     * @param color The piece color (White or Black).
     * @param type The piece classification (Pawn, Knight, etc.).
     */
    Piece(Color color, PieceType type);

public:
    /**
     * @brief Virtual destructor for polymorphic piece hierarchy.
     */
    virtual ~Piece() = default;

    /**
     * @brief Gets the color of the piece.
     * 
     * @details Returns whether the piece belongs to White or Black.
     * 
     * @return The piece Color.
     */
    Color color() const;

    /**
     * @brief Gets the classification of the piece.
     * 
     * @details Returns whether the piece is a Pawn, Knight, Bishop, Rook, Queen, or King.
     * 
     * @return The PieceType enum value.
     */
    PieceType type() const;

    /**
     * @brief Returns the standard FEN character symbol for the piece.
     * 
     * @details Returns uppercase characters for White ('P', 'N', 'B', 'R', 'Q', 'K')
     * and lowercase characters for Black ('p', 'n', 'b', 'r', 'q', 'k').
     * 
     * @return The FEN character representation.
     */
    char symbol() const;

    /**
     * @brief Generates pseudo-legal moves for this piece from a given square.
     * 
     * @details Evaluates board geometry and piece-specific movement capabilities,
     * appending candidate moves to the output vector without king-safety filtering.
     * 
     * @param position The current game position snapshot.
     * @param from The origin square where this piece is located.
     * @param out The destination vector to which generated moves are appended.
     */
    virtual void generateMoves(const Position& position, Square from, std::vector<Move>& out) const = 0;

    /**
     * @brief Tests if this piece attacks a target square on the given board.
     * 
     * @details Determines if the piece threatens the target square regardless of
     * whether the target is occupied by friend or foe, or empty. Used for check detection.
     * 
     * @param board The board layout containing piece placements.
     * @param from The square where this piece is positioned.
     * @param target The square being queried for attack threats.
     * @return True if this piece threatens target, false otherwise.
     */
    virtual bool attacks(const Board& board, Square from, Square target) const = 0;
};

} 
