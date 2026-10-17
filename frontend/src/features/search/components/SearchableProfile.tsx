import {
  Avatar,
  Badge,
  Button,
  Group,
  Paper,
  Stack,
  Text,
} from "@mantine/core";
import { IconChessKnight, IconUserPlus } from "@tabler/icons-react";
import { Link } from "react-router-dom";
import { notifications } from "@mantine/notifications";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { SearchResult } from "../../../shared/api/types";
import { sendFriendRequest } from "../../social/api/socialApi";

interface SearchableProfileProps {
  profile: SearchResult;
  isFriend: boolean;
  isRequested: boolean;
  relationshipLoading: boolean;
}

export function SearchableProfile({
  profile,
  isFriend,
  isRequested,
  relationshipLoading,
}: SearchableProfileProps) {
  const cache = useQueryClient();
  const requestFriendship = useMutation({
    mutationFn: () => sendFriendRequest(profile.username),
    onSuccess: () => {
      void cache.invalidateQueries({
        queryKey: ["friend-requests", "outgoing"],
      });
      notifications.show({
        title: "Request sent",
        message: `You can challenge ${profile.displayName} after they accept.`,
        color: "green",
      });
    },
    onError: (error: Error) =>
      notifications.show({
        title: "Could not send request",
        message: error.message,
        color: "red",
      }),
  });

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
        {isFriend ? (
          <Group gap="xs" wrap="nowrap">
            <Badge color="green" variant="light">
              Friends
            </Badge>
            <Button
              component={Link}
              to={`/play?opponent=${encodeURIComponent(profile.username)}`}
              leftSection={<IconChessKnight size={16} />}
              variant="light"
            >
              Challenge
            </Button>
          </Group>
        ) : (
          <Button
            leftSection={!isRequested && <IconUserPlus size={16} />}
            variant={isRequested ? "light" : "default"}
            disabled={isRequested || relationshipLoading}
            loading={requestFriendship.isPending || relationshipLoading}
            onClick={() => requestFriendship.mutate()}
          >
            {isRequested
              ? "Requested"
              : relationshipLoading
                ? "Checking"
                : "Add friend"}
          </Button>
        )}
      </Group>
      <Text size="xs" c="dimmed" mt="md">
        {profile.totalGames} games · {profile.wins} wins
      </Text>
    </Paper>
  );
}
