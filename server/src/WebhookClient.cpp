#include "net/WebhookClient.h"
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <ixwebsocket/IXHttpClient.h>

namespace net {

nlohmann::json GameRecordPayload::toJson() const {
    nlohmann::json j;
    j["roomCode"] = roomCode;
    j["roomToken"] = roomToken;
    j["whitePlayerId"] = whitePlayerId;
    j["blackPlayerId"] = blackPlayerId;
    if (winnerPlayerId.empty()) {
        j["winnerPlayerId"] = nullptr;
    } else {
        j["winnerPlayerId"] = winnerPlayerId;
    }
    j["isDraw"] = isDraw;
    j["terminationReason"] = terminationReason;
    j["reason_code"] = reasonCode;
    j["totalMoves"] = totalMoves;
    j["durationSeconds"] = durationSeconds;
    j["pgn"] = pgn;
    j["moves"] = moves;
    j["fens"] = fens;
    return j;
}

WebhookClient::WebhookClient(
    std::string endpointUrl,
    std::string internalToken,
    std::filesystem::path outboxDir
)
    : endpointUrl_(std::move(endpointUrl)),
      internalToken_(std::move(internalToken)),
      outboxDir_(std::move(outboxDir)) {
    if (endpointUrl_.empty()) {
        const char* envUrl = std::getenv("SPRING_WEBHOOK_URL");
        if (envUrl && envUrl[0] != '\0') {
            endpointUrl_ = envUrl;
        }
    }
    if (internalToken_.empty()) {
        const char* envToken = std::getenv("INTERNAL_WEBHOOK_SECRET");
        if (!envToken || envToken[0] == '\0') {
            envToken = std::getenv("INTERNAL_TOKEN");
        }
        if (envToken && envToken[0] != '\0') {
            internalToken_ = envToken;
        }
    }
    const char* envOutbox = std::getenv("CHESS_OUTBOX_DIR");
    if (envOutbox && envOutbox[0] != '\0') {
        outboxDir_ = envOutbox;
    }

    std::error_code ec;
    std::filesystem::create_directories(outboxDir_, ec);
}

WebhookClient::~WebhookClient() {
    stop();
}

void WebhookClient::start() {
    if (running_.load()) {
        return;
    }
    running_.store(true);
    workerThread_ = std::thread(&WebhookClient::workerLoop, this);
}

void WebhookClient::stop() {
    if (!running_.exchange(false)) {
        return;
    }
    cv_.notify_all();
    if (workerThread_.joinable()) {
        workerThread_.join();
    }
}

void WebhookClient::enqueue(const GameRecordPayload& payload) {
    std::error_code ec;
    std::filesystem::create_directories(outboxDir_, ec);

    auto nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();

    std::string filenameBase = "game_" + std::to_string(nowNs) + "_" + payload.roomCode;
    std::filesystem::path tmpPath = outboxDir_ / (filenameBase + ".tmp");
    std::filesystem::path jsonPath = outboxDir_ / (filenameBase + ".json");

    {
        std::ofstream ofs(tmpPath);
        if (ofs.is_open()) {
            ofs << payload.toJson().dump(2);
        }
    }

    std::filesystem::rename(tmpPath, jsonPath, ec);
    cv_.notify_one();
}

size_t WebhookClient::pendingOutboxCount() const {
    size_t count = 0;
    std::error_code ec;
    if (!std::filesystem::exists(outboxDir_, ec)) {
        return 0;
    }
    for (const auto& entry : std::filesystem::directory_iterator(outboxDir_, ec)) {
        if (entry.is_regular_file() && entry.path().extension() == ".json") {
            ++count;
        }
    }
    return count;
}

size_t WebhookClient::flushOnce() {
    if (endpointUrl_.empty()) {
        return 0;
    }

    std::error_code ec;
    if (!std::filesystem::exists(outboxDir_, ec)) {
        return 0;
    }

    size_t delivered = 0;
    std::vector<std::filesystem::path> files;
    for (const auto& entry : std::filesystem::directory_iterator(outboxDir_, ec)) {
        if (entry.is_regular_file() && entry.path().extension() == ".json") {
            files.push_back(entry.path());
        }
    }

    for (const auto& path : files) {
        std::ifstream ifs(path);
        if (!ifs.is_open()) {
            continue;
        }
        std::string content((std::istreambuf_iterator<char>(ifs)),
                            std::istreambuf_iterator<char>());
        ifs.close();

        if (sendPayload(content)) {
            std::filesystem::remove(path, ec);
            ++delivered;
        } else {
            break;
        }
    }

    return delivered;
}

const std::string& WebhookClient::getEndpointUrl() const {
    return endpointUrl_;
}

const std::filesystem::path& WebhookClient::getOutboxDir() const {
    return outboxDir_;
}

void WebhookClient::workerLoop() {
    int backoffSeconds = 1;
    while (running_.load()) {
        size_t delivered = flushOnce();
        if (delivered > 0) {
            backoffSeconds = 1;
        }

        std::unique_lock<std::mutex> lock(cvMutex_);
        if (pendingOutboxCount() > 0 && !endpointUrl_.empty()) {
            cv_.wait_for(lock, std::chrono::seconds(backoffSeconds), [this] {
                return !running_.load();
            });
            backoffSeconds = std::min(backoffSeconds * 2, 30);
        } else {
            cv_.wait_for(lock, std::chrono::seconds(5), [this] {
                return !running_.load();
            });
        }
    }
}

bool WebhookClient::sendPayload(const std::string& jsonString) {
    if (endpointUrl_.empty()) {
        return false;
    }

    try {
        ix::HttpClient httpClient;
        auto args = httpClient.createRequest(endpointUrl_, ix::HttpClient::kPost);
        args->extraHeaders["X-Internal-Token"] = internalToken_;
        args->extraHeaders["Content-Type"] = "application/json";

        auto response = httpClient.post(endpointUrl_, jsonString, args);
        if (response && response->statusCode >= 200 && response->statusCode < 300) {
            return true;
        }
    } catch (const std::exception& e) {
        std::cerr << "Webhook delivery error: " << e.what() << std::endl;
    } catch (...) {
        std::cerr << "Webhook delivery error: unknown exception" << std::endl;
    }

    return false;
}

} /* namespace net */
