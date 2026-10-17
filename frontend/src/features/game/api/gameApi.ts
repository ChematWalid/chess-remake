import { apiDownloadText, apiGet } from "../../../shared/api/client";
import {
  gameDetailSchema,
  gameSummarySchema,
  pageSchema,
  type GameDetail,
  type GameSummary,
  type Page,
} from "../../../shared/api/types";

export async function getMyGames(
  page: number,
  size = 15,
): Promise<Page<GameSummary>> {
  return apiGet(
    "/api/games/my-history",
    { params: { page, size } },
    pageSchema(gameSummarySchema),
  );
}
export async function getPlayerGames(
  publicId: string,
  page: number,
  size = 15,
): Promise<Page<GameSummary>> {
  return apiGet(
    `/api/games/user/${publicId}`,
    { params: { page, size } },
    pageSchema(gameSummarySchema),
  );
}
export async function getGame(gameId: number): Promise<GameDetail> {
  return apiGet(`/api/games/${gameId}`, undefined, gameDetailSchema);
}
export async function downloadPgn(gameId: number): Promise<string> {
  return apiDownloadText(`/api/games/${gameId}/pgn`);
}
