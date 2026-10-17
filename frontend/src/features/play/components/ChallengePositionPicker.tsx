import { Button, Group, Select, Stack, Text } from "@mantine/core";
import { Chessboard } from "react-chessboard";
import { useNavigate } from "react-router-dom";
import type { ChallengePosition } from "../../position/utils/positionUtils";

interface ChallengePositionPickerProps {
  mode: "STANDARD" | "CUSTOM";
  position: ChallengePosition | null;
  onModeChange: (mode: "STANDARD" | "CUSTOM") => void;
  onPositionChange: (position: ChallengePosition | null) => void;
}

export function ChallengePositionPicker({
  mode,
  position,
  onModeChange,
  onPositionChange,
}: ChallengePositionPickerProps) {
  const navigate = useNavigate();
  return (
    <>
      <Select
        label="Starting position"
        data={[
          { value: "STANDARD", label: "Standard game" },
          { value: "CUSTOM", label: "Custom position" },
        ]}
        value={mode}
        onChange={(value) =>
          onModeChange((value as "STANDARD" | "CUSTOM") ?? "STANDARD")
        }
        allowDeselect={false}
      />
      {mode === "CUSTOM" && (
        <Stack gap="xs">
          {position ? (
            <>
              <div style={{ maxWidth: 240 }}>
                <Chessboard
                  options={{
                    position: position.fen,
                    boardStyle: { width: 220 },
                    allowDragging: false,
                  }}
                />
              </div>
              <Text size="sm" c="dimmed">
                {position.fen.split(" ")[1] === "w" ? "White" : "Black"} to move
                {position.setupMoves.length
                  ? ` · ${position.setupMoves.length} setup moves`
                  : ""}
              </Text>
            </>
          ) : (
            <Text size="sm" c="dimmed">
              Choose a position to challenge from.
            </Text>
          )}
          <Group>
            <Button
              variant="default"
              onClick={() =>
                navigate("/position-setup", {
                  state: { challengePosition: position },
                })
              }
            >
              Set up position
            </Button>
            {position && (
              <Button
                variant="subtle"
                color="gray"
                onClick={() => onPositionChange(null)}
              >
                Clear
              </Button>
            )}
          </Group>
        </Stack>
      )}
    </>
  );
}
