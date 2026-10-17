#include "chess/rules/ChessGame.h"
#include "chess/notation/Fen.h"
#include "chess/pieces/Piece.h"

namespace chess {

ChessGame::ChessGame()
    : currentPosition(Fen::standard()),
      history{currentPosition},
      moves{},
      currentResult{Outcome::Ongoing, Reason::None},
      drawOfferedBy(std::nullopt) {
    updateGameResult();
}

ChessGame::ChessGame(Position startingPosition)
    : currentPosition(std::move(startingPosition)),
      history{currentPosition},
      moves{},
      currentResult{Outcome::Ongoing, Reason::None},
      drawOfferedBy(std::nullopt) {
    updateGameResult();
}

std::expected<Move, MoveError> ChessGame::play(Color player,
                                               Square from,
                                               Square to,
                                               std::optional<PieceType> promo) {
    if (isOver()) {
        return std::unexpected(MoveError::GameOver);
    }

    if (player != currentPosition.sideToMove()) {
        return std::unexpected(MoveError::NotYourTurn);
    }

    auto resolvedMove = MoveGenerator::resolve(currentPosition, from, to, promo);
    if (!resolvedMove.has_value()) {
        return std::unexpected(MoveError::IllegalMove);
    }

    Move move = *resolvedMove;
    currentPosition = currentPosition.after(move);
    history.push_back(currentPosition);
    moves.push_back(move);

    drawOfferedBy = std::nullopt;

    updateGameResult();

    return move;
}

void ChessGame::resign(Color resigningColor) {
    if (isOver()) {
        return;
    }
    Outcome outcome = (resigningColor == Color::White) ? Outcome::BlackWins : Outcome::WhiteWins;
    currentResult = GameResult{outcome, Reason::Resignation};
}

void ChessGame::offerDraw(Color player) {
    if (isOver()) {
        return;
    }
    drawOfferedBy = player;
}

bool ChessGame::acceptDraw(Color player) {
    if (isOver() || !drawOfferedBy.has_value()) {
        return false;
    }

    if (drawOfferedBy.value() == player) {
        return false;
    }

    currentResult = GameResult{Outcome::Draw, Reason::DrawAgreement};
    drawOfferedBy = std::nullopt;
    return true;
}

void ChessGame::declineDraw() {
    drawOfferedBy = std::nullopt;
}

void ChessGame::declareTimeout(Color loser) {
    if (isOver()) {
        return;
    }

    Color winner = (loser == Color::White) ? Color::Black : Color::White;
    bool winnerCannotMate = GameRules::hasInsufficientMaterial(currentPosition.board());

    if (!winnerCannotMate) {
        bool hasPieces = false;
        for (int file = 0; file < 8; ++file) {
            for (int rank = 0; rank < 8; ++rank) {
                const Piece* p = currentPosition.board().pieceAt(Square(file, rank));
                if (p != nullptr && p->color() == winner && p->type() != PieceType::King) {
                    hasPieces = true;
                    break;
                }
            }
            if (hasPieces) {
                break;
            }
        }
        if (!hasPieces) {
            winnerCannotMate = true;
        }
    }

    if (winnerCannotMate) {
        currentResult = GameResult{Outcome::Draw, Reason::Timeout};
    } else {
        Outcome outcome = (loser == Color::White) ? Outcome::BlackWins : Outcome::WhiteWins;
        currentResult = GameResult{outcome, Reason::Timeout};
    }
}

void ChessGame::declareAbandonment(Color loser) {
    if (isOver()) {
        return;
    }
    Outcome outcome = (loser == Color::White) ? Outcome::BlackWins : Outcome::WhiteWins;
    currentResult = GameResult{outcome, Reason::Abandonment};
}

const Position& ChessGame::position() const {
    return currentPosition;
}

const std::vector<Position>& ChessGame::positionHistory() const {
    return history;
}

const std::vector<Move>& ChessGame::movesHistory() const {
    return moves;
}

std::vector<Move> ChessGame::legalMoves() const {
    if (isOver()) {
        return {};
    }
    return MoveGenerator::legalMoves(currentPosition);
}

const GameResult& ChessGame::result() const {
    return currentResult;
}

bool ChessGame::isOver() const {
    return currentResult.outcome != Outcome::Ongoing;
}

std::optional<Color> ChessGame::pendingDrawOffer() const {
    return drawOfferedBy;
}

void ChessGame::updateGameResult() {
    if (GameRules::isCheckmate(currentPosition)) {
        Outcome outcome = (currentPosition.sideToMove() == Color::White)
                              ? Outcome::BlackWins
                              : Outcome::WhiteWins;
        currentResult = GameResult{outcome, Reason::Checkmate};
        return;
    }

    if (GameRules::isStalemate(currentPosition)) {
        currentResult = GameResult{Outcome::Draw, Reason::Stalemate};
        return;
    }

    if (GameRules::isFiftyMoveDraw(currentPosition)) {
        currentResult = GameResult{Outcome::Draw, Reason::FiftyMoveRule};
        return;
    }

    if (GameRules::isThreefoldRepetition(history)) {
        currentResult = GameResult{Outcome::Draw, Reason::ThreefoldRepetition};
        return;
    }

    if (GameRules::hasInsufficientMaterial(currentPosition.board())) {
        currentResult = GameResult{Outcome::Draw, Reason::InsufficientMaterial};
        return;
    }
}

} 
