#pragma once

namespace chess {

/**
 * @brief Represents the two player colors in chess.
 * 
 * @details Used to determine piece ownership, turn order, and orientation
 * for directional movements such as pawn pushes and castling rights.
 */
enum class Color {
    White,
    Black
};

/**
 * @brief Enumerates the six standard chess piece types.
 * 
 * @details Represents the classification of a piece independent of its color.
 */
enum class PieceType {
    Pawn,
    Knight,
    Bishop,
    Rook,
    Queen,
    King
};

/**
 * @brief Represents the side of the board for castling.
 * 
 * @details KingSide refers to the e/f/g/h files (O-O), while QueenSide refers
 * to the a/b/c/d/e files (O-O-O).
 */
enum class Side {
    KingSide,
    QueenSide
};

/**
 * @brief Categorizes the special mechanics and types of a chess move.
 * 
 * @details Distinguishes standard movements from special rule moves
 * such as castling, double pawn steps, promotions, and en passant.
 */
enum class MoveKind {
    Normal,
    DoublePawnPush,
    EnPassant,
    Castle,
    Promotion
};

/**
 * @brief Represents failure reasons when attempting a move.
 * 
 * @details Used for error reporting when a player attempts an action
 * that violates turn order, legality, or the current game status.
 */
enum class MoveError {
    GameOver,
    NotYourTurn,
    IllegalMove
};

/**
 * @brief Represents a 2D coordinate delta on the chessboard grid.
 * 
 * @details Used by sliding and stepping piece movement algorithms to step
 * across files (columns) and ranks (rows).
 */
struct Direction {
    /** @brief The horizontal file offset, with positive values pointing right. */
    int deltaFile;
    /** @brief The vertical rank offset, with positive values pointing up. */
    int deltaRank;
};

/**
 * @brief Represents the overall state of a chess game.
 * 
 * @details Indicates whether play is active or which party achieved victory.
 */
enum class Outcome {
    Ongoing,
    WhiteWins,
    BlackWins,
    Draw
};

/**
 * @brief The explicit rule or condition that caused a game termination.
 * 
 * @details Stored in the game record to explain why a game reached its outcome.
 */
enum class Reason {
    None,
    Checkmate,
    Stalemate,
    FiftyMoveRule,
    ThreefoldRepetition,
    InsufficientMaterial,
    Resignation,
    DrawAgreement,
    Timeout,
    Abandonment
};

/**
 * @brief Summarizes the final outcome and its specific justification.
 * 
 * @details Combines the high-level result (win/loss/draw) with the exact rule trigger.
 */
struct GameResult {
    /** @brief The final game outcome. */
    Outcome outcome;
    /** @brief The specific condition that ended the game. */
    Reason reason;
};

} 
