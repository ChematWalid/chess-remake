import {
  dehydrate,
  hydrate,
  type DehydratedState,
  type QueryClient,
} from "@tanstack/react-query";
import { useEffect, useState, type PropsWithChildren } from "react";
import { AppLoading } from "../components/AppLoading";
import { useAuth } from "../auth/AuthProvider";
import {
  deleteBrowserValue,
  getBrowserValue,
  setBrowserValue,
} from "./browserStorage";

const CACHE_MAX_AGE = 24 * 60 * 60 * 1000;
const PERSISTED_QUERY_PREFIXES = new Set([
  "friends",
  "friend-requests",
  "blocked-users",
  "my-history",
  "profile",
  "profile-search",
  "game-player-profile",
  "settings",
]);

interface PersistedQueryCache {
  state: DehydratedState;
  savedAt: number;
}

function storageKey(userId: string): string {
  return `query-cache:${userId}`;
}

function shouldPersist(queryKey: readonly unknown[]): boolean {
  return (
    typeof queryKey[0] === "string" && PERSISTED_QUERY_PREFIXES.has(queryKey[0])
  );
}

function persistQueryCache(queryClient: QueryClient, userId: string): void {
  const now = Date.now();
  const persistable = queryClient
    .getQueryCache()
    .getAll()
    .filter(
      (query) =>
        shouldPersist(query.queryKey) &&
        query.state.status === "success" &&
        query.state.dataUpdatedAt > now - CACHE_MAX_AGE,
    );
  if (!persistable.length) {
    void deleteBrowserValue(storageKey(userId));
    return;
  }

  const persistableHashes = new Set(
    persistable.map((query) => query.queryHash),
  );
  const state = dehydrate(queryClient, {
    shouldDehydrateQuery: (query) => persistableHashes.has(query.queryHash),
  });
  void setBrowserValue(storageKey(userId), {
    state,
    savedAt: now,
  } satisfies PersistedQueryCache).catch(() => undefined);
}

export function BrowserQueryCache({
  queryClient,
  children,
}: PropsWithChildren<{ queryClient: QueryClient }>) {
  const { user, isLoading } = useAuth();
  const userId = user?.publicId ?? null;
  const [restoredFor, setRestoredFor] = useState<string | null>(null);

  useEffect(() => {
    if (isLoading) return;
    if (!userId) {
      setRestoredFor(null);
      return;
    }

    let active = true;
    let timer: number | undefined;
    setRestoredFor(null);

    void getBrowserValue<PersistedQueryCache>(storageKey(userId))
      .then((cached) => {
        if (!active) return;
        if (cached && Date.now() - cached.savedAt < CACHE_MAX_AGE) {
          const queries = cached.state.queries.filter(
            (query) =>
              shouldPersist(query.queryKey) &&
              query.state.dataUpdatedAt > Date.now() - CACHE_MAX_AGE,
          );
          hydrate(queryClient, { ...cached.state, queries });
          void queryClient.invalidateQueries({
            predicate: (query) => shouldPersist(query.queryKey),
            refetchType: "none",
          });
        } else if (cached) {
          void deleteBrowserValue(storageKey(userId));
        }
        setRestoredFor(userId);
      })
      .catch(() => {
        if (active) setRestoredFor(userId);
      });

    const persist = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(
        () => persistQueryCache(queryClient, userId),
        250,
      );
    };

    const flush = () => {
      window.clearTimeout(timer);
      persistQueryCache(queryClient, userId);
    };

    const unsubscribe = queryClient.getQueryCache().subscribe(persist);
    window.addEventListener("pagehide", flush);
    return () => {
      active = false;
      window.clearTimeout(timer);
      window.removeEventListener("pagehide", flush);
      unsubscribe();
    };
  }, [isLoading, queryClient, userId]);

  if (!isLoading && userId !== null && restoredFor !== userId)
    return <AppLoading label="Loading your saved data" />;
  return children;
}
