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
  editedAt: string;
  dirty: boolean;
  deleted: boolean;
  savedAt: number;
  syncedAt?: string;
};

export type DraftSyncStatus =
  | "local"
  | "syncing"
  | "saved"
  | "offline"
  | "deleting"
  | "delete-offline"
  | "cleared";

function nextEditedAt(previous?: string): string {
  const previousTimestamp = previous ? Date.parse(previous) : Number.NaN;
  const timestamp = Number.isFinite(previousTimestamp)
    ? Math.max(Date.now(), previousTimestamp + 1)
    : Date.now();
  return new Date(timestamp).toISOString();
}

const saveQueues = new Map<string, Promise<unknown>>();

function serializeDraftSave(
  key: string,
  featureKey: string,
  payload: unknown,
  editedAt: string,
): Promise<UserDraft> {
  const previous = saveQueues.get(key) ?? Promise.resolve();
  const next = previous
    .catch(() => undefined)
    .then(() => saveMyDraft(featureKey, payload, editedAt));
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
  const userEditRevision = useRef(0);
  const retryTimer = useRef<number | undefined>(undefined);
  const draftRef = useRef<LocalDraft<T> | null>(null);
  const latestRef = useRef<{
    key: string;
    version: number;
    value: T;
    editedAt: string;
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
    const revisionAtStart = userEditRevision.current;
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
        if (userEditRevision.current !== revisionAtStart) {
          setReadyKey(localKey);
          return;
        }
        const restored = cached
          ? {
              ...cached,
              editedAt:
                cached.editedAt ?? new Date(cached.savedAt).toISOString(),
            }
          : null;
        draftRef.current = restored;
        latestRef.current = restored
          ? {
              key: localKey,
              version: restored.savedAt,
              value: restored.payload ?? initialRef.current,
              editedAt: restored.editedAt,
              deleted: restored.deleted,
            }
          : null;
        if (restored?.deleted) {
          setValue(initialRef.current);
          setHasDraft(false);
          setDirty(true);
          setStatus(navigator.onLine ? "deleting" : "delete-offline");
        } else if (restored) {
          setValue(restored.payload ?? initialRef.current);
          setHasDraft(true);
          setDirty(restored.dirty);
          setStatus(restored.dirty ? "local" : "saved");
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
        editedAt: remote.editedAt ?? remote.updatedAt,
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
        editedAt: cached.editedAt,
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
    const editedAt = isCurrentDraft
      ? latest.editedAt
      : (draftRef.current?.editedAt ?? nextEditedAt());
    const deleted = isCurrentDraft
      ? latest.deleted
      : (draftRef.current?.deleted ?? false);
    const cached: LocalDraft<T> = {
      payload: deleted ? undefined : snapshot,
      editedAt,
      dirty: true,
      deleted,
      savedAt: Date.now(),
    };
    draftRef.current = cached;
    latestRef.current = {
      key: localKey,
      version,
      value: snapshot,
      editedAt,
      deleted,
    };
    setHasDraft(!deleted);
    setStatus(
      deleted
        ? online
          ? "deleting"
          : "delete-offline"
        : online
          ? "local"
          : "offline",
    );
    void setBrowserValue(localKey, cached).catch(() =>
      setStatus(deleted ? "delete-offline" : "offline"),
    );

    const timeout = window.setTimeout(() => {
      if (!online) {
        setStatus(deleted ? "delete-offline" : "offline");
        return;
      }
      setStatus(deleted ? "deleting" : "syncing");
      const request: Promise<UserDraft | null> = deleted
        ? serializeDraftDelete(localKey, featureKey).then(() => null)
        : serializeDraftSave(localKey, featureKey, snapshot, editedAt);
      void request
        .then(async (saved) => {
          if (deleted) {
            if (latestRef.current?.version === version) {
              await deleteBrowserValue(localKey).catch(() => undefined);
              draftRef.current = null;
              setDirty(false);
              setStatus("cleared");
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
            payload: saved.payload as T,
            editedAt: saved.editedAt ?? saved.updatedAt,
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
              value: saved.payload as T,
              editedAt: saved.editedAt ?? saved.updatedAt,
              deleted: false,
            };
            setValue(saved.payload as T);
            setDirty(false);
            setStatus("saved");
            window.clearTimeout(retryTimer.current);
            await setBrowserValue(localKey, synced).catch(() => undefined);
            setRestoreVersion((current) => current + 1);
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
                ? deleted
                  ? "delete-offline"
                  : "offline"
                : deleted
                  ? "deleting"
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
      userEditRevision.current += 1;
      const version = Date.now() + Math.random();
      const previousEditedAt =
        latestRef.current?.key === localKey
          ? latestRef.current.editedAt
          : draftRef.current?.editedAt;
      const editedAt = nextEditedAt(previousEditedAt);
      draftRef.current = {
        payload: nextValue,
        editedAt,
        dirty: true,
        deleted: false,
        savedAt: version,
      };
      latestRef.current = {
        key: localKey ?? "",
        version,
        value: nextValue,
        editedAt,
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
    userEditRevision.current += 1;
    const version = Date.now() + Math.random();
    const previousEditedAt =
      latestRef.current?.key === localKey
        ? latestRef.current.editedAt
        : draftRef.current?.editedAt;
    const editedAt = nextEditedAt(previousEditedAt);
    draftRef.current = {
      editedAt,
      dirty: true,
      deleted: true,
      savedAt: version,
    };
    latestRef.current = {
      key: localKey ?? "",
      version,
      value: initialRef.current,
      editedAt,
      deleted: true,
    };
    setValue(initialRef.current);
    setDirty(true);
    setHasDraft(false);
    setStatus(online ? "deleting" : "delete-offline");
    setEditVersion((current) => current + 1);
  }, [localKey, online]);

  const flush = useCallback(() => {
    const latest = latestRef.current;
    if (!latest || latest.key !== localKey || !draftRef.current?.dirty) return;
    if (!navigator.onLine) {
      setStatus(latest.deleted ? "delete-offline" : "offline");
      return;
    }
    setStatus(latest.deleted ? "deleting" : "syncing");
    const request: Promise<UserDraft | null> = latest.deleted
      ? serializeDraftDelete(localKey, featureKey).then(() => null)
      : serializeDraftSave(localKey, featureKey, latest.value, latest.editedAt);
    void request
      .then(async (saved) => {
        if (latest.deleted) {
          if (latestRef.current?.version === latest.version) {
            await deleteBrowserValue(localKey).catch(() => undefined);
            draftRef.current = null;
            setStatus("cleared");
          }
          return;
        }
        if (!saved) return;
        if (latestRef.current?.version !== latest.version) return;
        const synced: LocalDraft<T> = {
          payload: saved.payload as T,
          editedAt: saved.editedAt ?? saved.updatedAt,
          dirty: false,
          deleted: false,
          savedAt: Date.now(),
          syncedAt: saved.updatedAt,
        };
        draftRef.current = synced;
        latestRef.current = {
          ...latest,
          value: saved.payload as T,
          editedAt: saved.editedAt ?? saved.updatedAt,
        };
        setValue(saved.payload as T);
        setDirty(false);
        setStatus("saved");
        setRestoreVersion((current) => current + 1);
        await setBrowserValue(localKey, synced).catch(() => undefined);
      })
      .catch(() =>
        setStatus(
          !navigator.onLine
            ? latest.deleted
              ? "delete-offline"
              : "offline"
            : latest.deleted
              ? "deleting"
              : "local",
        ),
      );
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
