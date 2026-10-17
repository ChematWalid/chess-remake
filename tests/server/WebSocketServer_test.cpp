#include "net/WebSocketServer.h"

#include <gtest/gtest.h>
#include <ixwebsocket/IXWebSocket.h>

#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>

#include <chrono>
#include <condition_variable>
#include <mutex>
#include <string>

namespace {

int findAvailablePort() {
    const int socketFd = socket(AF_INET, SOCK_STREAM, 0);
    if (socketFd < 0) return -1;

    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    address.sin_port = 0;

    if (bind(socketFd, reinterpret_cast<sockaddr*>(&address), sizeof(address)) != 0) {
        close(socketFd);
        return -1;
    }

    socklen_t addressLength = sizeof(address);
    if (getsockname(socketFd, reinterpret_cast<sockaddr*>(&address), &addressLength) != 0) {
        close(socketFd);
        return -1;
    }

    const int port = ntohs(address.sin_port);
    close(socketFd);
    return port;
}

}

TEST(WebSocketServerTest, ClosesConnectionAfterOversizedMessage) {
    const int port = findAvailablePort();
    ASSERT_GT(port, 0);

    net::WebSocketServer server(port, "127.0.0.1");
    ASSERT_TRUE(server.start());

    std::mutex mutex;
    std::condition_variable condition;
    bool opened = false;
    bool receivedNormalError = false;
    bool receivedSizeError = false;
    bool closed = false;

    ix::WebSocket client;
    client.setUrl("ws://127.0.0.1:" + std::to_string(port) + "/ws/play");
    client.setOnMessageCallback([&](const ix::WebSocketMessagePtr& message) {
        std::lock_guard<std::mutex> lock(mutex);
        if (message->type == ix::WebSocketMessageType::Open) {
            opened = true;
        } else if (message->type == ix::WebSocketMessageType::Message) {
            receivedNormalError = receivedNormalError ||
                message->str.find("Missing 'action' field") != std::string::npos;
            receivedSizeError = receivedSizeError ||
                message->str.find("maximum allowable size") != std::string::npos;
        } else if (message->type == ix::WebSocketMessageType::Close) {
            closed = true;
        }
        condition.notify_all();
    });
    client.start();

    {
        std::unique_lock<std::mutex> lock(mutex);
        ASSERT_TRUE(condition.wait_for(lock, std::chrono::seconds(3), [&] { return opened; }));
    }

    client.sendText("{}");

    {
        std::unique_lock<std::mutex> lock(mutex);
        ASSERT_TRUE(condition.wait_for(lock, std::chrono::seconds(3), [&] { return receivedNormalError; }));
        EXPECT_FALSE(closed);
    }

    client.sendText(std::string(net::WebSocketServer::kMaxIncomingMessageSize + 1, 'x'));

    {
        std::unique_lock<std::mutex> lock(mutex);
        EXPECT_TRUE(condition.wait_for(lock, std::chrono::seconds(3), [&] { return closed; }));
        EXPECT_TRUE(receivedSizeError);
    }

    client.stop();
    server.stop();
}
