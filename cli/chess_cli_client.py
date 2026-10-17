#!/usr/bin/env python3
"""
Interactive CLI client for the C++ Chess Server.
Connects via WebSocket, renders the board in ANSI ASCII, and lets players take turns.
"""

import argparse
import asyncio
import json
import sys
import websockets

def render_board(fen: str) -> str:
    """Renders the board from a FEN string matching the C++ BoardRenderer style."""
    placement = fen.split()[0]
    ranks = placement.split('/')
    lines = ["  +---+---+---+---+---+---+---+---+"]
    for i, rank in enumerate(ranks):
        rank_num = 8 - i
        row_str = f"{rank_num} |"
        for ch in rank:
            if ch.isdigit():
                row_str += " . |" * int(ch)
            else:
                row_str += f" {ch} |"
        lines.append(row_str)
        lines.append("  +---+---+---+---+---+---+---+---+")
    lines.append("    a   b   c   d   e   f   g   h")
    return "\n".join(lines)

def format_clock(ms: int) -> str:
    if ms < 0:
        return "∞"
    total_sec = max(0, ms // 1000)
    mins = total_sec // 60
    secs = total_sec % 60
    return f"{mins:02d}:{secs:02d}"

class ChessCliClient:
    def __init__(self, ws_url: str, room_code: str, token: str, player_name: str):
        self.ws_url = ws_url
        self.room_code = room_code
        self.token = token
        self.player_name = player_name
        self.my_color = None
        self.my_role = None
        self.current_fen = None
        self.side_to_move = "white"
        self.is_over = False
        self.white_time_ms = -1
        self.black_time_ms = -1

    async def run(self):
        url = self.ws_url.rstrip("/")

        print(f"\n=========================================")
        print(f"       CHESS TERMINAL CLIENT ({self.player_name})")
        print(f"=========================================")
        print(f"Connecting to {url}...")

        try:
            async with websockets.connect(url) as ws:
                print("✓ Connected to server.")
                if not self.room_code or not self.token:
                    print("Error: backend-issued --room and --token are required.")
                    return
                await ws.send(json.dumps({
                    "action": "connect_token",
                    "room_code": self.room_code,
                    "token": self.token,
                }))

                # Start background receive task and interactive input loop
                receive_task = asyncio.create_task(self.receive_loop(ws))
                input_task = asyncio.create_task(self.input_loop(ws))

                done, pending = await asyncio.wait(
                    [receive_task, input_task],
                    return_when=asyncio.FIRST_COMPLETED
                )
                for task in pending:
                    task.cancel()

        except Exception as e:
            print(f"\n[Connection closed or failed: {e}]")

    async def receive_loop(self, ws):
        async for raw_msg in ws:
            try:
                data = json.loads(raw_msg)
            except Exception:
                print(f"[Raw message]: {raw_msg}")
                continue

            event = data.get("event")

            if event == "room_created":
                self.room_code = data.get("room_code")
                self.my_color = data.get("color")
                print(f"\n🎉 Room Created! Code: [{self.room_code}]")
                print(f"Assigned Color: {self.my_color.upper()}")
                print(f"Share room code '{self.room_code}' with the opponent.")
                print("Waiting for opponent to connect...\n")

            elif event == "room_joined":
                self.room_code = data.get("room_code")
                role = data.get("role", "spectator")
                if role in ("white", "black"):
                    self.my_color = role
                    self.my_role = "player"
                    print(f"\n✓ Joined Room [{self.room_code}] as {self.my_color.upper()} (Player).")
                else:
                    self.my_role = "spectator"
                    print(f"\n✓ Joined Room [{self.room_code}] as SPECTATOR.")

            elif event == "game_started":
                self.current_fen = data.get("fen")
                self.white_time_ms = data.get("white_time_ms", -1)
                self.black_time_ms = data.get("black_time_ms", -1)
                self.side_to_move = data.get("side_to_move", "white")

                print("\n=========================================")
                print("           ⚔️  GAME STARTED!  ⚔️           ")
                print("=========================================")
                if self.my_color:
                    print(f"You are playing as: {self.my_color.upper()} ({self.player_name})")
                else:
                    print(f"You are watching as: SPECTATOR ({self.player_name})")
                print(f"Time control: {data.get('time_control')}")
                print(render_board(self.current_fen))
                self.print_turn_prompt()

            elif event == "move_made":
                self.current_fen = data.get("fen")
                self.side_to_move = data.get("side_to_move", "white")
                self.white_time_ms = data.get("white_time_ms", -1)
                self.black_time_ms = data.get("black_time_ms", -1)
                san = data.get("san", "")
                uci = data.get("uci", "")
                is_check = data.get("is_check", False)

                mover = "White" if self.side_to_move == "black" else "Black"
                print(f"\n>> {mover} played: {san} ({uci})")
                print(render_board(self.current_fen))

                if is_check:
                    print(f"⚠️  {self.side_to_move.upper()} is in CHECK!")

                self.print_turn_prompt()

            elif event == "error":
                print(f"\n❌ Server Error: {data.get('message')}")
                if not self.is_over:
                    self.print_turn_prompt()

            elif event == "draw_offered":
                by = data.get("by", "opponent")
                print(f"\n🤝 Draw offered by {by}! Type 'draw' to accept.")
                self.print_turn_prompt()

            elif event == "opponent_disconnected":
                print("\n⚠️  Opponent disconnected! Waiting for reconnection...")

            elif event == "opponent_reconnected":
                print("\n✓ Opponent reconnected!")

            elif event == "game_over":
                self.is_over = True
                result = data.get("result")
                reason = data.get("reason")
                winner = data.get("winner")
                reason_code = data.get("reason_code")
                print("\n=========================================")
                print("                GAME OVER                ")
                print("=========================================")
                print(f"Result : {result}")
                print(f"Reason : {reason} ({reason_code})")
                if winner:
                    print(f"Winner : {winner.upper()}")
                print("=========================================\n")
                return

    def print_turn_prompt(self):
        if self.is_over:
            return
        turn_str = self.side_to_move.capitalize()
        if self.my_color:
            is_my_turn = (self.side_to_move.lower() == self.my_color.lower())
            clock_info = ""
            if self.white_time_ms >= 0:
                clock_info = f" [W: {format_clock(self.white_time_ms)} | B: {format_clock(self.black_time_ms)}]"

            if is_my_turn:
                print(f"\n➤ Your turn ({turn_str}){clock_info} > ", end="", flush=True)
            else:
                print(f"\n⏳ Waiting for opponent ({turn_str}){clock_info}...", flush=True)
        else:
            print(f"\n➤ {turn_str} to move > ", end="", flush=True)

    async def input_loop(self, ws):
        loop = asyncio.get_running_loop()
        while not self.is_over:
            line = await loop.run_in_executor(None, sys.stdin.readline)
            if not line:
                break
            cmd = line.strip()
            if not cmd:
                continue

            if cmd in ("quit", "exit"):
                print("Exiting game...")
                break

            if cmd == "fen":
                print(f"Current FEN: {self.current_fen}")
                self.print_turn_prompt()
                continue

            if cmd == "resign":
                await ws.send(json.dumps({"action": "resign"}))
                continue

            if cmd == "draw":
                await ws.send(json.dumps({"action": "offer_draw"}))
                continue

            # Send move
            await ws.send(json.dumps({"action": "move", "move": cmd}))

def main():
    parser = argparse.ArgumentParser(description="C++ Chess Server CLI Client")
    parser.add_argument("--url", default="ws://127.0.0.1:9002/ws/play", help="WebSocket endpoint")
    parser.add_argument("--room", required=True, help="Room code returned by the backend")
    parser.add_argument("--token", required=True, help="Short-lived room token returned by the backend")
    parser.add_argument("--name", default="Player", help="Player label for output")

    args = parser.parse_args()

    client = ChessCliClient(
        ws_url=args.url,
        room_code=args.room.strip().upper(),
        token=args.token,
        player_name=args.name
    )

    try:
        asyncio.run(client.run())
    except KeyboardInterrupt:
        print("\nDisconnected.")

if __name__ == "__main__":
    main()
