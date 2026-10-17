#include "chess/render/BoardRenderer.h"
#include "chess/board/Square.h"
#include "chess/pieces/Piece.h"

#include <sstream>

namespace chess::cli {

std::string BoardRenderer::render(const Board& board) {
    std::ostringstream oss;
    oss << "  +---+---+---+---+---+---+---+---+\n";

    for (int rank = 7; rank >= 0; --rank) {
        oss << (rank + 1) << " |";
        for (int file = 0; file < 8; ++file) {
            Square sq(file, rank);
            const Piece* piece = board.pieceAt(sq);
            if (piece == nullptr) {
                oss << " . |";
            } else {
                oss << ' ' << piece->symbol() << " |";
            }
        }
        oss << "\n  +---+---+---+---+---+---+---+---+\n";
    }

    oss << "    a   b   c   d   e   f   g   h\n";
    return oss.str();
}

} ::cli
