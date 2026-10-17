#include "net/GameRoom.h"
#include "chess/notation/Fen.h"
#include "chess/notation/SanNotation.h"
#include "chess/notation/UciNotation.h"
#include "chess/rules/GameRules.h"
#include "net/Protocol.h"
#include <algorithm>
#include <iostream>
#include <random>

namespace net {

namespace {

std::string reasonToString(chess::Reason reason) {
    switch (reason) {
        case chess::Reason::Checkmate: return "Checkmate";
        case chess::Reason::Stalemate: return "Stalemate";
        case chess::Reason::FiftyMoveRule: return "Fifty-move rule";
        case chess::Reason::ThreefoldRepetition: return "Threefold repetition";
        case chess::Reason::InsufficientMaterial: return "Insufficient material";
        case chess::Reason::Resignation: return "Resignation";
        case chess::Reason::DrawAgreement: return "Mutual agreement";
        case chess::Reason::Timeout: return "Timeout";
        case chess::Reason::Abandonment: return "Abandonment";
        default: return "Normal";
    }
}

std::string reasonToMachineCode(chess::Reason reason) {
    switch (reason) {
        case chess::Reason::Checkmate: return "CHECKMATE";
        case chess::Reason::Resignation: return "RESIGNATION";
        case chess::Reason::Timeout: return "TIMEOUT";
        case chess::Reason::Stalemate: return "STALEMATE";
        case chess::Reason::DrawAgreement: return "DRAW_AGREED";
        case chess::Reason::InsufficientMaterial: return "INSUFFICIENT_MATERIAL";
        case chess::Reason::FiftyMoveRule: return "FIFTY_MOVE_RULE";
        case chess::Reason::ThreefoldRepetition: return "THREEFOLD_REPETITION";
        case chess::Reason::Abandonment: return "ABANDONED";
        default: return "UNKNOWN";
    }
}

std::string basePlayerId(const std::string& id) {
    size_t end = id.find_last_not_of("0123456789");
    if (end == std::string::npos) {
        return id;
    }
    return id.substr(0, end + 1);
}

bool isSamePlayer(const std::string& candidateId, const std::string& targetId) {
    if (candidateId.empty() || targetId.empty()) {
        return false;
    }
    if (candidateId == targetId) {
        return true;
    }
    return basePlayerId(candidateId) == basePlayerId(targetId);
}

}

GameRoom::GameRoom(
    std::string roomCode,
    std::shared_ptr<IClientSession> hostSession,
    const TimeControl& timeControl,
    std::optional<chess::Color> preferredColor,
    std::string hostUserId,
    std::string hostDisplayName,
    std::shared_ptr<WebhookClient> webhookClient
)
    : roomCode_(std::move(roomCode)),
      timeControl_(timeControl),
      clock_(std::make_unique<chess::ChessClock>(timeControl_)),
      webhookClient_(std::move(webhookClient)) {

    chess::Color hostColor = chess::Color::White;
    if (preferredColor.has_value()) {
        hostColor = *preferredColor;
    } else {
        static thread_local std::mt19937 rng{std::random_device{}()};
        std::uniform_int_distribution<int> dist(0, 1);
        hostColor = (dist(rng) == 0) ? chess::Color::White : chess::Color::Black;
    }

    std::string effectiveUserId = hostUserId;
    if (effectiveUserId.empty() && hostSession) {
        effectiveUserId = hostSession->getId();
    }
    std::string effectiveDisplayName = hostDisplayName.empty() ? "Player" : hostDisplayName;

    if (hostColor == chess::Color::White) {
        whiteSession_ = hostSession;
        whitePlayerId_ = effectiveUserId;
        whiteDisplayName_ = effectiveDisplayName;
    } else {
        blackSession_ = hostSession;
        blackPlayerId_ = effectiveUserId;
        blackDisplayName_ = effectiveDisplayName;
    }

    if (hostSession) {
        sendToSession(hostSession, Protocol::roomCreated(roomCode_, hostColor));
    }
}

std::string GameRoom::getRoomCode() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return roomCode_;
}

RoomState GameRoom::getState() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return state_;
}

chess::ChessGame GameRoom::getGame() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return game_;
}

const GameRoom::TimeControl& GameRoom::getTimeControl() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return timeControl_;
}

bool GameRoom::join(std::shared_ptr<IClientSession> session) {
    if (!session) {
        return false;
    }
    return joinWithIdentity(session, session->getId(), "Player", "");
}

