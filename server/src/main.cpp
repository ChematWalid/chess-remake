#include "net/WebSocketServer.h"
#include <atomic>
#include <csignal>
#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {
std::atomic<bool> g_shutdownRequested{false};

std::string requiredEnvironment(const char* name) {
    const char* value = std::getenv(name);
    if (value == nullptr || value[0] == '\0') {
        throw std::runtime_error(std::string("Missing required environment variable: ") + name);
    }
    return value;
}

void signalHandler(int /*signal*/) {
    g_shutdownRequested.store(true);
}
}

int main(int argc, char* argv[]) {
    int port = 0;
    std::string host;
    std::string jwtSecret;
    int disconnectGraceSeconds = 60;
    int roomStartTimeoutSeconds = 0;
    try {
        port = std::stoi(requiredEnvironment("CHESS_PORT"));
        host = requiredEnvironment("CHESS_HOST");
        jwtSecret = requiredEnvironment("CHESS_JWT_SECRET");
        requiredEnvironment("SPRING_WEBHOOK_URL");
        requiredEnvironment("INTERNAL_WEBHOOK_SECRET");
        requiredEnvironment("CHESS_OUTBOX_DIR");
        if (const char* configuredGrace = std::getenv("CHESS_DISCONNECT_GRACE_SECONDS");
            configuredGrace != nullptr && configuredGrace[0] != '\0') {
            disconnectGraceSeconds = std::stoi(configuredGrace);
        }
        roomStartTimeoutSeconds = std::stoi(requiredEnvironment("CHESS_ROOM_START_TIMEOUT_SECONDS"));
        if (disconnectGraceSeconds < 0 || disconnectGraceSeconds > 3600) {
            throw std::runtime_error("CHESS_DISCONNECT_GRACE_SECONDS must be between 0 and 3600");
        }
        if (roomStartTimeoutSeconds < 1 || roomStartTimeoutSeconds > 3600) {
            throw std::runtime_error("CHESS_ROOM_START_TIMEOUT_SECONDS must be between 1 and 3600");
        }
        if (port < 1 || port > 65535) {
            throw std::runtime_error("CHESS_PORT must be between 1 and 65535");
        }
    } catch (const std::exception& exception) {
        std::cerr << "Configuration error: " << exception.what() << std::endl;
        return 2;
    }

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--port" && i + 1 < argc) {
            port = std::stoi(argv[++i]);
        } else if (arg == "--host" && i + 1 < argc) {
            host = argv[++i];
        } else if (arg == "--help" || arg == "-h") {
            std::cout << "Usage: chess_server [--port <port>] [--host <host>]\n";
            std::cout << "CHESS_PORT, CHESS_HOST, CHESS_JWT_SECRET, SPRING_WEBHOOK_URL, "
                         "INTERNAL_WEBHOOK_SECRET, CHESS_OUTBOX_DIR, and "
                         "CHESS_ROOM_START_TIMEOUT_SECONDS are required.\n";
            return 0;
        }
    }

    std::signal(SIGINT, signalHandler);
    std::signal(SIGTERM, signalHandler);

    std::cout << "========================================================\n";
    std::cout << "             Chess WebSocket Server (C++23)             \n";
    std::cout << "========================================================\n";
    std::cout << " Endpoint: ws://" << host << ":" << port << "\n";
    std::cout << " Backend-authorized multi-game rooms and reconnects ready.\n";
    std::cout << " Timed and untimed (no time limit) games supported.\n";
    std::cout << " Press Ctrl+C to terminate server.\n";
    std::cout << "--------------------------------------------------------\n" << std::endl;

    auto webhookClient = std::make_shared<net::WebhookClient>();
    net::WebSocketServer server(port, host, webhookClient, jwtSecret,
                                std::chrono::seconds(disconnectGraceSeconds),
                                std::chrono::seconds(roomStartTimeoutSeconds));
    if (!server.start()) {
        std::cerr << "Error: Could not bind or start WebSocket server on " << host << ":" << port << std::endl;
        return 1;
    }

    std::cout << "Server successfully started and listening for connections.\n" << std::endl;

    while (!g_shutdownRequested.load()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
    }

    std::cout << "\nShutting down server gracefully..." << std::endl;
    server.stop();
    std::cout << "Server stopped." << std::endl;
    return 0;
}
