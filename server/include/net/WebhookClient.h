#pragma once

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <filesystem>
#include <mutex>
#include <nlohmann/json.hpp>
#include <string>
#include <thread>
#include <vector>

namespace net {

/**
 * @brief Structured game record payload for Spring backend persistence.
 */
struct GameRecordPayload {
    std::string roomCode;
    std::string roomToken;
    std::string whitePlayerId;
    std::string blackPlayerId;
    std::string winnerPlayerId;
    bool isDraw{false};
    std::string terminationReason;
    std::string reasonCode;
    int totalMoves{0};
    int64_t durationSeconds{0};
    std::string pgn;
    std::vector<std::string> moves;
    std::vector<std::string> fens;

    [[nodiscard]] nlohmann::json toJson() const;
};

/**
 * @brief Durable background webhook client with disk-backed outbox and retry/backoff.
 */
class WebhookClient {
public:
    /**
     * @brief Constructs webhook client with endpoint URL, auth token, and outbox path.
     */
    WebhookClient(
        std::string endpointUrl = "",
        std::string internalToken = "",
        std::filesystem::path outboxDir = ".outbox",
        std::string startEndpointUrl = ""
    );

    /**
     * @brief Destructor stopping background worker thread.
     */
    ~WebhookClient();

    WebhookClient(const WebhookClient&) = delete;
    WebhookClient& operator=(const WebhookClient&) = delete;

    /**
     * @brief Starts background retry and delivery worker thread.
     */
    void start();

    /**
     * @brief Stops background worker thread gracefully.
     */
    void stop();

    /**
     * @brief Atomically persists game record to durable outbox and wakes background worker.
     */
    void enqueue(const GameRecordPayload& payload);

    /**
     * @brief Confirms both players and the room assignment with the backend before starting play.
     */
    [[nodiscard]] bool markGameStarted(const std::string& roomCode,
                                       const std::string& whitePlayerId,
                                       const std::string& blackPlayerId);

    /**
     * @brief Returns count of pending game record files in outbox directory.
     */
    [[nodiscard]] size_t pendingOutboxCount() const;

    /**
     * @brief Processes one pending outbox cycle immediately.
     * @return Number of records successfully delivered.
     */
    size_t flushOnce();

    /**
     * @brief Returns configured endpoint URL.
     */
    [[nodiscard]] const std::string& getEndpointUrl() const;

    /**
     * @brief Returns configured outbox directory.
     */
    [[nodiscard]] const std::filesystem::path& getOutboxDir() const;

private:
    enum class DeliveryResult {
        Delivered,
        Retry,
        PermanentFailure
    };

    std::string endpointUrl_;
    std::string internalToken_;
    std::filesystem::path outboxDir_;
    std::string startEndpointUrl_;

    std::atomic<bool> running_{false};
    std::thread workerThread_;
    std::mutex cvMutex_;
    std::condition_variable cv_;

    void workerLoop();
    [[nodiscard]] DeliveryResult sendPayload(const std::string& jsonString);
};

} /* namespace net */
