import { Avatar, Button, Group, Paper, Stack, Text } from "@mantine/core";
import { IconUserPlus } from "@tabler/icons-react";
import { Link } from "react-router-dom";
import { useState } from "react";
import { notifications } from "@mantine/notifications";
import type { SearchResult } from "../../../shared/api/types";
import { sendFriendRequest } from "../../social/api/socialApi";

export function SearchableProfile({ profile }: { profile: SearchResult }) {
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  async function requestFriendship() {
    setBusy(true);
    try {
      await sendFriendRequest(profile.username);
      setSent(true);
      notifications.show({
        title: "Request sent",
        message: `You can challenge ${profile.displayName} after they accept.`,
        color: "green",
      });
    } catch (error) {
      notifications.show({
        title: "Could not send request",
        message: error instanceof Error ? error.message : "Try again.",
        color: "red",
      });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Paper className="search-profile" withBorder p="md" radius="md">
      <Group wrap="nowrap" align="center">
        <Avatar src={profile.avatarUrl} radius="xl" size={46} color="green">
          {profile.displayName.slice(0, 1).toUpperCase()}
        </Avatar>
        <Stack gap={2} style={{ minWidth: 0, flex: 1 }}>
          <Text
            component={Link}
            to={`/profile/${encodeURIComponent(profile.username)}`}
            fw={650}
            c="var(--text)"
            truncate
          >
            {profile.displayName}
          </Text>
          <Text size="sm" c="dimmed" truncate>
            @{profile.username}
          </Text>
        </Stack>
        <Button
          leftSection={<IconUserPlus size={16} />}
          variant={sent ? "light" : "default"}
          disabled={sent}
          loading={busy}
          onClick={() => void requestFriendship()}
        >
          {sent ? "Requested" : "Add"}
        </Button>
      </Group>
      <Text size="xs" c="dimmed" mt="md">
        {profile.totalGames} games · {profile.wins} wins
      </Text>
    </Paper>
  );
}
