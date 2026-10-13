#pragma once

#include <expected>
#include <optional>
#include <vector>

#include "chess/board/Position.h"
#include "chess/board/Square.h"
#include "chess/board/Types.h"
#include "chess/rules/GameRules.h"
#include "chess/rules/Move.h"
#include "chess/rules/MoveGenerator.h"

namespace chess {

/**
 * @brief Represents an active or completed game of chess.
 * 
 * @details Manages state transitions, position history, move logs, turn validation,
 * resignations, mutual draw agreements, timeouts, and automatic game termination detection (checkmate/draws).
 */
class ChessGame {
private:
    Position currentPosition;
    std::vector<Position> history;
    std::vector<Move> moves;
    GameResult currentResult;
    std::optional<Color> drawOfferedBy;

    void updateGameResult();

public:
    /**
     * @brief Constructs a chess game initialized to the standard opening position.
     */
    ChessGame();

    /**
     * @brief Constructs a chess game starting from an arbitrary custom position.
     * 
     * @param startingPosition The initial Position.
     */
    explicit ChessGame(Position startingPosition);

    /**
     * @brief Attempts to execute a move on behalf of the specified player.
     * 
     * @details Enforces turn order (returning NotYourTurn if player does not match side to move),
     * resolves the move against the legal move list, updates position history, and checks termination conditions.
     * 
     * @param player The color of the player attempting the move.
     * @param from The origin square.
     * @param to The target square.
     * @param promo The piece type to promote to, if applicable.
     * @return The executed Move with all flags, or MoveError on failure.
     */
    std::expected<Move, MoveError> play(Color player,
                                        Square from,
                                        Square to,
                                        std::optional<PieceType> promo = std::nullopt);

    /**
     * @brief Declares resignation by the specified player color.
     * 
     * @param resigningColor The color of the player resigning.
     */
    void resign(Color resigningColor);

    /**
     * @brief Submits a draw offer by the specified player.
     * 
     * @param player The color of the player offering the draw.
     */
    void offerDraw(Color player);

    /**
     * @brief Accepts a pending draw offer if one was proposed by the opponent.
     * 
     * @param player The color of the player accepting the draw.
     * @return True if a valid opponent offer was accepted, false otherwise.
     */
    bool acceptDraw(Color player);

    /**
     * @brief Declines any pending draw offer.
     */
    void declineDraw();

    /**
     * @brief Declares game termination due to a clock timeout.
     * 
     * @details Under FIDE Rule 6.9, if the non-flagged player has insufficient mating material,
     * the game outcome is recorded as a Draw rather than a win.
     * 
     * @param loser The player color whose clock expired.
     */
    void declareTimeout(Color loser);

    /**
     * @brief Declares game termination due to player abandonment after disconnect timeout.
     * 
     * @param loser The player color who abandoned the match.
     */
    void declareAbandonment(Color loser);

    /**
     * @brief Gets the current position snapshot.
     * 
     * @return Const reference to current Position.
     */
    const Position& position() const;

    /**
     * @brief Gets the chronological record of all positions reached in the game.
     * 
     * @return Const reference to position history vector.
     */
    const std::vector<Position>& positionHistory() const;

    /**
     * @brief Gets the chronological record of all executed moves.
     * 
     * @return Const reference to move history vector.
     */
    const std::vector<Move>& movesHistory() const;

    /**
     * @brief Computes all currently legal moves for the active player.
     * 
     * @return Vector of legal Move objects, or empty vector if the game is over.
     */
    std::vector<Move> legalMoves() const;

    /**
     * @brief Gets the current outcome and justification reason.
     * 
     * @return Const reference to GameResult.
     */
    const GameResult& result() const;

    /**
     * @brief Checks if the game has ended (by checkmate, draw, resignation, or timeout).
     * 
     * @return True if outcome is not Outcome::Ongoing, false otherwise.
     */
    bool isOver() const;

    /**
     * @brief Gets the player color that currently has an active draw offer, if any.
     * 
     * @return Optional containing the proposing Color, or std::nullopt.
     */
    std::optional<Color> pendingDrawOffer() const;
};

} // namespace chess
