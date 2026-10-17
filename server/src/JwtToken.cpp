#include "net/JwtToken.h"
#include <chrono>
#include <nlohmann/json.hpp>
#include <openssl/crypto.h>
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <vector>
#include "chess/notation/Fen.h"
#include "chess/rules/PositionValidator.h"

namespace net {

namespace {

constexpr size_t kMaxJwtSizeBytes = 64 * 1024;

const std::string kB64Chars =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    "abcdefghijklmnopqrstuvwxyz"
    "0123456789+/";

std::string base64UrlEncode(const std::string& input) {
    std::string ret;
    int val = 0;
    int valb = -6;
    for (unsigned char c : input) {
        val = (val << 8) + c;
        valb += 8;
        while (valb >= 0) {
            ret.push_back(kB64Chars[(val >> valb) & 0x3F]);
            valb -= 6;
        }
    }
    if (valb > -6) {
        ret.push_back(kB64Chars[((val << 8) >> (valb + 8)) & 0x3F]);
    }
    for (char& c : ret) {
        if (c == '+') {
            c = '-';
        } else if (c == '/') {
            c = '_';
        }
    }
    return ret;
}

std::string base64UrlDecode(const std::string& input) {
    std::string standardB64 = input;
    for (char& c : standardB64) {
        if (c == '-') {
            c = '+';
        } else if (c == '_') {
            c = '/';
        }
    }
    while (standardB64.size() % 4 != 0) {
        standardB64.push_back('=');
    }
    std::vector<int> table(256, -1);
    for (int i = 0; i < 64; ++i) {
        table[static_cast<unsigned char>(kB64Chars[i])] = i;
    }

    std::string out;
    int val = 0;
    int valb = -8;
    for (unsigned char c : standardB64) {
        if (c == '=') {
            break;
        }
        if (table[c] == -1) {
            return "";
        }
        val = (val << 6) + table[c];
        valb += 6;
        if (valb >= 0) {
            out.push_back(static_cast<char>((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    return out;
}

std::string computeHmacSha256(const std::string& data, const std::string& secret) {
    unsigned char md[EVP_MAX_MD_SIZE];
    unsigned int mdLen = 0;
    HMAC(
        EVP_sha256(),
        secret.data(),
        static_cast<int>(secret.size()),
        reinterpret_cast<const unsigned char*>(data.data()),
        data.size(),
        md,
        &mdLen
    );
    return std::string(reinterpret_cast<char*>(md), mdLen);
}

}

std::optional<PlayerToken> JwtToken::verify(
    const std::string& token,
    const std::string& secret
) {
    if (token.size() > kMaxJwtSizeBytes) {
        return std::nullopt;
    }

    auto dot1 = token.find('.');
    if (dot1 == std::string::npos) {
        return std::nullopt;
    }
    auto dot2 = token.find('.', dot1 + 1);
    if (dot2 == std::string::npos || token.find('.', dot2 + 1) != std::string::npos) {
        return std::nullopt;
    }

    const std::string headerJson = base64UrlDecode(token.substr(0, dot1));
    try {
        const auto header = nlohmann::json::parse(headerJson);
        if (!header.is_object() || header.value("alg", std::string{}) != "HS256"
                || (header.contains("typ") && header.value("typ", std::string{}) != "JWT")) {
            return std::nullopt;
        }
    } catch (...) {
        return std::nullopt;
    }

    std::string toSign = token.substr(0, dot2);
    std::string signatureB64 = token.substr(dot2 + 1);

    std::string expectedSig = computeHmacSha256(toSign, secret);
    std::string actualSig = base64UrlDecode(signatureB64);

    if (expectedSig.size() != actualSig.size() || base64UrlEncode(actualSig) != signatureB64) {
        return std::nullopt;
    }
    if (CRYPTO_memcmp(expectedSig.data(), actualSig.data(), expectedSig.size()) != 0) {
        return std::nullopt;
    }

    std::string payloadB64 = token.substr(dot1 + 1, dot2 - (dot1 + 1));
    std::string payloadJsonStr = base64UrlDecode(payloadB64);
    if (payloadJsonStr.empty()) {
        return std::nullopt;
    }

    nlohmann::json j;
    try {
        j = nlohmann::json::parse(payloadJsonStr);
    } catch (...) {
        return std::nullopt;
    }

    if (!j.is_object() || !j.contains("exp") || !j["exp"].is_number_integer()) {
        return std::nullopt;
    }
    int64_t exp = j["exp"].get<int64_t>();

    auto now = std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();

    if (now >= exp) {
        return std::nullopt;
    }

    PlayerToken pt;
    pt.exp = exp;

    if (j.contains("roomCode") && j["roomCode"].is_string()) {
        pt.roomCode = j["roomCode"].get<std::string>();
    } else if (j.contains("room_code") && j["room_code"].is_string()) {
        pt.roomCode = j["room_code"].get<std::string>();
    }

    if (j.contains("userId") && j["userId"].is_string()) {
        pt.userId = j["userId"].get<std::string>();
    } else if (j.contains("user_id") && j["user_id"].is_string()) {
        pt.userId = j["user_id"].get<std::string>();
    }

    if (j.contains("displayName") && j["displayName"].is_string()) {
        pt.displayName = j["displayName"].get<std::string>();
    } else if (j.contains("display_name") && j["display_name"].is_string()) {
        pt.displayName = j["display_name"].get<std::string>();
    }

    if (j.contains("color") && j["color"].is_string()) {
        pt.color = j["color"].get<std::string>();
    }

    if (j.contains("initialSeconds") && j["initialSeconds"].is_number()) {
        pt.initialSeconds = j["initialSeconds"].get<int64_t>();
    } else if (j.contains("initial_seconds") && j["initial_seconds"].is_number()) {
        pt.initialSeconds = j["initial_seconds"].get<int64_t>();
    }

    if (j.contains("incrementSeconds") && j["incrementSeconds"].is_number()) {
        pt.incrementSeconds = j["incrementSeconds"].get<int64_t>();
    } else if (j.contains("increment_seconds") && j["increment_seconds"].is_number()) {
        pt.incrementSeconds = j["increment_seconds"].get<int64_t>();
    }
    if (j.contains("initial_fen") && j["initial_fen"].is_string()) {
        pt.initialFen = j["initial_fen"].get<std::string>();
    }
    if (j.contains("setup_moves") && j["setup_moves"].is_array()) {
        for (const auto& move : j["setup_moves"]) {
            if (!move.is_string()) return std::nullopt;
            pt.setupMoves.push_back(move.get<std::string>());
        }
    }
    if (j.contains("setup_fens") && j["setup_fens"].is_array()) {
        for (const auto& fen : j["setup_fens"]) {
            if (!fen.is_string()) return std::nullopt;
            pt.setupFens.push_back(fen.get<std::string>());
        }
    }

    if (pt.roomCode.empty() || pt.userId.empty() || pt.displayName.empty()
            || (pt.color != "white" && pt.color != "black")
            || pt.initialSeconds < 0 || pt.incrementSeconds < 0) {
        return std::nullopt;
    }

    if (pt.setupMoves.size() > 300 || pt.setupFens.size() > 301) return std::nullopt;
    if (pt.initialFen.empty()) {
        if (!pt.setupMoves.empty() || !pt.setupFens.empty()) return std::nullopt;
    } else {
        if (pt.initialFen.size() > 128) return std::nullopt;
        for (const auto& fen : pt.setupFens) {
            if (fen.size() > 128) return std::nullopt;
        }
        try {
            if (!chess::PositionValidator::isPlayable(chess::Fen::parse(pt.initialFen))
                    || !chess::PositionValidator::replayMatches(pt.setupMoves, pt.setupFens, pt.initialFen)) {
                return std::nullopt;
            }
        } catch (const std::exception&) {
            return std::nullopt;
        }
    }

    return pt;
}

std::string JwtToken::create(
    const PlayerToken& claims,
    const std::string& secret
) {
    std::string header = R"({"alg":"HS256","typ":"JWT"})";

    nlohmann::json j;
    j["room_code"] = claims.roomCode;
    j["user_id"] = claims.userId;
    j["display_name"] = claims.displayName;
    j["color"] = claims.color;
    j["initial_seconds"] = claims.initialSeconds;
    j["increment_seconds"] = claims.incrementSeconds;
    if (!claims.initialFen.empty()) j["initial_fen"] = claims.initialFen;
    j["setup_moves"] = claims.setupMoves;
    j["setup_fens"] = claims.setupFens;
    j["exp"] = claims.exp;

    std::string headerB64 = base64UrlEncode(header);
    std::string payloadB64 = base64UrlEncode(j.dump());
    std::string toSign = headerB64 + "." + payloadB64;
    std::string signature = computeHmacSha256(toSign, secret);
    std::string sigB64 = base64UrlEncode(signature);

    return toSign + "." + sigB64;
}

}
