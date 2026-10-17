#include "net/RoomManager.h"
#include "net/Protocol.h"
#include "net/RoomCode.h"
#include <nlohmann/json.hpp>
#include <vector>

namespace net {

RoomManager::RoomManager(
    std::shared_ptr<WebhookClient> webhookClient,
    std::string jwtSecret,
    std::chrono::seconds disconnectGracePeriod,
    std::chrono::seconds roomStartTimeout
)
    : webhookClient_(std::move(webhookClient)),
      jwtSecret_(std::move(jwtSecret)),
      disconnectGracePeriod_(disconnectGracePeriod),
      roomStartTimeout_(roomStartTimeout) {}

std::shared_ptr<GameRoom> RoomManager::createRoom(
    std::shared_ptr<IClientSession> hostSession,
    const TimeControl& timeControl,
    std::optional<chess::Color> preferredColor
) {
    if (!hostSession) {
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(mutex_);

    auto it = sessionToRoom_.find(hostSession->getId());
    if (it != sessionToRoom_.end()) {
        auto existingRoomIt = rooms_.find(it->second);
        if (existingRoomIt != rooms_.end()) {
            auto existingRoom = existingRoomIt->second;
            if (existingRoom->getState() == RoomState::InProgress &&
                existingRoom->getPlayerColor(hostSession->getId()).has_value()) {
                hostSession->send(Protocol::error("Cannot create room while playing in an in-progress game"));
                return nullptr;
            }
            existingRoom->handleDisconnect(hostSession->getId());
        }
        sessionToRoom_.erase(it);
    }

    std::string code;
    do {
        code = RoomCode::generate(6);
    } while (rooms_.contains(code));

    auto room = std::make_shared<GameRoom>(
        code,
        hostSession,
        timeControl,
        preferredColor,
        hostSession ? hostSession->getId() : "",
        "Player",
        webhookClient_
    );
    room->setDisconnectGracePeriod(disconnectGracePeriod_);
    room->setStartTimeout(roomStartTimeout_);
    rooms_[code] = room;
    sessionToRoom_[hostSession->getId()] = code;

    return room;
}

std::shared_ptr<GameRoom> RoomManager::createRoomWithIdentity(
    const std::string& roomCode,
    std::shared_ptr<IClientSession> hostSession,
    const TimeControl& timeControl,
    std::optional<chess::Color> preferredColor,
    const std::string& hostUserId,
    const std::string& hostDisplayName
) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (hostSession) {
        auto it = sessionToRoom_.find(hostSession->getId());
        if (it != sessionToRoom_.end()) {
            auto existingRoomIt = rooms_.find(it->second);
            if (existingRoomIt != rooms_.end()) {
                auto existingRoom = existingRoomIt->second;
                if (existingRoom->getState() == RoomState::InProgress &&
                    existingRoom->getPlayerColor(hostSession->getId()).has_value()) {
                    hostSession->send(Protocol::error("Cannot create room while playing in an in-progress game"));
                    return nullptr;
                }
                existingRoom->handleDisconnect(hostSession->getId());
            }
            sessionToRoom_.erase(it);
        }
    }

    auto room = std::make_shared<GameRoom>(
        roomCode,
        hostSession,
        timeControl,
        preferredColor,
        hostUserId,
        hostDisplayName,
        webhookClient_
    );
    room->setDisconnectGracePeriod(disconnectGracePeriod_);
    room->setStartTimeout(roomStartTimeout_);
    rooms_[roomCode] = room;
    if (hostSession) {
        sessionToRoom_[hostSession->getId()] = roomCode;
    }

    return room;
}

bool RoomManager::joinRoom(
    const std::string& roomCode,
    std::shared_ptr<IClientSession> session
) {
    if (!session) {
        return false;
    }

    std::shared_ptr<GameRoom> room;
    {
        std::lock_guard<std::mutex> lock(mutex_);

        auto it = sessionToRoom_.find(session->getId());
        if (it != sessionToRoom_.end()) {
            if (it->second == roomCode) {
                auto existingRoomIt = rooms_.find(roomCode);
                if (existingRoomIt != rooms_.end()) {
                    return existingRoomIt->second->join(session);
                }
            }
            auto existingRoomIt = rooms_.find(it->second);
            if (existingRoomIt != rooms_.end()) {
                auto existingRoom = existingRoomIt->second;
                if (existingRoom->getState() == RoomState::InProgress &&
                    existingRoom->getPlayerColor(session->getId()).has_value()) {
                    session->send(Protocol::error("Cannot join room while playing in an in-progress game"));
                    return false;
                }
                existingRoom->handleDisconnect(session->getId());
            }
            sessionToRoom_.erase(it);
        }

        auto roomIt = rooms_.find(roomCode);
        if (roomIt == rooms_.end()) {
            session->send(Protocol::error("Room not found: " + roomCode));
            return false;
        }
        room = roomIt->second;
        sessionToRoom_[session->getId()] = roomCode;
    }

    return room->join(session);
}

void RoomManager::routeMessage(
    const std::shared_ptr<IClientSession>& session,
    const std::string& rawJson
) {
    if (!session) {
        return;
    }

    nlohmann::json j;
    try {
        j = nlohmann::json::parse(rawJson);
    } catch (const std::exception& e) {
        session->send(Protocol::error(std::string("Malformed JSON: ") + e.what()));
        return;
    }

    if (!j.contains("action") || !j["action"].is_string()) {
        session->send(Protocol::error("Missing 'action' field"));
        return;
    }

    std::string action = j["action"].get<std::string>();

    if (action == "connect_token") {
        std::string roomCode;
        std::string token;
        if (j.contains("room_code") && j["room_code"].is_string()) {
            roomCode = j["room_code"].get<std::string>();
        } else if (j.contains("room") && j["room"].is_string()) {
            roomCode = j["room"].get<std::string>();
        }
        if (j.contains("token") && j["token"].is_string()) {
            token = j["token"].get<std::string>();
        }
        if (!roomCode.empty() && !token.empty()) {
            if (!handleTokenConnect(session, roomCode, token)) {
                session->close();
            }
        } else {
            session->send(Protocol::error("Missing 'room_code' or 'token'"));
            session->close();
        }
        return;
    }

    if (action == "create_room" || action == "join_room" || action == "join_with_token") {
        session->send(Protocol::error("This room action is no longer supported"));
        session->close();
        return;
    }

    std::shared_ptr<GameRoom> room = getRoomBySession(session->getId());
    if (!room) {
        session->send(Protocol::error("Not currently in a game room"));
        session->close();
        return;
    }

    if (action == "move") {
        auto moveStr = Protocol::parseMove(j);
        if (moveStr) {
            room->handleMove(session->getId(), *moveStr);
        } else {
            session->send(Protocol::error("Missing move specification ('uci' or 'san')"));
        }
    } else if (action == "offer_draw") {
        room->handleOfferDraw(session->getId());
    } else if (action == "accept_draw") {
        room->handleAcceptDraw(session->getId());
    } else if (action == "decline_draw") {
        room->handleDeclineDraw(session->getId());
    } else if (action == "resign") {
        room->handleResign(session->getId());
    } else if (action == "leave_room") {
        room->handleDisconnect(session->getId());
        std::lock_guard<std::mutex> lock(mutex_);
        sessionToRoom_.erase(session->getId());
    } else {
        session->send(Protocol::error("Unknown action: " + action));
    }
}

void RoomManager::onDisconnect(const std::string& sessionId) {
    std::shared_ptr<GameRoom> room;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = sessionToRoom_.find(sessionId);
        if (it != sessionToRoom_.end()) {
            std::string code = it->second;
            sessionToRoom_.erase(it);
            auto roomIt = rooms_.find(code);
            if (roomIt != rooms_.end()) {
                room = roomIt->second;
            }
        }
    }

