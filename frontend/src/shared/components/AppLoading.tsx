import { IconChessRook } from "@tabler/icons-react";
import "./AppLoading.css";

export function AppLoading({ label }: { label: string }) {
  return (
    <main className="app-loading" role="status" aria-live="polite">
      <div className="app-loading-card">
        <span className="app-loading-mark" aria-hidden="true">
          <IconChessRook size={30} stroke={1.6} />
        </span>
        <span className="app-loading-spinner" aria-hidden="true" />
        <div>
          <strong>Rook</strong>
          <p>{label}</p>
        </div>
      </div>
    </main>
  );
}
