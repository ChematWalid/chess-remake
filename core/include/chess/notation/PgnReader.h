#pragma once

#include <filesystem>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace chess {

/**
 * @brief Represents a single parsed chess game from a Portable Game Notation (PGN) record.
 */
struct PgnGame {
    std::unordered_map<std::string, std::string> headers;
    std::vector<std::string> moves;
    std::string result;

    /**
     * @brief Gets a header value by tag name or returns the default value.
     */
    [[nodiscard]] std::string getHeader(const std::string& key, const std::string& defaultValue = "") const {
        auto it = headers.find(key);
        return (it != headers.end()) ? it->second : defaultValue;
    }
};

/**
 * @brief Parser and file loader for Portable Game Notation (PGN) files.
 *
 * Supports stripping comments, recursive annotations, move numbers, and NAGs.
 */
class PgnReader {
public:
    /**
     * @brief Parses a raw PGN text buffer containing one or more games.
     * @param pgnContent The PGN string content.
     * @return Vector of parsed PgnGame objects.
     */
    [[nodiscard]] static std::vector<PgnGame> parse(std::string_view pgnContent);

    /**
     * @brief Reads a .pgn file from disk and parses all contained games.
     * @param filepath Path to the .pgn file.
     * @return Vector of parsed PgnGame objects.
     */
    [[nodiscard]] static std::vector<PgnGame> loadFromFile(const std::filesystem::path& filepath);
};

} // namespace chess
