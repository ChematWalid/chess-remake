export function resolveEngineUrl(engineUrl: string): string {
  const url = new URL(engineUrl, window.location.origin);
  const loopbackHosts = new Set(["localhost", "127.0.0.1", "[::1]"]);

  if (loopbackHosts.has(url.hostname.toLowerCase())) {
    if (!import.meta.env.DEV)
      throw new Error(
        "The game server URL is local. Configure APP_ENGINE_WS_URL with its public wss:// address.",
      );
    url.hostname = window.location.hostname;
  }

  return url.toString();
}
