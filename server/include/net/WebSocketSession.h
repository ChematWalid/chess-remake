#pragma once

#include "net/IClientSession.h"
#include <ixwebsocket/IXWebSocket.h>
#include <memory>
#include <string>

namespace net {

/**
 * @brief Concrete IClientSession implementation wrapping an active IXWebSocket connection.
 */
class WebSocketSession : public IClientSession {
public:
    /**
     * @brief Constructs a session with an identifier and a weak pointer to the socket.
     */
    WebSocketSession(std::string id, std::weak_ptr<ix::WebSocket> ws);

    /**
     * @brief Destructor.
     */
    ~WebSocketSession() override = default;

    /**
     * @brief Returns unique connection session identifier.
     */
    [[nodiscard]] std::string getId() const override;

    /**
     * @brief Sends a text frame over the WebSocket.
     */
    void send(const std::string& message) override;

    /**
     * @brief Gracefully terminates the WebSocket connection.
     */
    void close() override;

private:
    std::string id_;
    std::weak_ptr<ix::WebSocket> ws_;
};

}
