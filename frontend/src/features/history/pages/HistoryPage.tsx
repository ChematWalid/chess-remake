import { Group, Pagination, Stack, Table, Text } from "@mantine/core";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "../../../shared/components/PageState";
import { getMyGames, getPlayerGames } from "../../game/api/gameApi";
import { useAuth } from "../../../shared/auth/AuthProvider";
import { GameHistoryRow } from "../components/GameHistoryRow";

export function HistoryPage() {
  const [params, setParams] = useSearchParams();
  const { user } = useAuth();
  const page = Math.max(0, Number(params.get("page") ?? 0));
  const playerId = params.get("player");
  const viewedPlayerId = playerId ?? user?.publicId ?? null;
  const history = useQuery({
    queryKey: ["my-history", playerId ?? user?.publicId, page],
    queryFn: () =>
      playerId ? getPlayerGames(playerId, page) : getMyGames(page),
  });
  return (
    <div>
      <header className="page-heading">
        <div>
          <h1>{playerId ? "Player game history" : "Your games"}</h1>
          <p>
            {playerId
              ? "Completed games for this player."
              : "Revisit every completed match."}
          </p>
        </div>
      </header>
      {history.isLoading ? (
        <LoadingState label="Loading game history" />
      ) : history.isError && !history.data ? (
        <ErrorState
          message={history.error.message}
          onRetry={() => void history.refetch()}
        />
      ) : history.data?.items.length ? (
        <Stack>
          <div className="panel" style={{ padding: 0, overflowX: "auto" }}>
            <Table highlightOnHover verticalSpacing="md" miw={620}>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>White</Table.Th>
                  <Table.Th>Black</Table.Th>
                  <Table.Th>Result</Table.Th>
                  <Table.Th>Moves</Table.Th>
                  <Table.Th>Date</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {history.data.items.map((game) => (
                  <GameHistoryRow
                    key={game.id}
                    game={game}
                    viewedPlayerId={viewedPlayerId}
                  />
                ))}
              </Table.Tbody>
            </Table>
          </div>
          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              {history.data.totalElements} games
            </Text>
            <Pagination
              total={Math.max(
                1,
                Math.ceil(history.data.totalElements / history.data.size),
              )}
              value={page + 1}
              onChange={(next) =>
                setParams((current) => {
                  current.set("page", String(next - 1));
                  return current;
                })
              }
              color="green"
            />
          </Group>
        </Stack>
      ) : (
        <EmptyState
          title="Your first game is waiting"
          description="Challenge a friend and your finished games will be saved here."
        />
      )}
    </div>
  );
}
