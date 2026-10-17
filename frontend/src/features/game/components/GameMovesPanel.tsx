import { ActionIcon, Button, Group, Text, Tooltip } from "@mantine/core";
import {
  IconPlayerPause,
  IconPlayerPlay,
  IconPlayerTrackNext,
  IconPlayerTrackPrev,
} from "@tabler/icons-react";
import { Link } from "react-router-dom";
import type { GameDetail } from "../../../shared/api/types";
import { EmptyState } from "../../../shared/components/PageState";
import type { PendingMove } from "./MoveDialogs";
import { PremoveQueue } from "./PremoveQueue";

interface GameMovesPanelProps {
  live: boolean;
  connected: boolean;
  moveText: string[];
  premoves: PendingMove[];
  onPremoveRemove: (index: number) => void;
  onPremovesClear: () => void;
  replay: GameDetail | null;
  onChallengeFromPosition: () => void;
  index: number;
  livePositionIndex: number | null;
  livePositionsCount: number;
  playing: boolean;
  onIndexChange: (index: number) => void;
  onLivePositionChange: (index: number | null) => void;
  onPlayingChange: (playing: boolean) => void;
}

export function GameMovesPanel({
  live,
  connected,
  moveText,
  premoves,
  onPremoveRemove,
  onPremovesClear,
  replay,
  onChallengeFromPosition,
  index,
  livePositionIndex,
  livePositionsCount,
  playing,
  onIndexChange,
  onLivePositionChange,
  onPlayingChange,
}: GameMovesPanelProps) {
  return (
    <aside className="moves-panel panel">
      {live ? (
        <>
          <Group justify="space-between">
            <Text fw={700}>Moves</Text>
            <Text size="sm" c="dimmed">
              {livePositionIndex === null
                ? connected
                  ? "Live"
                  : "Offline"
                : `Viewing ${livePositionIndex}/${livePositionsCount - 1}`}
            </Text>
          </Group>
          {livePositionsCount > 0 && (
            <Group mt="sm" justify="center">
              <Tooltip label="First position">
                <ActionIcon
                  variant="subtle"
                  onClick={() => onLivePositionChange(0)}
                  aria-label="First position"
                >
                  <IconPlayerTrackPrev size={18} />
                </ActionIcon>
              </Tooltip>
              <Tooltip label="Previous move">
                <ActionIcon
                  variant="subtle"
                  onClick={() =>
                    onLivePositionChange(
                      Math.max(
                        0,
                        (livePositionIndex ?? livePositionsCount - 1) - 1,
                      ),
                    )
                  }
                  aria-label="Previous move"
                  disabled={(livePositionIndex ?? livePositionsCount - 1) === 0}
                >
                  <IconPlayerTrackPrev size={18} />
                </ActionIcon>
              </Tooltip>
              <Tooltip label="Next move">
                <ActionIcon
                  variant="subtle"
                  onClick={() => {
                    const next =
                      (livePositionIndex ?? livePositionsCount - 1) + 1;
                    onLivePositionChange(
                      next >= livePositionsCount - 1 ? null : next,
                    );
                  }}
                  aria-label="Next move"
                  disabled={livePositionIndex === null}
                >
                  <IconPlayerTrackNext size={18} />
                </ActionIcon>
              </Tooltip>
              <Tooltip label="Return to live position">
                <ActionIcon
                  variant="subtle"
                  onClick={() => onLivePositionChange(null)}
                  aria-label="Return to live position"
                  disabled={livePositionIndex === null}
                >
                  <IconPlayerTrackNext size={18} />
                </ActionIcon>
              </Tooltip>
            </Group>
          )}
          {moveText.length ? (
            <ol className="move-list">
              {moveText.map((move, i) => (
                <li key={`${i}-${move}`}>
                  <Button
                    variant={
                      (livePositionIndex ?? livePositionsCount - 1) === i + 1
                        ? "light"
                        : "subtle"
                    }
                    size="compact-xs"
                    onClick={() => onLivePositionChange(i + 1)}
                  >
                    {move}
                  </Button>
                </li>
              ))}
            </ol>
          ) : (
            <EmptyState
              title="The game is ready"
              description="Make the first move on the board."
            />
          )}
          <PremoveQueue
            premoves={premoves}
            onRemove={onPremoveRemove}
            onClear={onPremovesClear}
          />
        </>
      ) : replay ? (
        <>
          <Group justify="space-between">
            <Text fw={700}>Moves</Text>
            <Text size="sm" c="dimmed">
              {index}/{replay.moves.length}
            </Text>
          </Group>
          <Button
            fullWidth
            variant="light"
            mt="sm"
            onClick={onChallengeFromPosition}
          >
            Challenge from this position
          </Button>
          <Group mt="sm" justify="center">
            <Tooltip label="First position">
              <ActionIcon
                variant="subtle"
                onClick={() => onIndexChange(0)}
                aria-label="First move"
              >
                <IconPlayerTrackPrev size={18} />
              </ActionIcon>
            </Tooltip>
            <Tooltip label={playing ? "Pause replay" : "Autoplay"}>
              <ActionIcon
                variant="subtle"
                onClick={() => onPlayingChange(!playing)}
                aria-label={playing ? "Pause replay" : "Autoplay"}
              >
                {playing ? (
                  <IconPlayerPause size={18} />
                ) : (
                  <IconPlayerPlay size={18} />
                )}
              </ActionIcon>
            </Tooltip>
            <Tooltip label="Next move">
              <ActionIcon
                variant="subtle"
                onClick={() =>
                  onIndexChange(Math.min(replay.fens.length - 1, index + 1))
                }
                aria-label="Next move"
              >
                <IconPlayerTrackNext size={18} />
              </ActionIcon>
            </Tooltip>
          </Group>
          <ol className="move-list">
            {replay.moves.map((move, i) => (
              <li key={`${i}-${move}`}>
                <Button
                  variant={index === i + 1 ? "light" : "subtle"}
                  size="compact-xs"
                  onClick={() => onIndexChange(i + 1)}
                >
                  {move}
                </Button>
              </li>
            ))}
          </ol>
          <Button
            component={Link}
            to="/history"
            variant="default"
            fullWidth
            mt="lg"
          >
            Back to games
          </Button>
        </>
      ) : null}
    </aside>
  );
}
