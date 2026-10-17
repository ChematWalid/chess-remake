import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "../auth/AuthProvider";
import { ApiError } from "../api/client";
import type { UserDraft } from "../api/types";
import {
  deleteMyDraft,
  getMyDrafts,
  saveMyDraft,
} from "../../features/user/api/userApi";
import {
  deleteBrowserValue,
  getBrowserValue,
  setBrowserValue,
} from "../storage/browserStorage";

type LocalDraft<T> = {
  payload?: T;
  dirty: boolean;
  deleted: boolean;
  savedAt: number;
  syncedAt?: string;
};

export type DraftSyncStatus = "local" | "syncing" | "saved" | "offline";

const saveQueues = new Map<string, Promise<unknown>>();

function serializeDraftSave(
  key: string,
  featureKey: string,
  payload: unknown,
): Promise<UserDraft> {
  const previous = saveQueues.get(key) ?? Promise.resolve();
  const next = previous
    .catch(() => undefined)
    .then(() => saveMyDraft(featureKey, payload));
  saveQueues.set(key, next);
  const cleanup = () => {
    if (saveQueues.get(key) === next) saveQueues.delete(key);
  };
  void next.then(cleanup, cleanup);
  return next;
}

function serializeDraftDelete(key: string, featureKey: string): Promise<void> {
  const previous = saveQueues.get(key) ?? Promise.resolve();
  const next = previous
    .catch(() => undefined)
    .then(() => deleteMyDraft(featureKey));
  saveQueues.set(key, next);
  const cleanup = () => {
    if (saveQueues.get(key) === next) saveQueues.delete(key);
  };
  void next.then(cleanup, cleanup);
  return next;
}

