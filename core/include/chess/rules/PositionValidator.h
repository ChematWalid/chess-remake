#pragma once

#include "chess/board/Position.h"
#include <string>
#include <vector>

namespace chess {

class PositionValidator {
public:
    [[nodiscard]] static bool isPlayable(const Position& position);
    [[nodiscard]] static bool replayMatches(
        const std::vector<std::string>& sanMoves,
        const std::vector<std::string>& fens,
        const std::string& finalFen
    );
};

}
