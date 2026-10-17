#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace net {

/**
 * @brief Decoded claims from an authenticated player token.
 */
struct PlayerToken {
    std::string roomCode;
    std::string userId;
    std::string displayName;
    std::string color;
    int64_t initialSeconds{0};
    int64_t incrementSeconds{0};
    int64_t exp{0};
    std::string initialFen;
    std::vector<std::string> setupMoves;
    std::vector<std::string> setupFens;
};

/**
 * @brief Utilities for creating and cryptographically validating HS256 JWT tokens.
 */
class JwtToken {
public:
    /**
     * @brief Verifies an HS256 JWT token against the given secret and checks expiration.
     * @param token Compact JWT string (header.payload.signature).
     * @param secret Shared HMAC-SHA256 secret key.
     * @return Decoded PlayerToken on success, std::nullopt if signature is invalid or token expired.
     */
    [[nodiscard]] static std::optional<PlayerToken> verify(
        const std::string& token,
        const std::string& secret
    );

    /**
     * @brief Mints a signed HS256 JWT token for testing and inter-service authentication.
     * @param claims Token payload claims.
     * @param secret Shared HMAC-SHA256 secret key.
     * @return Compact JWT string.
     */
    [[nodiscard]] static std::string create(
        const PlayerToken& claims,
        const std::string& secret
    );
};

}
