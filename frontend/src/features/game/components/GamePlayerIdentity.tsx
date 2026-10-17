import { Avatar, Group, Stack, Text } from "@mantine/core";
import "./GamePlayerIdentity.css";

interface GamePlayerIdentityProps {
  displayName: string;
  username: string;
  avatarUrl: string | null;
  side: "White" | "Black";
}

export function GamePlayerIdentity({
  displayName,
  username,
  avatarUrl,
  side,
}: GamePlayerIdentityProps) {
  return (
    <Group className="game-player-identity" gap="sm" wrap="nowrap">
      <Avatar
        src={avatarUrl}
        alt={`${displayName} avatar`}
        radius="xl"
        size={42}
      >
        {displayName.slice(0, 1).toUpperCase()}
      </Avatar>
      <Stack gap={0} className="game-player-identity-copy">
        <Text fw={650} truncate>
          {displayName}
        </Text>
        <Text size="xs" c="dimmed" truncate>
          @{username} · {side}
        </Text>
      </Stack>
    </Group>
  );
}
