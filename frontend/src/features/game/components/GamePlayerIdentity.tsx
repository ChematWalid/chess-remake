import { Avatar, Group, Stack, Text } from "@mantine/core";
import "./GamePlayerIdentity.css";

interface GamePlayerIdentityProps {
  displayName: string;
  username: string;
  avatarUrl: string | null;
  side: "White" | "Black";
  isActive?: boolean;
  showTurnIndicator?: boolean;
}

export function GamePlayerIdentity({
  displayName,
  username,
  avatarUrl,
  side,
  isActive = false,
  showTurnIndicator = false,
}: GamePlayerIdentityProps) {
  return (
    <Group
      className={`game-player-identity${isActive && showTurnIndicator ? " game-player-identity--active" : ""}`}
      gap="sm"
      wrap="nowrap"
      aria-label={`${displayName}, ${side}${isActive ? ", to move" : ""}`}
    >
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
        {isActive && showTurnIndicator && (
          <Text
            size="xs"
            fw={700}
            className="game-player-identity-turn"
            role="status"
            aria-live="polite"
          >
            To move
          </Text>
        )}
      </Stack>
    </Group>
  );
}
