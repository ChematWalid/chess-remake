import { Button, Paper, Stack, Text, TextInput, Title } from "@mantine/core";
import { useForm } from "@mantine/form";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { notifications } from "@mantine/notifications";
import { useAuth } from "../../../shared/auth/AuthProvider";
import { completeOAuthOnboarding } from "../api/authApi";
import { consumeReturnPath } from "../utils/returnPath";

export function OnboardingPage() {
  const [busy, setBusy] = useState(false);
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const form = useForm({
    initialValues: { username: "" },
    validate: {
      username: (value) =>
        /^[a-zA-Z0-9_]{3,30}$/.test(value)
          ? null
          : "Use 3–30 letters, numbers, or underscores.",
    },
  });
  async function submit({ username }: { username: string }) {
    setBusy(true);
    try {
      const auth = await completeOAuthOnboarding(username);
      signIn(auth);
      navigate(consumeReturnPath() ?? "/play", { replace: true });
    } catch (error) {
      notifications.show({
        title: "Username unavailable",
        message:
          error instanceof Error ? error.message : "Choose another username.",
        color: "red",
      });
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="auth-page">
      <Paper className="auth-card" p="xl" radius="lg" withBorder>
        <Title order={1}>Choose your username</Title>
        <Text c="dimmed" mt="sm">
          This will be your unique name across the chess community.
        </Text>
        <form onSubmit={form.onSubmit((values) => void submit(values))}>
          <Stack mt="lg">
            <TextInput
              label="Username"
              autoFocus
              autoComplete="username"
              {...form.getInputProps("username")}
            />
            <Button type="submit" loading={busy}>
              Continue
            </Button>
          </Stack>
        </form>
      </Paper>
    </main>
  );
}
