export type ChallengePosition = {
  fen: string;
  setupMoves: string[];
  setupFens: string[];
};
export type BoardPiece =
  | "wK"
  | "wQ"
  | "wR"
  | "wB"
  | "wN"
  | "wP"
  | "bK"
  | "bQ"
  | "bR"
  | "bB"
  | "bN"
  | "bP";

export const standardFen =
  "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
export const emptyFen = "8/8/8/8/8/8/8/8 w - - 0 1";
export const pieceToFen: Record<BoardPiece, string> = {
  wK: "K",
  wQ: "Q",
  wR: "R",
  wB: "B",
  wN: "N",
  wP: "P",
  bK: "k",
  bQ: "q",
  bR: "r",
  bB: "b",
  bN: "n",
  bP: "p",
};

export function normalizeFen(fen: string): string {
  const fields = fen.trim().split(/\s+/);
  if (fields.length !== 6) throw new Error("Enter all six FEN fields.");
  return fields.join(" ");
}

export function fenAfterSetupMove(
  fen: string,
  from: string,
  to: string,
  piece: string,
): string {
  const fields = normalizeFen(fen).split(" ");
  fields[3] = "-";
  const fromRank = Number(from[1]);
  const toRank = Number(to[1]);
  if (
    piece.toLowerCase() === "p" &&
    from[0] === to[0] &&
    Math.abs(fromRank - toRank) === 2
  ) {
    fields[3] = `${from[0]}${(fromRank + toRank) / 2}`;
  }
  return fields.join(" ");
}

export function fenBoard(fen: string): Record<string, string> {
  const ranks = (fen.split(" ")[0] ?? "").split("/");
  if (ranks.length !== 8) throw new Error("The FEN board needs eight ranks.");
  const board: Record<string, string> = {};
  ranks.forEach((rankText, rankIndex) => {
    let file = 0;
    for (const char of rankText) {
      if (/^[1-8]$/.test(char)) file += Number(char);
      else {
        if (!/[KQRBNPkqrbnp]/.test(char) || file > 7)
          throw new Error("The FEN board is invalid.");
        const square = `${String.fromCharCode(97 + file)}${8 - rankIndex}`;
        board[square] = char;
        file += 1;
      }
    }
    if (file !== 8)
      throw new Error("Each FEN rank must contain eight squares.");
  });
  return board;
}

export function serializeFenBoard(
  board: Record<string, string>,
  fields: string[],
): string {
  const rows: string[] = [];
  for (let rank = 8; rank >= 1; rank -= 1) {
    let row = "";
    let blanks = 0;
    for (let file = 0; file < 8; file += 1) {
      const square = `${String.fromCharCode(97 + file)}${rank}`;
      const value = board[square];
      if (!value) blanks += 1;
      else {
        if (blanks) row += blanks;
        blanks = 0;
        row += value;
      }
    }
    if (blanks) row += blanks;
    rows.push(row);
  }
  return [rows.join("/"), ...fields.slice(1)].join(" ");
}

export function chessPieceId(value: string): BoardPiece | null {
  if (value in pieceToFen) return value as BoardPiece;
  return null;
}
