import { Chessboard } from "react-chessboard";
import type { ChessboardOptions, PieceRenderObject } from "react-chessboard";
import type { BoardPalette } from "../../../shared/api/types";
import piecesSprite from "../../../../pngfind.com-chess-pieces-png-6922125.png";

const pieceColumns: Record<string, number> = {
  K: 0,
  Q: 1,
  B: 2,
  N: 3,
  R: 4,
  P: 5,
};
const spritePieces: PieceRenderObject = Object.fromEntries(
  ["w", "b"].flatMap((color) =>
    Object.keys(pieceColumns).map((piece) => {
      const column = pieceColumns[piece] ?? 0;
      const row = color === "w" ? 0 : 1;
      const key = `${color}${piece}`;
      return [
        key,
        () => (
          <div
            className={`sprite-piece sprite-${color}`}
            style={{
              backgroundImage: `url(${piecesSprite})`,
              backgroundPosition: `${column * 20}% ${row * 100}%`,
            }}
            aria-label={`${color === "w" ? "White" : "Black"} ${piece}`}
          />
        ),
      ];
    }),
  ),
);

const palettes: Record<BoardPalette, { light: string; dark: string }> = {
  FOREST: { light: "#d6dfcf", dark: "#78916e" },
  WALNUT: { light: "#e6d5ba", dark: "#987052" },
  MARBLE: { light: "#e3e5e2", dark: "#88918c" },
};

export interface ChessBoardProps extends Omit<
  ChessboardOptions,
  "boardStyle" | "lightSquareStyle" | "darkSquareStyle" | "pieces"
> {
  palette: BoardPalette;
  label: string;
}

export function ChessBoard({ palette, label, ...options }: ChessBoardProps) {
  const colors = palettes[palette];
  return (
    <div className="chessboard-frame" role="group" aria-label={label}>
      <Chessboard
        options={{
          ...options,
          pieces: spritePieces,
          boardStyle: {
            borderRadius: 8,
            overflow: "hidden",
            boxShadow: "0 18px 55px #0004",
          },
          lightSquareStyle: { backgroundColor: colors.light },
          darkSquareStyle: { backgroundColor: colors.dark },
          animationDurationInMs: 170,
          dragActivationDistance: 4,
          allowAutoScroll: true,
        }}
      />
    </div>
  );
}
