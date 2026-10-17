import type { GameSummary } from "../../../shared/api/types";

export interface HistoryResultPresentation {
  label: string;
  color: "green" | "red" | "gray" | "blue";
}

export function getHistoryResult(
  game: GameSummary,
  viewedPlayerId: string | null,
): HistoryResultPresentation {
  if (game.draw) return { label: "Draw", color: "gray" };
  if (!game.winnerPublicUserId)
    return { label: "Result unavailable", color: "gray" };

  const winningSide =
    game.winnerPublicUserId === game.whitePublicUserId
      ? "White"
      : game.winnerPublicUserId === game.blackPublicUserId
        ? "Black"
        : null;
  if (!winningSide) return { label: "Result unavailable", color: "gray" };

  const isViewedPlayerInGame =
    viewedPlayerId !== null &&
    (game.whitePublicUserId === viewedPlayerId ||
      game.blackPublicUserId === viewedPlayerId);
  if (!isViewedPlayerInGame) {
    return { label: `${winningSide} won`, color: "blue" };
  }

  const viewedPlayerWon = game.winnerPublicUserId === viewedPlayerId;
  return {
    label: `${viewedPlayerWon ? "Win" : "Loss"} · ${winningSide} won`,
    color: viewedPlayerWon ? "green" : "red",
  };
}
