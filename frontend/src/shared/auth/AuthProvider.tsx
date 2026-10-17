import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { setAccessToken, setSessionExpiredHandler } from "../api/client";
import type { AuthResponse, UserSummary } from "../api/types";
import { logout, restoreSession } from "../../features/auth/api/authApi";

interface AuthContextValue {
  user: UserSummary | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  signIn: (auth: AuthResponse) => void;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<UserSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const signIn = useCallback(
    (auth: AuthResponse) => {
      queryClient.clear();
      setAccessToken(auth.accessToken);
      setUser(auth.user);
      sessionStorage.setItem("chess.user", JSON.stringify(auth.user));
    },
    [queryClient],
  );
  const signOut = useCallback(async () => {
    try {
      await logout();
    } finally {
      setAccessToken(null);
      setUser(null);
      sessionStorage.removeItem("chess.user");
      queryClient.clear();
    }
  }, [queryClient]);

  useEffect(() => {
    setSessionExpiredHandler(() => {
      setAccessToken(null);
      setUser(null);
      sessionStorage.removeItem("chess.user");
    });
    const cached = sessionStorage.getItem("chess.user");
    if (cached) {
      try {
        setUser(JSON.parse(cached) as UserSummary);
      } catch {
        sessionStorage.removeItem("chess.user");
      }
    }
    restoreSession()
      .then((auth) => {
        setAccessToken(auth.accessToken);
        if (auth.user) setUser(auth.user);
      })
      .catch(() => {
        setAccessToken(null);
      })
      .finally(() => setIsLoading(false));
  }, []);

  const value = useMemo(
    () => ({
      user,
      isAuthenticated: user !== null,
      isLoading,
      signIn,
      signOut,
    }),
    [user, isLoading, signIn, signOut],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used within AuthProvider");
  return value;
}
