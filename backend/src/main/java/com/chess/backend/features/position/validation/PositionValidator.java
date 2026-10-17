package com.chess.backend.features.position.validation;

import com.github.bhlangonijr.chesslib.Board;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class PositionValidator {
    private static final Pattern SETUP_MOVE = Pattern.compile(
            "(?:O-O(?:-O)?|0-0(?:-0)?|[KQRBN](?:[a-h]|[1-8]|[a-h][1-8])?x?[a-h][1-8]|(?:[a-h]x[a-h][1-8]|[a-h][1-8])(?:=[QRBN])?)[+#]?");
    private static final Pattern EN_PASSANT = Pattern.compile("(?:-|[a-h][36])");
    private static final Set<Character> PIECES = Set.of('K', 'Q', 'R', 'B', 'N', 'P', 'k', 'q', 'r', 'b', 'n', 'p');

    private PositionValidator() { }

    public static String validate(String initialFen, List<String> setupMoves, List<String> setupFens) {
        if (initialFen == null) {
            return setupMoves.isEmpty() && setupFens.isEmpty() ? null : "Position setup data requires a custom FEN";
        }
        String fenError = validateFen(initialFen);
        if (fenError != null) return fenError;
        if (setupMoves.size() > 300 || setupMoves.stream().anyMatch(move -> move == null || !SETUP_MOVE.matcher(move).matches()))
            return "Position setup contains invalid moves";
        if ((!setupMoves.isEmpty() && setupFens.size() != setupMoves.size() + 1)
                || (setupMoves.isEmpty() && !setupFens.isEmpty())) return "Position replay data is inconsistent";
        for (String fen : setupFens) {
            fenError = validateFen(fen);
            if (fenError != null) return fenError;
        }
        if (!setupMoves.isEmpty() && !replayMatches(setupMoves, setupFens, initialFen))
            return "The setup moves do not match the selected position";
        return null;
    }

    private static boolean replayMatches(List<String> setupMoves, List<String> setupFens, String initialFen) {
        try {
            Board board = new Board();
            board.setEnableEvents(false);
            board.loadFromFen(setupFens.getFirst());
            if (!isPlayable(board)) return false;
            for (int index = 0; index < setupMoves.size(); index++) {
                if (!board.doMove(setupMoves.get(index)) || !isPlayable(board)) return false;
                Board expected = new Board();
                expected.setEnableEvents(false);
                expected.loadFromFen(setupFens.get(index + 1));
                if (!board.getFen().equals(expected.getFen())) return false;
            }
            Board selected = new Board();
            selected.setEnableEvents(false);
            selected.loadFromFen(initialFen);
            return board.getFen().equals(selected.getFen());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isPlayable(Board board) {
        return !board.legalMoves().isEmpty() && !board.isDraw() && !board.isMated();
    }

    private static String validateFen(String fen) {
        if (fen == null || fen.length() > 128) return "Position FEN is invalid";
        String[] fields = fen.trim().split("\\s+");
        if (fields.length != 6 || !(fields[1].equals("w") || fields[1].equals("b"))
                || !validCastlingRights(fields[2]) || !EN_PASSANT.matcher(fields[3]).matches()
                || !fields[4].matches("\\d+") || !fields[5].matches("[1-9]\\d*")) return "Position FEN is invalid";
        try {
            if (Integer.parseInt(fields[4]) >= 100 || Integer.parseInt(fields[5]) < 1) return "Position FEN is not playable";
        } catch (NumberFormatException exception) {
            return "Position FEN is invalid";
        }
        String[] ranks = fields[0].split("/", -1);
        if (ranks.length != 8) return "Position FEN is invalid";
        int whiteKings = 0;
        int blackKings = 0;
        int whitePawns = 0;
        int blackPawns = 0;
        int whiteKingFile = -1;
        int whiteKingRank = -1;
        int blackKingFile = -1;
        int blackKingRank = -1;
        char[][] board = new char[8][8];
        for (int rankIndex = 0; rankIndex < ranks.length; rankIndex++) {
            int files = 0;
            for (char value : ranks[rankIndex].toCharArray()) {
                if (value >= '1' && value <= '8') files += value - '0';
                else if (PIECES.contains(value)) {
                    if (files >= 8) return "Position FEN is invalid";
                    board[rankIndex][files] = value;
                    files++;
                    if (value == 'K') {
                        whiteKings++;
                        whiteKingFile = files - 1;
                        whiteKingRank = 7 - rankIndex;
                    }
                    if (value == 'k') {
                        blackKings++;
                        blackKingFile = files - 1;
                        blackKingRank = 7 - rankIndex;
                    }
                    if (value == 'P') whitePawns++;
                    if (value == 'p') blackPawns++;
                    if ((value == 'P' || value == 'p') && (rankIndex == 0 || rankIndex == 7))
                        return "Pawns cannot be placed on the first or eighth rank";
                } else return "Position FEN is invalid";
                if (files > 8) return "Position FEN is invalid";
            }
            if (files != 8) return "Position FEN is invalid";
        }
        if (whiteKings != 1 || blackKings != 1 || whitePawns > 8 || blackPawns > 8)
            return "A playable position needs one king per side and no more than eight pawns per side";
        if (Math.abs(whiteKingFile - blackKingFile) <= 1 && Math.abs(whiteKingRank - blackKingRank) <= 1)
            return "The kings cannot be next to each other";
        boolean whiteInCheck = isInCheck(board, whiteKingFile, whiteKingRank, false);
        boolean blackInCheck = isInCheck(board, blackKingFile, blackKingRank, true);
        if ((whiteInCheck && blackInCheck) || (fields[1].equals("w") ? blackInCheck : whiteInCheck))
            return "Only the side to move can be in check";
        if (hasRight(fields[2], 'K') && !(board[7][4] == 'K' && board[7][7] == 'R')
                || hasRight(fields[2], 'Q') && !(board[7][4] == 'K' && board[7][0] == 'R')
                || hasRight(fields[2], 'k') && !(board[0][4] == 'k' && board[0][7] == 'r')
                || hasRight(fields[2], 'q') && !(board[0][4] == 'k' && board[0][0] == 'r'))
            return "Castling rights must match the kings and rooks on the board";
        if (!fields[3].equals("-")) {
            int file = fields[3].charAt(0) - 'a';
            if (fields[1].equals("b")) {
                if (fields[3].charAt(1) != '3' || board[5][file] != '\0' || board[4][file] != 'P' || board[6][file] != '\0')
                    return "En passant state does not match the board";
            } else if (fields[3].charAt(1) != '6' || board[2][file] != '\0' || board[3][file] != 'p' || board[1][file] != '\0') {
                return "En passant state does not match the board";
            }
        }
        if (hasInsufficientMaterial(board)) return "The position has insufficient material to continue";
        if (!hasLegalMove(board, fields[1].equals("w"), fields[2], fields[3]))
            return "The position has no legal moves";
        return null;
    }

    private static boolean hasLegalMove(char[][] board, boolean whiteToMove, String castlingRights, String enPassant) {
        for (int row = 0; row < 8; row++) {
            for (int file = 0; file < 8; file++) {
                char piece = board[row][file];
                if (piece == '\0' || whiteToMove != Character.isUpperCase(piece)) continue;
                char type = Character.toUpperCase(piece);
                if (type == 'P') {
                    int direction = whiteToMove ? -1 : 1;
                    int startRow = whiteToMove ? 6 : 1;
                    int nextRow = row + direction;
                    if (inside(file, nextRow) && board[nextRow][file] == '\0') {
                        if (isLegalMove(board, row, file, nextRow, file, whiteToMove, false, false)) return true;
                        int doubleRow = row + direction * 2;
                        if (row == startRow && board[doubleRow][file] == '\0'
                                && isLegalMove(board, row, file, doubleRow, file, whiteToMove, false, false)) return true;
                    }
                    for (int fileOffset : new int[]{-1, 1}) {
                        int targetFile = file + fileOffset;
                        if (!inside(targetFile, nextRow)) continue;
                        char target = board[nextRow][targetFile];
                        if (target != '\0' && whiteToMove != Character.isUpperCase(target)
                                && isLegalMove(board, row, file, nextRow, targetFile, whiteToMove, false, false)) return true;
                        if (enPassant.equals("" + (char) ('a' + targetFile) + (8 - nextRow))
                                && isLegalMove(board, row, file, nextRow, targetFile, whiteToMove, true, false)) return true;
                    }
                    continue;
                }
                if (type == 'N' || type == 'K') {
                    int[][] offsets = type == 'N'
                            ? new int[][]{{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}}
                            : new int[][]{{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
                    for (int[] offset : offsets) {
                        int targetRow = row + offset[0];
                        int targetFile = file + offset[1];
                        if (!inside(targetFile, targetRow)) continue;
                        char target = board[targetRow][targetFile];
                        if ((target == '\0' || whiteToMove != Character.isUpperCase(target))
                                && isLegalMove(board, row, file, targetRow, targetFile, whiteToMove, false, false)) return true;
                    }
                    if (type == 'K' && canCastle(board, whiteToMove, castlingRights)
                            && !isSquareAttacked(board, row, file, !whiteToMove)) return true;
                    continue;
                }
                int[][] directions = switch (type) {
                    case 'B' -> new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
                    case 'R' -> new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                    default -> new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                };
                for (int[] direction : directions) {
                    int targetRow = row + direction[0];
                    int targetFile = file + direction[1];
                    while (inside(targetFile, targetRow)) {
                        char target = board[targetRow][targetFile];
                        if (target == '\0' || whiteToMove != Character.isUpperCase(target)) {
                            if (isLegalMove(board, row, file, targetRow, targetFile, whiteToMove, false, false)) return true;
                        }
                        if (target != '\0') break;
                        targetRow += direction[0];
                        targetFile += direction[1];
                    }
                }
            }
        }
        return false;
    }

    private static boolean isLegalMove(char[][] board, int fromRow, int fromFile, int toRow, int toFile,
                                       boolean white, boolean enPassant, boolean castle) {
        char[][] next = new char[8][8];
        for (int row = 0; row < 8; row++) next[row] = board[row].clone();
        char piece = next[fromRow][fromFile];
        next[fromRow][fromFile] = '\0';
        next[toRow][toFile] = piece;
        if (enPassant) next[toRow + (white ? 1 : -1)][toFile] = '\0';
        if (castle) {
            int rookFrom = toFile == 6 ? 7 : 0;
            int rookTo = toFile == 6 ? 5 : 3;
            next[fromRow][rookTo] = next[fromRow][rookFrom];
            next[fromRow][rookFrom] = '\0';
        }
        int king = white ? 'K' : 'k';
        for (int row = 0; row < 8; row++) {
            for (int file = 0; file < 8; file++) {
                if (next[row][file] == king) return !isSquareAttacked(next, row, file, !white);
            }
        }
        return false;
    }

    private static boolean canCastle(char[][] board, boolean white, String rights) {
        int row = white ? 7 : 0;
        char king = white ? 'K' : 'k';
        char rook = white ? 'R' : 'r';
        for (boolean kingSide : new boolean[]{true, false}) {
            char right = white ? (kingSide ? 'K' : 'Q') : (kingSide ? 'k' : 'q');
            int rookFile = kingSide ? 7 : 0;
            int kingTargetFile = kingSide ? 6 : 2;
            if (rights.indexOf(right) < 0 || board[row][4] != king || board[row][rookFile] != rook) continue;
            int firstFile = kingSide ? 5 : 3;
            boolean clear = true;
            for (int file = firstFile; kingSide ? file <= 6 : file >= 1; file += kingSide ? 1 : -1) {
                if (board[row][file] != '\0') clear = false;
            }
            if (!clear || isSquareAttacked(board, row, 4, !white)) continue;
            int transitFile = kingSide ? 5 : 3;
            char[][] transit = new char[8][8];
            for (int index = 0; index < 8; index++) transit[index] = board[index].clone();
            transit[row][4] = '\0';
            transit[row][transitFile] = king;
            if (isSquareAttacked(transit, row, transitFile, !white)) continue;
            if (isLegalMove(board, row, 4, row, kingTargetFile, white, false, true)) return true;
        }
        return false;
    }

    private static boolean isSquareAttacked(char[][] board, int row, int file, boolean byWhite) {
        char pawn = byWhite ? 'P' : 'p';
        int pawnSourceRow = row + (byWhite ? 1 : -1);
        for (int fileOffset : new int[]{-1, 1}) {
            int sourceFile = file + fileOffset;
            if (inside(sourceFile, pawnSourceRow) && board[pawnSourceRow][sourceFile] == pawn) return true;
        }
        char knight = byWhite ? 'N' : 'n';
        for (int[] offset : new int[][]{{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}}) {
            int sourceRow = row + offset[0];
            int sourceFile = file + offset[1];
            if (inside(sourceFile, sourceRow) && board[sourceRow][sourceFile] == knight) return true;
        }
        char king = byWhite ? 'K' : 'k';
        for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for (int fileOffset : new int[]{-1, 0, 1}) {
                if (rowOffset == 0 && fileOffset == 0) continue;
                int sourceRow = row + rowOffset;
                int sourceFile = file + fileOffset;
                if (inside(sourceFile, sourceRow) && board[sourceRow][sourceFile] == king) return true;
            }
        }
        for (int[] direction : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}}) {
            int sourceRow = row + direction[0];
            int sourceFile = file + direction[1];
            while (inside(sourceFile, sourceRow)) {
                char piece = board[sourceRow][sourceFile];
                if (piece != '\0') {
                    boolean attackingPiece = byWhite == Character.isUpperCase(piece);
                    char type = Character.toUpperCase(piece);
                    if (attackingPiece && (type == 'Q'
                            || (direction[0] == 0 || direction[1] == 0) && type == 'R'
                            || direction[0] != 0 && direction[1] != 0 && type == 'B')) return true;
                    break;
                }
                sourceRow += direction[0];
                sourceFile += direction[1];
            }
        }
        return false;
    }

    private static boolean isInCheck(char[][] board, int file, int rank, boolean attackedByWhite) {
        return isSquareAttacked(board, 7 - rank, file, attackedByWhite);
    }

    private static boolean hasInsufficientMaterial(char[][] board) {
        int whitePawns = 0, blackPawns = 0, whiteRooks = 0, blackRooks = 0;
        int whiteQueens = 0, blackQueens = 0, whiteKnights = 0, blackKnights = 0;
        int whiteBishops = 0, blackBishops = 0, whiteBishopColor = -1, blackBishopColor = -1;
        for (int row = 0; row < 8; row++) {
            for (int file = 0; file < 8; file++) {
                char piece = board[row][file];
                switch (piece) {
                    case 'P' -> whitePawns++;
                    case 'p' -> blackPawns++;
                    case 'R' -> whiteRooks++;
                    case 'r' -> blackRooks++;
                    case 'Q' -> whiteQueens++;
                    case 'q' -> blackQueens++;
                    case 'N' -> whiteKnights++;
                    case 'n' -> blackKnights++;
                    case 'B' -> { whiteBishops++; whiteBishopColor = (file + 7 - row) % 2; }
                    case 'b' -> { blackBishops++; blackBishopColor = (file + 7 - row) % 2; }
                    default -> { }
                }
            }
        }
        if (whitePawns + blackPawns + whiteRooks + blackRooks + whiteQueens + blackQueens > 0) return false;
        int whiteMinors = whiteKnights + whiteBishops;
        int blackMinors = blackKnights + blackBishops;
        return whiteMinors == 0 && blackMinors == 0
                || whiteMinors == 1 && blackMinors == 0
                || whiteMinors == 0 && blackMinors == 1
                || whiteBishops == 1 && blackBishops == 1 && whiteKnights == 0 && blackKnights == 0
                && whiteBishopColor == blackBishopColor;
    }

    private static boolean inside(int file, int row) {
        return file >= 0 && file < 8 && row >= 0 && row < 8;
    }

    private static boolean validCastlingRights(String rights) {
        if (rights.equals("-")) return true;
        if (rights.isEmpty()) return false;
        Set<Character> seen = new HashSet<>();
        for (char right : rights.toCharArray()) {
            if ("KQkq".indexOf(right) < 0 || !seen.add(right)) return false;
        }
        return true;
    }

    private static boolean hasRight(String rights, char right) {
        return rights.indexOf(right) >= 0;
    }
}
