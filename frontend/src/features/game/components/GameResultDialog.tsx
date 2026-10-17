import {
  Avatar,
  Badge,
  Button,
  Group,
  Modal,
  Stack,
  Text,
} from "@mantine/core";
import "./GameResultDialog.css";

export type GameSide = "white" | "black";

export interface ResultPlayer {
  displayName: string;
  username: string;
  avatarUrl: string | null;
}

interface GameResultDialogProps {
  opened: boolean;
  winnerSide: GameSide | null;
  viewerSide: GameSide;
  reason: string;
  whitePlayer: ResultPlayer;
  blackPlayer: ResultPlayer;
  onClose: () => void;
}

interface ResultPlayerCardProps {
  player: ResultPlayer;
  side: "White" | "Black";
  result: "Winner" | "Loser" | "Draw";
}

export function GameResultDialog({
  opened,
  winnerSide,
  viewerSide,
  reason,
  whitePlayer,
  blackPlayer,
  onClose,
}: GameResultDialogProps) {
  const isDraw = winnerSide === null;
  const viewerWon = !isDraw && winnerSide === viewerSide;
  const winner = winnerSide === "white" ? whitePlayer : blackPlayer;
  const loser = winnerSide === "white" ? blackPlayer : whitePlayer;
  const winnerLabel = winnerSide === "white" ? "White" : "Black";
  const loserLabel = winnerSide === "white" ? "Black" : "White";

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={isDraw ? "Game drawn" : viewerWon ? "You won" : "You lost"}
      centered
      size="sm"
    >
      <Stack className="game-result-dialog" gap="lg">
        <Text ta="center" c="dimmed">
          {isDraw
            ? `Draw by ${reason}.`
            : `${winner.displayName} won by ${reason}.`}
        </Text>
        {isDraw ? (
          <Group justify="center" gap="xl">
            <ResultPlayerCard player={whitePlayer} side="White" result="Draw" />
            <ResultPlayerCard player={blackPlayer} side="Black" result="Draw" />
          </Group>
        ) : (
          <Stack gap="sm">
            <ResultPlayerCard
              player={winner}
              side={winnerLabel}
              result="Winner"
            />
            <ResultPlayerCard player={loser} side={loserLabel} result="Loser" />
          </Stack>
        )}
        <Button onClick={onClose} fullWidth>
          Continue
        </Button>
      </Stack>
    </Modal>
  );
}

function ResultPlayerCard({ player, side, result }: ResultPlayerCardProps) {
  return (
    <Group className="game-result-player" justify="space-between" wrap="nowrap">
      <Group gap="sm" wrap="nowrap" miw={0}>
        <Avatar
          src={player.avatarUrl}
          alt={`${player.displayName} avatar`}
          radius="xl"
        >
          {player.displayName.slice(0, 1).toUpperCase()}
        </Avatar>
        <Stack gap={0} miw={0}>
          <Text fw={650} truncate>
            {player.displayName}
          </Text>
          <Text size="xs" c="dimmed" truncate>
            @{player.username} · {side}
          </Text>
        </Stack>
      </Group>
      <Badge
        color={
          result === "Winner" ? "green" : result === "Loser" ? "red" : "gray"
        }
      >
        {result}
      </Badge>
    </Group>
  );
}
