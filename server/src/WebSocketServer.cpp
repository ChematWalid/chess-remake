#include "net/WebSocketServer.h"
#include "net/Protocol.h"
#include <chrono>
#include <iostream>

namespace net {

WebSocketServer::WebSocketServer(
    int port,
    std::string host,
    std::shared_ptr<WebhookClient> webhookClient,
    std::string jwtSecret,
    std::chrono::seconds disconnectGracePeriod,
    std::chrono::seconds roomStartTimeout
)
    : port_(port),
      host_(std::move(host)),
      server_(port_, host_),
      webhookClient_(std::move(webhookClient)),
      roomManager_(webhookClient_, std::move(jwtSecret), disconnectGracePeriod, roomStartTimeout) {}

WebSocketServer::~WebSocketServer() {
    stop();
}

bool WebSocketServer::start() {
    if (running_.load()) {
        return true;
    }

    if (webhookClient_) {
        webhookClient_->start();
    }

    server_.setOnConnectionCallback(
        [this](std::weak_ptr<ix::WebSocket> weakWs, std::shared_ptr<ix::ConnectionState> state) {
            try {
                auto ws = weakWs.lock();
                if (!ws) {
                    return;
                }

                std::string id = state->getId();
                {
                    std::lock_guard<std::mutex> lock(sessionsMutex_);
                    sessions_[id] = std::make_shared<WebSocketSession>(id, weakWs);
                }

                ws->setOnMessageCallback(
                    [this, id](const ix::WebSocketMessagePtr& msg) {
                        try {
                            if (msg->type == ix::WebSocketMessageType::Open) {
                                std::shared_ptr<WebSocketSession> session;
                                {
                                    std::lock_guard<std::mutex> lock(sessionsMutex_);
                                    auto it = sessions_.find(id);
                                    if (it != sessions_.end()) {
                                        session = it->second;
                                    }
                                }
                                if (session) {
                                    handleConnectionOpen(session, msg->openInfo.uri);
                                }
                            } else if (msg->type == ix::WebSocketMessageType::Message) {
                                if (msg->str.size() > kMaxIncomingMessageSize) {
                                    std::shared_ptr<WebSocketSession> session;
                                    {
                                        std::lock_guard<std::mutex> lock(sessionsMutex_);
                                        auto it = sessions_.find(id);
                                        if (it != sessions_.end()) {
                                            session = it->second;
                                        }
                                    }
                                    if (session) {
                                        session->send(Protocol::error("Message payload exceeds maximum allowable size"));
                                        session->close();
                                    }
                                    return;
                                }

                                std::shared_ptr<WebSocketSession> session;
                                {
                                    std::lock_guard<std::mutex> lock(sessionsMutex_);
                                    auto it = sessions_.find(id);
                                    if (it != sessions_.end()) {
                                        session = it->second;
                                    }
                                }
                                if (session) {
                                    roomManager_.routeMessage(session, msg->str);
                                }
                            } else if (msg->type == ix::WebSocketMessageType::Close || msg->type == ix::WebSocketMessageType::Error) {
                                roomManager_.onDisconnect(id);
                                {
                                    std::lock_guard<std::mutex> lock(sessionsMutex_);
                                    sessions_.erase(id);
                                }
                            }
                        } catch (const std::exception& e) {
                            std::cerr << "WebSocket message callback exception: " << e.what() << std::endl;
                        } catch (...) {
                            std::cerr << "WebSocket message callback unknown exception" << std::endl;
                        }
                    }
                );
            } catch (const std::exception& e) {
                std::cerr << "WebSocket connection callback exception: " << e.what() << std::endl;
            } catch (...) {
                std::cerr << "WebSocket connection callback unknown exception" << std::endl;
            }
        }
    );

    auto res = server_.listen();
    if (!res.first) {
        std::cerr << "Failed to listen on " << host_ << ":" << port_ << " - " << res.second << std::endl;
        return false;
    }

    server_.start();
    running_.store(true);

    tickThread_ = std::thread(&WebSocketServer::tickLoop, this);
    return true;
}

void WebSocketServer::stop() {
    if (!running_.exchange(false)) {
        return;
    }

    if (webhookClient_) {
        webhookClient_->stop();
    }

    server_.stop();

    if (tickThread_.joinable()) {
        tickThread_.join();
    }

    std::lock_guard<std::mutex> lock(sessionsMutex_);
    sessions_.clear();
}

bool WebSocketServer::isRunning() const {
    return running_.load();
}

int WebSocketServer::getPort() const {
    return port_;
}

const std::string& WebSocketServer::getHost() const {
    return host_;
}

RoomManager& WebSocketServer::getRoomManager() {
    return roomManager_;
}

const RoomManager& WebSocketServer::getRoomManager() const {
    return roomManager_;
}

std::shared_ptr<WebhookClient> WebSocketServer::getWebhookClient() const {
    return webhookClient_;
}

void WebSocketServer::setWebhookClient(std::shared_ptr<WebhookClient> client) {
    webhookClient_ = std::move(client);
    roomManager_.setWebhookClient(webhookClient_);
}

void WebSocketServer::tickLoop() {
    while (running_.load()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        roomManager_.tickClocks();
        roomManager_.sweepDeadRooms();
    }
}

void WebSocketServer::handleConnectionOpen(
    const std::shared_ptr<WebSocketSession>& session,
    const std::string& uri
) {
    if (uri != "/ws/play") {
        session->send(Protocol::error("Unsupported WebSocket endpoint"));
        session->close();
    }
}

}
