import { apiDelete, apiGet, apiPost } from "../../../shared/api/client";
import {
  friendRequestSchema,
  friendSchema,
  type Friend,
  type FriendRequest,
} from "../../../shared/api/types";
import { z } from "zod";

export async function getFriends(): Promise<Friend[]> {
  return apiGet("/api/friends", undefined, z.array(friendSchema));
}
export async function getBlockedUsers(): Promise<Friend[]> {
  return apiGet("/api/friends/blocked", undefined, z.array(friendSchema));
}
export async function getIncomingRequests(): Promise<FriendRequest[]> {
  return apiGet(
    "/api/friends/requests/incoming",
    undefined,
    z.array(friendRequestSchema),
  );
}
export async function getOutgoingRequests(): Promise<FriendRequest[]> {
  return apiGet(
    "/api/friends/requests/outgoing",
    undefined,
    z.array(friendRequestSchema),
  );
}
export async function sendFriendRequest(username: string): Promise<void> {
  return apiPost(`/api/friends/request/${encodeURIComponent(username)}`);
}
export async function acceptFriendRequest(requestId: number): Promise<void> {
  return apiPost(`/api/friends/accept/${requestId}`);
}
export async function declineFriendRequest(requestId: number): Promise<void> {
  return apiPost(`/api/friends/decline/${requestId}`);
}
export async function removeFriend(publicUserId: string): Promise<void> {
  return apiDelete(`/api/friends/${publicUserId}`);
}
export async function blockUser(publicUserId: string): Promise<void> {
  return apiPost(`/api/friends/block/${publicUserId}`);
}
export async function unblockUser(publicUserId: string): Promise<void> {
  return apiDelete(`/api/friends/block/${publicUserId}`);
}
export async function createFriendInvite(): Promise<{ path: string }> {
  return apiPost(
    "/api/friends/invite",
    undefined,
    undefined,
    z.object({ path: z.string() }),
  );
}
export async function redeemFriendInvite(token: string): Promise<void> {
  return apiPost("/api/friends/invite/redeem", { token });
}
