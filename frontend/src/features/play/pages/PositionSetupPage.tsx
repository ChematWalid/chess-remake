import { Button, Group, Select, Stack, Text } from "@mantine/core";
import { Chess } from "chess.js";
import { useRef, useState } from "react";
import type { ChessboardOptions } from "react-chessboard";
import { useLocation, useNavigate } from "react-router-dom";
import { notifications } from "@mantine/notifications";
import { FenTools } from "../../position/components/FenTools";
import { PositionBoardEditor } from "../../position/components/PositionBoardEditor";
import {
  chessPieceId,
  emptyFen,
  fenBoard,
  normalizeFen,
  pieceToFen,
  serializeFenBoard,
  standardFen,
} from "../../position/utils/positionUtils";
import type {
  BoardPiece,
  ChallengePosition,
} from "../../position/utils/positionUtils";

export function PositionSetupPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as {
    challengePosition?: ChallengePosition;
  } | null;
  const importedPosition = state?.challengePosition;
  const [mode, setMode] = useState<"MOVES" | "DIRECT">(
    importedPosition && importedPosition.setupMoves.length > 0
      ? "MOVES"
      : "DIRECT",
  );
  const [fen, setFen] = useState(importedPosition?.fen ?? standardFen);
  const [start, setStart] = useState(
    importedPosition && importedPosition.setupMoves.length === 0
      ? "CUSTOM"
      : "STANDARD",
  );
  const [piece, setPiece] = useState<BoardPiece | null>(null);
  const [fenInput, setFenInput] = useState(
    importedPosition?.fen ?? standardFen,
  );
  const [setupMoves, setSetupMoves] = useState(
    importedPosition?.setupMoves ?? [],
  );
  const [setupFens, setSetupFens] = useState(importedPosition?.setupFens ?? []);
  const gameRef = useRef<Chess | null>(null);

  function switchMode(nextMode: "MOVES" | "DIRECT") {
    setMode(nextMode);
    if (nextMode === "MOVES") {
      setFen(standardFen);
      setFenInput(standardFen);
      setSetupMoves([]);
      setSetupFens([]);
      gameRef.current = new Chess();
    }
  }

  function setBoardStart(value: string | null) {
    if (value === "CUSTOM") return;
    const nextFen = value === "EMPTY" ? emptyFen : standardFen;
    setStart(value ?? "STANDARD");
    setFen(nextFen);
    setFenInput(nextFen);
    setSetupMoves([]);
    setSetupFens([]);
    gameRef.current = value === "EMPTY" ? null : new Chess();
    if (value === "EMPTY" && mode === "MOVES") setMode("DIRECT");
  }

  function importFen() {
    try {
      const nextFen = normalizeFen(fenInput);
      fenBoard(nextFen);
      setFen(nextFen);
      setStart("CUSTOM");
      setMode("DIRECT");
      setSetupMoves([]);
      setSetupFens([]);
      gameRef.current = null;
      notifications.show({
        title: "FEN loaded",
        message: "The editor is now in direct setup mode.",
        color: "green",
      });
    } catch (error) {
      notifications.show({
        title: "Invalid FEN",
        message:
          error instanceof Error
            ? error.message
            : "Check the FEN and try again.",
        color: "red",
      });
    }
  }

  function handleSquareClick(square: string) {
    if (mode !== "DIRECT") return;
    try {
      const board = fenBoard(fen);
      if (piece) board[square] = pieceToFen[piece];
      else delete board[square];
      const fields = fen.split(" ");
      fields[2] = "-";
      fields[3] = "-";
      const nextFen = serializeFenBoard(board, fields);
      setFen(nextFen);
      setFenInput(nextFen);
      setStart("CUSTOM");
    } catch {
      notifications.show({
        title: "Could not update the board",
        message: "Import a valid FEN or reset the board first.",
        color: "red",
      });
    }
  }

  function handlePieceDrop({
    piece: pieceId,
    sourceSquare,
    targetSquare,
  }: {
    piece: string;
    sourceSquare: string;
    targetSquare: string;
  }): boolean {
    if (mode === "DIRECT") {
      const selected = chessPieceId(pieceId);
      if (!selected) return false;
      const board = fenBoard(fen);
      delete board[sourceSquare];
      board[targetSquare] = pieceToFen[selected];
      const fields = fen.split(" ");
      fields[2] = "-";
      fields[3] = "-";
      const nextFen = serializeFenBoard(board, fields);
      setFen(nextFen);
      setFenInput(nextFen);
      setStart("CUSTOM");
      return true;
    }
    if (setupMoves.length >= 300) return false;
    try {
      if (!gameRef.current) gameRef.current = new Chess(fen);
      const move = gameRef.current.move({
        from: sourceSquare,
        to: targetSquare,
        promotion: "q",
      });
      if (!move) return false;
      const nextFen = gameRef.current.fen({ forceEnpassantSquare: true });
      setFen(nextFen);
      setSetupMoves((previous) => [...previous, move.san]);
      setSetupFens((previous) => [
        ...(previous.length ? previous : [standardFen]),
        nextFen,
      ]);
      setFenInput(nextFen);
      return true;
    } catch {
      return false;
    }
  }

  function savePosition() {
    try {
      const nextFen = normalizeFen(fen);
      const chess = new Chess(undefined, { skipValidation: true });
      chess.load(nextFen, { skipValidation: true });
      const pieces = Object.values(fenBoard(nextFen));
      if (
        pieces.filter((value) => value === "K").length !== 1 ||
        pieces.filter((value) => value === "k").length !== 1
      ) {
        throw new Error("A playable position needs one king for each side.");
      }
      if (
        pieces.filter((value) => value === "P").length > 8 ||
        pieces.filter((value) => value === "p").length > 8
      ) {
        throw new Error(
          "A playable position can have no more than eight pawns per side.",
        );
      }
      if (!chess.moves().length || chess.isGameOver())
        throw new Error("Choose a position with at least one legal move.");
      if (chess.isInsufficientMaterial())
        throw new Error(
          "Choose a position where both sides can still play for a result.",
        );
      navigate("/play", {
        state: {
          challengePosition: {
            fen: nextFen,
            setupMoves: mode === "MOVES" ? setupMoves : [],
            setupFens: mode === "MOVES" ? setupFens : [],
          } satisfies ChallengePosition,
        },
      });
    } catch (error) {
      notifications.show({
        title: "Position needs attention",
        message:
          error instanceof Error
            ? error.message
            : "Choose a playable position.",
        color: "red",
      });
    }
  }

  const onPieceDrop: ChessboardOptions["onPieceDrop"] = ({
    piece,
    sourceSquare,
    targetSquare,
  }) =>
    targetSquare
      ? handlePieceDrop({ piece: piece.pieceType, sourceSquare, targetSquare })
      : false;

  return (
    <div>
      <header className="page-heading">
        <div>
          <h1>Set up a position</h1>
          <p>Build a playable position for your challenge.</p>
        </div>
      </header>
      <div className="panel" style={{ maxWidth: 980, marginInline: "auto" }}>
        <Group grow mb="md">
          <Button
            variant={mode === "MOVES" ? "filled" : "default"}
            onClick={() => switchMode("MOVES")}
          >
            Play a move sequence
          </Button>
          <Button
            variant={mode === "DIRECT" ? "filled" : "default"}
            onClick={() => setMode("DIRECT")}
          >
            Arrange pieces
          </Button>
        </Group>
        <Group align="flex-start" grow wrap="wrap">
          <Stack style={{ minWidth: 280, flex: 1 }}>
            <Select
              label={mode === "MOVES" ? "Sequence starts from" : "Start with"}
              data={[
                { value: "STANDARD", label: "Standard position" },
                { value: "EMPTY", label: "Empty board" },
                { value: "CUSTOM", label: "Current FEN" },
              ]}
              value={start}
              onChange={setBoardStart}
              allowDeselect={false}
            />
            <PositionBoardEditor
              mode={mode}
              fen={fen}
              selectedPiece={piece}
              onFenChange={(value) => {
                setFen(value);
                setFenInput(value);
                if (value.split(" ")[1] !== fen.split(" ")[1]) {
                  const fields = value.split(" ");
                  fields[3] = "-";
                  const nextFen = fields.join(" ");
                  setFen(nextFen);
                  setFenInput(nextFen);
                }
              }}
              onPieceSelect={setPiece}
              onSquareClick={handleSquareClick}
              onPieceDrop={onPieceDrop}
            />
            {mode === "MOVES" && (
              <Text size="sm" c="dimmed">
                Setup moves: {setupMoves.length ? setupMoves.join(" ") : "none"}
              </Text>
            )}
            <FenTools
              value={fenInput}
              onChange={setFenInput}
              onImport={importFen}
            />
            <Group justify="flex-end" mt="md">
              <Button variant="default" onClick={() => navigate(-1)}>
                Cancel
              </Button>
              <Button onClick={savePosition}>Use this position</Button>
            </Group>
          </Stack>
        </Group>
      </div>
    </div>
  );
}
