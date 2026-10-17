import { Avatar, Button, Group, Stack, Tabs, Text } from "@mantine/core";
import { IconCopy, IconUserPlus } from "@tabler/icons-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useState } from "react";
import { notifications } from "@mantine/notifications";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "../../../shared/components/PageState";
import {
  acceptFriendRequest,
  createFriendInvite,
  declineFriendRequest,
  getBlockedUsers,
  getFriends,
  getIncomingRequests,
  getOutgoingRequests,
  removeFriend,
  unblockUser,
} from "../api/socialApi";

export function FriendsPage() {
  const [tab, setTab] = useState<string | null>("friends");
  const cache = useQueryClient();
  const friends = useQuery({ queryKey: ["friends"], queryFn: getFriends });
  const incoming = useQuery({
    queryKey: ["friend-requests", "incoming"],
    queryFn: getIncomingRequests,
  });
  const outgoing = useQuery({
    queryKey: ["friend-requests", "outgoing"],
    queryFn: getOutgoingRequests,
  });
  const blocked = useQuery({
    queryKey: ["blocked-users"],
    queryFn: getBlockedUsers,
  });
  const accept = useMutation({
    mutationFn: acceptFriendRequest,
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["friends"] });
      void cache.invalidateQueries({ queryKey: ["friend-requests"] });
    },
  });
  const decline = useMutation({
    mutationFn: declineFriendRequest,
    onSuccess: () =>
      void cache.invalidateQueries({ queryKey: ["friend-requests"] }),
  });
  const remove = useMutation({
    mutationFn: removeFriend,
    onSuccess: () => void cache.invalidateQueries({ queryKey: ["friends"] }),
  });
  const unblock = useMutation({
    mutationFn: unblockUser,
    onSuccess: () =>
      void cache.invalidateQueries({ queryKey: ["blocked-users"] }),
  });
  const invite = useMutation({
    mutationFn: createFriendInvite,
    onSuccess: async (result) => {
      const url = result.path.startsWith("http")
        ? result.path
        : `${window.location.origin}${result.path}`;
      await navigator.clipboard.writeText(url);
      notifications.show({
        title: "Invite copied",
        message: "Share the one-time link with a friend.",
        color: "green",
      });
    },
  });

  return (
    <div>
      <header className="page-heading">
        <div>
          <h1>Friends</h1>
          <p>Your chess circle and incoming requests.</p>
        </div>
        <Button
          leftSection={<IconCopy size={16} />}
          variant="default"
          loading={invite.isPending}
          onClick={() => invite.mutate()}
        >
          Invite a friend
        </Button>
      </header>
      <Tabs value={tab} onChange={setTab} keepMounted={false}>
        <Tabs.List>
          <Tabs.Tab value="friends">Friends</Tabs.Tab>
          <Tabs.Tab value="incoming">
            Incoming {incoming.data?.length ? `(${incoming.data.length})` : ""}
          </Tabs.Tab>
          <Tabs.Tab value="outgoing">Sent</Tabs.Tab>
          <Tabs.Tab value="blocked">Blocked</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="friends" pt="lg">
          {friends.isLoading ? (
            <LoadingState />
          ) : friends.isError ? (
            <ErrorState
              message={friends.error.message}
              onRetry={() => void friends.refetch()}
            />
          ) : friends.data?.length ? (
            <Stack>
              {friends.data.map((friend) => (
                <Group
                  key={friend.publicUserId}
                  className="panel"
                  justify="space-between"
                  wrap="nowrap"
                >
                  <Group wrap="nowrap">
                    <Avatar src={friend.avatarUrl} radius="xl">
                      {friend.displayName[0]}
                    </Avatar>
                    <Stack gap={2}>
                      <Link
                        to={`/profile/${encodeURIComponent(friend.username)}`}
                      >
                        {friend.displayName}
                      </Link>
                      <Text size="sm" c="dimmed">
                        @{friend.username} ·{" "}
                        {friend.presence.toLowerCase().replace("_", " ")}
                      </Text>
                    </Stack>
                  </Group>
                  <Group>
                    <Button
                      component={Link}
                      to={`/play?opponent=${encodeURIComponent(friend.username)}`}
                      variant="light"
                    >
                      Challenge
                    </Button>
                    <Button
                      variant="subtle"
                      color="gray"
                      onClick={() => remove.mutate(friend.publicUserId)}
                    >
                      Remove
                    </Button>
                  </Group>
                </Group>
              ))}
            </Stack>
          ) : (
            <EmptyState
              title="No friends yet"
              description="Search for players or invite someone to start your chess circle."
            />
          )}
        </Tabs.Panel>
        <Tabs.Panel value="incoming" pt="lg">
          {incoming.isLoading ? (
            <LoadingState />
          ) : incoming.isError ? (
            <ErrorState
              message={incoming.error.message}
              onRetry={() => void incoming.refetch()}
            />
          ) : incoming.data?.length ? (
            <Stack>
              {incoming.data.map((request) => (
                <Group
                  key={request.requestId}
                  className="panel"
                  justify="space-between"
                >
                  <Group>
                    <Avatar src={request.avatarUrl} radius="xl">
                      {request.displayName[0]}
                    </Avatar>
                    <Stack gap={2}>
                      <Text fw={600}>{request.displayName}</Text>
                      <Text size="sm" c="dimmed">
                        @{request.username}
                      </Text>
                    </Stack>
                  </Group>
                  <Group>
                    <Button
                      loading={accept.isPending}
                      onClick={() => accept.mutate(request.requestId)}
                    >
                      Accept
                    </Button>
                    <Button
                      variant="default"
                      onClick={() => decline.mutate(request.requestId)}
                    >
                      Decline
                    </Button>
                  </Group>
                </Group>
              ))}
            </Stack>
          ) : (
            <EmptyState
              title="All caught up"
              description="New friend requests will appear here."
            />
          )}
        </Tabs.Panel>
        <Tabs.Panel value="outgoing" pt="lg">
          {outgoing.isLoading ? (
            <LoadingState />
          ) : outgoing.isError ? (
            <ErrorState
              message={outgoing.error.message}
              onRetry={() => void outgoing.refetch()}
            />
          ) : outgoing.data?.length ? (
            <Stack>
              {outgoing.data.map((request) => (
                <Group
                  key={request.requestId}
                  className="panel"
                  justify="space-between"
                >
                  <Text>
                    {request.displayName}{" "}
                    <Text span c="dimmed">
                      @{request.username} · {request.status.toLowerCase()}
                    </Text>
                  </Text>
                  <IconUserPlus size={18} />
                </Group>
              ))}
            </Stack>
          ) : (
            <EmptyState
              title="No sent requests"
              description="Find a player to add them as a friend."
            />
          )}
        </Tabs.Panel>
        <Tabs.Panel value="blocked" pt="lg">
          {blocked.isLoading ? (
            <LoadingState />
          ) : blocked.isError ? (
            <ErrorState
              message={blocked.error.message}
              onRetry={() => void blocked.refetch()}
            />
          ) : blocked.data?.length ? (
            <Stack>
              {blocked.data.map((person) => (
                <Group
                  key={person.publicUserId}
                  className="panel"
                  justify="space-between"
                >
                  <Group>
                    <Avatar src={person.avatarUrl} radius="xl">
                      {person.displayName[0]}
                    </Avatar>
                    <Stack gap={2}>
                      <Text fw={600}>{person.displayName}</Text>
                      <Text size="sm" c="dimmed">
                        @{person.username}
                      </Text>
                    </Stack>
                  </Group>
                  <Button
                    variant="default"
                    loading={unblock.isPending}
                    onClick={() => unblock.mutate(person.publicUserId)}
                  >
                    Unblock
                  </Button>
                </Group>
              ))}
            </Stack>
          ) : (
            <EmptyState
              title="No blocked players"
              description="Players you block will appear here."
            />
          )}
        </Tabs.Panel>
      </Tabs>
    </div>
  );
}
