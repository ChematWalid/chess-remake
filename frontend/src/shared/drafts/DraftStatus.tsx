import { Button, Group, Text } from "@mantine/core";
import type { DraftSyncStatus } from "./useSyncedDraft";

interface DraftStatusProps {
  status: DraftSyncStatus;
  hasDraft: boolean;
  onClear: () => void;
}

export function DraftStatus({ status, hasDraft, onClear }: DraftStatusProps) {
  const labels: Record<DraftSyncStatus, string> = {
    local: "Saved in this browser. Syncing when available.",
    syncing: "Syncing draft...",
    saved: "Draft saved to your account.",
    offline: "Saved in this browser. Sync will retry when online.",
    deleting: "Removing saved draft...",
    "delete-offline": "Draft removal is saved here and will sync when online.",
    cleared: "Saved draft cleared.",
  };
  const label =
    hasDraft ||
    status === "deleting" ||
    status === "delete-offline" ||
    status === "cleared"
      ? labels[status]
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