bool GameRoom::joinWithIdentity(
    std::shared_ptr<IClientSession> session,
    const std::string& userId,
    const std::string& displayName,
    const std::string& preferredColor
) {
    if (!session) {
        return false;
    }

    std::lock_guard<std::mutex> lock(mutex_);

    if ((whiteSession_ && whiteSession_->getId() == session->getId()) ||
        (blackSession_ && blackSession_->getId() == session->getId())) {
        sendToSession(session, Protocol::error("Already seated in this room"));
        return false;
    }

    for (const auto& spec : spectators_) {
        if (spec && spec->getId() == session->getId()) {
            sendToSession(session, Protocol::error("Already spectating this room"));
            return false;
        }
    }

    std::string role;
    bool gameStarting = false;

    if (!whiteSession_ && isSamePlayer(userId, whitePlayerId_)) {
        whiteSession_ = session;
        if (!displayName.empty()) {
            whiteDisplayName_ = displayName;
        }
        whiteDisconnectedAt_.reset();
        dead_ = false;
        role = "white";
    } else if (!blackSession_ && isSamePlayer(userId, blackPlayerId_)) {
        blackSession_ = session;
        if (!displayName.empty()) {
            blackDisplayName_ = displayName;
        }
        blackDisconnectedAt_.reset();
        dead_ = false;
        role = "black";
    } else if (state_ == RoomState::WaitingForOpponent) {
        if (preferredColor == "white" && whitePlayerId_.empty()) {
            whiteSession_ = session;
            whitePlayerId_ = userId;
            whiteDisplayName_ = displayName.empty() ? "White" : displayName;
            role = "white";
            if (blackSession_) {
                gameStarting = true;
            }
        } else if (preferredColor == "black" && blackPlayerId_.empty()) {
            blackSession_ = session;
            blackPlayerId_ = userId;
            blackDisplayName_ = displayName.empty() ? "Black" : displayName;
            role = "black";
            if (whiteSession_) {
                gameStarting = true;
            }
        } else if (whitePlayerId_.empty()) {
            whiteSession_ = session;
            whitePlayerId_ = userId;
            whiteDisplayName_ = displayName.empty() ? "White" : displayName;
            role = "white";
            if (blackSession_) {
                gameStarting = true;
            }
        } else if (blackPlayerId_.empty()) {
            blackSession_ = session;
            blackPlayerId_ = userId;
            blackDisplayName_ = displayName.empty() ? "Black" : displayName;
            role = "black";
            if (whiteSession_) {
                gameStarting = true;
            }
        } else {
            spectators_.push_back(session);
            role = "spectator";
        }
    } else {
        spectators_.push_back(session);
        role = "spectator";
    }

    if (state_ == RoomState::WaitingForOpponent && whiteSession_ && blackSession_) {
        gameStarting = true;
    }

    sendToSession(session, Protocol::roomJoined(roomCode_, role));

    if (gameStarting) {
        if (webhookClient_ && !webhookClient_->markGameStarted(roomCode_, whitePlayerId_, blackPlayerId_)) {
            if (role == "white") {
                whiteSession_.reset();
            } else {
                blackSession_.reset();
            }
            sendToSession(session, Protocol::error("Backend could not confirm this game room"));
            return false;
        }
        state_ = RoomState::InProgress;
        startedAt_ = std::chrono::system_clock::now();
        if (!timeControl_.isUnlimited) {
            clock_->start(chess::Color::White);
        }

        std::string fen = chess::Fen::serialize(game_.position());
        int64_t wMs = getRemainingTimeMs(chess::Color::White);
        int64_t bMs = getRemainingTimeMs(chess::Color::Black);

        std::string startMsg = Protocol::gameStarted(
            roomCode_, fen, timeControl_, wMs, bMs, getAllMovesSan()
        );
        if (whiteSession_) sendToSession(whiteSession_, startMsg);
        if (blackSession_) sendToSession(blackSession_, startMsg);
        for (const auto& spec : spectators_) {
            sendToSession(spec, startMsg);
        }
    } else if (state_ == RoomState::InProgress) {
        std::string fen = chess::Fen::serialize(game_.position());
        int64_t wMs = getRemainingTimeMs(chess::Color::White);
        int64_t bMs = getRemainingTimeMs(chess::Color::Black);
        sendToSession(
            session,
            Protocol::gameStarted(roomCode_, fen, timeControl_, wMs, bMs, getAllMovesSan())
        );
    }

    return true;
}

void GameRoom::setWebhookClient(std::shared_ptr<WebhookClient> webhookClient) {
    std::lock_guard<std::mutex> lock(mutex_);
    webhookClient_ = std::move(webhookClient);
}

