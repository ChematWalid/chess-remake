import { Suspense, lazy, useEffect } from "react";
import { Navigate, Route, Routes, useNavigate } from "react-router-dom";
import { AppShell } from "../shared/components/AppShell";
import { useAuth } from "../shared/auth/AuthProvider";
import { RealtimeProvider } from "../features/realtime/RealtimeProvider";
import { useRealtime } from "../features/realtime/RealtimeContext";
import { challengeEventSchema } from "../shared/api/types";

function AssignedGameNavigator() {
  const { subscribe } = useRealtime();
  const navigate = useNavigate();
  useEffect(
    () =>
      subscribe("/user/queue/challenges", (payload) => {
        const event = challengeEventSchema.parse(payload);
        if (event.eventType === "MATCH_ASSIGNED")
          navigate(`/game/${event.challengeId}`);
      }),
    [subscribe, navigate],
  );
  return null;
}

const LandingPage = lazy(() =>
  import("../features/landing/pages/LandingPage").then((module) => ({
    default: module.LandingPage,
  })),
);
const AuthPage = lazy(() =>
  import("../features/auth/pages/AuthPage").then((module) => ({
    default: module.AuthPage,
  })),
);
const AuthActionPage = lazy(() =>
  import("../features/auth/pages/AuthActionPage").then((module) => ({
    default: module.AuthActionPage,
  })),
);
const OAuthCallbackPage = lazy(() =>
  import("../features/auth/pages/OAuthCallbackPage").then((module) => ({
    default: module.OAuthCallbackPage,
  })),
);
const OnboardingPage = lazy(() =>
  import("../features/auth/pages/OnboardingPage").then((module) => ({
    default: module.OnboardingPage,
  })),
);
const PlayPage = lazy(() =>
  import("../features/play/pages/PlayPage").then((module) => ({
    default: module.PlayPage,
  })),
);
const SearchPage = lazy(() =>
  import("../features/search/pages/SearchPage").then((module) => ({
    default: module.SearchPage,
  })),
);
const FriendsPage = lazy(() =>
  import("../features/social/pages/FriendsPage").then((module) => ({
    default: module.FriendsPage,
  })),
);
const ProfilePage = lazy(() =>
  import("../features/profile/pages/ProfilePage").then((module) => ({
    default: module.ProfilePage,
  })),
);
const HistoryPage = lazy(() =>
  import("../features/history/pages/HistoryPage").then((module) => ({
    default: module.HistoryPage,
  })),
);
const GamePage = lazy(() =>
  import("../features/game/pages/GamePage").then((module) => ({
    default: module.GamePage,
  })),
);
const SettingsPage = lazy(() =>
  import("../features/settings/pages/SettingsPage").then((module) => ({
    default: module.SettingsPage,
  })),
);
const InvitePage = lazy(() =>
  import("../features/social/pages/InvitePage").then((module) => ({
    default: module.InvitePage,
  })),
);

function RequireAuth() {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading)
    return <main className="public-loading">Restoring your session…</main>;
  return isAuthenticated ? <AppShell /> : <Navigate to="/login" replace />;
}

export function AppRoutes() {
  const { isAuthenticated } = useAuth();
  return (
    <RealtimeProvider>
      <AssignedGameNavigator />
      <Suspense
        fallback={<main className="public-loading">Loading page…</main>}
      >
        <Routes>
          <Route
            path="/"
            element={
              isAuthenticated ? (
                <Navigate to="/play" replace />
              ) : (
                <LandingPage />
              )
            }
          />
          <Route path="/login" element={<AuthPage mode="login" />} />
          <Route path="/register" element={<AuthPage mode="register" />} />
          <Route
            path="/forgot-password"
            element={<AuthActionPage mode="forgot" />}
          />
          <Route
            path="/reset-password"
            element={<AuthActionPage mode="reset" />}
          />
          <Route
            path="/verify-email"
            element={<AuthActionPage mode="verify" />}
          />
          <Route path="/oauth2/redirect" element={<OAuthCallbackPage />} />
          <Route path="/friend-invite/:token" element={<InvitePage />} />
          <Route element={<RequireAuth />}>
            <Route path="/onboarding" element={<OnboardingPage />} />
            <Route path="/play" element={<PlayPage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/friends" element={<FriendsPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/profile/:username" element={<ProfilePage />} />
            <Route path="/history" element={<HistoryPage />} />
            <Route path="/games/:id" element={<GamePage />} />
            <Route path="/game/:challengeId" element={<GamePage live />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </RealtimeProvider>
  );
}
