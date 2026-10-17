import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const backend =
    process.env.VITE_API_PROXY_TARGET ||
    env.VITE_API_PROXY_TARGET ||
    env.VITE_API_BASE_URL ||
    "http://localhost:8080";
  return {
    plugins: [react()],
    define: {
      global: "globalThis",
    },
    server: {
      proxy: {
        "/api": backend,
        "/oauth2": backend,
        "/login/oauth2": backend,
        "/ws": { target: backend, ws: true },
      },
    },
  };
});
