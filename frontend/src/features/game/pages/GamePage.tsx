import { Button, Group, Text } from "@mantine/core";
import { IconDownload } from "@tabler/icons-react";
import { Chess, type Square } from "chess.js";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { notifications } from "@mantine/notifications";
import type { ChessboardOptions } from "react-chessboard";
import type { ChallengeDetail, GameDetail } from "../../../shared/api/types";
import { defaultSettings } from "../../../shared/api/types";
import { ErrorState, LoadingState } from "../../../shared/components/PageState";
import { getChallenge } from "../../challenge/api/challengeApi";
import { downloadPgn as fetchPgn, getGame } from "../api/gameApi";
import { getSettings } from "../../user/api/userApi";
import { ChessBoard } from "../components/ChessBoard";
import { GameMovesPanel } from "../components/GameMovesPanel";
import { MoveDialogs, type PendingMove } from "../components/MoveDialogs";
import { playUiTone } from "../../../shared/utils/playUiTone";
import "./GamePage.css";

interface EngineEvent {
  event?: string;
  fen?: string;
  san?: string;
  side_to_move?: "white" | "black";
  white_time_ms?: number;
  black_time_ms?: number;
  result?: string;
  reason?: string;
  message?: string;
  color?: "white" | "black";
}

export function GamePage({ live = false }: { live?: boolean }) {
  const { id = "0", challengeId = "0" } = useParams();
  const [fen, setFen] = useState("start");
  const [moveText, setMoveText] = useState<string[]>([]);
  const [selected, setSelected] = useState<Square | null>(null);
  const [premove, setPremove] = useState<PendingMove | null>(null);
  const [pending, setPending] = useState<PendingMove | null>(null);
  const [promotion, setPromotion] = useState<PendingMove | null>(null);
  const [color, setColor] = useState<"white" | "black">("white");
  const [connected, setConnected] = useState(false);
  const [finished, setFinished] = useState<string | null>(null);
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [clock, setClock] = useState<{ white: number; black: number } | null>(
    null,
  );
  const socket = useRef<WebSocket | null>(null);
  const livePreferences = useRef({ color, premove });
  livePreferences.current = { color, premove };
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
      if (connection.status !== "ACCEPTED" || !connection.connectUrl || !connection.token) {
        setFinished("This accepted game is no longer available.");
        return;
      }
      const ws = new WebSocket(connection.connectUrl);
      socket.current = ws;
      ws.onopen = () => {
        ws.send(JSON.stringify({
          action: "connect_token",
          room_code: connection.roomCode,
          token: connection.token,
        }));
        hasWarned = false;
      };
      ws.onclose = () => {
        setConnected(false);
        if (!stopped) retryTimer = window.setTimeout(() => void connect(true), 1500);
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
            setConnected(true);
            setFen(event.fen);
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
            setFen(event.fen);
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
            const queued = livePreferences.current.premove;
            if (
              queued &&
              event.side_to_move === livePreferences.current.color
            ) {
              if (!sendEngineMove(queued, event.fen))
                notifications.show({
                  title: "Premove cancelled",
                  message:
                    "That move is no longer legal in the current position.",
                  color: "yellow",
                });
              setPremove(null);
            }
          } else if (event.event === "game_over") {
            setFinished(
              event.reason
                ? `${event.result ?? "Game over"} · ${event.reason}`
                : (event.result ?? "Game over"),
            );
            setPremove(null);
          } else if (event.event === "error") {
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
        return new Chess(fen === "start" ? undefined : fen).turn();
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
  }, [live, connected, hasClock, finished, fen]);

  useEffect(() => {
    if (!playing || !detail.data || !("fens" in detail.data)) return;
    const totalPositions = detail.data.fens.length;
    const timer = window.setInterval(
      () =>
        setIndex((current) =>
          current >= totalPositions - 1 ? 0 : current + 1,
        ),
      900,
    );
    return () => window.clearInterval(timer);
  }, [playing, detail.data]);

  const replay =
    !live && detail.data && "summary" in detail.data
      ? (detail.data as GameDetail)
      : null;
  const opponentUsername =
    live && detail.data && "opponentUsername" in detail.data
      ? detail.data.opponentUsername
      : null;
  const currentFen = replay
    ? (replay.fens[index] ?? replay.fens[0] ?? new Chess().fen())
    : fen === "start"
      ? new Chess().fen()
      : fen;
  const turn = useMemo(() => {
    try {
      return new Chess(currentFen).turn();
    } catch {
      return "w";
    }
  }, [currentFen]);
  const prefs = preferences.data;
  const colors =
    prefs.boardOrientation === "AUTO"
      ? color
      : (prefs.boardOrientation.toLowerCase() as "white" | "black");
  const legalTargets =
    selected && prefs.showLegalMoveHints
      ? new Chess(currentFen)
          .moves({ square: selected, verbose: true })
          .map((move) => move.to)
      : [];

  function sendEngineMove(move: PendingMove, position = currentFen): boolean {
    const ws = socket.current;
    if (!ws || ws.readyState !== WebSocket.OPEN) return false;
    try {
      const game = new Chess(position);
      const applied = game.move({
        from: move.from,
        to: move.to,
        promotion: move.promotion ?? "q",
      });
      if (!applied) return false;
      ws.send(
        JSON.stringify({
          action: "move",
          uci: `${move.from}${move.to}${move.promotion ?? ""}`,
        }),
      );
      return true;
    } catch {
      return false;
    }
  }

  function attemptMove(from: Square, to: Square) {
    const game = new Chess(currentFen);
    if (turn !== (color === "white" ? "w" : "b")) {
      if (prefs.premovesEnabled) {
        setPremove({ from, to });
        notifications.show({
          title: "Premove queued",
          message: "Your move will be sent when it becomes your turn.",
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
    const target = square as Square;
    if (selected) {
      attemptMove(selected, target);
      return;
    }
    const piece = new Chess(currentFen).get(target);
    if (piece && piece.color === color[0]) setSelected(target);
  }

  const boardOptions: ChessboardOptions = {
    position: currentFen,
    boardOrientation: colors,
    showNotation: prefs.showCoordinates,
    squareStyles: {
      ...(selected ? { [selected]: { background: "#d0bd64aa" } } : {}),
      ...Object.fromEntries(
        legalTargets.map((square) => [
          square,
          {
            background: "radial-gradient(circle, #647b45 22%, transparent 24%)",
          },
        ]),
      ),
    },
    canDragPiece: ({ piece }) =>
      live &&
      !finished &&
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
            <Text fw={600}>
              {live
                ? opponentUsername
                  ? `Opponent · ${opponentUsername}`
                  : "Opponent"
                : summary?.blackUsername}
            </Text>
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
              <Text fw={600}>You · {color === "white" ? "White" : "Black"}</Text>
              {clock && (
                <Text className="clock">{formatClock(clock[color])}</Text>
              )}
            </div>
          )}
          {live && (
            <Group mt="md" justify="center">
              <Button
                variant="default"
                onClick={() =>
                  socket.current?.send(JSON.stringify({ action: "offer_draw" }))
                }
                disabled={!connected || Boolean(finished)}
              >
                Offer draw
              </Button>
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
          premove={premove}
          replay={replay}
          index={index}
          playing={playing}
          onIndexChange={setIndex}
          onPlayingChange={setPlaying}
        />
      </div>
      <MoveDialogs
        promotion={promotion}
        pending={pending}
        onPromotionClose={() => setPromotion(null)}
        onPromotionSelect={(piece) => {
          if (!promotion) return;
          const move = { ...promotion, promotion: piece };
          setPromotion(null);
          if (prefs.moveConfirmation) setPending(move);
          else sendEngineMove(move);
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
