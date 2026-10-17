#include "chess/notation/PgnReader.h"

#include <cctype>
#include <fstream>
#include <sstream>

namespace chess {

namespace {

std::string trim(std::string_view s) {
    size_t start = 0;
    while (start < s.size() && std::isspace(static_cast<unsigned char>(s[start]))) {
        ++start;
    }
    size_t end = s.size();
    while (end > start && std::isspace(static_cast<unsigned char>(s[end - 1]))) {
        --end;
    }
    return std::string(s.substr(start, end - start));
}

bool isResultToken(std::string_view s) {
    return (s == "1-0" || s == "0-1" || s == "1/2-1/2" || s == "*");
}

bool isMoveNumber(std::string_view s) {
    if (s.empty()) return false;
    if (!std::isdigit(static_cast<unsigned char>(s[0]))) return false;

    size_t i = 0;
    while (i < s.size() && std::isdigit(static_cast<unsigned char>(s[i]))) {
        ++i;
    }
    if (i == s.size()) {
        return false;
    }
    while (i < s.size() && s[i] == '.') {
        ++i;
    }
    return (i == s.size());
}

std::string stripCommentsAndVariations(std::string_view movetext) {
    std::string cleaned;
    cleaned.reserve(movetext.size());

    int commentDepth = 0;
    int variationDepth = 0;

    for (size_t i = 0; i < movetext.size(); ++i) {
        char c = movetext[i];
        if (c == '{') {
            ++commentDepth;
        } else if (c == '}' && commentDepth > 0) {
            --commentDepth;
        } else if (c == '(') {
            ++variationDepth;
        } else if (c == ')' && variationDepth > 0) {
            --variationDepth;
        } else if (c == ';') {
            while (i < movetext.size() && movetext[i] != '\n') {
                ++i;
            }
        } else if (commentDepth == 0 && variationDepth == 0) {
            cleaned.push_back(c);
        }
    }
    return cleaned;
}

void parseMovesFromMovetext(std::string_view movetext, PgnGame& game) {
    std::string cleaned = stripCommentsAndVariations(movetext);
    std::istringstream moveStream(cleaned);
    std::string token;

    while (moveStream >> token) {
        if (isResultToken(token)) {
            game.result = token;
        } else if (isMoveNumber(token)) {
            continue;
        } else if (token.starts_with('$')) {
            continue;
        } else {
            size_t dotPos = token.rfind('.');
            if (dotPos != std::string::npos) {
                token = token.substr(dotPos + 1);
            }
            token = trim(token);
            if (!token.empty() && !isResultToken(token)) {
                game.moves.push_back(token);
            }
        }
    }
}

}

std::vector<PgnGame> PgnReader::parse(std::string_view pgnContent) {
    std::vector<PgnGame> games;
    std::string text(pgnContent);
    std::istringstream stream(text);

    std::string line;
    PgnGame currentGame;
    std::string movetext;
    bool inGame = false;

    auto finishCurrentGame = [&]() {
        if (!currentGame.headers.empty() || !movetext.empty()) {
            parseMovesFromMovetext(movetext, currentGame);

            if (!currentGame.moves.empty() || !currentGame.headers.empty()) {
                if (currentGame.result.empty() && currentGame.headers.contains("Result")) {
                    currentGame.result = currentGame.headers["Result"];
                }
                games.push_back(std::move(currentGame));
            }
            currentGame = PgnGame{};
            movetext.clear();
            inGame = false;
        }
    };

    while (std::getline(stream, line)) {
        std::string trimmed = trim(line);
        if (trimmed.empty()) {
            continue;
        }

        if (trimmed.front() == '[' && trimmed.back() == ']') {
            auto spacePos = trimmed.find(' ');
            auto firstQuote = trimmed.find('"');
            auto lastQuote = trimmed.rfind('"');

            if (spacePos != std::string::npos && firstQuote != std::string::npos && lastQuote > firstQuote) {
                if (!movetext.empty()) {
                    finishCurrentGame();
                }
                inGame = true;
                std::string key = trimmed.substr(1, spacePos - 1);
                std::string value = trimmed.substr(firstQuote + 1, lastQuote - firstQuote - 1);
                currentGame.headers[key] = value;
                continue;
            }
        }

        inGame = true;
        movetext += ' ';
        movetext += trimmed;
    }

    if (inGame) {
        finishCurrentGame();
    }

    return games;
}

std::vector<PgnGame> PgnReader::loadFromFile(const std::filesystem::path& filepath) {
    std::ifstream file(filepath);
    if (!file.is_open()) {
        return {};
    }
    std::stringstream buffer;
    buffer << file.rdbuf();
    return parse(buffer.str());
}

} 
