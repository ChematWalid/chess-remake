import {
  Button,
  Paper,
  PasswordInput,
  Stack,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { useForm } from "@mantine/form";
import { z } from "zod";
import { useSearchParams, Link } from "react-router-dom";
import { useState } from "react";
import { notifications } from "@mantine/notifications";
import { ApiError } from "../../../shared/api/client";
import {
  requestPasswordReset,
  resetPassword,
  verifyEmail,
} from "../api/authApi";

type ActionMode = "forgot" | "reset" | "verify";

export function AuthActionPage({ mode }: { mode: ActionMode }) {
  const [search] = useSearchParams();
  const token = search.get("token") ?? "";
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);
  const schema =
    mode === "forgot"
      ? z.object({ email: z.string().email() })
      : z.object({ password: z.string().min(10).max(72) });
  const form = useForm({
    initialValues: mode === "forgot" ? { email: "" } : { password: "" },
    validate: (values) => {
      const parsed = schema.safeParse(values);
      return parsed.success
        ? {}
        : Object.fromEntries(
            parsed.error.issues.map((issue) => [
              String(issue.path[0]),
              issue.message,
            ]),
          );
    },
  });

  async function submit(values: typeof form.values) {
    setBusy(true);
    try {
      if (mode === "forgot")
        await requestPasswordReset((values as { email: string }).email);
      else if (mode === "reset")
        await resetPassword(token, (values as { password: string }).password);
      else await verifyEmail(token);
      setDone(true);
    } catch (error) {
      notifications.show({
        title: "Could not complete request",
        message:
          error instanceof ApiError ? error.message : "Please try again.",
        color: "red",
      });
    } finally {
      setBusy(false);
    }
  }

  const title =
    mode === "forgot"
      ? "Reset your password"
      : mode === "reset"
        ? "Choose a new password"
        : "Verify your email";
  const message = done
    ? mode === "forgot"
      ? "If an account matches that email, a reset link is on its way."
      : mode === "reset"
        ? "Password updated. You can sign in now."
        : "Your email is verified. You can sign in now."
    : null;
  return (
    <main className="auth-page">
      <Paper className="auth-card" p="xl" radius="lg" withBorder>
        <Title order={1}>{title}</Title>
        {message ? (
          <Stack mt="md">
            <Text c="dimmed">{message}</Text>
            <Button component={Link} to="/login">
              Continue to sign in
            </Button>
          </Stack>
        ) : mode === "verify" ? (
          <Stack mt="md">
            <Text c="dimmed">Confirm the email address on your account.</Text>
            <Button
              loading={busy}
              disabled={!token}
              onClick={() => void submit({} as typeof form.values)}
            >
              Verify email
            </Button>
          </Stack>
        ) : (
          <form onSubmit={form.onSubmit((values) => void submit(values))}>
            <Stack mt="md">
              {mode === "forgot" ? (
                <TextInput
                  label="Email"
                  type="email"
                  autoComplete="email"
                  {...form.getInputProps("email")}
                />
              ) : (
                <PasswordInput
                  label="New password"
                  autoComplete="new-password"
                  {...form.getInputProps("password")}
                />
              )}
              <Button type="submit" loading={busy}>
                {mode === "forgot" ? "Send reset link" : "Update password"}
              </Button>
            </Stack>
          </form>
        )}
        <Text size="sm" ta="center" mt="xl">
          <Link to="/login">Back to sign in</Link>
        </Text>
      </Paper>
    </main>
  );
}
