#include "chess/board/Square.h"

#include <stdexcept>

namespace chess {

Square::Square(int file, int rank) {
    if (file < 0 || file > 7 || rank < 0 || rank > 7) {
        throw std::out_of_range("Square: file and rank must be in range 0-7");
    }
    idx = static_cast<int8_t>(rank * 8 + file);
}

Square Square::fromString(const std::string& s) {
    if (s.length() != 2) {
        throw std::invalid_argument("Square: string must be exactly 2 characters (e.g. \"e4\")");
    }

    int file = s[0] - 'a';
    int rank = s[1] - '1';

    if (file < 0 || file > 7 || rank < 0 || rank > 7) {
        throw std::invalid_argument("Square: invalid square string \"" + s + "\"");
    }

    return Square(file, rank);
}

std::optional<Square> Square::tryOffset(Square s, int df, int dr) {
    int newFile = s.file() + df;
    int newRank = s.rank() + dr;

    if (newFile < 0 || newFile > 7 || newRank < 0 || newRank > 7) {
        return std::nullopt;
    }

    return Square(newFile, newRank);
}

int Square::file() const {
    return idx % 8;
}

int Square::rank() const {
    return idx / 8;
}

int Square::index() const {
    return idx;
}

std::string Square::toString() const {
    std::string s = "  ";
    s[0] = static_cast<char>('a' + file());
    s[1] = static_cast<char>('1' + rank());
    return s;
}

bool Square::operator==(const Square& other) const {
    return idx == other.idx;
}

} 
