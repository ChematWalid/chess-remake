#include "net/Protocol.h"
#include <cmath>

namespace net {

std::optional<Protocol::CreateRoomParams> Protocol::parseCreateRoom(const nlohmann::json& json) {
    CreateRoomParams params;

    if (json.contains("time_control")) {
        const auto& tc = json["time_control"];
        if (tc.is_string()) {
            if (tc.get<std::string>() == "unlimited") {
                params.timeControl = chess::ChessClock::TimeControl::unlimited();
            } else {
                return std::nullopt;
            }
        } else if (tc.is_object()) {
            double totalInitialSeconds = 0.0;
            bool hasInitial = false;

            if (tc.contains("minutes")) {
                if (!tc["minutes"].is_number()) {
                    return std::nullopt;
                }
                double mins = tc["minutes"].get<double>();
                if (mins <= 0.0) {
                    return std::nullopt;
                }
                totalInitialSeconds += mins * 60.0;
                hasInitial = true;
            }

            if (tc.contains("seconds")) {
                if (!tc["seconds"].is_number()) {
                    return std::nullopt;
                }
                double secs = tc["seconds"].get<double>();
                if (secs <= 0.0) {
                    return std::nullopt;
                }
                totalInitialSeconds += secs;
                hasInitial = true;
            }

            if (!hasInitial) {
                return std::nullopt;
            }

            int64_t initialSec = static_cast<int64_t>(std::round(totalInitialSeconds));
            if (initialSec < kMinInitialSeconds || initialSec > kMaxInitialSeconds) {
                return std::nullopt;
            }

            int64_t incSec = 0;
            if (tc.contains("increment")) {
                if (!tc["increment"].is_number()) {
                    return std::nullopt;
                }
                double inc = tc["increment"].get<double>();
                if (inc < 0.0) {
                    return std::nullopt;
                }
                incSec = static_cast<int64_t>(std::round(inc));
                if (incSec < kMinIncrementSeconds || incSec > kMaxIncrementSeconds) {
                    return std::nullopt;
                }
            }

            params.timeControl = chess::ChessClock::TimeControl::fromSeconds(initialSec, incSec);
        } else {
            return std::nullopt;
        }
    } else {
        params.timeControl = chess::ChessClock::TimeControl::unlimited();
    }

    if (json.contains("preferred_color")) {
        if (!json["preferred_color"].is_string()) {
            return std::nullopt;
        }
        std::string col = json["preferred_color"].get<std::string>();
        if (col == "white" || col == "White") {
            params.preferredColor = chess::Color::White;
        } else if (col == "black" || col == "Black") {
            params.preferredColor = chess::Color::Black;
        } else if (col == "random" || col == "Random") {
            params.preferredColor = std::nullopt;
        } else {
            return std::nullopt;
        }
    }

    return params;
}

std::optional<std::string> Protocol::parseJoinRoom(const nlohmann::json& json) {
    if (json.contains("room_code") && json["room_code"].is_string()) {
        return json["room_code"].get<std::string>();
    }
    return std::nullopt;
}

std::optional<std::string> Protocol::parseMove(const nlohmann::json& json) {
    if (json.contains("uci") && json["uci"].is_string()) {
        return json["uci"].get<std::string>();
    }
    if (json.contains("san") && json["san"].is_string()) {
        return json["san"].get<std::string>();
    }
    if (json.contains("move") && json["move"].is_string()) {
        return json["move"].get<std::string>();
    }
    return std::nullopt;
}

std::string Protocol::roomCreated(const std::string& roomCode, chess::Color color) {
    nlohmann::json j;
    j["event"] = "room_created";
    j["room_code"] = roomCode;
    j["color"] = (color == chess::Color::White) ? "white" : "black";
    return j.dump();
}

std::string Protocol::roomJoined(const std::string& roomCode, const std::string& role) {
    nlohmann::json j;
    j["event"] = "room_joined";
    j["room_code"] = roomCode;
    j["role"] = role;
    return j.dump();
}

std::string Protocol::gameStarted(
    const std::string& roomCode,
    const std::string& fen,
    const TimeControl& timeControl,
    int64_t whiteTimeMs,
    int64_t blackTimeMs
) {
    nlohmann::json j;
    j["event"] = "game_started";
    j["room_code"] = roomCode;
    j["fen"] = fen;
    if (timeControl.isUnlimited) {
        j["time_control"] = "unlimited";
    } else {
        j["time_control"] = nlohmann::json{
            {"initial_seconds", std::chrono::duration_cast<std::chrono::seconds>(timeControl.initialTime).count()},
            {"increment_seconds", std::chrono::duration_cast<std::chrono::seconds>(timeControl.increment).count()}
        };
    }
    j["white_time_ms"] = whiteTimeMs;
    j["black_time_ms"] = blackTimeMs;
    return j.dump();
}

std::string Protocol::moveMade(
    const std::string& san,
    const std::string& uci,
    const std::string& fen,
    chess::Color sideToMove,
    bool isCheck,
    int64_t whiteTimeMs,
    int64_t blackTimeMs
) {
    nlohmann::json j;
    j["event"] = "move_made";
    j["san"] = san;
    j["uci"] = uci;
    j["fen"] = fen;
    j["side_to_move"] = (sideToMove == chess::Color::White) ? "white" : "black";
    j["is_check"] = isCheck;
    j["white_time_ms"] = whiteTimeMs;
    j["black_time_ms"] = blackTimeMs;
    return j.dump();
}

std::string Protocol::drawOffered(chess::Color color) {
    nlohmann::json j;
    j["event"] = "draw_offered";
    j["color"] = (color == chess::Color::White) ? "white" : "black";
    return j.dump();
}

std::string Protocol::drawDeclined() {
    nlohmann::json j;
    j["event"] = "draw_declined";
    return j.dump();
}

std::string Protocol::gameOver(
    const std::string& result,
    const std::optional<chess::Color>& winner,
    const std::string& reason,
    const std::string& reasonCode,
    const std::string& pgn,
    const std::vector<std::string>& fens,
    const std::vector<std::string>& moves
) {
    nlohmann::json j;
    j["event"] = "game_over";
    j["result"] = result;
    if (winner.has_value()) {
        j["winner"] = (*winner == chess::Color::White) ? "white" : "black";
    } else {
        j["winner"] = nullptr;
    }
    j["reason"] = reason;
    if (!reasonCode.empty()) {
        j["reason_code"] = reasonCode;
    }
    if (!pgn.empty()) {
        j["pgn"] = pgn;
    }
    if (!fens.empty()) {
        j["fens"] = fens;
    }
    if (!moves.empty()) {
        j["moves"] = moves;
    }
    return j.dump();
}

std::string Protocol::error(const std::string& message) {
    nlohmann::json j;
    j["event"] = "error";
    j["message"] = message;
    return j.dump();
}

std::string Protocol::timeSync(int64_t whiteTimeMs, int64_t blackTimeMs) {
    nlohmann::json j;
    j["event"] = "time_sync";
    j["white_time_ms"] = whiteTimeMs;
    j["black_time_ms"] = blackTimeMs;
    return j.dump();
}

std::string Protocol::opponentDisconnected() {
    nlohmann::json j;
    j["event"] = "opponent_disconnected";
    return j.dump();
}

} /* namespace net */
