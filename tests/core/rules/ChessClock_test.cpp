#include "chess/rules/ChessClock.h"

#include <chrono>
#include <thread>
#include <gtest/gtest.h>

using namespace chess;

TEST(ChessClock, UnlimitedClockHasNoTimeLimit) {
    ChessClock clock;

    EXPECT_TRUE(clock.isUnlimited());
    EXPECT_FALSE(clock.isFlagged(Color::White));
    EXPECT_FALSE(clock.isFlagged(Color::Black));
    EXPECT_EQ(clock.timeRemaining(Color::White), std::chrono::milliseconds::max());
    EXPECT_EQ(clock.timeRemaining(Color::Black), std::chrono::milliseconds::max());

    clock.start(Color::White);
    std::this_thread::sleep_for(std::chrono::milliseconds(20));

    EXPECT_FALSE(clock.isFlagged(Color::White));
    EXPECT_EQ(clock.timeRemaining(Color::White), std::chrono::milliseconds::max());

    clock.press(Color::White);
    std::this_thread::sleep_for(std::chrono::milliseconds(20));

    EXPECT_FALSE(clock.isFlagged(Color::Black));
}

TEST(ChessClock, InitialTimeAndConfiguration) {
    auto tc = ChessClock::TimeControl::standard(5, 3);
    ChessClock clock(tc);

    EXPECT_FALSE(clock.isUnlimited());
    EXPECT_EQ(clock.timeControl().initialTime, std::chrono::milliseconds(300000));
    EXPECT_EQ(clock.timeControl().increment, std::chrono::milliseconds(3000));
    EXPECT_EQ(clock.timeRemaining(Color::White), std::chrono::milliseconds(300000));
    EXPECT_EQ(clock.timeRemaining(Color::Black), std::chrono::milliseconds(300000));
    EXPECT_FALSE(clock.isFlagged(Color::White));
    EXPECT_FALSE(clock.isFlagged(Color::Black));
}

TEST(ChessClock, ActivePlayerDeductionAndFischerIncrement) {
    auto tc = ChessClock::TimeControl::standard(1, 2); // 1 min (60,000ms), 2s inc
    ChessClock clock(tc);

    clock.start(Color::White);
    std::this_thread::sleep_for(std::chrono::milliseconds(50));

    auto whiteMid = clock.timeRemaining(Color::White);
    auto blackMid = clock.timeRemaining(Color::Black);

    EXPECT_LT(whiteMid, std::chrono::milliseconds(60000));
    EXPECT_EQ(blackMid, std::chrono::milliseconds(60000)); // Black time untouched

    // White presses clock -> receives 2,000ms increment
    clock.press(Color::White);
    auto whiteAfterPress = clock.timeRemaining(Color::White);
    EXPECT_GT(whiteAfterPress, whiteMid); // increment added
}

TEST(ChessClock, PauseAndResume) {
    auto tc = ChessClock::TimeControl::standard(1, 0);
    ChessClock clock(tc);

    clock.start(Color::White);
    std::this_thread::sleep_for(std::chrono::milliseconds(40));

    clock.pause();
    auto pausedTime1 = clock.timeRemaining(Color::White);

    std::this_thread::sleep_for(std::chrono::milliseconds(50));
    auto pausedTime2 = clock.timeRemaining(Color::White);

    // While paused, time should remain identical
    EXPECT_EQ(pausedTime1, pausedTime2);

    clock.resume();
    std::this_thread::sleep_for(std::chrono::milliseconds(40));
    auto resumedTime = clock.timeRemaining(Color::White);

    EXPECT_LT(resumedTime, pausedTime2);
}

TEST(ChessClock, FlagExpirationDetection) {
    // 25ms base time, 0 increment
    ChessClock::TimeControl quick{
        .initialTime = std::chrono::milliseconds(25),
        .increment = std::chrono::milliseconds(0),
        .isUnlimited = false
    };
    ChessClock clock(quick);

    clock.start(Color::White);
    EXPECT_FALSE(clock.isFlagged(Color::White));

    std::this_thread::sleep_for(std::chrono::milliseconds(45));

    EXPECT_TRUE(clock.isFlagged(Color::White));
    EXPECT_FALSE(clock.isFlagged(Color::Black));
    EXPECT_EQ(clock.timeRemaining(Color::White), std::chrono::milliseconds::zero());
}

/**
 * Regression test for Item 2: Large minutes overflow and fromSeconds factory.
 */
TEST(ChessClock, LargeMinutesOverflowAndFromSeconds) {
    auto tcLarge = ChessClock::TimeControl::standard(40000, 0);
    EXPECT_EQ(tcLarge.initialTime.count(), 2400000000LL);

    auto tcSubMinute = ChessClock::TimeControl::fromSeconds(30, 2);
    EXPECT_FALSE(tcSubMinute.isUnlimited);
    EXPECT_EQ(tcSubMinute.initialTime.count(), 30000);
    EXPECT_EQ(tcSubMinute.increment.count(), 2000);
}
