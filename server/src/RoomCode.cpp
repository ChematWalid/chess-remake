#include "net/RoomCode.h"

#include <random>

namespace net {

std::string RoomCode::generate(size_t length) {
    static thread_local std::mt19937 rng{std::random_device{}()};
    std::uniform_int_distribution<size_t> dist(0, CHARACTERS.size() - 1);

    std::string code;
    code.reserve(length);
    for (size_t i = 0; i < length; ++i) {
        code.push_back(CHARACTERS[dist(rng)]);
    }
    return code;
}

bool RoomCode::isValid(std::string_view code) {
    if (code.empty()) {
        return false;
    }
    for (char c : code) {
        if (CHARACTERS.find(c) == std::string_view::npos) {
            return false;
        }
    }
    return true;
}

}
