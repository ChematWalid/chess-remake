import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { MantineProvider } from "@mantine/core";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "./shared/auth/AuthProvider";
import { BrowserQueryCache } from "./shared/storage/BrowserQueryCache";
import { AppRoutes } from "./routes/AppRoutes";
import { SettingsAppearance } from "./shared/settings/SettingsAppearance";
import { theme } from "./shared/settings/theme";
import "@mantine/core/styles.css";
import "@mantine/notifications/styles.css";
import "./styles/global.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 60_000, retry: 1, refetchOnWindowFocus: true },
  },
});
queryClient.setQueryDefaults(["challenges"], { staleTime: 0 });
queryClient.setQueryDefaults(["challenge-detail"], { staleTime: 0 });

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <MantineProvider theme={theme} defaultColorScheme="dark">
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <BrowserQueryCache queryClient={queryClient}>
              <SettingsAppearance>
                <AppRoutes />
              </SettingsAppearance>
            </BrowserQueryCache>
          </AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </MantineProvider>
  </React.StrictMode>,
);
