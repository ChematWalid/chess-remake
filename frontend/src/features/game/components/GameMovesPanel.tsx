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
  index: number;
  playing: boolean;
  onIndexChange: (index: number) => void;
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
  index,
  playing,
  onIndexChange,
  onPlayingChange,
}: GameMovesPanelProps) {
  return (
    <aside className="moves-panel panel">
      {live ? (
        <>
          <Group justify="space-between">
            <Text fw={700}>Moves</Text>
            <Text size="sm" c="dimmed">
              {connected ? "Live" : "Offline"}
            </Text>
          </Group>
          {moveText.length ? (
            <ol className="move-list">
              {moveText.map((move, i) => (
                <li key={`${i}-${move}`}>{move}</li>
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
