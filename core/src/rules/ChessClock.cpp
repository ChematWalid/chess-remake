#include "chess/rules/ChessClock.h"

namespace chess {

ChessClock::TimeControl ChessClock::TimeControl::unlimited() {
    return TimeControl{
        .initialTime = std::chrono::milliseconds(0),
        .increment = std::chrono::milliseconds(0),
        .isUnlimited = true
    };
}

ChessClock::TimeControl ChessClock::TimeControl::fromSeconds(int64_t initialSeconds, int64_t incrementSeconds) {
    return TimeControl{
        .initialTime = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::seconds(initialSeconds)),
        .increment = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::seconds(incrementSeconds)),
        .isUnlimited = false
    };
}

ChessClock::TimeControl ChessClock::TimeControl::standard(int baseMinutes, int incrementSeconds) {
    return fromSeconds(static_cast<int64_t>(baseMinutes) * 60, incrementSeconds);
}

ChessClock::ChessClock()
    : ChessClock(TimeControl::unlimited()) {}

ChessClock::ChessClock(TimeControl timeControl)
    : control(timeControl),
      whiteRemaining(timeControl.initialTime),
      blackRemaining(timeControl.initialTime),
      runningColor(std::nullopt),
      lastUpdate(std::chrono::steady_clock::now()),
      isPaused(false) {}

void ChessClock::updateElapsedTime() {
    if (control.isUnlimited || !runningColor.has_value() || isPaused) {
        return;
    }

    auto now = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - lastUpdate);
    lastUpdate = now;

    if (*runningColor == Color::White) {
        whiteRemaining -= elapsed;
        if (whiteRemaining < std::chrono::milliseconds::zero()) {
            whiteRemaining = std::chrono::milliseconds::zero();
        }
    } else {
        blackRemaining -= elapsed;
        if (blackRemaining < std::chrono::milliseconds::zero()) {
            blackRemaining = std::chrono::milliseconds::zero();
        }
    }
}

void ChessClock::start(Color player) {
    if (control.isUnlimited) {
        runningColor = player;
        return;
    }

    runningColor = player;
    lastUpdate = std::chrono::steady_clock::now();
    isPaused = false;
}

void ChessClock::press(Color player) {
    if (control.isUnlimited) {
        runningColor = (player == Color::White) ? Color::Black : Color::White;
        return;
    }

    updateElapsedTime();

    if (player == Color::White) {
        whiteRemaining += control.increment;
        runningColor = Color::Black;
    } else {
        blackRemaining += control.increment;
        runningColor = Color::White;
    }

    lastUpdate = std::chrono::steady_clock::now();
}

void ChessClock::pause() {
    if (control.isUnlimited) {
        isPaused = true;
        return;
    }

    updateElapsedTime();
    isPaused = true;
}

void ChessClock::resume() {
    if (control.isUnlimited) {
        isPaused = false;
        return;
    }

    if (isPaused) {
        lastUpdate = std::chrono::steady_clock::now();
        isPaused = false;
    }
}

std::chrono::milliseconds ChessClock::timeRemaining(Color player) const {
    if (control.isUnlimited) {
        return std::chrono::milliseconds::max();
    }

    auto remaining = (player == Color::White) ? whiteRemaining : blackRemaining;
    if (runningColor.has_value() && *runningColor == player && !isPaused) {
        auto now = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - lastUpdate);
        remaining -= elapsed;
    }

    if (remaining < std::chrono::milliseconds::zero()) {
        return std::chrono::milliseconds::zero();
    }
    return remaining;
}

bool ChessClock::isFlagged(Color player) const {
    if (control.isUnlimited) {
        return false;
    }
    return timeRemaining(player) <= std::chrono::milliseconds::zero();
}

bool ChessClock::isUnlimited() const {
    return control.isUnlimited;
}

const ChessClock::TimeControl& ChessClock::timeControl() const {
    return control;
}

} 
