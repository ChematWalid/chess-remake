package com.chess.backend.features.position.validation;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class PositionValidator {
    private static final Pattern SETUP_MOVE = Pattern.compile("[A-Za-z0-9+#=xO-]{1,10}");
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
        if (!setupMoves.isEmpty() && !initialFen.equals(setupFens.getLast()))
            return "The setup moves do not end at the selected position";
        return null;
    }

    private static String validateFen(String fen) {
        if (fen == null || fen.length() > 128) return "Position FEN is invalid";
        String[] fields = fen.trim().split("\\s+");
        if (fields.length != 6 || !(fields[1].equals("w") || fields[1].equals("b"))
                || !validCastlingRights(fields[2]) || !EN_PASSANT.matcher(fields[3]).matches()
                || !fields[4].matches("\\d+") || !fields[5].matches("[1-9]\\d*")) return "Position FEN is invalid";
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
        return null;
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
