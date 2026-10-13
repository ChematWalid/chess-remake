#pragma once

#include <chrono>
#include <optional>

#include "chess/board/Types.h"

namespace chess {

/**
 * @brief Authoritative game clock managing player time controls and flag expiration.
 * 
 * @details Supports untimed games (no time limit) as well as timed matches with
 * base duration and optional Fischer increment added after each turn.
 */
class ChessClock {
public:
    /**
     * @brief Configuration specifying the time constraints of a game.
     */
    struct TimeControl {
        std::chrono::milliseconds initialTime; /**< Base time per player at start. */
        std::chrono::milliseconds increment;   /**< Bonus added to player clock after each move. */
        bool isUnlimited;                       /**< True if the game has no clock / time limit. */

        /**
         * @brief Creates an untimed control (no clock / no time limit).
         */
        static TimeControl unlimited();

        /**
         * @brief Creates a standard timed control with base minutes and second increment.
         */
        static TimeControl standard(int baseMinutes, int incrementSeconds = 0);

        /**
         * @brief Creates a timed control with base seconds and second increment.
         */
        static TimeControl fromSeconds(int64_t initialSeconds, int64_t incrementSeconds = 0);
    };

private:
    TimeControl control;
    std::chrono::milliseconds whiteRemaining;
    std::chrono::milliseconds blackRemaining;
    std::optional<Color> runningColor;
    std::chrono::steady_clock::time_point lastUpdate;
    bool isPaused;

    void updateElapsedTime();

public:
    /**
     * @brief Constructs an untimed game clock by default.
     */
    ChessClock();

    /**
     * @brief Constructs a game clock with a specific time control.
     * 
     * @param timeControl The time control configuration.
     */
    explicit ChessClock(TimeControl timeControl);

    /**
     * @brief Starts the clock for the designated player.
     * 
     * @param player The color of the player whose clock should begin running.
     */
    void start(Color player);

    /**
     * @brief Switches the clock to the opponent after a player completes their move.
     * 
     * @details Deducts elapsed time, adds increment to the player, and switches active timer.
     * 
     * @param player The color of the player who just finished their move.
     */
    void press(Color player);

    /**
     * @brief Pauses the running clock without advancing time.
     */
    void pause();

    /**
     * @brief Resumes the clock for the active player.
     */
    void resume();

    /**
     * @brief Calculates the exact remaining time for a player.
     * 
     * @param player The color of the player.
     * @return Duration remaining in milliseconds (or max duration if untimed).
     */
    std::chrono::milliseconds timeRemaining(Color player) const;

    /**
     * @brief Checks if a player has run out of time.
     * 
     * @param player The color of the player to evaluate.
     * @return True if time has expired, false if time remains or game is untimed.
     */
    bool isFlagged(Color player) const;

    /**
     * @brief Checks whether the clock is in untimed mode (no time limit).
     * 
     * @return True if game has no time limit, false otherwise.
     */
    bool isUnlimited() const;

    /**
     * @brief Gets the configured time control.
     * 
     * @return Const reference to TimeControl.
     */
    const TimeControl& timeControl() const;
};

} // namespace chess