    if (room) {
        room->handleDisconnect(sessionId);
    }
}

void RoomManager::tickClocks() {
    std::vector<std::shared_ptr<GameRoom>> activeRooms;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        activeRooms.reserve(rooms_.size());
        for (const auto& [_, room] : rooms_) {
            activeRooms.push_back(room);
        }
    }

    for (const auto& room : activeRooms) {
        room->tickClock();
    }
}

void RoomManager::sweepDeadRooms() {
    std::lock_guard<std::mutex> lock(mutex_);
    std::vector<std::string> deadCodes;
    for (auto it = rooms_.begin(); it != rooms_.end(); ) {
        if (it->second->isDead()) {
            deadCodes.push_back(it->first);
            it = rooms_.erase(it);
        } else {
            ++it;
        }
    }
    if (!deadCodes.empty()) {
        std::erase_if(sessionToRoom_, [&](const auto& pair) {
            return std::ranges::find(deadCodes, pair.second) != deadCodes.end();
        });
    }
}

size_t RoomManager::activeRoomCount() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return rooms_.size();
}

std::shared_ptr<GameRoom> RoomManager::getRoom(const std::string& roomCode) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = rooms_.find(roomCode);
    if (it != rooms_.end()) {
        return it->second;
    }
    return nullptr;
}

