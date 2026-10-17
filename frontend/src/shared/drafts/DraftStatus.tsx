import { Button, Group, Text } from "@mantine/core";
import type { DraftSyncStatus } from "./useSyncedDraft";

interface DraftStatusProps {
  status: DraftSyncStatus;
  hasDraft: boolean;
  onClear: () => void;
}

export function DraftStatus({ status, hasDraft, onClear }: DraftStatusProps) {
  const label = hasDraft
    ? {
        local: "Saved in this browser. Syncing when available.",
        syncing: "Syncing draft...",
        saved: "Draft saved to your account.",
        offline: "Saved in this browser. Sync will retry when online.",
      }[status]
    : "Your changes save automatically as you work.";
  return (
    <Group gap="xs" justify="space-between" wrap="nowrap" aria-live="polite">
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      {hasDraft && (
        <Button size="compact-xs" variant="subtle" onClick={onClear}>
          Clear saved draft
        </Button>
      )}
    </Group>
  );
}
