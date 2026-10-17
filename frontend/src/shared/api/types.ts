import { z } from "zod";

export interface AppResponse<T> {
  success: boolean;
  code: number | null;
  errorCode: string | null;
  message: string;
  data: T | null;
  errors: Record<string, string> | null;
  timestamp: string | null;
}

export const pageSchema = <T extends z.ZodType>(item: T) =>
  z.object({
    items: z.array(item),
    page: z.number().int(),
    size: z.number().int(),
    totalElements: z.number().int(),
  });
export type Page<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
};

export const userSummarySchema = z.object({
  publicId: z.string().uuid(),
  username: z.string(),
  displayName: z.string(),
  email: z.string().email(),
});
export type UserSummary = z.infer<typeof userSummarySchema>;
export const authResponseSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.string(),
  expiresInSeconds: z.number(),
  onboardingRequired: z.boolean(),
  user: userSummarySchema,
});
export type AuthResponse = z.infer<typeof authResponseSchema>;

export const profileSchema = z.object({
  id: z.number().optional(),
  publicId: z.string().uuid(),
  username: z.string(),
  displayName: z.string(),
  bio: z.string().nullable(),
  avatarUrl: z.string().nullable(),
  searchable: z.boolean().optional(),
  totalGames: z.number().int(),
  wins: z.number().int(),
  losses: z.number().int(),
  draws: z.number().int(),
});
export type Profile = z.infer<typeof profileSchema>;
export const userDraftSchema = z.object({
  featureKey: z.string(),
  payload: z.unknown(),
  updatedAt: z.string(),
  editedAt: z.string().optional(),
});
export type UserDraft = z.infer<typeof userDraftSchema>;
export const searchResultSchema = z.object({
  publicId: z.string().uuid(),
  username: z.string(),
  displayName: z.string(),
  avatarUrl: z.string().nullable(),
  totalGames: z.number().int(),
  wins: z.number().int(),
  losses: z.number().int(),
  draws: z.number().int(),
});
export type SearchResult = z.infer<typeof searchResultSchema>;

export const presenceStatusSchema = z.enum([
  "ONLINE",
  "IN_GAME",
  "OFFLINE",
  "UNKNOWN",
]);
export type PresenceStatus = z.infer<typeof presenceStatusSchema>;
export const presenceEventSchema = z.object({
  publicUserId: z.string().uuid(),
  status: presenceStatusSchema,
});
export const userNotificationSchema = z.object({
  type: z.enum([
    "FRIEND_REQUEST",
    "FRIEND_REQUEST_ACCEPTED",
    "FRIEND_REQUEST_DECLINED",
    "GAME_SAVED",
  ]),
  subjectId: z.string(),
});
export const friendshipStatusSchema = z.enum([
  "PENDING",
  "ACCEPTED",
  "DECLINED",
  "BLOCKED",
]);
export type FriendshipStatus = z.infer<typeof friendshipStatusSchema>;
export const friendSchema = z.object({
  publicUserId: z.string().uuid(),
  username: z.string(),
  displayName: z.string(),
  avatarUrl: z.string().nullable(),
  presence: presenceStatusSchema,
});
export type Friend = z.infer<typeof friendSchema>;
export const friendRequestSchema = z.object({
  requestId: z.number().int(),
  publicUserId: z.string().uuid(),
  username: z.string(),
  displayName: z.string(),
  status: friendshipStatusSchema,
  avatarUrl: z.string().nullable(),
  createdAt: z.string(),
});
export type FriendRequest = z.infer<typeof friendRequestSchema>;

export const gameSummarySchema = z.object({
  id: z.number().int(),
  whiteUsername: z.string(),
  blackUsername: z.string(),
  draw: z.boolean(),
  whitePublicUserId: z.string().uuid().nullable(),
  blackPublicUserId: z.string().uuid().nullable(),
  winnerPublicUserId: z.string().uuid().nullable(),
  terminationReason: z.string(),
  totalMoves: z.number().int(),
  playedAt: z.string(),
  gameType: z.string().default("STANDARD"),
});
export type GameSummary = z.infer<typeof gameSummarySchema>;
export const gameDetailSchema = z.object({
  summary: gameSummarySchema,
  pgn: z.string(),
  moves: z.array(z.string()),
  fens: z.array(z.string()),
  gameType: z.string().default("STANDARD"),
  setupMoves: z.array(z.string()).default([]),
  setupFens: z.array(z.string()).default([]),
  initialTimeSeconds: z.number().int().nullable(),
  incrementSeconds: z.number().int().nullable(),
  durationSeconds: z.number().int(),
});
export type GameDetail = z.infer<typeof gameDetailSchema>;