void GameRoom::handleMove(const std::string& sessionId, const std::string& moveStr) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (state_ != RoomState::InProgress) {
        sendToSession(
            (whiteSession_ && whiteSession_->getId() == sessionId) ? whiteSession_ : blackSession_,
            Protocol::error("Game is not in progress")
        );
        return;
    }

    auto colorOpt = getPlayerColorLocked(sessionId);
    if (!colorOpt.has_value()) {
        return;
    }
    chess::Color color = *colorOpt;

    if (game_.position().sideToMove() != color) {
        sendToPlayer(color, Protocol::error("Not your turn"));
        return;
    }

    if (!timeControl_.isUnlimited && clock_->isFlagged(color)) {
        game_.declareTimeout(color);
        finishGameWithResult(game_.result());
        return;
    }

    chess::SanNotation sanParser;
    chess::UciNotation uciParser;

    auto parsedMove = sanParser.parse(game_.position(), moveStr);
    if (!parsedMove) {
        parsedMove = uciParser.parse(game_.position(), moveStr);
    }

    if (!parsedMove) {
        sendToPlayer(color, Protocol::error("Invalid or illegal move: " + moveStr));
        return;
    }

    std::string sanStr = sanParser.serialize(game_.position(), *parsedMove);
    std::string uciStr = parsedMove->toUci();

    auto playResult = game_.play(color, parsedMove->from(), parsedMove->to(), parsedMove->promotion());
    if (!playResult) {
        sendToPlayer(color, Protocol::error("Move rejected by game rules"));
        return;
    }

    if (!timeControl_.isUnlimited) {
        clock_->press(color);
    }

    std::string newFen = chess::Fen::serialize(game_.position());
    chess::Color nextSide = game_.position().sideToMove();
    bool isCheck = game_.position().isInCheck(nextSide);
    int64_t wMs = getRemainingTimeMs(chess::Color::White);
    int64_t bMs = getRemainingTimeMs(chess::Color::Black);

    std::string moveMsg = Protocol::moveMade(sanStr, uciStr, newFen, nextSide, isCheck, wMs, bMs);
    broadcast(moveMsg);

    if (game_.isOver()) {
        finishGameWithResult(game_.result());
    }
}

void GameRoom::handleOfferDraw(const std::string& sessionId) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (state_ != RoomState::InProgress) return;

    auto colorOpt = getPlayerColorLocked(sessionId);
    if (!colorOpt.has_value()) return;
    chess::Color color = *colorOpt;

    game_.offerDraw(color);

    chess::Color opponent = (color == chess::Color::White) ? chess::Color::Black : chess::Color::White;
    sendToPlayer(opponent, Protocol::drawOffered(color));
}

void GameRoom::handleAcceptDraw(const std::string& sessionId) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (state_ != RoomState::InProgress) return;

    auto colorOpt = getPlayerColorLocked(sessionId);
    if (!colorOpt.has_value()) return;
    chess::Color color = *colorOpt;

    if (!game_.acceptDraw(color)) {
        sendToPlayer(color, Protocol::error("No draw offer to accept"));
        return;
    }

    finishGameWithResult(game_.result());
}

void GameRoom::handleDeclineDraw(const std::string& sessionId) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (state_ != RoomState::InProgress) return;

    auto colorOpt = getPlayerColorLocked(sessionId);
    if (!colorOpt.has_value()) return;
    chess::Color color = *colorOpt;

    game_.declineDraw();

    chess::Color opponent = (color == chess::Color::White) ? chess::Color::Black : chess::Color::White;
    sendToPlayer(opponent, Protocol::drawDeclined());
}

void GameRoom::handleResign(const std::string& sessionId) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (state_ != RoomState::InProgress) return;

    auto colorOpt = getPlayerColorLocked(sessionId);
    if (!colorOpt.has_value()) return;
    chess::Color color = *colorOpt;

    game_.resign(color);
    finishGameWithResult(game_.result());
}

void GameRoom::handleDisconnect(const std::string& sessionId) {
    std::lock_guard<std::mutex> lock(mutex_);

    std::erase_if(spectators_, [&](const auto& s) {
        return s && s->getId() == sessionId;
    });

    if (whiteSession_ && whiteSession_->getId() == sessionId) {
        whiteSession_.reset();
        whiteDisconnectedAt_ = std::chrono::steady_clock::now();
        if (state_ == RoomState::InProgress && blackSession_) {
            sendToSession(blackSession_, Protocol::opponentDisconnected());
        } else if (state_ == RoomState::WaitingForOpponent) {
            state_ = RoomState::Finished;
            dead_ = true;
        }
    } else if (blackSession_ && blackSession_->getId() == sessionId) {
        blackSession_.reset();
        blackDisconnectedAt_ = std::chrono::steady_clock::now();
        if (state_ == RoomState::InProgress && whiteSession_) {
            sendToSession(whiteSession_, Protocol::opponentDisconnected());
        } else if (state_ == RoomState::WaitingForOpponent) {
            state_ = RoomState::Finished;
            dead_ = true;
        }
    }
}

