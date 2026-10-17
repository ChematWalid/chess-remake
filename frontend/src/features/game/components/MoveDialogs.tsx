import { Button, Group, Modal, Stack, Text } from "@mantine/core";
import type { Square } from "chess.js";

export interface PendingMove {
  from: Square;
  to: Square;
  promotion?: "q" | "r" | "b" | "n";
}

interface MoveDialogsProps {
  promotion: PendingMove | null;
  pending: PendingMove | null;
  onPromotionClose: () => void;
  onPromotionSelect: (piece: NonNullable<PendingMove["promotion"]>) => void;
  onPendingClose: () => void;
  onPendingConfirm: () => void;
}

export function MoveDialogs({
  promotion,
  pending,
  onPromotionClose,
  onPromotionSelect,
  onPendingClose,
  onPendingConfirm,
}: MoveDialogsProps) {
  return (
    <>
      <Modal
        opened={Boolean(promotion)}
        onClose={onPromotionClose}
        title="Choose a promotion"
        centered
      >
        <Group grow>
          {(["q", "r", "b", "n"] as const).map((piece) => (
            <Button
              key={piece}
              variant="default"
              onClick={() => onPromotionSelect(piece)}
            >
              {{ q: "Queen", r: "Rook", b: "Bishop", n: "Knight" }[piece]}
            </Button>
          ))}
        </Group>
      </Modal>
      <Modal
        opened={Boolean(pending)}
        onClose={onPendingClose}
        title="Confirm move"
        centered
      >
        <Stack>
          <Text>
            Send {pending?.from} to {pending?.to}?
          </Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={onPendingClose}>
              Cancel
            </Button>
            <Button onClick={onPendingConfirm}>Confirm move</Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
