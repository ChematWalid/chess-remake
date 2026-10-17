import {
  Anchor,
  Button,
  Paper,
  PasswordInput,
  Stack,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { useForm } from "@mantine/form";
import { zodResolver } from "mantine-form-zod-resolver";
import { z } from "zod";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { useState } from "react";
import { IconChessKnight } from "@tabler/icons-react";
import { notifications } from "@mantine/notifications";
import { ApiError } from "../../../shared/api/client";
import type { AuthResponse } from "../../../shared/api/types";
import { useAuth } from "../../../shared/auth/AuthProvider";
import { login, register } from "../api/authApi";
import {
  consumeReturnPath,
  peekReturnPath,
  safeReturnPath,
  storeReturnPath,
} from "../utils/returnPath";
import "./AuthPage.css";

type AuthMode = "login" | "register";
const loginSchema = z.object({
  identifier: z.string().min(1, "Enter your username or email"),
  password: z.string().min(1, "Enter your password"),
});
const registerSchema = z.object({
  username: z
    .string()
    .min(3)
    .max(30)
    .regex(/^[a-zA-Z0-9_]+$/, "Use letters, numbers, and underscores"),
  email: z.string().email(),
  displayName: z.string().max(80),
  password: z.string().min(10).max(72),
});

export function AuthPage({ mode }: { mode: AuthMode }) {
  const isLogin = mode === "login";
  const { isAuthenticated, signIn } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const returnTo =
    safeReturnPath(searchParams.get("returnTo")) ?? peekReturnPath();
  const [submitting, setSubmitting] = useState(false);
  const form = useForm({
    initialValues: isLogin
      ? { identifier: "", password: "" }
      : { username: "", email: "", displayName: "", password: "" },
    validate: zodResolver(isLogin ? loginSchema : registerSchema),
  });

  if (isAuthenticated)
    return <Navigate to={isLogin ? "/" : (returnTo ?? "/play")} replace />;

  async function submit(values: typeof form.values) {
    setSubmitting(true);
    try {
      if (isLogin) {
        const result: AuthResponse = await login(
          values as { identifier: string; password: string },
        );
        signIn(result);
        const storedReturnTo = consumeReturnPath();
        const destination = returnTo ?? storedReturnTo ?? "/play";
        if (result.onboardingRequired) storeReturnPath(destination);
        navigate(result.onboardingRequired ? "/onboarding" : destination, {
          replace: true,
        });
      } else {
        storeReturnPath(returnTo);
        await register(
          values as {
            username: string;
            email: string;
            displayName: string;
            password: string;
          },
        );
        notifications.show({
          title: "Check your email",
          message:
            "We sent a verification link to finish creating your account.",
          color: "green",
        });
        navigate(
          returnTo
            ? `/login?returnTo=${encodeURIComponent(returnTo)}`
            : "/login",
        );
      }
    } catch (error) {
      notifications.show({
        title: "Could not continue",
        message:
          error instanceof ApiError ? error.message : "Please try again.",
        color: "red",
      });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-page">
      <Link to="/" className="brand auth-brand">
        <IconChessKnight size={28} />
        <span>rook</span>
      </Link>
      <Paper className="auth-card" p="xl" radius="lg" withBorder>
        <Title order={1}>
          {isLogin ? "Welcome back" : "Create your account"}
        </Title>
        <Text c="dimmed" mt={6} mb="xl">
          {isLogin
            ? "Sign in to play with your friends."
            : "A quiet place for good games."}
        </Text>
        <form onSubmit={form.onSubmit((values) => void submit(values))}>
          <Stack gap="md">
            {isLogin ? (
              <TextInput
                label="Username or email"
                autoComplete="username"
                autoFocus
                {...form.getInputProps("identifier")}
              />
            ) : (
              <>
                <TextInput
                  label="Username"
                  autoComplete="username"
                  autoFocus
                  {...form.getInputProps("username")}
                />
                <TextInput
                  label="Display name"
                  autoComplete="nickname"
                  {...form.getInputProps("displayName")}
                />
                <TextInput
                  label="Email"
                  type="email"
                  autoComplete="email"
                  {...form.getInputProps("email")}
                />
              </>
            )}
            <PasswordInput
              label="Password"
              autoComplete={isLogin ? "current-password" : "new-password"}
              {...form.getInputProps("password")}
            />
            {isLogin && (
              <Anchor
                component={Link}
                to="/forgot-password"
                size="sm"
                ta="right"
              >
                Forgot password?
              </Anchor>
            )}
            <Button type="submit" loading={submitting} fullWidth mt="sm">
              {isLogin ? "Sign in" : "Create account"}
            </Button>
            <Button
              component="a"
              href={`${import.meta.env.VITE_API_BASE_URL ?? ""}/oauth2/authorization/google`}
              onClick={() => storeReturnPath(returnTo)}
              variant="default"
              fullWidth
            >
              Continue with Google
            </Button>
          </Stack>
        </form>
        <Text c="dimmed" size="sm" ta="center" mt="xl">
          {isLogin ? "New to rook?" : "Already have an account?"}{" "}
          <Anchor
            component={Link}
            to={`${isLogin ? "/register" : "/login"}${returnTo ? `?returnTo=${encodeURIComponent(returnTo)}` : ""}`}
          >
            {isLogin ? "Create account" : "Sign in"}
          </Anchor>
        </Text>
      </Paper>
    </main>
  );
}