std::shared_ptr<GameRoom> RoomManager::getRoomBySession(const std::string& sessionId) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = sessionToRoom_.find(sessionId);
    if (it != sessionToRoom_.end()) {
        auto roomIt = rooms_.find(it->second);
        if (roomIt != rooms_.end()) {
            return roomIt->second;
        }
    }
    return nullptr;
}

bool RoomManager::handleTokenConnect(
    const std::shared_ptr<IClientSession>& session,
    const std::string& roomCode,
    const std::string& tokenStr
) {
    if (!session) {
        return false;
    }

    auto claimsOpt = JwtToken::verify(tokenStr, jwtSecret_);
    if (!claimsOpt.has_value()) {
        session->send(Protocol::error("Invalid or expired token"));
        return false;
    }

    const auto& claims = *claimsOpt;
    if (claims.roomCode != roomCode) {
        session->send(Protocol::error("Token room mismatch"));
        return false;
    }

    std::unique_lock<std::mutex> lock(mutex_);

    auto itExisting = sessionToRoom_.find(session->getId());
    if (itExisting != sessionToRoom_.end()) {
        if (itExisting->second != roomCode) {
            auto currentRoomIt = rooms_.find(itExisting->second);
            if (currentRoomIt != rooms_.end() && currentRoomIt->second->getState() == RoomState::InProgress &&
                currentRoomIt->second->getPlayerColor(session->getId()).has_value()) {
                session->send(Protocol::error("Cannot join room while playing in an in-progress game"));
                return false;
            }
            if (currentRoomIt != rooms_.end()) {
                currentRoomIt->second->handleDisconnect(session->getId());
            }
            sessionToRoom_.erase(itExisting);
        }
    }

    auto roomIt = rooms_.find(roomCode);
    std::shared_ptr<GameRoom> room;
    if (roomIt == rooms_.end()) {
        TimeControl tc = (claims.initialSeconds > 0)
            ? TimeControl::fromSeconds(claims.initialSeconds, claims.incrementSeconds)
            : TimeControl::unlimited();
        std::optional<chess::Color> prefColor;
        if (claims.color == "white") prefColor = chess::Color::White;
        else if (claims.color == "black") prefColor = chess::Color::Black;

        room = std::make_shared<GameRoom>(
            roomCode,
            session,
            tc,
            prefColor,
            claims.userId,
            claims.displayName,
            webhookClient_,
            claims.initialFen,
            claims.setupMoves,
            claims.setupFens
        );
        room->setDisconnectGracePeriod(disconnectGracePeriod_);
        room->setStartTimeout(roomStartTimeout_);
        rooms_[roomCode] = room;
        sessionToRoom_[session->getId()] = roomCode;
        return true;
    } else {
        room = roomIt->second;
    }

    lock.unlock();

    bool ok = room->joinWithIdentity(session, claims.userId, claims.displayName, claims.color);
    if (ok) {
        std::lock_guard<std::mutex> lock2(mutex_);
        sessionToRoom_[session->getId()] = roomCode;
    }
    return ok;
}

void RoomManager::setWebhookClient(std::shared_ptr<WebhookClient> client) {
    std::lock_guard<std::mutex> lock(mutex_);
    webhookClient_ = std::move(client);
    for (auto& [code, room] : rooms_) {
        if (room) {
            room->setWebhookClient(webhookClient_);
        }
    }
}

std::shared_ptr<WebhookClient> RoomManager::getWebhookClient() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return webhookClient_;
}

void RoomManager::setJwtSecret(std::string secret) {
    std::lock_guard<std::mutex> lock(mutex_);
    jwtSecret_ = std::move(secret);
}

const std::string& RoomManager::getJwtSecret() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return jwtSecret_;
}

}
