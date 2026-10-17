#include <gtest/gtest.h>
#include "net/JwtToken.h"
#include "chess/notation/Fen.h"
#include "chess/rules/MoveGenerator.h"
#include "chess/notation/SanNotation.h"
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
    auto signatureStart = tampered.rfind('.') + 1;
    tampered[signatureStart] = (tampered[signatureStart] == 'a') ? 'b' : 'a';
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

TEST(JwtTokenTest, RejectsOversizedSignedToken) {
    const std::string secret = "test-secret-key-42";
    net::PlayerToken claims;
    claims.roomCode = "ABC999";
    claims.userId = "user-123";
    claims.displayName = std::string(64 * 1024, 'A');
    claims.color = "white";
    claims.exp = std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count() + 3600;

    const std::string token = net::JwtToken::create(claims, secret);
    ASSERT_GT(token.size(), 64 * 1024);
    EXPECT_FALSE(net::JwtToken::verify(token, secret).has_value());
}

TEST(JwtTokenTest, RejectsTokensWithEmptyRequiredClaims) {
    const std::string secret = "test-secret-key-42";
    net::PlayerToken claims;
    claims.roomCode = "ABC999";
    claims.userId = "user-123";
    claims.displayName = "Alice";
    claims.color = "white";
    claims.exp = std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count() + 3600;

    auto missingRoom = claims;
    missingRoom.roomCode.clear();
    EXPECT_FALSE(net::JwtToken::verify(net::JwtToken::create(missingRoom, secret), secret).has_value());

    auto missingPlayer = claims;
    missingPlayer.userId.clear();
    EXPECT_FALSE(net::JwtToken::verify(net::JwtToken::create(missingPlayer, secret), secret).has_value());

    auto missingDisplayName = claims;
    missingDisplayName.displayName.clear();
    EXPECT_FALSE(net::JwtToken::verify(net::JwtToken::create(missingDisplayName, secret), secret).has_value());
}

TEST(JwtTokenTest, PreservesValidatedCustomStartingPositionAndReplay) {
    const std::string secret = "test-secret-key-42";
    const auto start = chess::Fen::standard();
    const auto move = chess::MoveGenerator::resolve(start, chess::Square(4, 1), chess::Square(4, 3));
    ASSERT_TRUE(move.has_value());
    chess::SanNotation notation;
    const std::string sanMove = notation.serialize(start, *move);
    const auto finalPosition = start.after(*move);

    net::PlayerToken claims;
    claims.roomCode = "CUS123";
    claims.userId = "user-456";
    claims.displayName = "Bob";
    claims.color = "black";
    claims.initialFen = chess::Fen::serialize(finalPosition);
    claims.setupMoves = {sanMove};
    claims.setupFens = {chess::Fen::serialize(start), claims.initialFen};
    claims.exp = std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count() + 3600;

    const auto token = net::JwtToken::create(claims, secret);
    const auto decoded = net::JwtToken::verify(token, secret);
    ASSERT_TRUE(decoded.has_value());
    EXPECT_EQ(decoded->initialFen, claims.initialFen);
    EXPECT_EQ(decoded->setupMoves, claims.setupMoves);
    EXPECT_EQ(decoded->setupFens, claims.setupFens);
}

TEST(JwtTokenTest, RejectsCustomReplayThatDoesNotMatchItsFenSnapshots) {
    const std::string secret = "test-secret-key-42";
    const auto start = chess::Fen::standard();
    net::PlayerToken claims;
    claims.roomCode = "CUS123";
    claims.userId = "user-456";
    claims.displayName = "Bob";
    claims.color = "white";
    claims.initialFen = chess::Fen::serialize(start);
    claims.setupMoves = {"e4"};
    claims.setupFens = {claims.initialFen, claims.initialFen};
    claims.exp = std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count() + 3600;

    EXPECT_FALSE(net::JwtToken::verify(net::JwtToken::create(claims, secret), secret).has_value());
}
