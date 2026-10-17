import { apiGet, apiPost, apiPut } from "../../../shared/api/client";
import {
  pageSchema,
  profileSchema,
  searchResultSchema,
  userSettingsSchema,
  type Profile,
  type SearchResult,
  type Page,
  type UserSettings,
} from "../../../shared/api/types";

export interface ProfileUpdate {
  username: string;
  displayName: string;
  bio: string;
  searchable: boolean;
}
export async function getMyProfile(): Promise<Profile> {
  return apiGet("/api/users/me", undefined, profileSchema);
}
export async function getProfile(username: string): Promise<Profile> {
  return apiGet(
    `/api/users/${encodeURIComponent(username)}/profile`,
    undefined,
    profileSchema,
  );
}
export async function updateMyProfile(
  details: ProfileUpdate,
): Promise<Profile> {
  return apiPut("/api/users/me", details, profileSchema);
}
export async function searchProfiles(
  query: string,
  page = 0,
  size = 24,
): Promise<Page<SearchResult>> {
  return apiGet(
    "/api/users/search",
    { params: { q: query, page, size } },
    pageSchema(searchResultSchema),
  );
}
export async function uploadAvatar(file: File): Promise<void> {
  const body = new FormData();
  body.append("file", file);
  await apiPost<void, FormData>("/api/users/me/avatar", body, {
    headers: { "Content-Type": "multipart/form-data" },
  });
}
export async function getSettings(): Promise<UserSettings> {
  return apiGet("/api/users/me/settings", undefined, userSettingsSchema);
}
export async function updateSettings(
  settings: UserSettings,
): Promise<UserSettings> {
  return apiPut("/api/users/me/settings", settings, userSettingsSchema);
}
