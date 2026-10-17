import { Button, Group, Text } from "@mantine/core";
import {
  IconArrowUpRight,
  IconChessKnight,
  IconDeviceGamepad2,
} from "@tabler/icons-react";
import { Link } from "react-router-dom";
import "./LandingPage.css";

export function LandingPage() {
  return (
    <main className="landing-page">
      <header>
        <Link to="/" className="brand">
          <IconChessKnight size={28} />
          <span>rook</span>
        </Link>
        <Group>
          <Button component={Link} to="/login" variant="subtle" color="gray">
            Sign in
          </Button>
          <Button component={Link} to="/register">
            Join free
          </Button>
        </Group>
      </header>
      <section className="landing-hero">
        <div className="hero-copy">
          <span className="eyebrow">
            <IconDeviceGamepad2 size={16} /> CHESS, WITH YOUR PEOPLE
          </span>
          <h1>
            Make room
            <br />
            for a good game.
          </h1>
          <Text c="dimmed" size="lg" maw={460}>
            A simple place to challenge friends, replay your games, and keep the
            focus on the board.
          </Text>
          <Group mt="xl">
            <Button
              component={Link}
              to="/register"
              size="md"
              rightSection={<IconArrowUpRight size={18} />}
            >
              Get started
            </Button>
            <Button
              component={Link}
              to="/login"
              size="md"
              variant="subtle"
              color="gray"
            >
              I have an account
            </Button>
          </Group>
        </div>
        <div className="hero-board" aria-label="Chess board illustration">
          {Array.from({ length: 64 }, (_, i) => {
            const row = Math.floor(i / 8);
            const blackPieces = ["♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜"];
            const whitePieces = ["♖", "♘", "♗", "♕", "♔", "♗", "♘", "♖"];
            const piece =
              row === 0
                ? blackPieces[i % 8]
                : row === 1
                  ? "♟"
                  : row === 6
                    ? "♙"
                    : row === 7
                      ? whitePieces[i % 8]
                      : "";
            return (
              <div
                key={i}
                className={
                  (row + (i % 8)) % 2 === 0 ? "light-square" : "dark-square"
                }
              >
                {piece}
              </div>
            );
          })}
        </div>
      </section>
      <footer>
        <Text size="sm" c="dimmed">
          Thoughtful play, no noise.
        </Text>
      </footer>
    </main>
  );
}
