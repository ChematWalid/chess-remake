import {
  Button,
  Group,
  Select,
  Stack,
  Switch,
  Tabs,
  Text,
} from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { notifications } from "@mantine/notifications";
import { defaultSettings, type UserSettings } from "../../../shared/api/types";
import { ErrorState, LoadingState } from "../../../shared/components/PageState";
import {
  getMyProfile,
  getSettings,
  updateMyProfile,
  updateSettings,
} from "../../user/api/userApi";

export function SettingsPage() {
  const [draft, setDraft] = useState<UserSettings>(defaultSettings);
  const [category, setCategory] = useState<string | null>("appearance");
  const cache = useQueryClient();
  const settings = useQuery({ queryKey: ["settings"], queryFn: getSettings });
  const profile = useQuery({
    queryKey: ["profile", "me"],
    queryFn: getMyProfile,
  });
  useEffect(() => {
    if (settings.data) setDraft(settings.data);
  }, [settings.data]);
  const save = useMutation({
    mutationFn: updateSettings,
    onSuccess: (value) => {
      cache.setQueryData(["settings"], value);
      setDraft(value);
      notifications.show({
        title: "Settings saved",
        message: "Your preferences sync with your account.",
        color: "green",
      });
    },
    onError: (error: Error) =>
      notifications.show({
        title: "Could not save settings",
        message: error.message,
        color: "red",
      }),
  });
  const saveProfile = useMutation({
    mutationFn: (searchable: boolean) =>
      updateMyProfile({
        username: profile.data?.username ?? "",
        displayName: profile.data?.displayName ?? "",
        bio: profile.data?.bio ?? "",
        searchable,
      }),
    onSuccess: (value) => {
      cache.setQueryData(["profile", "me"], value);
      notifications.show({
        title: "Privacy setting saved",
        message: "Search visibility updated.",
        color: "green",
      });
    },
  });
  function set<K extends keyof UserSettings>(key: K, value: UserSettings[K]) {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  if (settings.isLoading || profile.isLoading)
    return <LoadingState label="Loading your preferences" />;
  if (settings.isError || profile.isError)
    return (
      <ErrorState
        message={
          settings.error?.message ??
          profile.error?.message ??
          "Settings could not be loaded"
        }
        onRetry={() => {
          void settings.refetch();
          void profile.refetch();
        }}
      />
    );
  return (
    <div>
      <header className="page-heading">
        <div>
          <h1>Settings</h1>
          <p>Preferences saved to your account and synced across devices.</p>
        </div>
        <Button onClick={() => save.mutate(draft)} loading={save.isPending}>
          Save changes
        </Button>
      </header>
      <Tabs
        value={category}
        onChange={setCategory}
        orientation="vertical"
        variant="pills"
        keepMounted={false}
        className="settings-tabs"
      >
        <Tabs.List>
          <Tabs.Tab value="appearance">Appearance</Tabs.Tab>
          <Tabs.Tab value="game">Game</Tabs.Tab>
          <Tabs.Tab value="notifications">Notifications</Tabs.Tab>
          <Tabs.Tab value="privacy">Privacy</Tabs.Tab>
        </Tabs.List>
        <section className="panel settings-panel">
          <Tabs.Panel value="appearance">
            <Stack>
              <Text fw={700} size="lg">
                Appearance
              </Text>
              <Select
                label="Theme"
                data={[
                  { value: "DARK", label: "Dark" },
                  { value: "LIGHT", label: "Light" },
                  { value: "SYSTEM", label: "Use device setting" },
                ]}
                value={draft.themePreference}
                onChange={(value) =>
                  value &&
                  set(
                    "themePreference",
                    value as UserSettings["themePreference"],
                  )
                }
              />
              <Select
                label="Board colors"
                data={[
                  { value: "FOREST", label: "Forest" },
                  { value: "WALNUT", label: "Walnut" },
                  { value: "MARBLE", label: "Marble" },
                ]}
                value={draft.boardPalette}
                onChange={(value) =>
                  value &&
                  set("boardPalette", value as UserSettings["boardPalette"])
                }
              />
              <Select
                label="Board orientation"
                data={[
                  { value: "AUTO", label: "Follow my color" },
                  { value: "WHITE", label: "Always white" },
                  { value: "BLACK", label: "Always black" },
                ]}
                value={draft.boardOrientation}
                onChange={(value) =>
                  value &&
                  set(
                    "boardOrientation",
                    value as UserSettings["boardOrientation"],
                  )
                }
              />
              <Select
                label="Motion"
                data={[
                  { value: "SYSTEM", label: "Use device setting" },
                  { value: "REDUCED", label: "Reduced motion" },
                  { value: "FULL", label: "Full motion" },
                ]}
                value={draft.motionPreference}
                onChange={(value) =>
                  value &&
                  set(
                    "motionPreference",
                    value as UserSettings["motionPreference"],
                  )
                }
              />
            </Stack>
          </Tabs.Panel>
          <Tabs.Panel value="game">
            <Stack>
              <Text fw={700} size="lg">
                Game preferences
              </Text>
              <Switch
                label="Show board coordinates"
                checked={draft.showCoordinates}
                onChange={(event) =>
                  set("showCoordinates", event.currentTarget.checked)
                }
              />
              <Switch
                label="Highlight legal moves"
                checked={draft.showLegalMoveHints}
                onChange={(event) =>
                  set("showLegalMoveHints", event.currentTarget.checked)
                }
              />
              <Switch
                label="Allow one premove"
                checked={draft.premovesEnabled}
                onChange={(event) =>
                  set("premovesEnabled", event.currentTarget.checked)
                }
              />
              <Switch
                label="Confirm moves before sending"
                checked={draft.moveConfirmation}
                onChange={(event) =>
                  set("moveConfirmation", event.currentTarget.checked)
                }
              />
              <Switch
                label="Game sounds"
                checked={draft.gameSoundsEnabled}
                onChange={(event) =>
                  set("gameSoundsEnabled", event.currentTarget.checked)
                }
              />
            </Stack>
          </Tabs.Panel>
          <Tabs.Panel value="notifications">
            <Stack>
              <Text fw={700} size="lg">
                Notifications
              </Text>
              <Switch
                label="In-app notifications"
                checked={draft.inAppNotificationsEnabled}
                onChange={(event) =>
                  set("inAppNotificationsEnabled", event.currentTarget.checked)
                }
              />
              <Switch
                label="Notification sounds"
                checked={draft.notificationSoundsEnabled}
                onChange={(event) =>
                  set("notificationSoundsEnabled", event.currentTarget.checked)
                }
              />
              <Text size="sm" c="dimmed">
                Email delivery and challenge expiry are managed by the service.
              </Text>
            </Stack>
          </Tabs.Panel>
          <Tabs.Panel value="privacy">
            <Stack>
              <Text fw={700} size="lg">
                Privacy
              </Text>
              <Switch
                label="Let players find me in search"
                checked={profile.data?.searchable ?? true}
                onChange={(event) =>
                  saveProfile.mutate(event.currentTarget.checked)
                }
              />
              <Text size="sm" c="dimmed">
                When disabled, your profile and games remain available through
                existing friend connections.
              </Text>
            </Stack>
          </Tabs.Panel>
        </section>
      </Tabs>
      <Group justify="flex-end" mt="lg">
        <Button onClick={() => save.mutate(draft)} loading={save.isPending}>
          Save changes
        </Button>
      </Group>
    </div>
  );
}
