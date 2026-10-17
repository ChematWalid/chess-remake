import { createContext, useContext } from "react";

export interface RealtimeValue {
  connected: boolean;
  connectionStatus:
    "idle" | "connecting" | "connected" | "reconnecting" | "disconnected";
  connectionError: string | null;
  publish: (destination: string, body: unknown) => void;
  subscribe: (
    destination: string,
    listener: (payload: unknown) => void,
  ) => () => void;
}

export const RealtimeContext = createContext<RealtimeValue | null>(null);

export function useRealtime(): RealtimeValue {
  const value = useContext(RealtimeContext);
  if (!value)
    throw new Error("useRealtime must be used within RealtimeProvider");
  return value;
}