export function useSyncedDraft<T>(
  featureKey: string,
  initialValue: T,
  options: { enabled?: boolean; restoreExisting?: boolean } = {},
) {
  const enabled = options.enabled ?? true;
  const restoreExisting = options.restoreExisting ?? true;
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const userId = user?.publicId ?? null;
  const localKey = userId ? `draft-local:${userId}:${featureKey}` : null;
  const [value, setValue] = useState(initialValue);
  const [readyKey, setReadyKey] = useState<string | null>(null);
  const [hasDraft, setHasDraft] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [editVersion, setEditVersion] = useState(0);
  const [status, setStatus] = useState<DraftSyncStatus>("local");
  const [online, setOnline] = useState(
    () => typeof navigator === "undefined" || navigator.onLine,
  );
  const [localRevision, setLocalRevision] = useState(0);
  const [restoreVersion, setRestoreVersion] = useState(0);
  const [retryVersion, setRetryVersion] = useState(0);
  const retryTimer = useRef<number | undefined>(undefined);
  const draftRef = useRef<LocalDraft<T> | null>(null);
  const latestRef = useRef<{
    key: string;
    version: number;
    value: T;
    deleted: boolean;
  } | null>(null);
  const initialRef = useRef(initialValue);
  const draftQuery = useQuery({
    queryKey: ["my-drafts", userId],
    queryFn: getMyDrafts,
    enabled: Boolean(enabled && restoreExisting && userId),
    staleTime: 0,
    refetchOnMount: "always",
    refetchOnWindowFocus: true,
  });
  const isReady = !enabled || !userId || readyKey === localKey;

  useEffect(() => {
    const handleOnline = () => setOnline(true);
    const handleOffline = () => setOnline(false);
    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);
    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  useEffect(() => {
    if (!enabled || !localKey) {
      draftRef.current = null;
      setReadyKey(localKey);
      return;
    }

    let active = true;
    setReadyKey(null);
    setHasDraft(false);
    setDirty(false);
    draftRef.current = null;

    if (!restoreExisting) {
      setValue(initialRef.current);
      setReadyKey(localKey);
      setRestoreVersion((current) => current + 1);
      return () => {
        active = false;
      };
    }

    void getBrowserValue<LocalDraft<T>>(localKey)
      .then((cached) => {
        if (!active) return;
        draftRef.current = cached ?? null;
        latestRef.current = cached
          ? {
              key: localKey,
              version: cached.savedAt,
              value: cached.payload ?? initialRef.current,
              deleted: cached.deleted,
            }
          : null;
        if (cached?.deleted) {
          setValue(initialRef.current);
          setHasDraft(false);
          setDirty(true);
        } else if (cached) {
          setValue(cached.payload ?? initialRef.current);
          setHasDraft(true);
          setDirty(cached.dirty);
          setStatus(cached.dirty ? "local" : "saved");
        }
        setReadyKey(localKey);
        setRestoreVersion((current) => current + 1);
      })
      .catch(() => {
        if (active) setReadyKey(localKey);
      });

    return () => {
      active = false;
    };
  }, [enabled, featureKey, localKey, restoreExisting]);

  useEffect(() => {
    if (
      !enabled ||
      !localKey ||
      !isReady ||
      !restoreExisting ||
      !draftQuery.data
    )
      return;
    const local = draftRef.current;
    if (local?.dirty) return;

    const remote = draftQuery.data.find(
      (draft) => draft.featureKey === featureKey,
    );
    if (!remote) {
      if (local) {
        draftRef.current = null;
        latestRef.current = null;
        setHasDraft(false);
        setValue(initialRef.current);
        setLocalRevision((current) => current + 1);
      }
      return;
    }

    if (
      !local ||
      !local.syncedAt ||
      Date.parse(remote.updatedAt) > Date.parse(local.syncedAt)
    ) {
      const remoteValue = remote.payload as T;
      const cached: LocalDraft<T> = {
        payload: remoteValue,
        dirty: false,
        deleted: false,
        savedAt: Date.now(),
        syncedAt: remote.updatedAt,
      };
      draftRef.current = cached;
      latestRef.current = {
        key: localKey,
        version: cached.savedAt,
        value: remoteValue,
        deleted: false,
      };
      setValue(remoteValue);
      setHasDraft(true);
      setDirty(false);
      setStatus("saved");
      void setBrowserValue(localKey, cached).catch(() => undefined);
      setRestoreVersion((current) => current + 1);
    }
  }, [
    draftQuery.data,
    enabled,
    featureKey,
    isReady,
    localKey,
    localRevision,
    restoreExisting,
  ]);

  useEffect(() => {
    if (!enabled || !userId || !localKey || !isReady || !dirty) return;

    const latest = latestRef.current;
    const isCurrentDraft = latest?.key === localKey;
    const version = isCurrentDraft ? latest.version : editVersion;
    const snapshot = isCurrentDraft ? latest.value : value;
    const deleted = isCurrentDraft
      ? latest.deleted
      : (draftRef.current?.deleted ?? false);
    const cached: LocalDraft<T> = {
      payload: deleted ? undefined : snapshot,
      dirty: true,
      deleted,
      savedAt: Date.now(),
    };
    draftRef.current = cached;
    latestRef.current = {
      key: localKey,
      version,
      value: snapshot,
      deleted,
    };
    setHasDraft(!deleted);
    setStatus(online ? "local" : "offline");
    void setBrowserValue(localKey, cached).catch(() => setStatus("offline"));

    const timeout = window.setTimeout(() => {
      if (!online) {
        setStatus("offline");
        return;
      }
      setStatus("syncing");
      const request: Promise<UserDraft | null> = deleted
        ? serializeDraftDelete(localKey, featureKey).then(() => null)
        : serializeDraftSave(localKey, featureKey, snapshot);
      void request
        .then(async (saved) => {
          if (deleted) {
            if (latestRef.current?.version === version) {
              await deleteBrowserValue(localKey).catch(() => undefined);
              draftRef.current = null;
              setDirty(false);
              setStatus("saved");
              window.clearTimeout(retryTimer.current);
            }
            queryClient.setQueryData<UserDraft[]>(
              ["my-drafts", userId],
              (current) =>
                (current ?? []).filter(
                  (draft) => draft.featureKey !== featureKey,
                ),
            );
            return;
          }
          if (!saved) return;
          const synced: LocalDraft<T> = {
            payload: snapshot,
            dirty: false,
            deleted: false,
            savedAt: Date.now(),
            syncedAt: saved.updatedAt,
          };
          if (latestRef.current?.version === version) {
            draftRef.current = synced;
            latestRef.current = {
              key: localKey,
              version,
              value: snapshot,
              deleted: false,
            };
            setDirty(false);
            setStatus("saved");
            window.clearTimeout(retryTimer.current);
            await setBrowserValue(localKey, synced).catch(() => undefined);
          }
          queryClient.setQueryData<UserDraft[]>(
            ["my-drafts", userId],
            (current) => [
              ...(current ?? []).filter(
                (draft) => draft.featureKey !== featureKey,
              ),
              saved,
            ],
          );
        })
        .catch((error: unknown) => {
          if (latestRef.current?.version === version) {
            setStatus(
              !navigator.onLine ||
                (error instanceof ApiError && error.status === 0)
                ? "offline"
                : "local",
            );
            window.clearTimeout(retryTimer.current);
            if (navigator.onLine)
              retryTimer.current = window.setTimeout(
                () => setRetryVersion((current) => current + 1),
                5_000,
              );
          }
        });
    }, 700);

    return () => window.clearTimeout(timeout);
  }, [
    dirty,
    editVersion,
    enabled,
    featureKey,
    isReady,
    localKey,
    online,
    queryClient,
    retryVersion,
    userId,
    value,
  ]);

  const update = useCallback(
    (nextValue: T) => {
      const version = Date.now() + Math.random();
      draftRef.current = {
        payload: nextValue,
        dirty: true,
        deleted: false,
        savedAt: version,
      };
      latestRef.current = {
        key: localKey ?? "",
        version,
        value: nextValue,
        deleted: false,
      };
      setValue(nextValue);
      setDirty(true);
      setHasDraft(true);
      setEditVersion((current) => current + 1);
    },
    [localKey],
  );

  const clear = useCallback(() => {
    const version = Date.now() + Math.random();
    draftRef.current = {
      dirty: true,
      deleted: true,
      savedAt: version,
    };
    latestRef.current = {
      key: localKey ?? "",
      version,
      value: initialRef.current,
      deleted: true,
    };
    setValue(initialRef.current);
    setDirty(true);
    setHasDraft(false);
    setEditVersion((current) => current + 1);
  }, [localKey]);

  const flush = useCallback(() => {
    const latest = latestRef.current;
    if (!latest || latest.key !== localKey || !draftRef.current?.dirty) return;
    if (!navigator.onLine) {
      setStatus("offline");
      return;
    }
    setStatus("syncing");
    const request: Promise<UserDraft | null> = latest.deleted
      ? serializeDraftDelete(localKey, featureKey).then(() => null)
      : serializeDraftSave(localKey, featureKey, latest.value);
    void request
      .then(async (saved) => {
        if (latest.deleted) {
          if (latestRef.current?.version === latest.version) {
            await deleteBrowserValue(localKey).catch(() => undefined);
            draftRef.current = null;
          }
          return;
        }
        if (!saved) return;
        if (latestRef.current?.version !== latest.version) return;
        const synced: LocalDraft<T> = {
          payload: latest.value,
          dirty: false,
          deleted: false,
          savedAt: Date.now(),
          syncedAt: saved.updatedAt,
        };
        draftRef.current = synced;
        await setBrowserValue(localKey, synced).catch(() => undefined);
      })
      .catch(() => setStatus("offline"));
  }, [featureKey, localKey]);

  useEffect(() => {
    const handleBlur = () => flush();
    window.addEventListener("blur", handleBlur);
    window.addEventListener("pagehide", handleBlur);
    return () => {
      window.removeEventListener("blur", handleBlur);
      window.removeEventListener("pagehide", handleBlur);
      window.clearTimeout(retryTimer.current);
      flush();
    };
  }, [flush]);

  return { value, update, clear, hasDraft, isReady, restoreVersion, status };
}
