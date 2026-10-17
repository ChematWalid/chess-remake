import { Client, type IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren,
} from "react";
import { notifications } from "@mantine/notifications";
import { useQueryClient } from "@tanstack/react-query";
import { useQuery } from "@tanstack/react-query";
import {
  getAccessToken,
  refreshSessionAccessToken,
} from "../../shared/api/client";
import {
  challengeEventSchema,
  presenceEventSchema,
  userNotificationSchema,
  type BackendError,
} from "../../shared/api/types";
import { useAuth } from "../../shared/auth/AuthProvider";
import { defaultSettings } from "../../shared/api/types";
import { getSettings } from "../user/api/userApi";
import { playUiTone } from "../../shared/utils/playUiTone";
import { RealtimeContext, type RealtimeValue } from "./RealtimeContext";

export function RealtimeProvider({ children }: PropsWithChildren) {
  const { isAuthenticated } = useAuth();
  const cache = useQueryClient();
  const { data: settings = defaultSettings } = useQuery({
    queryKey: ["settings"],
    queryFn: getSettings,
    initialData: defaultSettings,
    enabled: isAuthenticated,
  });
  const settingsRef = useRef(settings);
  settingsRef.current = settings;
  const client = useRef<Client | null>(null);
  const listeners = useRef(new Map<string, Set<(payload: unknown) => void>>());
  const [connected, setConnected] = useState(false);
  const [connectionStatus, setConnectionStatus] =
    useState<RealtimeValue["connectionStatus"]>("idle");
  const [connectionError, setConnectionError] = useState<string | null>(null);
  const subscribe = useCallback(
    (destination: string, listener: (payload: unknown) => void) => {
      const entries = listeners.current.get(destination) ?? new Set();
      entries.add(listener);
      listeners.current.set(destination, entries);
      return () => {
        entries.delete(listener);
        if (!entries.size) listeners.current.delete(destination);
      };
    },
    [],
  );
  const publish = useCallback((destination: string, body: unknown) => {
    if (!client.current?.connected)
      throw new Error("Realtime connection is unavailable");
    client.current.publish({ destination, body: JSON.stringify(body) });
  }, []);

  useEffect(() => {
    if (!isAuthenticated) {
      setConnected(false);
      setConnectionStatus("idle");
      setConnectionError(null);
      return;
    }
    const base = import.meta.env.VITE_API_BASE_URL || window.location.origin;
    const socketUrl = new URL("/ws", base).toString();
    setConnectionStatus("connecting");
    setConnectionError(null);
    let lastTokenRefreshAt = 0;
    const stomp = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      beforeConnect: async () => {
        if (Date.now() - lastTokenRefreshAt >= 60_000) {
          lastTokenRefreshAt = Date.now();
          await refreshSessionAccessToken();
        }
        const token = getAccessToken();
        if (!token) {
          setConnected(false);
          setConnectionStatus("disconnected");
          setConnectionError(
            "Your session expired. Sign in again to send challenges.",
          );
          void stomp.deactivate();
          return;
        }
        stomp.connectHeaders = { Authorization: `Bearer ${token}` };
      },
      reconnectDelay: 2500,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        setConnected(true);
        setConnectionStatus("connected");
        setConnectionError(null);
        for (const destination of [
          "/user/topic/presence",
          "/user/topic/challenges",
          "/user/topic/notifications",
          "/user/topic/errors",
        ]) {
          stomp.subscribe(destination, (message: IMessage) => {
            try {
              const raw: unknown = JSON.parse(message.body);
              const payload = destination.endsWith("/challenges")
                ? challengeEventSchema.parse(raw)
                : destination.endsWith("/presence")
                  ? presenceEventSchema.parse(raw)
                  : destination.endsWith("/notifications")
                    ? userNotificationSchema.parse(raw)
                    : raw;
              listeners.current
                .get(destination)
                ?.forEach((listener) => listener(payload));
              if (destination.endsWith("/presence")) {
                const presence = presenceEventSchema.parse(payload);
                cache.setQueryData<
                  Array<{ publicUserId: string; presence: string }>
                >(["friends"], (friends) =>
                  friends?.map((friend) =>
                    friend.publicUserId === presence.publicUserId
                      ? { ...friend, presence: presence.status }
                      : friend,
                  ),
                );
              }
              if (destination.endsWith("/errors")) {
                const error = payload as BackendError;
                notifications.show({
                  title: error.errorCode,
                  message: error.message,
                  color: "red",
                });
              }
              if (destination.endsWith("/challenges")) {
                const challenge = challengeEventSchema.parse(payload);
                void cache.invalidateQueries({ queryKey: ["challenges"] });
                if (
                  challenge.eventType === "STATUS_CHANGED" &&
                  challenge.status === "EXPIRED"
                ) {
                  notifications.show({
                    title: "Game room expired",
                    message:
                      "Both players did not connect in time. You can send a new challenge.",
                    color: "yellow",
                  });
                }
                if (
                  challenge.eventType === "INVITATION_RECEIVED" &&
                  settingsRef.current.inAppNotificationsEnabled
                ) {
                  notifications.show({
                    title: "New game challenge",
                    message: `${challenge.otherDisplayName ?? challenge.otherUsername ?? "A friend"} invited you to play.`,
                    color: "green",
                  });
                  if (settingsRef.current.notificationSoundsEnabled)
                    playUiTone(740);
                }
              }
              if (destination.endsWith("/notifications")) {
                const notice = userNotificationSchema.parse(payload);
                const messages = {
                  FRIEND_REQUEST: [
                    "Friend request",
                    "Someone sent you a friend request.",
                  ],
                  FRIEND_REQUEST_ACCEPTED: [
                    "Friend request accepted",
                    "You can now challenge each other.",
                  ],
                  FRIEND_REQUEST_DECLINED: [
                    "Friend request declined",
                    "Your friend request was declined.",
                  ],
                  GAME_SAVED: [
                    "Game saved",
                    "Your completed game is available in your history.",
                  ],
                } as const;
                const [title, messageText] = messages[notice.type];
                if (settingsRef.current.inAppNotificationsEnabled) {
                  notifications.show({
                    title,
                    message: messageText,
                    color: "green",
                  });
                  if (settingsRef.current.notificationSoundsEnabled)
                    playUiTone(740);
                }
                void cache.invalidateQueries({ queryKey: ["friends"] });
                void cache.invalidateQueries({ queryKey: ["friend-requests"] });
                void cache.invalidateQueries({ queryKey: ["my-history"] });
              }
            } catch {
              notifications.show({
                title: "Connection message error",
                message: "Received an unreadable server event.",
                color: "red",
              });
            }
          });
        }
      },
      onDisconnect: () => {
        setConnected(false);
        setConnectionStatus("reconnecting");
      },
      onWebSocketClose: () => {
        setConnected(false);
        setConnectionStatus(stomp.active ? "reconnecting" : "disconnected");
        setConnectionError(
          (current) =>
            current ??
            "Realtime connection closed. The client will retry automatically.",
        );
      },
      onWebSocketError: () => {
        setConnected(false);
        setConnectionStatus("reconnecting");
        setConnectionError(
          "Could not reach the realtime server. Retrying automatically.",
        );
      },
      onStompError: (frame) => {
        setConnected(false);
        setConnectionStatus("reconnecting");
        setConnectionError(
          frame.headers.message ??
            frame.body ??
            "The realtime server rejected the connection.",
        );
      },
    });
    client.current = stomp;
    stomp.activate();
    return () => {
      setConnected(false);
      setConnectionStatus("idle");
      client.current = null;
      void stomp.deactivate();
    };
  }, [isAuthenticated, cache]);

  const value = useMemo(
    () => ({
      connected,
      connectionStatus,
      connectionError,
      publish,
      subscribe,
    }),
    [connected, connectionStatus, connectionError, publish, subscribe],
  );
  return (
    <RealtimeContext.Provider value={value}>
      {children}
    </RealtimeContext.Provider>
  );
}
