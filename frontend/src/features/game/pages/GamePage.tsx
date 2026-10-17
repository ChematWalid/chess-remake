import { Button, Group, Text } from "@mantine/core";
import { IconDownload } from "@tabler/icons-react";
import { Chess, type Square } from "chess.js";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { notifications } from "@mantine/notifications";
import type { ChessboardOptions } from "react-chessboard";
import type { ChallengeDetail, GameDetail } from "../../../shared/api/types";
import { useAuth } from "../../../shared/auth/AuthProvider";
import { defaultSettings } from "../../../shared/api/types";
import { ErrorState, LoadingState } from "../../../shared/components/PageState";
import { getChallenge } from "../../challenge/api/challengeApi";
import { downloadPgn as fetchPgn, getGame } from "../api/gameApi";
import { getMyProfile, getProfile, getSettings } from "../../user/api/userApi";
import { ChessBoard } from "../components/ChessBoard";
import { GamePlayerIdentity } from "../components/GamePlayerIdentity";
import { GameMovesPanel } from "../components/GameMovesPanel";
import {
  GameResultDialog,
  type GameSide,
} from "../components/GameResultDialog";
import { MoveDialogs, type PendingMove } from "../components/MoveDialogs";
import { playUiTone } from "../../../shared/utils/playUiTone";
import { resolveEngineUrl } from "../utils/resolveEngineUrl";
import "./GamePage.css";

interface EngineEvent {
  event?: string;
  fen?: string;
  uci?: string;
  san?: string;
  moves?: string[];
  setup_moves?: string[];
  setup_fens?: string[];
  side_to_move?: "white" | "black";
  white_time_ms?: number;
  black_time_ms?: number;
  result?: string;
  winner?: GameSide | null;
  reason?: string;
  message?: string;
  color?: "white" | "black";
}

interface OptimisticMove {
  uci: string;
  fen: string;
}

function fenForTurn(fen: string, color: "white" | "black"): string {
  const fields = fen.split(" ");
  fields[1] = color === "white" ? "w" : "b";
  fields[3] = "-";
  return fields.join(" ");
}

function projectPremove(
  game: Chess,
  move: PendingMove,
  color: "white" | "black",
): boolean {
  const piece = game.get(move.from);
  const captured = game.get(move.to);
  if (!piece || piece.color !== color[0] || captured?.type === "k")
    return false;

  const sourceFile = move.from.charCodeAt(0);
  const destinationFile = move.to.charCodeAt(0);
  const sourceRank = Number(move.from[1]);
  const destinationRank = Number(move.to[1]);
  const isPawn = piece.type === "p";
  const isPromotion =
    isPawn && (destinationRank === 1 || destinationRank === 8);
  const isCastling =
    piece.type === "k" && Math.abs(sourceFile - destinationFile) === 2;
  const isEnPassant = isPawn && sourceFile !== destinationFile && !captured;

  if (isEnPassant) {
    const capturedSquare =
      `${move.to[0]}${destinationRank + (color === "white" ? -1 : 1)}` as Square;
    game.remove(capturedSquare);
  }
  game.remove(move.from);
  game.remove(move.to);

  if (
    !game.put(
      {
        color: piece.color,
        type: move.promotion ?? (isPromotion ? "q" : piece.type),
      },
      move.to,
    )
  ) {
    return false;
  }

  if (isCastling) {
    const rookFrom =
      `${destinationFile > sourceFile ? "h" : "a"}${sourceRank}` as Square;
    const rookTo =
      `${destinationFile > sourceFile ? "f" : "d"}${sourceRank}` as Square;
    const rook = game.remove(rookFrom);
    if (rook) game.put(rook, rookTo);
  }

  return true;
}

function isLegalPremove(
  fen: string,
  move: PendingMove,
  color: "white" | "black",
): boolean {
  const game = new Chess(undefined, { skipValidation: true });
  game.load(fenForTurn(fen, color), { skipValidation: true });
  return game
    .moves({ square: move.from, verbose: true })
    .some((candidate) => candidate.to === move.to);
}

