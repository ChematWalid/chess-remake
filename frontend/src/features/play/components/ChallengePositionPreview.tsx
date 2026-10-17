import { Group, Text } from "@mantine/core";
import { Chessboard } from "react-chessboard";
import { standardFen } from "../../position/utils/positionUtils";

interface ChallengePositionPreviewProps {
  fen: string | null | undefined;
  challengerSide: string;
  initialTimeSeconds: number | null;
  incrementSeconds: number | null;
}

export function ChallengePositionPreview({
  fen,
  challengerSide,
  initialTimeSeconds,
  incrementSeconds,
}: ChallengePositionPreviewProps) {
  const shownFen = fen ?? standardFen;
  return (
    <Group align="flex-start" wrap="nowrap">
      <div style={{ width: 130, flex: "0 0 130px" }}>
        <Chessboard
          options={{
            position: shownFen,
            boardStyle: { width: 130 },
            allowDragging: false,
          }}
        />
      </div>
      <div>
        <Text size="sm" c="dimmed">
          {initialTimeSeconds
            ? `${Math.floor(initialTimeSeconds / 60)}+${incrementSeconds ?? 0}`
            : "Untimed"}
          {" · "}
          {challengerSide.toLowerCase()}` · $
          {shownFen.split(" ")[1] === "w" ? "White" : "Black"} to move`
        </Text>
        <Text size="xs" c="dimmed">
          {challengerSide === "RANDOM"
            ? "Colors are chosen when you accept."
            : `Challenger plays ${challengerSide.toLowerCase()}, you play ${challengerSide === "WHITE" ? "black" : "white"}.`}
        </Text>
      </div>
    </Group>
  );
}
