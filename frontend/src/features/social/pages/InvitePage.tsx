import { Button, Paper, Stack, Text, Title } from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { notifications } from "@mantine/notifications";
import { redeemFriendInvite } from "../api/socialApi";
import { useAuth } from "../../../shared/auth/AuthProvider";

export function InvitePage() {
  const { token = "" } = useParams();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const redeem = useMutation({
    mutationFn: () => redeemFriendInvite(token),
    onSuccess: () => {
      notifications.show({
        title: "You are friends",
        message: "The invite was accepted.",
        color: "green",
      });
      navigate("/friends", { replace: true });
    },
    onError: (error: Error) =>
      notifications.show({
        title: "Invite unavailable",
        message: error.message,
        color: "red",
      }),
  });
  return (
    <main className="auth-page">
      <Paper className="auth-card" p="xl" radius="lg" withBorder>
        <Stack>
          <Title order={1}>Friend invitation</Title>
          <Text c="dimmed">
            Join your friend’s chess circle and challenge them to a game.
          </Text>
          {isAuthenticated ? (
            <Button loading={redeem.isPending} onClick={() => redeem.mutate()}>
              Accept invitation
            </Button>
          ) : (
            <Button
              component={Link}
              to={`/login?returnTo=${encodeURIComponent(`/friend-invite/${token}`)}`}
            >
              Sign in to accept
            </Button>
          )}
        </Stack>
      </Paper>
    </main>
  );
}
