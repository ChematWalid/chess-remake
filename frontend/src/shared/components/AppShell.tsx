import {
  AppShell as MantineShell,
  Burger,
  Group,
  NavLink,
  Text,
  UnstyledButton,
} from "@mantine/core";
import {
  IconChessKnight,
  IconFriends,
  IconHistory,
  IconHome2,
  IconSearch,
  IconSettings,
  IconUserCircle,
} from "@tabler/icons-react";
import { Link, Outlet, useLocation } from "react-router-dom";
import { useState } from "react";
import { useAuth } from "../auth/AuthProvider";
import "./AppShell.css";

const links = [
  { to: "/play", label: "Play", icon: IconHome2 },
  { to: "/friends", label: "Friends", icon: IconFriends },
  { to: "/search", label: "Find players", icon: IconSearch },
  { to: "/history", label: "Games", icon: IconHistory },
  { to: "/profile", label: "Profile", icon: IconUserCircle },
  { to: "/settings", label: "Settings", icon: IconSettings },
];

export function AppShell() {
  const [opened, setOpened] = useState(false);
  const location = useLocation();
  const { user, signOut } = useAuth();
  return (
    <MantineShell
      header={{ height: 64 }}
      navbar={{ width: 228, breakpoint: "sm", collapsed: { mobile: !opened } }}
      padding="md"
    >
      <MantineShell.Header className="app-header">
        <Group h="100%" px="md" justify="space-between">
          <Group gap="sm">
            <Burger
              opened={opened}
              onClick={() => setOpened((value) => !value)}
              hiddenFrom="sm"
              size="sm"
              aria-label="Toggle navigation"
            />
            <Link to="/play" className="brand">
              <IconChessKnight size={27} stroke={1.6} />
              <span>rook</span>
            </Link>
          </Group>
          <Group gap="md">
            <Text size="sm" c="dimmed" visibleFrom="sm">
              {user?.displayName}
            </Text>
            <UnstyledButton
              className="text-button"
              onClick={() => void signOut()}
            >
              Sign out
            </UnstyledButton>
          </Group>
        </Group>
      </MantineShell.Header>
      <MantineShell.Navbar p="md" className="side-nav">
        <Text size="xs" fw={700} tt="uppercase" c="dimmed" mb="sm" pl="sm">
          Workspace
        </Text>
        {links.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            component={Link}
            to={to}
            label={label}
            leftSection={<Icon size={18} stroke={1.7} />}
            active={
              location.pathname === to ||
              (to === "/history" && location.pathname.startsWith("/games/"))
            }
            onClick={() => setOpened(false)}
          />
        ))}
      </MantineShell.Navbar>
      <MantineShell.Main className="app-main">
        <Outlet />
      </MantineShell.Main>
    </MantineShell>
  );
}
