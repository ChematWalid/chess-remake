import { apiGet } from "../../../shared/api/client";
import {
  challengeDetailSchema,
  challengeSummarySchema,
  pageSchema,
  type ChallengeDetail,
  type ChallengeSummary,
  type Page,
} from "../../../shared/api/types";

export type ChallengeRequest = {
  toUsername: string;
  challengerSide: "WHITE" | "BLACK" | "RANDOM";
  initialTimeSeconds?: number;
  incrementSeconds?: number;
  initialFen?: string;
  setupMoves?: string[];
  setupFens?: string[];
};
export async function getIncomingChallenges(): Promise<Page<ChallengeSummary>> {
  return apiGet(
    "/api/challenges/incoming",
    undefined,
    pageSchema(challengeSummarySchema),
  );
}
export async function getOutgoingChallenges(): Promise<Page<ChallengeSummary>> {
  return apiGet(
    "/api/challenges/outgoing",
    undefined,
    pageSchema(challengeSummarySchema),
  );
}
export async function getChallenge(
  challengeId: number,
): Promise<ChallengeDetail> {
  return apiGet(
    `/api/challenges/${challengeId}`,
    undefined,
    challengeDetailSchema,
  );
}
export async function acceptChallenge(
  publish: (destination: string, body: unknown) => void,
  challengeId: number,
): Promise<void> {
  publish("/app/challenge.accept", { challengeId });
}
export async function declineChallenge(
  publish: (destination: string, body: unknown) => void,
  challengeId: number,
): Promise<void> {
  publish("/app/challenge.decline", { challengeId });
}
export function cancelChallenge(
  publish: (destination: string, body: unknown) => void,
  challengeId: number,
): void {
  publish("/app/challenge.cancel", { challengeId });
}
export function sendGameChallenge(
  publish: (destination: string, body: unknown) => void,
  request: ChallengeRequest,
): void {
  publish("/app/challenge.send", request);
}