export const boardPaletteSchema = z.enum(["FOREST", "WALNUT", "MARBLE"]);
export const boardOrientationSchema = z.enum(["AUTO", "WHITE", "BLACK"]);
export const motionPreferenceSchema = z.enum(["SYSTEM", "REDUCED", "FULL"]);
export const themePreferenceSchema = z.enum(["DARK", "LIGHT", "SYSTEM"]);
export type BoardPalette = z.infer<typeof boardPaletteSchema>;
export type BoardOrientation = z.infer<typeof boardOrientationSchema>;
export type MotionPreference = z.infer<typeof motionPreferenceSchema>;
export type ThemePreference = z.infer<typeof themePreferenceSchema>;
export const userSettingsSchema = z.object({
  boardPalette: boardPaletteSchema,
  boardOrientation: boardOrientationSchema,
  motionPreference: motionPreferenceSchema,
  themePreference: themePreferenceSchema,
  showCoordinates: z.boolean(),
  showLegalMoveHints: z.boolean(),
  premovesEnabled: z.boolean(),
  moveConfirmation: z.boolean(),
  gameSoundsEnabled: z.boolean(),
  turnIndicatorEnabled: z.boolean().default(true),
  turnSoundEnabled: z.boolean().default(true),
  inAppNotificationsEnabled: z.boolean(),
  notificationSoundsEnabled: z.boolean(),
});
export type UserSettings = z.infer<typeof userSettingsSchema>;

export const challengeStatusSchema = z.enum([
  "PENDING",
  "ACCEPTED",
  "DECLINED",
  "CANCELED",
  "EXPIRED",
  "COMPLETED",
]);
export const gameSideSchema = z.enum(["WHITE", "BLACK", "RANDOM"]);
export const challengeSummarySchema = z.object({
  challengeId: z.number().int(),
  status: challengeStatusSchema,
  challengerPublicUserId: z.string().uuid(),
  challengerUsername: z.string(),
  challengedPublicUserId: z.string().uuid(),
  challengedUsername: z.string(),
  initialTimeSeconds: z.number().int().nullable(),
  incrementSeconds: z.number().int().nullable(),
  challengerSide: gameSideSchema,
  playerColor: gameSideSchema.nullable(),
  roomCode: z.string().nullable(),
  initialFen: z.string().nullable().optional(),
  setupMoves: z.array(z.string()).default([]),
  setupFens: z.array(z.string()).default([]),
  createdAt: z.string(),
  expiresAt: z.string(),
});
export type ChallengeSummary = z.infer<typeof challengeSummarySchema>;
export const challengeDetailSchema = z.object({
  challengeId: z.number().int(),
  status: challengeStatusSchema,
  opponentPublicUserId: z.string().uuid(),
  opponentUsername: z.string(),
  initialTimeSeconds: z.number().int().nullable(),
  incrementSeconds: z.number().int().nullable(),
  challengerSide: gameSideSchema,
  playerColor: z.enum(["WHITE", "BLACK"]).nullable(),
  roomCode: z.string().nullable(),
  initialFen: z.string().nullable().optional(),
  setupMoves: z.array(z.string()).default([]),
  setupFens: z.array(z.string()).default([]),
  connectUrl: z.string().nullable(),
  token: z.string().nullable(),
  expiresAt: z.string(),
});
export type ChallengeDetail = z.infer<typeof challengeDetailSchema>;
export const challengeEventSchema = z.object({
  eventType: z.enum([
    "INVITATION_RECEIVED",
    "INVITATION_SENT",
    "STATUS_CHANGED",
    "MATCH_ASSIGNED",
  ]),
  challengeId: z.number().int(),
  status: challengeStatusSchema,
  actorPublicUserId: z.string().uuid().nullable(),
  otherUsername: z.string().nullable(),
  otherDisplayName: z.string().nullable(),
  initialTimeSeconds: z.number().int().nullable(),
  incrementSeconds: z.number().int().nullable(),
  challengerSide: gameSideSchema,
  roomCode: z.string().nullable(),
  color: z.enum(["WHITE", "BLACK", "RANDOM"]).nullable(),
  connectUrl: z.string().nullable(),
  token: z.string().nullable(),
  message: z.string().nullable(),
  initialFen: z.string().nullable().optional(),
  setupMoves: z.array(z.string()).default([]),
  setupFens: z.array(z.string()).default([]),
});
export type ChallengeEvent = z.infer<typeof challengeEventSchema>;

export interface BackendError {
  errorCode: string;
  message: string;
}

export const defaultSettings: UserSettings = {
  boardPalette: "FOREST",
  boardOrientation: "AUTO",
  motionPreference: "SYSTEM",
  themePreference: "DARK",
  showCoordinates: true,
  showLegalMoveHints: true,
  premovesEnabled: true,
  moveConfirmation: false,
  gameSoundsEnabled: true,
  turnIndicatorEnabled: true,
  turnSoundEnabled: true,
  inAppNotificationsEnabled: true,
  notificationSoundsEnabled: false,
};
