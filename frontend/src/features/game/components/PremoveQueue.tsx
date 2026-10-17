import { ActionIcon, Button, Group, Stack, Text } from "@mantine/core";
import { IconX } from "@tabler/icons-react";
import type { PendingMove } from "./MoveDialogs";

interface PremoveQueueProps {
  premoves: PendingMove[];
  onRemove: (index: number) => void;
  onClear: () => void;
}

export function PremoveQueue({ premoves, onRemove, onClear }: PremoveQueueProps) {
  if (!premoves.length) return null;

  return (
    <section className="premove-queue" aria-label="Queued premoves">
      <Group justify="space-between" align="center">
        <Text size="sm" fw={600}>
          Premoves · {premoves.length}
        </Text>
        <Button variant="subtle" size="compact-xs" onClick={onClear}>
          Cancel all
        </Button>
      </Group>
      <Stack gap={4} mt="xs">
        {premoves.map((move, index) => (
          <Group
            key={`${index}-${move.from}-${move.to}`}
            justify="space-between"
            className="premove-queue-item"
          >
            <Text size="sm" c="dimmed">
              {index + 1}. {move.from} → {move.to}
            </Text>
            <ActionIcon
              variant="subtle"
              color="gray"
              size="sm"
              aria-label={`Cancel premove ${index + 1}`}
              onClick={() => onRemove(index)}
            >
              <IconX size={15} />
            </ActionIcon>
          </Group>
        ))}
      </Stack>
    </section>
  );
}
