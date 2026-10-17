import { useEffect, useRef } from "react";
import { Center, Loader, Stack, Text } from "@mantine/core";
import { useNavigate, useSearchParams } from "react-router-dom";
import { notifications } from "@mantine/notifications";
import { ApiError } from "../../../shared/api/client";
import type { AuthResponse } from "../../../shared/api/types";
import { useAuth } from "../../../shared/auth/AuthProvider";
import { exchangeOAuthCode } from "../api/authApi";
import { consumeReturnPath } from "../utils/returnPath";

export function OAuthCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { signIn } = useAuth();
  const exchanged = useRef(false);
  useEffect(() => {
    if (exchanged.current) return;
    exchanged.current = true;
    const code = params.get("code");
    if (!code) {
      navigate("/login", { replace: true });
      return;
    }
    exchangeOAuthCode(code)
      .then((result: AuthResponse) => {
        signIn(result);
        const returnTo = result.onboardingRequired ? null : consumeReturnPath();
        navigate(
          result.onboardingRequired ? "/onboarding" : (returnTo ?? "/play"),
          { replace: true },
        );
      })
      .catch((error: unknown) => {
        consumeReturnPath();
        notifications.show({
          title: "Google sign in failed",
          message:
            error instanceof ApiError ? error.message : "Please try again.",
          color: "red",
        });
        navigate("/login", { replace: true });
      });
  }, [navigate, params, signIn]);
  return (
    <Center mih="100vh">
      <Stack align="center">
        <Loader color="green" />
        <Text c="dimmed">Finishing sign in…</Text>
      </Stack>
    </Center>
  );
}
