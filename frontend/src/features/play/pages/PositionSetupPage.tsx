import { Button, Group, Select, Stack, Text } from "@mantine/core";
import { Chess } from "chess.js";
import { useEffect, useRef, useState } from "react";
import type { ChessboardOptions } from "react-chessboard";
import { useLocation, useNavigate } from "react-router-dom";
import { notifications } from "@mantine/notifications";
import { FenTools } from "../../position/components/FenTools";
import { PositionBoardEditor } from "../../position/components/PositionBoardEditor";
import {
  chessPieceId,
  emptyFen,
  fenAfterSetupMove,
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

interface EditorSnapshot {
  mode: "MOVES" | "DIRECT";
  fen: string;
  start: string;
  setupMoves: string[];
  setupFens: string[];
}

interface EditorHistory {
  past: EditorSnapshot[];
  future: EditorSnapshot[];
}

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
  const [history, setHistory] = useState<EditorHistory>({
    past: [],
    future: [],
  });

  function snapshot(): EditorSnapshot {
    return { mode, fen, start, setupMoves, setupFens };
  }

  function applySnapshot(value: EditorSnapshot) {
    setMode(value.mode);
    setFen(value.fen);
    setFenInput(value.fen);
    setStart(value.start);
    setSetupMoves(value.setupMoves);
    setSetupFens(value.setupFens);
    gameRef.current = value.mode === "MOVES" ? new Chess(value.fen) : null;
    setPiece(null);
  }

  function commitSnapshot(value: EditorSnapshot) {
    setHistory((current) => ({
      past: [...current.past, snapshot()].slice(-100),
      future: [],
    }));
    applySnapshot(value);
  }

  function undo() {
    const previous = history.past.at(-1);
    if (!previous) return;
    const current = snapshot();
    applySnapshot(previous);
    setHistory({
      past: history.past.slice(0, -1),
      future: [current, ...history.future],
    });
  }

  function redo() {
    const next = history.future[0];
    if (!next) return;
    const current = snapshot();
    applySnapshot(next);
    setHistory({
      past: [...history.past, current].slice(-100),
      future: history.future.slice(1),
    });
  }

  useEffect(() => {
    function handleHistoryShortcut(event: KeyboardEvent) {
      if (!(event.metaKey || event.ctrlKey) || event.key.toLowerCase() !== "z")
        return;
      const target = event.target;
      if (
        target instanceof HTMLElement &&
        (target.isContentEditable || target.closest("input, textarea, select"))
      )
        return;
      event.preventDefault();
      if (event.shiftKey) redo();
      else undo();
    }
    window.addEventListener("keydown", handleHistoryShortcut);
    return () => window.removeEventListener("keydown", handleHistoryShortcut);
  });

  function switchMode(nextMode: "MOVES" | "DIRECT") {
    if (nextMode === mode) return;
    const next: EditorSnapshot = { ...snapshot(), mode: nextMode };
    if (nextMode === "MOVES") {
      next.fen = standardFen;
      next.start = "STANDARD";
      next.setupMoves = [];
      next.setupFens = [];
      gameRef.current = new Chess();
    }
    commitSnapshot(next);
  }

  function setBoardStart(value: string | null) {
    if (value === "CUSTOM") return;
    const nextFen = value === "EMPTY" ? emptyFen : standardFen;
    const next: EditorSnapshot = {
      ...snapshot(),
      start: value ?? "STANDARD",
      fen: nextFen,
      setupMoves: [],
      setupFens: [],
    };
    gameRef.current = value === "EMPTY" ? null : new Chess();
    if (value === "EMPTY" && mode === "MOVES") next.mode = "DIRECT";
    commitSnapshot(next);
  }

  function importFen() {
    try {
      const nextFen = normalizeFen(fenInput);
      fenBoard(nextFen);
      commitSnapshot({
        mode: "DIRECT",
        fen: nextFen,
        start: "CUSTOM",
        setupMoves: [],
        setupFens: [],
      });
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
      commitSnapshot({ ...snapshot(), fen: nextFen, start: "CUSTOM" });
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
      commitSnapshot({ ...snapshot(), fen: nextFen, start: "CUSTOM" });
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
      const nextFen = fenAfterSetupMove(
        gameRef.current.fen(),
        move.from,
        move.to,
        move.piece,
      );
      commitSnapshot({
        ...snapshot(),
        fen: nextFen,
        setupMoves: [...setupMoves, move.san],
        setupFens: [...(setupFens.length ? setupFens : [standardFen]), nextFen],
      });
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
        <Group justify="space-between" mb="md">
          <Text size="sm" c="dimmed">
            Choose a setup style, then build the position on the board.
          </Text>
          <Group gap="xs">
            <Button
              variant="default"
              onClick={undo}
              disabled={!history.past.length}
            >
              Undo
            </Button>
            <Button
              variant="default"
              onClick={redo}
              disabled={!history.future.length}
            >
              Redo
            </Button>
          </Group>
        </Group>
        <Group grow mb="md">
          <Button
            variant={mode === "MOVES" ? "filled" : "default"}
            aria-pressed={mode === "MOVES"}
            onClick={() => switchMode("MOVES")}
          >
            Play a move sequence
          </Button>
          <Button
            variant={mode === "DIRECT" ? "filled" : "default"}
            aria-pressed={mode === "DIRECT"}
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
            <Text size="sm" c="dimmed">
              {mode === "MOVES"
                ? "Make legal moves from the starting position to build your challenge."
                : "Place, move, or remove pieces to create a custom board."}
            </Text>
            <PositionBoardEditor
              mode={mode}
              fen={fen}
              selectedPiece={piece}
              onFenChange={(value) => {
                let nextFen = value;
                if (value.split(" ")[1] !== fen.split(" ")[1]) {
                  const fields = value.split(" ");
                  fields[3] = "-";
                  nextFen = fields.join(" ");
                }
                commitSnapshot({ ...snapshot(), fen: nextFen });
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
