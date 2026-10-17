import {
  Button,
  Group,
  NumberInput,
  Select,
  SimpleGrid,
  Stack,
  Text,
} from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { notifications } from "@mantine/notifications";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "../../../shared/components/PageState";
import { getFriends } from "../../social/api/socialApi";
import {
  acceptChallenge,
  cancelChallenge,
  declineChallenge,
  getIncomingChallenges,
  getOutgoingChallenges,
  sendGameChallenge,
  type ChallengeRequest,
} from "../../challenge/api/challengeApi";
import { useRealtime } from "../../realtime/RealtimeContext";

const timeOptions = [
  { value: "unlimited", label: "Untimed" },
  { value: "60+0", label: "1 min · Bullet" },
  { value: "180+0", label: "3 min · Blitz" },
  { value: "180+2", label: "3 min + 2 sec" },
  { value: "300+0", label: "5 min · Blitz" },
  { value: "600+0", label: "10 min · Rapid" },
  { value: "900+10", label: "15 min + 10 sec" },
  { value: "custom", label: "Custom" },
];

export function PlayPage() {
  const [params] = useSearchParams();
  const [opponent, setOpponent] = useState<string | null>(
    params.get("opponent"),
  );
  const [time, setTime] = useState<string | null>("unlimited");
  const [customMinutes, setCustomMinutes] = useState(5);
  const [increment, setIncrement] = useState(0);
  const [side, setSide] = useState("RANDOM");
  const { connected, connectionStatus, connectionError, publish } =
    useRealtime();
  const cache = useQueryClient();
  const friends = useQuery({ queryKey: ["friends"], queryFn: getFriends });
  const incoming = useQuery({
    queryKey: ["challenges", "incoming"],
    queryFn: getIncomingChallenges,
    refetchInterval: 15_000,
  });
  const outgoing = useQuery({
    queryKey: ["challenges", "outgoing"],
    queryFn: getOutgoingChallenges,
    refetchInterval: 15_000,
  });
  const activeGames = useMemo(
    () =>
      [...(incoming.data?.items ?? []), ...(outgoing.data?.items ?? [])].filter(
        (challenge) => challenge.status === "ACCEPTED",
      ),
    [incoming.data, outgoing.data],
  );
  const friendOptions = useMemo(
    () =>
      (friends.data ?? []).map((friend) => ({
        value: friend.username,
        label: `${friend.displayName} · @${friend.username}`,
      })),
    [friends.data],
  );
  const accept = useMutation({
    mutationFn: (challengeId: number) => acceptChallenge(publish, challengeId),
    onSuccess: () => void cache.invalidateQueries({ queryKey: ["challenges"] }),
  });
  const decline = useMutation({
    mutationFn: (challengeId: number) => declineChallenge(publish, challengeId),
    onSuccess: () => void cache.invalidateQueries({ queryKey: ["challenges"] }),
  });
  const cancel = useMutation({
    mutationFn: async (challengeId: number) => {
      cancelChallenge(publish, challengeId);
    },
    onSuccess: () => void cache.invalidateQueries({ queryKey: ["challenges"] }),
  });

  function sendChallenge() {
    if (!connected) {
      notifications.show({
        title: "Connecting",
        message:
          "The secure challenge channel is reconnecting. Try again in a moment.",
        color: "yellow",
      });
      return;
    }
    if (!opponent) {
      notifications.show({
        title: "Choose a friend",
        message: "Select a friend before sending a challenge.",
        color: "yellow",
      });
      return;
    }
    const request: ChallengeRequest = {
      toUsername: opponent,
      challengerSide: side as ChallengeRequest["challengerSide"],
    };
    if (time === "custom") {
      request.initialTimeSeconds = Math.round(customMinutes * 60);
      request.incrementSeconds = increment;
    } else if (time && time !== "unlimited") {
      const [initial = "0", added = "0"] = time.split("+");
      request.initialTimeSeconds = Number(initial);
      request.incrementSeconds = Number(added);
    }
    try {
      sendGameChallenge(publish, request);
      notifications.show({
        title: "Challenge sent",
        message: `Waiting for ${opponent} to respond.`,
        color: "green",
      });
    } catch (error) {
      notifications.show({
        title: "Could not send challenge",
        message: error instanceof Error ? error.message : "Please retry.",
        color: "red",
      });
    }
  }

  return (
    <div>
      <header className="page-heading">
        <div>
          <h1>Play chess</h1>
          <p>Pick a friend and set the board.</p>
        </div>
        <Text
          size="sm"
          c={
            connected
              ? "green"
              : connectionStatus === "reconnecting" ||
                  connectionStatus === "disconnected"
                ? "red"
                : "dimmed"
          }
        >
          {connected
            ? "● Connected"
            : connectionStatus === "reconnecting"
              ? "● Reconnecting"
              : connectionStatus === "disconnected"
                ? "● Disconnected"
                : connectionStatus === "idle"
                  ? "● Offline"
                  : "○ Connecting"}
        </Text>
        {connectionError && (
          <Text size="xs" c="red">
            {connectionError}
          </Text>
        )}
      </header>
      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
        <section className="panel">
          <Text fw={700} size="lg">
            New challenge
          </Text>
          <Text c="dimmed" size="sm" mt={4} mb="lg">
            You can challenge accepted friends only.
          </Text>
          {friends.isLoading ? (
            <LoadingState label="Loading friends" />
          ) : friends.isError ? (
            <ErrorState
              message={friends.error.message}
              onRetry={() => void friends.refetch()}
            />
          ) : !friends.data?.length ? (
            <EmptyState
              title="Add a friend first"
              description="Search for a player, send a request, then challenge them here."
            />
          ) : (
            <Stack>
              <Select
                label="Friend"
                placeholder="Choose a player"
                searchable
                data={friendOptions}
                value={opponent}
                onChange={setOpponent}
              />
              <Select
                label="Time control"
                data={timeOptions}
                value={time}
                onChange={setTime}
                allowDeselect={false}
              />
              {time === "custom" && (
                <Group grow>
                  <NumberInput
                    label="Minutes"
                    min={0.1}
                    max={1440}
                    decimalScale={1}
                    value={customMinutes}
                    onChange={(value) => setCustomMinutes(Number(value) || 5)}
                  />
                  <NumberInput
                    label="Increment (seconds)"
                    min={0}
                    max={3600}
                    value={increment}
                    onChange={(value) => setIncrement(Number(value) || 0)}
                  />
                </Group>
              )}
              <Select
                label="Play as"
                data={[
                  { value: "RANDOM", label: "Random color" },
                  { value: "WHITE", label: "White" },
                  { value: "BLACK", label: "Black" },
                ]}
                value={side}
                onChange={(value) => setSide(value ?? "RANDOM")}
                allowDeselect={false}
              />
              <Button onClick={sendChallenge} mt="sm">
                {connected ? "Send challenge" : "Reconnect to send challenge"}
              </Button>
            </Stack>
          )}
        </section>
        <Stack>
          {activeGames.length > 0 && (
            <section className="panel">
              <Text fw={700} size="lg" mb="md">
                Active games
              </Text>
              <Stack>
                {activeGames.map((challenge) => (
                  <Group
                    key={challenge.challengeId}
                    justify="space-between"
                    wrap="nowrap"
                  >
                    <Text size="sm">
                      {challenge.challengerUsername} vs.{" "}
                      {challenge.challengedUsername}
                    </Text>
                    <Button
                      component={Link}
                      to={`/game/${challenge.challengeId}`}
                      size="xs"
                    >
                      Rejoin
                    </Button>
                  </Group>
                ))}
              </Stack>
            </section>
          )}
          <section className="panel">
            <Group justify="space-between" mb="md">
              <Text fw={700} size="lg">
                Incoming
              </Text>
              <Text size="sm" c="dimmed">
                {incoming.data?.totalElements ?? 0}
              </Text>
            </Group>
            {incoming.isLoading ? (
              <LoadingState />
            ) : incoming.data?.items.filter((item) => item.status === "PENDING")
                .length ? (
              <Stack>
                {incoming.data.items
                  .filter((item) => item.status === "PENDING")
                  .map((challenge) => (
                    <Group
                      key={challenge.challengeId}
                      justify="space-between"
                      wrap="nowrap"
                      className="challenge-row"
                    >
                      <div>
                        <Text fw={600}>{challenge.challengerUsername}</Text>
                        <Text size="sm" c="dimmed">
                          {challenge.initialTimeSeconds
                            ? `${Math.floor(challenge.initialTimeSeconds / 60)}+${challenge.incrementSeconds ?? 0}`
                            : "Untimed"}{" "}
                          · {challenge.challengerSide.toLowerCase()}
                        </Text>
                      </div>
                      <Group gap="xs">
                        <Button
                          size="xs"
                          onClick={() => accept.mutate(challenge.challengeId)}
                        >
                          Accept
                        </Button>
                        <Button
                          size="xs"
                          variant="default"
                          onClick={() => decline.mutate(challenge.challengeId)}
                        >
                          Decline
                        </Button>
                      </Group>
                    </Group>
                  ))}
              </Stack>
            ) : (
              <Text size="sm" c="dimmed">
                No open challenges.
              </Text>
            )}
          </section>
          <section className="panel">
            <Text fw={700} size="lg" mb="md">
              Sent
            </Text>
            {outgoing.isLoading ? (
              <LoadingState />
            ) : outgoing.data?.items.filter((item) => item.status === "PENDING")
                .length ? (
              <Stack>
                {outgoing.data.items
                  .filter((item) => item.status === "PENDING")
                  .map((challenge) => (
                    <Group key={challenge.challengeId} justify="space-between">
                      <Text>Waiting for {challenge.challengedUsername}</Text>
                      <Button
                        variant="subtle"
                        color="gray"
                        onClick={() => cancel.mutate(challenge.challengeId)}
                      >
                        Cancel
                      </Button>
                    </Group>
                  ))}
              </Stack>
            ) : (
              <Text size="sm" c="dimmed">
                No pending outgoing challenges.
              </Text>
            )}
          </section>
        </Stack>
      </SimpleGrid>
    </div>
  );
}
