#include "chess/board/CastlingRights.h"

#include <stdexcept>

namespace chess {

namespace {

constexpr uint8_t WHITE_OO  = 1 << 0;
constexpr uint8_t WHITE_OOO = 1 << 1;
constexpr uint8_t BLACK_OO  = 1 << 2;
constexpr uint8_t BLACK_OOO = 1 << 3;

uint8_t bitFor(Color color, Side side) {
    if (color == Color::White) {
        return (side == Side::KingSide) ? WHITE_OO : WHITE_OOO;
    }
    return (side == Side::KingSide) ? BLACK_OO : BLACK_OOO;
}

} // namespace

CastlingRights::CastlingRights(uint8_t m) : mask(m) {}

CastlingRights::CastlingRights() : mask(0) {}

CastlingRights CastlingRights::all() {
    return CastlingRights(WHITE_OO | WHITE_OOO | BLACK_OO | BLACK_OOO);
}

CastlingRights CastlingRights::none() {
    return CastlingRights(0);
}

bool CastlingRights::has(Color color, Side side) const {
    return (mask & bitFor(color, side)) != 0;
}

void CastlingRights::remove(Color color, Side side) {
    mask &= static_cast<uint8_t>(~bitFor(color, side));
}

void CastlingRights::removeAll(Color color) {
    if (color == Color::White) {
        mask &= static_cast<uint8_t>(~(WHITE_OO | WHITE_OOO));
    } else {
        mask &= static_cast<uint8_t>(~(BLACK_OO | BLACK_OOO));
    }
}

std::string CastlingRights::toString() const {
    std::string s;
    if (has(Color::White, Side::KingSide))  s += 'K';
    if (has(Color::White, Side::QueenSide)) s += 'Q';
    if (has(Color::Black, Side::KingSide))  s += 'k';
    if (has(Color::Black, Side::QueenSide)) s += 'q';
    return s.empty() ? "-" : s;
}

CastlingRights CastlingRights::fromString(const std::string& str) {
    if (str == "-") {
        return CastlingRights::none();
    }

    uint8_t m = 0;
    for (char c : str) {
        switch (c) {
            case 'K': m |= WHITE_OO;  break;
            case 'Q': m |= WHITE_OOO; break;
            case 'k': m |= BLACK_OO;  break;
            case 'q': m |= BLACK_OOO; break;
            default:
                throw std::invalid_argument("CastlingRights: invalid character in string \"" + str + "\"");
        }
    }
    return CastlingRights(m);
}

bool CastlingRights::operator==(const CastlingRights& other) const {
    return mask == other.mask;
}

} // namespace chess