void GameRoom::tickClock() {
    std::lock_guard<std::mutex> lock(mutex_);

    if (state_ == RoomState::WaitingForOpponent
            && std::chrono::steady_clock::now() - createdAt_ >= startTimeout_) {
        dead_ = true;
        state_ = RoomState::Finished;
        broadcast(Protocol::error("Game room expired before both players joined"));
        return;
    }

    if (state_ != RoomState::InProgress) {
        return;
    }

    auto now = std::chrono::steady_clock::now();
    if (whiteDisconnectedAt_.has_value() &&
        (now - *whiteDisconnectedAt_) >= disconnectGracePeriod_) {
        game_.declareAbandonment(chess::Color::White);
        finishGameWithResult(game_.result());
        dead_ = true;
        return;
    }

    if (blackDisconnectedAt_.has_value() &&
        (now - *blackDisconnectedAt_) >= disconnectGracePeriod_) {
        game_.declareAbandonment(chess::Color::Black);
        finishGameWithResult(game_.result());
        dead_ = true;
        return;
    }

    if (!timeControl_.isUnlimited) {
        chess::Color side = game_.position().sideToMove();
        if (clock_->isFlagged(side)) {
            game_.declareTimeout(side);
            finishGameWithResult(game_.result());
            dead_ = true;
        }
    }
}

void GameRoom::broadcast(const std::string& message) {
    if (whiteSession_) sendToSession(whiteSession_, message);
    if (blackSession_) sendToSession(blackSession_, message);
    for (const auto& spec : spectators_) {
        sendToSession(spec, message);
    }
}

void GameRoom::setDisconnectGracePeriod(std::chrono::seconds gracePeriod) {
    std::lock_guard<std::mutex> lock(mutex_);
    disconnectGracePeriod_ = gracePeriod;
}

void GameRoom::setStartTimeout(std::chrono::seconds timeout) {
    std::lock_guard<std::mutex> lock(mutex_);
    startTimeout_ = timeout;
}

void GameRoom::markDead() {
    std::lock_guard<std::mutex> lock(mutex_);
    dead_ = true;
}

bool GameRoom::isDead() const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (dead_) {
        return true;
    }
    if (!whiteSession_ && !blackSession_ && spectators_.empty()) {
        return true;
    }
    return false;
}

std::optional<chess::Color> GameRoom::getPlayerColor(const std::string& sessionId) const {
    std::lock_guard<std::mutex> lock(mutex_);
    return getPlayerColorLocked(sessionId);
}

std::optional<chess::Color> GameRoom::getPlayerColorLocked(const std::string& sessionId) const {
    if (whiteSession_ && whiteSession_->getId() == sessionId) {
        return chess::Color::White;
    }
    if (blackSession_ && blackSession_->getId() == sessionId) {
        return chess::Color::Black;
    }
    return std::nullopt;
}

void GameRoom::sendToSession(const std::shared_ptr<IClientSession>& session, const std::string& msg) {
    if (session) {
        session->send(msg);
    }
}

void GameRoom::sendToPlayer(chess::Color color, const std::string& msg) {
    if (color == chess::Color::White) {
        sendToSession(whiteSession_, msg);
    } else {
        sendToSession(blackSession_, msg);
    }
}

int64_t GameRoom::getRemainingTimeMs(chess::Color color) const {
    if (timeControl_.isUnlimited) {
        return -1;
    }
    return clock_->timeRemaining(color).count();
}

