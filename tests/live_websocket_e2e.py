#!/usr/bin/env python3
"""Run the shared STOMP, authenticated engine WebSocket, and replay E2E flow."""

import runpy
from pathlib import Path


runner = Path(__file__).resolve().parents[1] / "backend" / "collections" / "run-live-e2e.py"
runpy.run_path(str(runner), run_name="__main__")
