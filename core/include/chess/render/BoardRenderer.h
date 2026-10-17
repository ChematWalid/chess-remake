#pragma once

#include <string>

#include "chess/board/Board.h"

namespace chess::cli {

/**
 * @brief Renders the chessboard state into a clean ASCII terminal representation.
 */
class BoardRenderer {
public:
    /**
     * @brief Formats the 8x8 board with rank numbers, file letters, and piece characters.
     * 
     * @details White pieces are rendered as uppercase (P, N, B, R, Q, K).
     * Black pieces are rendered as lowercase (p, n, b, r, q, k).
     * Empty squares are rendered as dots (.).
     * 
     * @param board The Board to render.
     * @return Formatted multi-line string.
     */
    static std::string render(const Board& board);
};

}
