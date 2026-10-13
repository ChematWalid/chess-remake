#pragma once

#include <string>
#include <string_view>

namespace net {

/**
 * @brief Utility for generating and validating human-readable room invite codes.
 *
 * Uses uppercase alphanumeric characters excluding easily confused glyphs (0, O, 1, I, L).
 */
class RoomCode {
public:
    /**
     * @brief Generates a random alphanumeric room code.
     * @param length Number of characters (default 6).
     * @return Generated room code string (e.g. "A7K9X2").
     */
    [[nodiscard]] static std::string generate(size_t length = 6);

    /**
     * @brief Checks if a string contains only valid room code characters.
     */
    [[nodiscard]] static bool isValid(std::string_view code);

private:
    static constexpr std::string_view CHARACTERS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
};

}
