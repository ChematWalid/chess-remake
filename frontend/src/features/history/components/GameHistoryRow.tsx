import { Anchor, Badge, Table, Text } from "@mantine/core";
import { Link } from "react-router-dom";
import type { GameSummary } from "../../../shared/api/types";
import { getHistoryResult } from "../utils/getHistoryResult";

interface GameHistoryRowProps {
  game: GameSummary;
  viewedPlayerId: string | null;
}

export function GameHistoryRow({ game, viewedPlayerId }: GameHistoryRowProps) {
  const result = getHistoryResult(game, viewedPlayerId);

  return (
    <Table.Tr>
      <Table.Td>{game.whiteUsername}</Table.Td>
      <Table.Td>{game.blackUsername}</Table.Td>
      <Table.Td>
        <Badge color={result.color} variant="light">
          {result.label}
        </Badge>
      </Table.Td>
      <Table.Td>{game.totalMoves}</Table.Td>
      <Table.Td>
        <Text size="sm" c="dimmed">
          {new Date(game.playedAt).toLocaleDateString()}
        </Text>
      </Table.Td>
      <Table.Td>
        <Anchor component={Link} to={`/games/${game.id}`}>
          Review
        </Anchor>
      </Table.Td>
    </Table.Tr>
  );
}
