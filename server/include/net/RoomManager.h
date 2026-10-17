#pragma once

#include "net/GameRoom.h"
#include "net/IClientSession.h"
#include "net/JwtToken.h"
#include "net/WebhookClient.h"
#include <memory>
#include <chrono>
#include <mutex>
#include <string>
#include <unordered_map>

namespace net {

/**
 * @brief Thread-safe manager for concurrent game rooms and client session routing.
 */
class RoomManager {
public:
    using TimeControl = chess::ChessClock::TimeControl;

    RoomManager(
        std::shared_ptr<WebhookClient> webhookClient = nullptr,
        std::string jwtSecret = "",
        std::chrono::seconds disconnectGracePeriod = std::chrono::seconds(60)
    );
    ~RoomManager() = default;

    /** Non-copyable. */
    RoomManager(const RoomManager&) = delete;
    RoomManager& operator=(const RoomManager&) = delete;

    /**
     * @brief Creates a new game room with an invite code and registers the host.
     */
    std::shared_ptr<GameRoom> createRoom(
        std::shared_ptr<IClientSession> hostSession,
        const TimeControl& timeControl = TimeControl::unlimited(),
        std::optional<chess::Color> preferredColor = std::nullopt
    );

    /**
     * @brief Creates a game room with an explicit room code, user identity, and time control.
     */
    std::shared_ptr<GameRoom> createRoomWithIdentity(
        const std::string& roomCode,
        std::shared_ptr<IClientSession> hostSession,
        const TimeControl& timeControl,
        std::optional<chess::Color> preferredColor,
        const std::string& hostUserId,
        const std::string& hostDisplayName
    );

    /**
     * @brief Joins an existing game room using its invite code.
     */
    bool joinRoom(
        const std::string& roomCode,
        std::shared_ptr<IClientSession> session
    );

    /**
     * @brief Connects a client session via signed token and room code.
     */
    bool handleTokenConnect(
        const std::shared_ptr<IClientSession>& session,
        const std::string& roomCode,
        const std::string& tokenStr
    );

    void setWebhookClient(std::shared_ptr<WebhookClient> client);
    [[nodiscard]] std::shared_ptr<WebhookClient> getWebhookClient() const;

    void setJwtSecret(std::string secret);
    [[nodiscard]] const std::string& getJwtSecret() const;

    /**
     * @brief Routes an incoming JSON message from a client session to the appropriate room.
     */
    void routeMessage(
        const std::shared_ptr<IClientSession>& session,
        const std::string& rawJson
    );

    /**
     * @brief Handles disconnection of a client session.
     */
    void onDisconnect(const std::string& sessionId);

    /**
     * @brief Periodic tick for all active room clocks.
     */
    void tickClocks();

    /**
     * @brief Removes closed or empty rooms.
     */
    void sweepDeadRooms();

    /**
     * @brief Returns the total number of active rooms.
     */
    [[nodiscard]] size_t activeRoomCount() const;

    /**
     * @brief Retrieves a room by its invite code.
     */
    [[nodiscard]] std::shared_ptr<GameRoom> getRoom(const std::string& roomCode) const;

    /**
     * @brief Retrieves a room associated with a session ID.
     */
    [[nodiscard]] std::shared_ptr<GameRoom> getRoomBySession(const std::string& sessionId) const;

private:
    mutable std::mutex mutex_;
    std::unordered_map<std::string, std::shared_ptr<GameRoom>> rooms_;
    std::unordered_map<std::string, std::string> sessionToRoom_;
    std::shared_ptr<WebhookClient> webhookClient_;
    std::string jwtSecret_;
    std::chrono::seconds disconnectGracePeriod_;
};

} /* namespace net */
