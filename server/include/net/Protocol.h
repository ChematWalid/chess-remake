#pragma once

#include "chess/board/Types.h"
#include "chess/rules/ChessClock.h"
#include <cstdint>
#include <nlohmann/json.hpp>
#include <optional>
#include <string>
#include <vector>

namespace net {

/**
 * @brief Helper and serializer for the JSON WebSocket communication protocol.
 */
class Protocol {
public:
    using TimeControl = chess::ChessClock::TimeControl;

    /**
     * @brief Parses move notation string (SAN or UCI) from a move action.
     */
    static std::optional<std::string> parseMove(const nlohmann::json& json);

    /**
     * @brief Serializes the room_created notification event.
     */
    [[nodiscard]] static std::string roomCreated(
        const std::string& roomCode,
        chess::Color color
    );

    /**
     * @brief Serializes the room_joined notification event.
     */
    [[nodiscard]] static std::string roomJoined(
        const std::string& roomCode,
        const std::string& role
    );

    /**
     * @brief Serializes the game_started notification event.
     */
    [[nodiscard]] static std::string gameStarted(
        const std::string& roomCode,
        const std::string& fen,
        const TimeControl& timeControl,
        int64_t whiteTimeMs,
        int64_t blackTimeMs,
        const std::vector<std::string>& moves
    );

    /**
     * @brief Serializes the move_made broadcast event.
     */
    [[nodiscard]] static std::string moveMade(
        const std::string& san,
        const std::string& uci,
        const std::string& fen,
        chess::Color sideToMove,
        bool isCheck,
        int64_t whiteTimeMs,
        int64_t blackTimeMs
    );

    /**
     * @brief Serializes the draw_offered notification event.
     */
    [[nodiscard]] static std::string drawOffered(chess::Color color);

    /**
     * @brief Serializes the draw_declined notification event.
     */
    [[nodiscard]] static std::string drawDeclined();

    /**
     * @brief Serializes the game_over conclusion event, including full game replay history.
     */
    [[nodiscard]] static std::string gameOver(
        const std::string& result,
        const std::optional<chess::Color>& winner,
        const std::string& reason,
        const std::string& reasonCode = "",
        const std::string& pgn = "",
        const std::vector<std::string>& fens = {},
        const std::vector<std::string>& moves = {}
    );

    /**
     * @brief Serializes an error event.
     */
    [[nodiscard]] static std::string error(const std::string& message);

    /**
     * @brief Serializes a time synchronization event.
     */
    [[nodiscard]] static std::string timeSync(int64_t whiteTimeMs, int64_t blackTimeMs);

    /**
     * @brief Serializes an opponent disconnection event.
     */
    [[nodiscard]] static std::string opponentDisconnected();
};

} /* namespace net */