export function GamePage({ live = false }: { live?: boolean }) {
  const { id = "0", challengeId = "0" } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [fen, setFen] = useState("start");
  const [moveText, setMoveText] = useState<string[]>([]);
  const [selected, setSelected] = useState<Square | null>(null);
  const [premoves, setPremoves] = useState<PendingMove[]>([]);
  const [optimisticMove, setOptimisticMove] = useState<OptimisticMove | null>(
    null,
  );
  const [pending, setPending] = useState<PendingMove | null>(null);
  const [promotion, setPromotion] = useState<PendingMove | null>(null);
  const [color, setColor] = useState<"white" | "black">("white");
  const [connected, setConnected] = useState(false);
  const [finished, setFinished] = useState<string | null>(null);
  const [gameResult, setGameResult] = useState<{
    winnerSide: GameSide | null;
    reason: string;
  } | null>(null);
  const [drawOfferedBy, setDrawOfferedBy] = useState<"white" | "black" | null>(
    null,
  );
  const [drawOfferSent, setDrawOfferSent] = useState(false);
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [clock, setClock] = useState<{ white: number; black: number } | null>(
    null,
  );
  const socket = useRef<WebSocket | null>(null);
  const optimisticMoveRef = useRef<OptimisticMove | null>(null);
  const optimisticMoveTimeout = useRef<number | undefined>(undefined);
  const livePreferences = useRef({ color, premoves });
  livePreferences.current = { color, premoves };
  const detail = useQuery<ChallengeDetail | GameDetail>({
    queryKey: live ? ["challenge-detail", challengeId] : ["game", id],
    queryFn: async () =>
      live ? getChallenge(Number(challengeId)) : getGame(Number(id)),
    enabled: Number(live ? challengeId : id) > 0,
  });
  const preferences = useQuery({
    queryKey: ["settings"],
    queryFn: getSettings,
    initialData: defaultSettings,
  });
  const settingsRef = useRef(preferences.data);
  settingsRef.current = preferences.data;
  const hasClock = clock !== null;

  function clearOptimisticMove() {
    if (optimisticMoveTimeout.current !== undefined) {
      window.clearTimeout(optimisticMoveTimeout.current);
      optimisticMoveTimeout.current = undefined;
    }
    optimisticMoveRef.current = null;
    setOptimisticMove(null);
  }

  useEffect(
    () => () => {
      if (optimisticMoveTimeout.current !== undefined)
        window.clearTimeout(optimisticMoveTimeout.current);
    },
    [],
  );

  useEffect(() => {
    if (!live || !detail.data || !("playerColor" in detail.data)) return;
    const assignedColor = detail.data.playerColor;
    if (assignedColor === "WHITE" || assignedColor === "BLACK")
      setColor(assignedColor.toLowerCase() as "white" | "black");
  }, [live, detail.data]);

  useEffect(() => {
    if (
      !live ||
      !detail.data ||
      !("connectUrl" in detail.data) ||
      !detail.data.connectUrl
    )
      return;
    const initialConnection = detail.data;
    let stopped = false;
    let retryTimer: number | undefined;
    let hasWarned = false;
    const connect = async (refreshCredentials: boolean) => {
      if (stopped) return;
      let connection = initialConnection;
      if (refreshCredentials) {
        try {
          connection = await getChallenge(Number(challengeId));
        } catch {
          if (!stopped)
            retryTimer = window.setTimeout(() => void connect(true), 3000);
          return;
        }
      }
      if (stopped) return;
      if (
        connection.status !== "ACCEPTED" ||
        !connection.connectUrl ||
        !connection.token
      ) {
        setFinished("This accepted game is no longer available.");
        return;
      }
      let engineUrl: string;
      try {
        engineUrl = resolveEngineUrl(connection.connectUrl);
      } catch (error) {
        setConnected(false);
        setFinished(
          error instanceof Error
            ? error.message
            : "The game server has no public WebSocket address.",
        );
        return;
      }
      const ws = new WebSocket(engineUrl);
      socket.current = ws;
      ws.onopen = () => {
        ws.send(
          JSON.stringify({
            action: "connect_token",
            room_code: connection.roomCode,
            token: connection.token,
          }),
        );
        hasWarned = false;
      };
      ws.onclose = () => {
        setConnected(false);
        clearOptimisticMove();
        if (!stopped)
          retryTimer = window.setTimeout(() => void connect(true), 1500);
      };
      ws.onerror = () => {
        if (!hasWarned)
          notifications.show({
            title: "Game connection interrupted",
            message: "Reconnecting to your reserved seat…",
            color: "yellow",
          });
        hasWarned = true;
      };
      ws.onmessage = (message) => {
        try {
          const event = JSON.parse(String(message.data)) as EngineEvent;
          if (event.event === "room_created" || event.event === "room_joined") {
            setConnected(true);
          }
          if (event.event === "game_started" && event.fen) {
            clearOptimisticMove();
            setConnected(true);
            setFen(event.fen);
            setMoveText([...(event.setup_moves ?? []), ...(event.moves ?? [])]);
            setClock({
              white: event.white_time_ms ?? 0,
              black: event.black_time_ms ?? 0,
            });
            if ("playerColor" in detail.data) {
              const assignedColor = detail.data.playerColor;
              if (assignedColor === "WHITE" || assignedColor === "BLACK")
                setColor(assignedColor.toLowerCase() as "white" | "black");
            }
          } else if (event.event === "move_made" && event.fen) {
            const unconfirmedMove = optimisticMoveRef.current;
            if (
              unconfirmedMove &&
              event.uci &&
              unconfirmedMove.uci !== event.uci
            ) {
              notifications.show({
                title: "Move not accepted",
                message:
                  "The server position changed. The board was synchronized.",
                color: "yellow",
              });
            }
            clearOptimisticMove();
            setFen(event.fen);
            setDrawOfferedBy(null);
            setDrawOfferSent(false);
            setClock({
              white: event.white_time_ms ?? 0,
              black: event.black_time_ms ?? 0,
            });
            if (event.san) {
              setMoveText((moves) => [...moves, event.san as string]);
              if (settingsRef.current.gameSoundsEnabled)
                playUiTone(event.san.includes("x") ? 520 : 660);
            }
            setSelected(null);
            const queuedPremoves = livePreferences.current.premoves;
            const queued = queuedPremoves[0];
            if (
              queued &&
              event.side_to_move === livePreferences.current.color
            ) {
              updatePremoves(queuedPremoves.slice(1));
              if (!sendEngineMove(queued, event.fen)) {
                updatePremoves([]);
                notifications.show({
                  title: "Premove cancelled",
                  message:
                    "That move is no longer legal. The remaining premoves were cancelled.",
                  color: "yellow",
                });
              }
            }
          } else if (event.event === "game_over") {
            clearOptimisticMove();
            setDrawOfferedBy(null);
            setDrawOfferSent(false);
            setFinished(
              event.reason
                ? `${event.result ?? "Game over"} · ${event.reason}`
                : (event.result ?? "Game over"),
            );
            setGameResult({
              winnerSide: event.winner ?? null,
              reason: event.reason ?? "the game rules",
            });
            updatePremoves([]);
          } else if (event.event === "draw_offered") {
            setDrawOfferedBy(event.color ?? null);
            setDrawOfferSent(false);
          } else if (event.event === "draw_declined") {
            setDrawOfferedBy(null);
            setDrawOfferSent(false);
            notifications.show({
              title: "Draw declined",
              message: "Your opponent declined the draw offer.",
              color: "yellow",
            });
          } else if (event.event === "error") {
            if (optimisticMoveRef.current) clearOptimisticMove();
            notifications.show({
              title: "Move rejected",
              message:
                event.message ?? "The server could not accept that action.",
              color: "red",
            });
          } else if (event.event === "opponent_disconnected") {
            notifications.show({
              title: "Opponent disconnected",
              message: "Their seat is reserved while they reconnect.",
              color: "yellow",
            });
          }
        } catch {
          notifications.show({
            title: "Game update error",
            message: "A server update could not be read.",
            color: "red",
          });
        }
      };
    };
    void connect(false);
    return () => {
      stopped = true;
      if (retryTimer) window.clearTimeout(retryTimer);
      socket.current?.close();
      socket.current = null;
    };
  }, [live, detail.data]);

  useEffect(() => {
    if (!live || !connected || !hasClock || finished) return;
    const sideToMove = (() => {
      try {
        const clockFen = optimisticMove?.fen ?? fen;
        return new Chess(clockFen === "start" ? undefined : clockFen).turn();
      } catch {
        return "w";
      }
    })();
    const timer = window.setInterval(
      () =>
        setClock((current) =>
          current
            ? {
                ...current,
                [sideToMove === "w" ? "white" : "black"]: Math.max(
                  0,
                  current[sideToMove === "w" ? "white" : "black"] - 250,
                ),
              }
            : current,
        ),
      250,
    );
    return () => window.clearInterval(timer);
  }, [live, connected, hasClock, finished, fen, optimisticMove]);

  useEffect(() => {
    if (!playing || !detail.data || !("fens" in detail.data)) return;
    const totalPositions = detail.data.setupMoves.length
      ? detail.data.setupFens.length + detail.data.fens.length - 1
      : detail.data.fens.length;
    const timer = window.setInterval(
      () =>
        setIndex((current) =>
          current >= totalPositions - 1 ? 0 : current + 1,
        ),
      900,
    );
    return () => window.clearInterval(timer);
  }, [playing, detail.data]);

  useEffect(() => {
    if (live || !detail.data || !("moves" in detail.data)) return;
    const moveCount = detail.data.moves.length + detail.data.setupMoves.length;

    const handleReviewKeyDown = (event: KeyboardEvent) => {
      const target = event.target;
      if (
        target instanceof HTMLElement &&
        (target.isContentEditable ||
          target.closest("input, textarea, select, [role='dialog']"))
      )
        return;

      let nextIndex: number | null = null;
      if (event.key === "ArrowLeft") nextIndex = Math.max(0, index - 1);
      else if (event.key === "ArrowRight")
        nextIndex = Math.min(moveCount, index + 1);
      else if (event.key === "Home") nextIndex = 0;
      else if (event.key === "End") nextIndex = moveCount;

      if (nextIndex !== null) {
        event.preventDefault();
        setPlaying(false);
        setIndex(nextIndex);
      }
    };

    window.addEventListener("keydown", handleReviewKeyDown);
    return () => window.removeEventListener("keydown", handleReviewKeyDown);
  }, [live, detail.data, index]);

  const replayRecord =
    !live && detail.data && "summary" in detail.data
      ? (detail.data as GameDetail)
      : null;
  const replay = replayRecord
    ? {
        ...replayRecord,
        moves: [...replayRecord.setupMoves, ...replayRecord.moves],
        fens: replayRecord.setupMoves.length
          ? [...replayRecord.setupFens, ...replayRecord.fens.slice(1)]
          : replayRecord.fens,
      }
    : null;
  const opponentUsername =
    live && detail.data && "opponentUsername" in detail.data
      ? detail.data.opponentUsername
      : null;
  const whiteUsername = replay
    ? replay.summary.whiteUsername
    : color === "white"
      ? user?.username
      : opponentUsername;
  const blackUsername = replay
    ? replay.summary.blackUsername
    : color === "black"
      ? user?.username
      : opponentUsername;
  const whiteProfile = useQuery({
    queryKey: ["game-player-profile", whiteUsername],
    queryFn: () =>
      live && whiteUsername === user?.username
        ? getMyProfile()
        : getProfile(whiteUsername!),
    enabled: Boolean(whiteUsername),
    staleTime: 5 * 60 * 1000,
  });
  const blackProfile = useQuery({
    queryKey: ["game-player-profile", blackUsername],
    queryFn: () =>
      live && blackUsername === user?.username
        ? getMyProfile()
        : getProfile(blackUsername!),
    enabled: Boolean(blackUsername),
    staleTime: 5 * 60 * 1000,
  });
  const whitePlayer = {
    username: whiteUsername ?? "white",
    displayName: whiteProfile.data?.displayName ?? whiteUsername ?? "White",
    avatarUrl: whiteProfile.data?.avatarUrl ?? null,
  };
  const blackPlayer = {
    username: blackUsername ?? "black",
    displayName: blackProfile.data?.displayName ?? blackUsername ?? "Black",
    avatarUrl: blackProfile.data?.avatarUrl ?? null,
  };
  const topPlayer = live
    ? color === "white"
      ? { ...blackPlayer, side: "Black" as const }
      : { ...whitePlayer, side: "White" as const }
    : { ...blackPlayer, side: "Black" as const };
  const bottomPlayer = live
    ? color === "white"
      ? { ...whitePlayer, side: "White" as const }
      : { ...blackPlayer, side: "Black" as const }
    : { ...whitePlayer, side: "White" as const };
  const currentFen = replay
    ? (replay.fens[index] ?? replay.fens[0] ?? new Chess().fen())
    : fen === "start"
      ? new Chess().fen()
      : fen;
  const displayedFen = useMemo(() => {
    const position = live ? (optimisticMove?.fen ?? currentFen) : currentFen;
    if (!live || !premoves.length) return position;
    const projection = new Chess(position);
    for (const move of premoves) {
      if (
        !isLegalPremove(projection.fen(), move, color) ||
        !projectPremove(projection, move, color)
      ) {
        break;
      }
    }
    return projection.fen();
  }, [live, optimisticMove, premoves, currentFen, color]);
  const turn = useMemo(() => {
    try {
      return new Chess(currentFen).turn();
    } catch {
      return "w";
    }
  }, [currentFen]);
  const checkedKingSquare = useMemo(() => {
    const game = new Chess(displayedFen);
    if (!game.isCheck()) return null;
    return (
      game
        .board()
        .flatMap((rank, rankIndex) =>
          rank.map((piece, fileIndex) =>
            piece?.type === "k" && piece.color === game.turn()
              ? (`${"abcdefgh"[fileIndex]}${8 - rankIndex}` as Square)
              : null,
          ),
        )
        .find((square) => square !== null) ?? null
    );
  }, [displayedFen]);
  const prefs = preferences.data;
  const colors =
    prefs.boardOrientation === "AUTO"
      ? color
      : (prefs.boardOrientation.toLowerCase() as "white" | "black");
  const legalMoves =
    selected && prefs.showLegalMoveHints
      ? (() => {
          const game = new Chess(undefined, { skipValidation: true });
          if (live && turn !== (color === "white" ? "w" : "b")) {
            game.load(fenForTurn(displayedFen, color), {
              skipValidation: true,
            });
          } else {
            game.load(displayedFen, { skipValidation: true });
          }
          return game;
        })().moves({ square: selected, verbose: true })
      : [];

  function sendEngineMove(move: PendingMove, position = currentFen): boolean {
    const ws = socket.current;
    if (
      !ws ||
      ws.readyState !== WebSocket.OPEN ||
      optimisticMoveRef.current !== null
    )
      return false;
    try {
      const game = new Chess(position);
      const playerColor = livePreferences.current.color;
      if (game.turn() !== (playerColor === "white" ? "w" : "b")) return false;
      const applied = game.move({
        from: move.from,
        to: move.to,
        promotion: move.promotion ?? "q",
      });
      if (!applied) return false;
      const uci = `${move.from}${move.to}${move.promotion ?? applied.promotion ?? ""}`;
      const optimistic = { uci, fen: game.fen() };
      optimisticMoveRef.current = optimistic;
      setOptimisticMove(optimistic);
      optimisticMoveTimeout.current = window.setTimeout(() => {
        if (optimisticMoveRef.current !== optimistic) return;
        optimisticMoveRef.current = null;
        optimisticMoveTimeout.current = undefined;
        setOptimisticMove(null);
        notifications.show({
          title: "Move confirmation delayed",
          message: "The board was restored while waiting for the game server.",
          color: "yellow",
        });
      }, 5000);
      ws.send(
        JSON.stringify({
          action: "move",
          uci,
        }),
      );
      return true;
    } catch {
      clearOptimisticMove();
      return false;
    }
  }

  function updatePremoves(nextPremoves: PendingMove[]) {
    livePreferences.current = {
      ...livePreferences.current,
      premoves: nextPremoves,
    };
    setPremoves(nextPremoves);
  }

  function removePremove(index: number) {
    updatePremoves(
      livePreferences.current.premoves.filter((_, i) => i !== index),
    );
  }

  function clearPremoves() {
    updatePremoves([]);
  }

  function attemptMove(from: Square, to: Square) {
    if (optimisticMoveRef.current) return;
    const game = new Chess(currentFen);
    if (turn !== (color === "white" ? "w" : "b")) {
      if (prefs.premovesEnabled) {
        const move = { from, to };
        if (!isLegalPremove(displayedFen, move, color)) {
          notifications.show({
            title: "Premove not queued",
            message: "That move is not legal from the planned position.",
            color: "yellow",
          });
          setSelected(null);
          return;
        }
        const plannedGame = new Chess(undefined, { skipValidation: true });
        plannedGame.load(fenForTurn(displayedFen, color), {
          skipValidation: true,
        });
        const promotes = plannedGame
          .moves({ square: from, verbose: true })
          .some((candidate) => candidate.to === to && candidate.promotion);
        const nextPremoves = [
          ...livePreferences.current.premoves,
          { from, to, ...(promotes ? { promotion: "q" as const } : {}) },
        ];
        updatePremoves(nextPremoves);
        setSelected(null);
        notifications.show({
          title: "Premove queued",
          message: `Premove ${nextPremoves.length} will be sent in order when it becomes your turn.`,
          autoClose: 1800,
        });
      }
      return;
    }
    const available = game
      .moves({ square: from, verbose: true })
      .filter((move) => move.to === to);
    if (!available.length) {
      setSelected(null);
      return;
    }
    if (available.some((move) => move.promotion)) {
      setPromotion({ from, to });
      setSelected(null);
      return;
    }
    const move = { from, to };
    if (prefs.moveConfirmation) setPending(move);
    else if (!sendEngineMove(move))
      notifications.show({
        title: "Move unavailable",
        message: "The game connection is not ready.",
        color: "yellow",
      });
  }

  function onSquareClick(square: string) {
    if (optimisticMoveRef.current) return;
    const target = square as Square;
    if (selected) {
      const piece = new Chess(displayedFen).get(target);
      if (piece && piece.color === color[0]) {
        setSelected(target === selected ? null : target);
        return;
      }
      attemptMove(selected, target);
      return;
    }
    const piece = new Chess(displayedFen).get(target);
    if (piece && piece.color === color[0]) setSelected(target);
  }

  const boardOptions: ChessboardOptions = {
    position: displayedFen,
    boardOrientation: colors,
    showNotation: prefs.showCoordinates,
    squareStyles: {
      ...(selected ? { [selected]: { background: "#d0bd64aa" } } : {}),
      ...Object.fromEntries(
        legalMoves.map((move) => [
          move.to,
          {
            background: move.isCapture()
              ? "radial-gradient(circle, #d64141 25%, #d6414177 29%, transparent 32%)"
              : "radial-gradient(circle, #647b45 22%, transparent 24%)",
          },
        ]),
      ),
      ...(checkedKingSquare
        ? {
            [checkedKingSquare]: {
              background:
                "radial-gradient(circle, #e5484daa 38%, #e5484d66 100%)",
            },
          }
        : {}),
    },
    canDragPiece: ({ piece }) =>
      live &&
      !finished &&
      !optimisticMove &&
      piece.pieceType.startsWith(color === "white" ? "w" : "b"),
    onSquareClick: ({ square }) => onSquareClick(square),
    onPieceDrop: ({ sourceSquare, targetSquare }) => {
      if (targetSquare)
        attemptMove(sourceSquare as Square, targetSquare as Square);
      return false;
    },
  };

  async function downloadPgn() {
    if (!replay) return;
    try {
      const pgn = await fetchPgn(replay.summary.id);
      const href = URL.createObjectURL(
        new Blob([pgn], { type: "application/x-chess-pgn" }),
      );
      const anchor = document.createElement("a");
      anchor.href = href;
      anchor.download = `rook-game-${replay.summary.id}.pgn`;
      anchor.click();
      URL.revokeObjectURL(href);
    } catch (error) {
      notifications.show({
        title: "PGN download failed",
        message: error instanceof Error ? error.message : "Please retry.",
        color: "red",
      });
    }
  }

  if (detail.isLoading)
    return <LoadingState label={live ? "Joining game" : "Loading replay"} />;
  if (detail.isError || !detail.data)
    return (
      <ErrorState
        message={detail.error?.message ?? "Game was not found"}
        onRetry={() => void detail.refetch()}
      />
    );
  if (live && "status" in detail.data && detail.data.status !== "ACCEPTED")
    return (
      <ErrorState
        message="This challenge is no longer an active game. Return to Play to start another match."
        onRetry={() => void detail.refetch()}
      />
    );

  const summary = replay?.summary;
  return (
    <div className="game-page">
      <header className="page-heading">
        <div>
          <h1>{live ? "Live game" : "Game review"}</h1>
          <p>
            {live
              ? connected
                ? "Connected to the game server"
                : "Connecting to the game server…"
              : `${summary?.whiteUsername ?? "White"} vs ${summary?.blackUsername ?? "Black"}`}
          </p>
        </div>
        {replay && (
          <Button
            leftSection={<IconDownload size={16} />}
            variant="default"
            onClick={() => void downloadPgn()}
          >
            Download PGN
          </Button>
        )}
      </header>
      <div className="game-layout">
        <section className="board-column">
          <div className="opponent-strip">
            <GamePlayerIdentity {...topPlayer} />
            {clock && (
              <Text className="clock">
                {formatClock(clock[color === "white" ? "black" : "white"])}
              </Text>
            )}
          </div>
          <ChessBoard
            {...boardOptions}
            palette={prefs.boardPalette}
            label="Chess board. Use drag and drop or click a piece and its destination."
          />
          {live && (
            <div className="opponent-strip">
              <GamePlayerIdentity {...bottomPlayer} />
              {clock && (
                <Text className="clock">{formatClock(clock[color])}</Text>
              )}
            </div>
          )}
          {!live && (
            <div className="opponent-strip">
              <GamePlayerIdentity {...bottomPlayer} />
            </div>
          )}
          {live && (
            <Group mt="md" justify="center">
              {drawOfferedBy && drawOfferedBy !== color ? (
                <>
                  <Button
                    color="green"
                    onClick={() => {
                      socket.current?.send(
                        JSON.stringify({ action: "accept_draw" }),
                      );
                      setDrawOfferedBy(null);
                    }}
                    disabled={!connected || Boolean(finished)}
                  >
                    Accept draw
                  </Button>
                  <Button
                    variant="default"
                    onClick={() => {
                      socket.current?.send(
                        JSON.stringify({ action: "decline_draw" }),
                      );
                      setDrawOfferedBy(null);
                    }}
                    disabled={!connected || Boolean(finished)}
                  >
                    Decline draw
                  </Button>
                </>
              ) : (
                <Button
                  variant="default"
                  onClick={() => {
                    socket.current?.send(
                      JSON.stringify({ action: "offer_draw" }),
                    );
                    setDrawOfferSent(true);
                  }}
                  disabled={!connected || Boolean(finished) || drawOfferSent}
                >
                  {drawOfferSent ? "Draw offered" : "Offer draw"}
                </Button>
              )}
              <Button
                color="red"
                variant="light"
                onClick={() =>
                  socket.current?.send(JSON.stringify({ action: "resign" }))
                }
                disabled={!connected || Boolean(finished)}
              >
                Resign
              </Button>
            </Group>
          )}
          {finished && (
            <div className="game-result" role="status">
              {finished}
            </div>
          )}
        </section>
        <GameMovesPanel
          live={live}
          connected={connected}
          moveText={moveText}
          premoves={premoves}
          onPremoveRemove={removePremove}
          onPremovesClear={clearPremoves}
          replay={replay}
          onChallengeFromPosition={() =>
            navigate("/play", {
              state: {
                challengePosition: {
                  fen: currentFen,
                  setupMoves: [],
                  setupFens: [],
                },
              },
            })
          }
          index={index}
          playing={playing}
          onIndexChange={setIndex}
          onPlayingChange={setPlaying}
        />
      </div>
      {live && gameResult && (
        <GameResultDialog
          opened
          winnerSide={gameResult.winnerSide}
          viewerSide={color}
          reason={gameResult.reason}
          whitePlayer={whitePlayer}
          blackPlayer={blackPlayer}
          onClose={() => setGameResult(null)}
        />
      )}
      <MoveDialogs
        promotion={promotion}
        pending={pending}
        onPromotionClose={() => setPromotion(null)}
        onPromotionSelect={(piece) => {
          if (!promotion) return;
          const move = { ...promotion, promotion: piece };
          setPromotion(null);
          if (prefs.moveConfirmation) setPending(move);
          else if (!sendEngineMove(move))
            notifications.show({
              title: "Move unavailable",
              message: "The game connection is not ready.",
              color: "yellow",
            });
        }}
        onPendingClose={() => setPending(null)}
        onPendingConfirm={() => {
          if (pending) sendEngineMove(pending);
          setPending(null);
        }}
      />
    </div>
  );
}

function formatClock(milliseconds: number): string {
  const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, "0")}`;
}