void GameRoom::finishGame(const std::string& resultStr,
                          std::optional<chess::Color> winner,
                          const std::string& reasonDescription,
                          const std::string& reasonCode) {
    state_ = RoomState::Finished;
    if (!timeControl_.isUnlimited) {
        clock_->pause();
    }
    if (startedAt_.has_value()) {
        durationSeconds_ = std::chrono::duration_cast<std::chrono::seconds>(
            std::chrono::system_clock::now() - *startedAt_
        ).count();
    }
    std::string pgn = buildPgn(resultStr);
    broadcast(Protocol::gameOver(resultStr, winner, reasonDescription, reasonCode, pgn, getAllFens(), getAllMovesSan()));

    if (webhookClient_) {
        GameRecordPayload payload;
        payload.roomCode = roomCode_;
        payload.roomToken = roomCode_;
        payload.whitePlayerId = whitePlayerId_;
        payload.blackPlayerId = blackPlayerId_;
        if (winner.has_value()) {
            payload.winnerPlayerId = (*winner == chess::Color::White) ? whitePlayerId_ : blackPlayerId_;
        }
        payload.isDraw = (resultStr == "Draw");
        payload.terminationReason = reasonDescription;
        payload.reasonCode = reasonCode;
        payload.totalMoves = static_cast<int>(game_.movesHistory().size());
        payload.durationSeconds = durationSeconds_;
        payload.pgn = pgn;
        payload.moves = getAllMovesSan();
        payload.fens = getAllFens();

        try {
            webhookClient_->enqueue(payload);
        } catch (const std::exception& exception) {
            std::cerr << "CRITICAL: completed game could not be written to webhook outbox for room "
                      << roomCode_ << ": " << exception.what() << '\n';
        }
    }
}

void GameRoom::finishGameWithResult(const chess::GameResult& result) {
    std::string resultStr;
    std::optional<chess::Color> winner;
    switch (result.outcome) {
        case chess::Outcome::WhiteWins:
            resultStr = "WhiteWins";
            winner = chess::Color::White;
            break;
        case chess::Outcome::BlackWins:
            resultStr = "BlackWins";
            winner = chess::Color::Black;
            break;
        case chess::Outcome::Draw:
            resultStr = "Draw";
            winner = std::nullopt;
            break;
        default:
            resultStr = "Unknown";
            break;
    }
    finishGame(resultStr, winner, reasonToString(result.reason), reasonToMachineCode(result.reason));
}

std::string GameRoom::buildPgn(const std::string& resultStr) const {
    chess::SanNotation san;
    std::ostringstream oss;

    std::string utcDateTag = "1970.01.01";
    auto timePoint = startedAt_.value_or(std::chrono::system_clock::now());
    auto timeT = std::chrono::system_clock::to_time_t(timePoint);
    std::tm tmUtc;
    if (gmtime_r(&timeT, &tmUtc)) {
        char dateBuf[32];
        if (std::strftime(dateBuf, sizeof(dateBuf), "%Y.%m.%d", &tmUtc) > 0) {
            utcDateTag = dateBuf;
        }
    }

    std::string resTag = (resultStr == "WhiteWins") ? "1-0" : ((resultStr == "BlackWins") ? "0-1" : "1/2-1/2");

    oss << "[Event \"Live Room\"]\n";
    oss << "[Site \"Chess Server\"]\n";
    oss << "[Date \"" << utcDateTag << "\"]\n";
    oss << "[Round \"-\"]\n";
    oss << "[White \"" << (whiteDisplayName_.empty() ? "White" : whiteDisplayName_) << "\"]\n";
    oss << "[Black \"" << (blackDisplayName_.empty() ? "Black" : blackDisplayName_) << "\"]\n";
    oss << "[Result \"" << resTag << "\"]\n";

    if (timeControl_.isUnlimited) {
        oss << "[TimeControl \"-\"]\n\n";
    } else {
        int64_t initSec = timeControl_.initialTime.count() / 1000;
        int64_t incSec = timeControl_.increment.count() / 1000;
        oss << "[TimeControl \"" << initSec << "+" << incSec << "\"]\n\n";
    }

    const auto& positions = game_.positionHistory();
    const auto& moves = game_.movesHistory();
    for (size_t i = 0; i < moves.size(); ++i) {
        if (i % 2 == 0) {
            oss << (i / 2 + 1) << ". ";
        }
        if (i < positions.size()) {
            oss << san.serialize(positions[i], moves[i]) << " ";
        }
    }
    oss << resTag;
    return oss.str();
}

std::vector<std::string> GameRoom::getAllFens() const {
    std::vector<std::string> fens;
    for (const auto& pos : game_.positionHistory()) {
        fens.push_back(chess::Fen::serialize(pos));
    }
    return fens;
}

std::vector<std::string> GameRoom::getAllMovesSan() const {
    chess::SanNotation san;
    std::vector<std::string> sanMoves;
    const auto& positions = game_.positionHistory();
    const auto& moves = game_.movesHistory();
    for (size_t i = 0; i < moves.size(); ++i) {
        if (i < positions.size()) {
            sanMoves.push_back(san.serialize(positions[i], moves[i]));
        }
    }
    return sanMoves;
}

}
