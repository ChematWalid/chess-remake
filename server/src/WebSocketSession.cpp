#include "net/WebSocketSession.h"

namespace net {

WebSocketSession::WebSocketSession(std::string id, std::weak_ptr<ix::WebSocket> ws)
    : id_(std::move(id)), ws_(std::move(ws)) {}

std::string WebSocketSession::getId() const {
    return id_;
}

void WebSocketSession::send(const std::string& message) {
    if (auto sp = ws_.lock()) {
        sp->sendText(message);
    }
}

void WebSocketSession::close() {
    if (auto sp = ws_.lock()) {
        sp->close();
    }
}

}
