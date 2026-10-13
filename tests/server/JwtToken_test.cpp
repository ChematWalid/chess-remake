#include <gtest/gtest.h>
#include "net/JwtToken.h"
#include <chrono>

/**
 * @brief Tests creating and successfully validating HS256 JWT player tokens.
 */
TEST(JwtTokenTest, CreateAndVerifyValidToken) {
    std::string secret = "test-secret-key-42";
    net::PlayerToken claims;
    claims.roomCode = "ABC999";
    claims.userId = "user-123";
    claims.displayName = "Alice";
    claims.color = "white";
    claims.initialSeconds = 300;
    claims.incrementSeconds = 5;
    claims.exp = std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count() + 3600;

    std::string token = net::JwtToken::create(claims, secret);
    EXPECT_FALSE(token.empty());

    auto decoded = net::JwtToken::verify(token, secret);
    ASSERT_TRUE(decoded.has_value());
    EXPECT_EQ(decoded->roomCode, "ABC999");
    EXPECT_EQ(decoded->userId, "user-123");
    EXPECT_EQ(decoded->displayName, "Alice");
    EXPECT_EQ(decoded->color, "white");
    EXPECT_EQ(decoded->initialSeconds, 300);
    EXPECT_EQ(decoded->incrementSeconds, 5);
}

/**
 * @brief Tests that signature tampering or wrong secret is rejected.
 */
TEST(JwtTokenTest, RejectTamperedOrWrongSecret) {
    std::string secret = "test-secret-key-42";
    net::PlayerToken claims;
    claims.roomCode = "ABC999";
    claims.userId = "user-123";
    claims.displayName = "Alice";
    claims.exp = std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count() + 3600;

    std::string token = net::JwtToken::create(claims, secret);

    auto wrongSecretResult = net::JwtToken::verify(token, "different-secret");
    EXPECT_FALSE(wrongSecretResult.has_value());

    std::string tampered = token;
    tampered.back() = (tampered.back() == 'a') ? 'b' : 'a';
    auto tamperedResult = net::JwtToken::verify(tampered, secret);
    EXPECT_FALSE(tamperedResult.has_value());
}

/**
 * @brief Tests that expired tokens are rejected.
 */
TEST(JwtTokenTest, RejectExpiredToken) {
    std::string secret = "test-secret-key-42";
    net::PlayerToken claims;
    claims.roomCode = "ABC999";
    claims.userId = "user-123";
    claims.displayName = "Alice";
    claims.exp = std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count() - 100;

    std::string token = net::JwtToken::create(claims, secret);
    auto result = net::JwtToken::verify(token, secret);
    EXPECT_FALSE(result.has_value());
}
