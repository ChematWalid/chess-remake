import { useEffect, useState, type PropsWithChildren } from "react";
import { useQuery } from "@tanstack/react-query";
import { MantineProvider } from "@mantine/core";
import { Notifications } from "@mantine/notifications";
import { theme as mantineTheme } from "./theme";
import { defaultSettings } from "../api/types";
import { getSettings } from "../../features/user/api/userApi";
import { useAuth } from "../auth/AuthProvider";

export function SettingsAppearance({ children }: PropsWithChildren) {
  const { isAuthenticated } = useAuth();
  const { data = defaultSettings } = useQuery({
    queryKey: ["settings"],
    queryFn: getSettings,
    enabled: isAuthenticated,
  });
  const [systemTheme, setSystemTheme] = useState(
    () => window.matchMedia("(prefers-color-scheme: light)").matches,
  );
  const theme: "light" | "dark" =
    data.themePreference === "SYSTEM"
      ? systemTheme
        ? "light"
        : "dark"
      : (data.themePreference.toLowerCase() as "light" | "dark");

  useEffect(() => {
    const root = document.documentElement;
    const systemThemeMedia = window.matchMedia("(prefers-color-scheme: light)");
    const systemMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const applyPreferences = () => {
      root.dataset.theme = theme;
      root.dataset.motion =
        data.motionPreference === "SYSTEM"
          ? systemMotion.matches
            ? "reduced"
            : "full"
          : data.motionPreference.toLowerCase();
    };
    const updateTheme = () => setSystemTheme(systemThemeMedia.matches);
    applyPreferences();
    systemThemeMedia.addEventListener("change", updateTheme);
    systemMotion.addEventListener("change", applyPreferences);
    return () => {
      systemThemeMedia.removeEventListener("change", updateTheme);
      systemMotion.removeEventListener("change", applyPreferences);
    };
  }, [theme, data.motionPreference]);

  return (
    <MantineProvider theme={mantineTheme} forceColorScheme={theme}>
      <Notifications position="top-right" />
      {children}
    </MantineProvider>
  );
}
