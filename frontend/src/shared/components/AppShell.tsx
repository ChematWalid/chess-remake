import {
  AppShell as MantineShell,
  Avatar,
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
import { useEffect, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../auth/AuthProvider";
import { getMyProfile } from "../../features/user/api/userApi";
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
  const [navWidth, setNavWidth] = useState(() => {
    const savedWidth = Number(localStorage.getItem("rook.navWidth"));
    return Number.isFinite(savedWidth) && savedWidth >= 180 && savedWidth <= 400
      ? savedWidth
      : 228;
  });
  const [resizing, setResizing] = useState(false);
  const resizeStart = useRef({ x: 0, width: navWidth });
  const location = useLocation();
  const { user, signOut } = useAuth();
  const profile = useQuery({
    queryKey: ["profile", "me"],
    queryFn: getMyProfile,
    enabled: Boolean(user),
  });

  useEffect(() => {
    if (!resizing) return;
    const handlePointerMove = (event: PointerEvent) => {
      const width = Math.min(
        400,
        Math.max(180, resizeStart.current.width + event.clientX - resizeStart.current.x),
      );
      setNavWidth(width);
      localStorage.setItem("rook.navWidth", String(width));
    };
    const handlePointerUp = () => setResizing(false);
    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);
    return () => {
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
    };
  }, [resizing]);

  const startResize = (event: React.PointerEvent<HTMLDivElement>) => {
    resizeStart.current = {
      x: event.clientX,
      width: document.querySelector(".side-nav")?.getBoundingClientRect().width ?? navWidth,
    };
    event.preventDefault();
    setResizing(true);
  };

  return (
    <MantineShell
      header={{ height: 64 }}
      navbar={{ width: navWidth, breakpoint: "sm", collapsed: { mobile: !opened } }}
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
            <Link to="/" className="brand">
              <IconChessKnight size={27} stroke={1.6} />
              <span>rook</span>
            </Link>
          </Group>
          <Group gap="md">
            <Group gap="xs" visibleFrom="sm">
              <Text size="sm" c="dimmed">
                {user?.displayName}
              </Text>
              <Avatar src={profile.data?.avatarUrl} size="sm" radius="xl">
                {user?.displayName?.slice(0, 1)}
              </Avatar>
            </Group>
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
        <div
          className={`side-nav-resize${resizing ? " is-resizing" : ""}`}
          role="separator"
          aria-orientation="vertical"
          aria-label="Resize sidebar"
          onPointerDown={startResize}
        />
      </MantineShell.Navbar>
      <MantineShell.Main className="app-main">
        <Outlet />
      </MantineShell.Main>
    </MantineShell>
  );
}
