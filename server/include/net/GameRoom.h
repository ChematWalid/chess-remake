#pragma once

#include "chess/rules/ChessClock.h"
#include "chess/rules/ChessGame.h"
#include "net/IClientSession.h"
#include "net/WebhookClient.h"
#include <chrono>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <vector>

namespace net {

enum class RoomState {
    WaitingForOpponent,
    InProgress,
    Finished
};

/**
 * @brief Thread-safe game room hosting an authoritative chess game between two players.
 *
 * Supports spectators, millisecond chess clocks or unlimited (no time limit) clocks,
 * and handles FIDE moves, draw protocols, timeouts, and resignations.
 */
class GameRoom {
public:
    using TimeControl = chess::ChessClock::TimeControl;

    GameRoom(
        std::string roomCode,
        std::shared_ptr<IClientSession> hostSession,
        const TimeControl& timeControl,
        std::optional<chess::Color> preferredColor = std::nullopt,
        std::string hostUserId = "",
        std::string hostDisplayName = "",
        std::shared_ptr<WebhookClient> webhookClient = nullptr
    );

    ~GameRoom() = default;

    /** Non-copyable. */
    GameRoom(const GameRoom&) = delete;
    GameRoom& operator=(const GameRoom&) = delete;

    [[nodiscard]] std::string getRoomCode() const;
    [[nodiscard]] RoomState getState() const;
    [[nodiscard]] chess::ChessGame getGame() const;
    [[nodiscard]] const TimeControl& getTimeControl() const;

    /**
     * @brief Adds a second player, reconnects an existing player, or adds a spectator.
     */
    bool join(std::shared_ptr<IClientSession> session);

    /**
     * @brief Adds a second player, reconnects an existing player, or adds a spectator with user identity.
     */
    bool joinWithIdentity(
        std::shared_ptr<IClientSession> session,
        const std::string& userId,
        const std::string& displayName,
        const std::string& preferredColor = ""
    );

    /**
     * @brief Sets webhook client for posting completed game records.
     */
    void setWebhookClient(std::shared_ptr<WebhookClient> webhookClient);

    /**
     * @brief Processes a move string (UCI or SAN) sent by a player session.
     */
    void handleMove(const std::string& sessionId, const std::string& moveStr);

    /**
     * @brief Handles a mutual draw offer from a player session.
     */
    void handleOfferDraw(const std::string& sessionId);

    /**
     * @brief Handles acceptance of a pending draw offer.
     */
    void handleAcceptDraw(const std::string& sessionId);

    /**
     * @brief Handles declining a pending draw offer.
     */
    void handleDeclineDraw(const std::string& sessionId);

    /**
     * @brief Handles resignation by a player session.
     */
    void handleResign(const std::string& sessionId);

    /**
     * @brief Handles client disconnection.
     */
    void handleDisconnect(const std::string& sessionId);

    /**
     * @brief Periodic clock tick checking for timeouts and disconnect grace period expiration.
     */
    void tickClock();

    /**
     * @brief Configures disconnect grace period.
     */
    void setDisconnectGracePeriod(std::chrono::seconds gracePeriod);

    /**
     * @brief Configures how long a reserved room can wait for its second player.
     */
    void setStartTimeout(std::chrono::seconds timeout);

    /**
     * @brief Explicitly marks the room as dead.
     */
    void markDead();

    /**
     * @brief Checks if the room is empty or can be cleaned up.
     */
    [[nodiscard]] bool isDead() const;

    /**
     * @brief Returns the color of the session in this room, if they are an active player.
     */
    [[nodiscard]] std::optional<chess::Color> getPlayerColor(const std::string& sessionId) const;

private:
    std::string roomCode_;
    RoomState state_{RoomState::WaitingForOpponent};
    TimeControl timeControl_;
    std::unique_ptr<chess::ChessClock> clock_;
    chess::ChessGame game_;

    std::shared_ptr<IClientSession> whiteSession_;
    std::shared_ptr<IClientSession> blackSession_;
    std::string whitePlayerId_;
    std::string blackPlayerId_;
    std::string whiteDisplayName_{"White"};
    std::string blackDisplayName_{"Black"};
    std::vector<std::shared_ptr<IClientSession>> spectators_;

    std::chrono::seconds disconnectGracePeriod_{60};
    std::chrono::seconds startTimeout_{120};
    std::chrono::steady_clock::time_point createdAt_{std::chrono::steady_clock::now()};
    std::optional<std::chrono::steady_clock::time_point> whiteDisconnectedAt_;
    std::optional<std::chrono::steady_clock::time_point> blackDisconnectedAt_;
    std::optional<std::chrono::system_clock::time_point> startedAt_;
    int64_t durationSeconds_{0};
    std::shared_ptr<WebhookClient> webhookClient_;
    bool dead_{false};

    mutable std::mutex mutex_;

    void broadcast(const std::string& message);
    [[nodiscard]] std::optional<chess::Color> getPlayerColorLocked(const std::string& sessionId) const;
    void sendToSession(const std::shared_ptr<IClientSession>& session, const std::string& msg);
    void sendToPlayer(chess::Color color, const std::string& msg);
    [[nodiscard]] int64_t getRemainingTimeMs(chess::Color color) const;

    void finishGame(const std::string& resultStr,
                    std::optional<chess::Color> winner,
                    const std::string& reasonDescription,
                    const std::string& reasonCode = "");
    void finishGameWithResult(const chess::GameResult& result);

    /**
     * @brief Generates standard PGN string for the full game history.
     */
    [[nodiscard]] std::string buildPgn(const std::string& resultStr) const;

    /**
     * @brief Returns chronological list of all FEN snapshots since game start.
     */
    [[nodiscard]] std::vector<std::string> getAllFens() const;

    /**
     * @brief Returns chronological list of all executed moves in SAN.
     */
    [[nodiscard]] std::vector<std::string> getAllMovesSan() const;
};

}
