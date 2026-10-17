import { Button, Checkbox, Group, Select, Stack, Text } from "@mantine/core";
import { Chessboard } from "react-chessboard";
import type { ChessboardOptions } from "react-chessboard";
import { pieceToFen } from "../utils/positionUtils";
import type { BoardPiece } from "../utils/positionUtils";

const pieceLabels: Record<BoardPiece, string> = {
  wK: "♔",
  wQ: "♕",
  wR: "♖",
  wB: "♗",
  wN: "♘",
  wP: "♙",
  bK: "♚",
  bQ: "♛",
  bR: "♜",
  bB: "♝",
  bN: "♞",
  bP: "♟",
};
const castlingLabels: Record<string, string> = {
  K: "White kingside",
  Q: "White queenside",
  k: "Black kingside",
  q: "Black queenside",
};

interface PositionBoardEditorProps {
  mode: "MOVES" | "DIRECT";
  fen: string;
  selectedPiece: BoardPiece | null;
  selectedSquare?: string | null;
  onFenChange: (fen: string) => void;
  onPieceSelect: (piece: BoardPiece | null) => void;
  onSquareClick: (square: string) => void;
  onPieceDrop: ChessboardOptions["onPieceDrop"];
}

export function PositionBoardEditor({
  mode,
  fen,
  selectedPiece,
  selectedSquare = null,
  onFenChange,
  onPieceSelect,
  onSquareClick,
  onPieceDrop,
}: PositionBoardEditorProps) {
  const fields = fen.split(" ");
  const castling = fields[2] ?? "-";

  function updateCastling(right: string, enabled: boolean) {
    const next = new Set(castling === "-" ? [] : castling);
    if (enabled) next.add(right);
    else next.delete(right);
    fields[2] =
      ["K", "Q", "k", "q"].filter((value) => next.has(value)).join("") || "-";
    onFenChange(fields.join(" "));
  }

  const options: ChessboardOptions = {
    position: fen,
    allowDragging: true,
    onPieceDrop,
    onSquareClick: ({ square }) => onSquareClick(square),
    boardOrientation: "white",
    boardStyle: { width: "100%" },
  };

  return (
    <Stack style={{ minWidth: 280, flex: 1 }}>
      <div style={{ width: "min(100%, 440px)", marginInline: "auto" }}>
        <Chessboard options={options} />
      </div>
      {mode === "DIRECT" ? (
        <>
          <Text size="sm">
            Select a piece and then a square to place it. Choose Erase, then a
            square to clear it. You can also drag pieces on the board.
          </Text>
          <Group gap="xs" aria-label="Choose a piece">
            {Object.keys(pieceToFen).map((key) => {
              const value = key as BoardPiece;
              return (
                <Button
                  key={value}
                  size="xs"
                  aria-label={`Place ${value[0] === "w" ? "white" : "black"} ${value[1]}`}
                  variant={selectedPiece === value ? "filled" : "default"}
                  onClick={() => onPieceSelect(value)}
                >
                  {pieceLabels[value]}
                </Button>
              );
            })}
          </Group>
          <Button
            size="xs"
            variant="default"
            onClick={() => onPieceSelect(null)}
            aria-label="Erase pieces from selected squares"
          >
            Erase
          </Button>
          <Select
            label="Side to move"
            data={[
              { value: "w", label: "White" },
              { value: "b", label: "Black" },
            ]}
            value={fields[1]}
            onChange={(value) => {
              fields[1] = value ?? "w";
              onFenChange(fields.join(" "));
            }}
            allowDeselect={false}
          />
          <Group gap="md" aria-label="Castling rights">
            {["K", "Q", "k", "q"].map((right) => (
              <Checkbox
                key={right}
                label={castlingLabels[right]}
                checked={castling.includes(right)}
                onChange={(event) =>
                  updateCastling(right, event.currentTarget.checked)
                }
              />
            ))}
          </Group>
        </>
      ) : (
        <Text size="sm">
          Click a piece and its destination, or drag it. The side to move
          follows the sequence.
          {selectedSquare ? ` Selected ${selectedSquare}.` : ""}
        </Text>
      )}
    </Stack>
  );
}
