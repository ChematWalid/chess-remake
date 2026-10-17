import {
  Avatar,
  Badge,
  Button,
  Group,
  Stack,
  Text,
  Textarea,
  TextInput,
} from "@mantine/core";
import { useForm } from "@mantine/form";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { useEffect } from "react";
import { notifications } from "@mantine/notifications";
import { AvatarEditor } from "../components/AvatarEditor";
import { useAuth } from "../../../shared/auth/AuthProvider";
import { ErrorState, LoadingState } from "../../../shared/components/PageState";
import {
  getMyProfile,
  getProfile,
  updateMyProfile,
  uploadAvatar,
} from "../../user/api/userApi";
import {
  blockUser,
  getFriends,
  sendFriendRequest,
} from "../../social/api/socialApi";

export function ProfilePage() {
  const { username } = useParams();
  const { user } = useAuth();
  const isOwner =
    !username || username.toLowerCase() === user?.username.toLowerCase();
  const queryKey = isOwner ? ["profile", "me"] : ["profile", username];
  const profile = useQuery({
    queryKey,
    queryFn: () => (isOwner ? getMyProfile() : getProfile(username ?? "")),
  });
  const data = profile.data;
  const friends = useQuery({
    queryKey: ["friends"],
    queryFn: getFriends,
    enabled: !isOwner,
  });
  const form = useForm({
    initialValues: { username: "", displayName: "", bio: "", searchable: true },
  });
  useEffect(() => {
    if (profile.data)
      form.setValues({
        username: profile.data.username,
        displayName: profile.data.displayName,
        bio: profile.data.bio ?? "",
        searchable: profile.data.searchable ?? true,
      });
  }, [profile.data]);
  const cache = useQueryClient();
  const update = useMutation({
    mutationFn: updateMyProfile,
    onSuccess: (result) => {
      cache.setQueryData(queryKey, result);
      notifications.show({
        title: "Profile saved",
        message: "Your profile changes are up to date.",
        color: "green",
      });
    },
    onError: (error: Error) =>
      notifications.show({
        title: "Could not save profile",
        message: error.message,
        color: "red",
      }),
  });
  const avatar = useMutation({
    mutationFn: uploadAvatar,
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["profile"] });
      void cache.invalidateQueries({ queryKey: ["friends"] });
      void cache.invalidateQueries({ queryKey: ["profile-search"] });
      notifications.show({
        title: "Profile photo updated",
        message:
          "Your new photo is now visible on your profile and to other players.",
        color: "green",
      });
    },
    onError: (error: Error) =>
      notifications.show({
        title: "Could not update photo",
        message: error.message,
        color: "red",
      }),
  });
  const requestFriend = useMutation({
    mutationFn: () =>
      data
        ? sendFriendRequest(data.username)
        : Promise.reject(new Error("Profile is unavailable")),
    onSuccess: () =>
      notifications.show({
        title: "Request sent",
        message: "You can challenge each other after the request is accepted.",
        color: "green",
      }),
    onError: (error: Error) =>
      notifications.show({
        title: "Friend request failed",
        message: error.message,
        color: "red",
      }),
  });
  const block = useMutation({
    mutationFn: () =>
      data
        ? blockUser(data.publicId)
        : Promise.reject(new Error("Profile is unavailable")),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["friends"] });
      notifications.show({
        title: "Player blocked",
        message: "They can no longer send you friend requests or challenges.",
        color: "green",
      });
    },
    onError: (error: Error) =>
      notifications.show({
        title: "Could not block player",
        message: error.message,
        color: "red",
      }),
  });

  if (profile.isLoading) return <LoadingState label="Loading profile" />;
  if (profile.isError || !profile.data)
    return (
      <ErrorState
        message={profile.error?.message ?? "Profile not found"}
        onRetry={() => void profile.refetch()}
      />
    );
  if (!data) return <ErrorState message="Profile not found" />;
  const isFriend =
    friends.data?.some((friend) => friend.publicUserId === data.publicId) ??
    false;
  return (
    <div>
      <header className="page-heading">
        <div>
          <h1>{isOwner ? "Your profile" : "Player profile"}</h1>
          <p>Stats and profile details.</p>
        </div>
      </header>
      <Stack gap="lg">
        <section className="panel">
          <Group align="center" wrap="nowrap">
            {isOwner && (
              <AvatarEditor
                avatarUrl={data.avatarUrl}
                displayName={data.displayName}
                isSaving={avatar.isPending}
                onSave={(file) => avatar.mutate(file)}
              />
            )}
            {!isOwner && (
              <Avatar src={data.avatarUrl} size={76} radius="xl">
                {data.displayName.slice(0, 1)}
              </Avatar>
            )}
            <div>
              <Text fw={700} size="xl">
                {data.displayName}
              </Text>
              <Text c="dimmed">@{data.username}</Text>
            </div>
            {!isOwner && (
              <Group ml="auto">
                <Button
                  component={Link}
                  to={`/play?opponent=${encodeURIComponent(data.username)}`}
                  disabled={!isFriend}
                >
                  Challenge
                </Button>
                {!isFriend && (
                  <Button
                    variant="default"
                    loading={requestFriend.isPending}
                    onClick={() => requestFriend.mutate()}
                  >
                    Add friend
                  </Button>
                )}
                <Button
                  color="red"
                  variant="subtle"
                  loading={block.isPending}
                  onClick={() => block.mutate()}
                >
                  Block
                </Button>
              </Group>
            )}
          </Group>
          <Group mt="xl" gap="sm">
            <Badge variant="light">{data.totalGames} games</Badge>
            <Badge color="green" variant="light">
              {data.wins} wins
            </Badge>
            <Badge color="red" variant="light">
              {data.losses} losses
            </Badge>
            <Badge color="gray" variant="light">
              {data.draws} draws
            </Badge>
          </Group>
          {data.bio && <Text mt="lg">{data.bio}</Text>}
          {!isOwner && (
            <Button
              component={Link}
              to={`/history?player=${data.publicId}`}
              variant="subtle"
              mt="md"
            >
              View game history
            </Button>
          )}
        </section>
        {isOwner && (
          <section className="panel">
            <Text fw={700} size="lg" mb="md">
              Edit profile
            </Text>
            <form onSubmit={form.onSubmit((values) => update.mutate(values))}>
              <Stack>
                <TextInput
                  label="Username"
                  {...form.getInputProps("username")}
                />
                <TextInput
                  label="Display name"
                  {...form.getInputProps("displayName")}
                />
                <Textarea
                  label="About you"
                  maxLength={500}
                  autosize
                  minRows={3}
                  {...form.getInputProps("bio")}
                />
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    {...form.getInputProps("searchable", { type: "checkbox" })}
                  />
                  <span>Let other players find me in search</span>
                </label>
                <Group justify="flex-end">
                  <Button type="submit" loading={update.isPending}>
                    Save profile
                  </Button>
                </Group>
              </Stack>
            </form>
          </section>
        )}
      </Stack>
    </div>
  );
}
