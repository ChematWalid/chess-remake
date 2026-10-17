const RETURN_PATH_KEY = "chess.auth.returnPath";

export function safeReturnPath(
  value: string | null | undefined,
): string | null {
  if (!value) return null;
  try {
    const destination = new URL(value, window.location.origin);
    if (destination.origin !== window.location.origin) return null;
    return `${destination.pathname}${destination.search}${destination.hash}`;
  } catch {
    return null;
  }
}

export function storeReturnPath(value: string | null | undefined): void {
  const destination = safeReturnPath(value);
  if (destination) sessionStorage.setItem(RETURN_PATH_KEY, destination);
}

export function consumeReturnPath(): string | null {
  const destination = safeReturnPath(sessionStorage.getItem(RETURN_PATH_KEY));
  sessionStorage.removeItem(RETURN_PATH_KEY);
  return destination;
}

export function peekReturnPath(): string | null {
  return safeReturnPath(sessionStorage.getItem(RETURN_PATH_KEY));
}
