#pragma once

#include <string>

namespace net {

/**
 * @brief Abstract interface representing a connected client session.
 *
 * Decouples game room and room management from the underlying network transport (e.g. WebSocket, mock).
 */
class IClientSession {
public:
    /**
     * @brief Virtual destructor.
     */
    virtual ~IClientSession() = default;

    /**
     * @brief Gets the unique session identifier.
     */
    [[nodiscard]] virtual std::string getId() const = 0;

    /**
     * @brief Sends a message payload to the client.
     */
    virtual void send(const std::string& message) = 0;

    /**
     * @brief Closes the connection to the client.
     */
    virtual void close() = 0;
};

}
