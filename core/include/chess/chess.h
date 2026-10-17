#pragma once

/** @name Core board representations */
#include "chess/board/Board.h"
#include "chess/board/CastlingRights.h"
#include "chess/board/Position.h"
#include "chess/board/Square.h"
#include "chess/board/Types.h"

/** @name Piece hierarchy */
#include "chess/pieces/Bishop.h"
#include "chess/pieces/King.h"
#include "chess/pieces/Knight.h"
#include "chess/pieces/Pawn.h"
#include "chess/pieces/Piece.h"
#include "chess/pieces/PieceFactory.h"
#include "chess/pieces/Queen.h"
#include "chess/pieces/Rook.h"
#include "chess/pieces/SlidingPiece.h"
#include "chess/pieces/StepPiece.h"

/** @name Rules and game flow */
#include "chess/rules/ChessClock.h"
#include "chess/rules/ChessGame.h"
#include "chess/rules/GameRules.h"
#include "chess/rules/Move.h"
#include "chess/rules/MoveGenerator.h"

/** @name Notation and serialization */
#include "chess/notation/Fen.h"
#include "chess/notation/MoveNotation.h"
#include "chess/notation/PgnReader.h"
#include "chess/notation/SanNotation.h"
#include "chess/notation/UciNotation.h"

/** @name Terminal rendering */
#include "chess/render/BoardRenderer.h"
