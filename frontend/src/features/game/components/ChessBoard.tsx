import { Chessboard, defaultPieces } from "react-chessboard";
import type { ChessboardOptions } from "react-chessboard";
import type { BoardPalette } from "../../../shared/api/types";
import "./ChessBoard.css";

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
          pieces: defaultPieces,
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
