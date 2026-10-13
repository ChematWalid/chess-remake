#include "chess/board/Square.h"
#include "chess/board/Types.h"
#include "chess/notation/Fen.h"
#include "chess/pieces/PieceFactory.h"

#include <gtest/gtest.h>
#include <stdexcept>

using namespace chess;

TEST(Fen, StandardPositionParse) {
    Position pos = Fen::standard();

    EXPECT_EQ(pos.sideToMove(), Color::White);
    EXPECT_EQ(pos.castling(), CastlingRights::all());
    EXPECT_FALSE(pos.enPassant().has_value());
    EXPECT_EQ(pos.halfmoveClock(), 0);
    EXPECT_EQ(pos.fullmoveNumber(), 1);

    EXPECT_EQ(pos.board().pieceAt(Square(0, 0)), &PieceFactory::get(Color::White, PieceType::Rook));
    EXPECT_EQ(pos.board().pieceAt(Square(4, 0)), &PieceFactory::get(Color::White, PieceType::King));
    EXPECT_EQ(pos.board().pieceAt(Square(4, 1)), &PieceFactory::get(Color::White, PieceType::Pawn));
    EXPECT_EQ(pos.board().pieceAt(Square(4, 6)), &PieceFactory::get(Color::Black, PieceType::Pawn));
    EXPECT_EQ(pos.board().pieceAt(Square(4, 7)), &PieceFactory::get(Color::Black, PieceType::King));
}

TEST(Fen, StandardPositionSerializeRoundTrip) {
    Position pos = Fen::standard();
    std::string fen = Fen::serialize(pos);
    EXPECT_EQ(fen, Fen::kStartingFen);
}

TEST(Fen, EnPassantAndCustomClocks) {
    std::string fenIn = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
    Position pos = Fen::parse(fenIn);

    EXPECT_EQ(pos.sideToMove(), Color::Black);
    ASSERT_TRUE(pos.enPassant().has_value());
    EXPECT_EQ(pos.enPassant()->toString(), "e3");
    EXPECT_EQ(pos.halfmoveClock(), 0);
    EXPECT_EQ(pos.fullmoveNumber(), 1);

    std::string fenOut = Fen::serialize(pos);
    EXPECT_EQ(fenOut, fenIn);
}

TEST(Fen, PartialCastlingAndDeepGame) {
    std::string fenIn = "r3k2r/8/8/8/8/8/8/R3K2R w Kq - 5 12";
    Position pos = Fen::parse(fenIn);

    EXPECT_EQ(pos.sideToMove(), Color::White);
    EXPECT_TRUE(pos.castling().has(Color::White, Side::KingSide));
    EXPECT_FALSE(pos.castling().has(Color::White, Side::QueenSide));
    EXPECT_FALSE(pos.castling().has(Color::Black, Side::KingSide));
    EXPECT_TRUE(pos.castling().has(Color::Black, Side::QueenSide));
    EXPECT_EQ(pos.halfmoveClock(), 5);
    EXPECT_EQ(pos.fullmoveNumber(), 12);

    std::string fenOut = Fen::serialize(pos);
    EXPECT_EQ(fenOut, fenIn);
}

TEST(Fen, ParseMalformedStrings) {
    // Incomplete
    EXPECT_THROW(Fen::parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"), std::invalid_argument);

    // Too many files in a rank
    EXPECT_THROW(Fen::parse("rnbqkbnr9/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"), std::invalid_argument);

    // Too few ranks
    EXPECT_THROW(Fen::parse("8/8/8/8/8/8/8 w - - 0 1"), std::invalid_argument);

    // Invalid active color
    EXPECT_THROW(Fen::parse("8/8/8/8/8/8/8/8 x - - 0 1"), std::invalid_argument);
}
