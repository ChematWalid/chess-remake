#include "net/WebSocketServer.h"
#include <atomic>
#include <csignal>
#include <iostream>
#include <string>

namespace {
std::atomic<bool> g_shutdownRequested{false};

void signalHandler(int /*signal*/) {
    g_shutdownRequested.store(true);
}
}

int main(int argc, char* argv[]) {
    int port = 9002;
    std::string host = "0.0.0.0";

    const char* envPort = std::getenv("CHESS_PORT");
    if (envPort && envPort[0] != '\0') {
        try {
            port = std::stoi(envPort);
        } catch (...) {
        }
    }
    const char* envHost = std::getenv("CHESS_HOST");
    if (envHost && envHost[0] != '\0') {
        host = envHost;
    }

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--port" && i + 1 < argc) {
            port = std::stoi(argv[++i]);
        } else if (arg == "--host" && i + 1 < argc) {
            host = argv[++i];
        } else if (arg == "--help" || arg == "-h") {
            std::cout << "Usage: chess_server [--port <port>] [--host <host>]\n";
            std::cout << "Default: --port 9002 --host 0.0.0.0 (or CHESS_PORT/CHESS_HOST)\n";
            return 0;
        }
    }

    std::signal(SIGINT, signalHandler);
    std::signal(SIGTERM, signalHandler);

    std::cout << "========================================================\n";
    std::cout << "             Chess WebSocket Server (C++23)             \n";
    std::cout << "========================================================\n";
    std::cout << " Endpoint: ws://" << host << ":" << port << "\n";
    std::cout << " Multi-game room system with invite codes ready.\n";
    std::cout << " Timed and untimed (no time limit) games supported.\n";
    std::cout << " Press Ctrl+C to terminate server.\n";
    std::cout << "--------------------------------------------------------\n" << std::endl;

    auto webhookClient = std::make_shared<net::WebhookClient>();
    net::WebSocketServer server(port, host, webhookClient);
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
