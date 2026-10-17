#pragma once

#include "net/RoomManager.h"
#include "net/WebSocketSession.h"
#include "net/WebhookClient.h"
#include <atomic>
#include <chrono>
#include <ixwebsocket/IXWebSocketServer.h>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>

namespace net {

/**
 * @brief High-performance WebSocket server managing network I/O and game dispatch.
 */
class WebSocketServer {
public:
    static constexpr size_t kMaxIncomingMessageSize = 65536;

    WebSocketServer(
        int port = 9002,
        std::string host = "0.0.0.0",
        std::shared_ptr<WebhookClient> webhookClient = nullptr,
        std::string jwtSecret = "",
        std::chrono::seconds disconnectGracePeriod = std::chrono::seconds(60)
    );
    ~WebSocketServer();

    /** Non-copyable. */
    WebSocketServer(const WebSocketServer&) = delete;
    WebSocketServer& operator=(const WebSocketServer&) = delete;

    /**
     * @brief Starts listening and background tick loop.
     * @return True if server started successfully.
     */
    bool start();

    /**
     * @brief Stops listening and terminates background threads.
     */
    void stop();

    [[nodiscard]] bool isRunning() const;
    [[nodiscard]] int getPort() const;
    [[nodiscard]] const std::string& getHost() const;

    [[nodiscard]] RoomManager& getRoomManager();
    [[nodiscard]] const RoomManager& getRoomManager() const;

    [[nodiscard]] std::shared_ptr<WebhookClient> getWebhookClient() const;
    void setWebhookClient(std::shared_ptr<WebhookClient> client);

private:
    int port_;
    std::string host_;
    ix::WebSocketServer server_;
    std::shared_ptr<WebhookClient> webhookClient_;
    RoomManager roomManager_;

    std::mutex sessionsMutex_;
    std::unordered_map<std::string, std::shared_ptr<WebSocketSession>> sessions_;

    std::atomic<bool> running_{false};
    std::thread tickThread_;

    void tickLoop();
    void handleConnectionOpen(const std::shared_ptr<WebSocketSession>& session, const std::string& uri);
};

}
