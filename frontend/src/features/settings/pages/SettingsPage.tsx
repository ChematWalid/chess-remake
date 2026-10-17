import { Button, SimpleGrid, Select, Stack, Tabs, Text } from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { notifications } from "@mantine/notifications";
import {
  IconBellRinging,
  IconChess,
  IconLock,
  IconPalette,
} from "@tabler/icons-react";
import { defaultSettings, type UserSettings } from "../../../shared/api/types";
import { ErrorState, LoadingState } from "../../../shared/components/PageState";
import {
  getMyProfile,
  getSettings,
  updateMyProfile,
  updateSettings,
} from "../../user/api/userApi";
import { SettingToggle } from "../components/SettingToggle";
import { DraftStatus } from "../../../shared/drafts/DraftStatus";
import { useSyncedDraft } from "../../../shared/drafts/useSyncedDraft";
import "./SettingsPage.css";

export function SettingsPage() {
  const [draft, setDraft] = useState<UserSettings>(defaultSettings);
  const settingsDraft = useSyncedDraft("settings-edit", defaultSettings);
  const [category, setCategory] = useState<string | null>("appearance");
  const cache = useQueryClient();
  const settings = useQuery({ queryKey: ["settings"], queryFn: getSettings });
  const profile = useQuery({
    queryKey: ["profile", "me"],
    queryFn: getMyProfile,
  });
  useEffect(() => {
    if (settings.data && settingsDraft.isReady)
      setDraft(settingsDraft.hasDraft ? settingsDraft.value : settings.data);
  }, [
    settings.data,
    settingsDraft.hasDraft,
    settingsDraft.isReady,
    settingsDraft.restoreVersion,
  ]);
  const save = useMutation({
    mutationFn: updateSettings,
    onSuccess: (value) => {
      cache.setQueryData(["settings"], value);
      setDraft(value);
      settingsDraft.clear();
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
    const next = { ...draft, [key]: value };
    setDraft(next);
    settingsDraft.update(next);
  }
  const hasUnsavedChanges =
    settings.data !== undefined &&
    JSON.stringify(draft) !== JSON.stringify(settings.data);

  if (settings.isLoading || profile.isLoading)
    return <LoadingState label="Loading your preferences" />;
  if (
    (settings.isError && !settings.data) ||
    (profile.isError && !profile.data)
  )
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
          <p>Make the board and the rest of the site work the way you like.</p>
        </div>
        <div className="settings-page__actions">
          <Text size="sm" c="dimmed">
            {hasUnsavedChanges ? "Unsaved changes" : "All changes saved"}
          </Text>
          {hasUnsavedChanges && (
            <Button
              variant="default"
              onClick={() => {
                const saved = settings.data ?? defaultSettings;
                setDraft(saved);
                settingsDraft.clear();
              }}
            >
              Discard
            </Button>
          )}
          <Button
            onClick={() => save.mutate(draft)}
            loading={save.isPending}
            disabled={!hasUnsavedChanges}
          >
            Save changes
          </Button>
        </div>
      </header>
      <div className="settings-page__draft-status">
        <DraftStatus
          status={settingsDraft.status}
          hasDraft={settingsDraft.hasDraft}
          onClear={() => {
            const saved = settings.data ?? defaultSettings;
            setDraft(saved);
            settingsDraft.clear();
          }}
        />
      </div>
      <Tabs
        value={category}
        onChange={setCategory}
        orientation="vertical"
        variant="pills"
        keepMounted={false}
        className="settings-tabs"
      >
        <Tabs.List>
          <Tabs.Tab value="appearance" leftSection={<IconPalette size={18} />}>
            Appearance
          </Tabs.Tab>
          <Tabs.Tab value="game" leftSection={<IconChess size={18} />}>
            Game
          </Tabs.Tab>
          <Tabs.Tab
            value="notifications"
            leftSection={<IconBellRinging size={18} />}
          >
            Notifications
          </Tabs.Tab>
          <Tabs.Tab value="privacy" leftSection={<IconLock size={18} />}>
            Privacy
          </Tabs.Tab>
        </Tabs.List>
        <section className="panel settings-panel">
          <Tabs.Panel value="appearance">
            <Stack gap="lg">
              <div className="settings-panel__heading">
                <Text fw={700} size="lg">
                  Appearance
                </Text>
                <Text size="sm" c="dimmed" mt={4}>
                  Choose how the site and chessboard look and move.
                </Text>
              </div>
              <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="lg">
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
              </SimpleGrid>
            </Stack>
          </Tabs.Panel>
          <Tabs.Panel value="game">
            <Stack gap="lg">
              <div className="settings-panel__heading">
                <Text fw={700} size="lg">
                  Game preferences
                </Text>
                <Text size="sm" c="dimmed" mt={4}>
                  Set the board behavior you want during a match.
                </Text>
              </div>
              <Stack gap={0}>
                <SettingToggle
                  label="Board coordinates"
                  description="Show file and rank labels around the board."
                  checked={draft.showCoordinates}
                  onChange={(checked) => set("showCoordinates", checked)}
                />
                <SettingToggle
                  label="Legal move hints"
                  description="Highlight squares you can move to after selecting a piece."
                  checked={draft.showLegalMoveHints}
                  onChange={(checked) => set("showLegalMoveHints", checked)}
                />
                <SettingToggle
                  label="Allow premoves"
                  description="Queue several moves and cancel them before they are played."
                  checked={draft.premovesEnabled}
                  onChange={(checked) => set("premovesEnabled", checked)}
                />
                <SettingToggle
                  label="Confirm moves"
                  description="Ask before sending each move to the game server."
                  checked={draft.moveConfirmation}
                  onChange={(checked) => set("moveConfirmation", checked)}
                />
                <SettingToggle
                  label="Game sounds"
                  description="Play sounds for moves and game events."
                  checked={draft.gameSoundsEnabled}
                  onChange={(checked) => set("gameSoundsEnabled", checked)}
                />
                <SettingToggle
                  label="Turn indicator"
                  description="Highlight the player whose turn it is."
                  checked={draft.turnIndicatorEnabled}
                  onChange={(checked) => set("turnIndicatorEnabled", checked)}
                />
                <SettingToggle
                  label="Turn sound"
                  description="Play a sound when it becomes your turn."
                  checked={draft.turnSoundEnabled}
                  onChange={(checked) => set("turnSoundEnabled", checked)}
                />
              </Stack>
            </Stack>
          </Tabs.Panel>
          <Tabs.Panel value="notifications">
            <Stack gap="lg">
              <div className="settings-panel__heading">
                <Text fw={700} size="lg">
                  Notifications
                </Text>
                <Text size="sm" c="dimmed" mt={4}>
                  Choose which updates appear while you use the site.
                </Text>
              </div>
              <Stack gap={0}>
                <SettingToggle
                  label="In-app notifications"
                  description="Show friend, challenge, and game updates as they happen."
                  checked={draft.inAppNotificationsEnabled}
                  onChange={(checked) =>
                    set("inAppNotificationsEnabled", checked)
                  }
                />
                <SettingToggle
                  label="Notification sounds"
                  description="Play a short sound when an in-app notification arrives."
                  checked={draft.notificationSoundsEnabled}
                  onChange={(checked) =>
                    set("notificationSoundsEnabled", checked)
                  }
                />
              </Stack>
              <Text size="sm" c="dimmed">
                Email delivery is managed separately from in-app alerts.
              </Text>
            </Stack>
          </Tabs.Panel>
          <Tabs.Panel value="privacy">
            <Stack gap="lg">
              <div className="settings-panel__heading">
                <Text fw={700} size="lg">
                  Privacy
                </Text>
                <Text size="sm" c="dimmed" mt={4}>
                  Control how other players can find you.
                </Text>
              </div>
              <Stack gap={0}>
                <SettingToggle
                  label="Search visibility"
                  description="Let players find your profile by username or display name."
                  checked={profile.data?.searchable ?? true}
                  onChange={(checked) => saveProfile.mutate(checked)}
                />
              </Stack>
              <Text size="sm" c="dimmed">
                When disabled, your profile and games remain available through
                existing friend connections.
              </Text>
            </Stack>
          </Tabs.Panel>
        </section>
      </Tabs>
    </div>
  );
}
