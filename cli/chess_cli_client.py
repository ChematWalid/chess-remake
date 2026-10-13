#!/usr/bin/env python3
"""
Interactive CLI client for the C++ Chess Server.
Connects via WebSocket, renders the board in ANSI ASCII, and lets players take turns.
"""

import argparse
import asyncio
import base64
import hashlib
import hmac
import json
import random
import string
import sys
import time
import websockets

def make_jwt_token(room_code: str, user_id: str, display_name: str, color: str = "", initial_seconds: int = 0, increment_seconds: int = 0, secret: str = "chess_secret_dev_key") -> str:
    header = '{"alg":"HS256","typ":"JWT"}'
    claims = {
        "room_code": room_code,
        "user_id": user_id,
        "display_name": display_name,
        "color": color,
        "initial_seconds": initial_seconds,
        "increment_seconds": increment_seconds,
        "exp": int(time.time()) + 86400
    }
    def b64url(data: bytes) -> str:
        return base64.urlsafe_b64encode(data).decode('ascii').rstrip('=')
    h_b64 = b64url(header.encode('utf-8'))
    p_b64 = b64url(json.dumps(claims).encode('utf-8'))
    to_sign = f"{h_b64}.{p_b64}"
    sig = hmac.new(secret.encode('utf-8'), to_sign.encode('utf-8'), hashlib.sha256).digest()
    return f"{to_sign}.{b64url(sig)}"

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
    def __init__(self, ws_url: str, create_room: bool, room_code: str, preferred_color: str, time_control: str, token: str, player_name: str):
        self.ws_url = ws_url
        self.create_room = create_room
        self.room_code = room_code
        self.preferred_color = preferred_color
        self.time_control = time_control
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
        url = self.ws_url
        if self.token and self.room_code:
            url = f"{self.ws_url}/ws/play?room={self.room_code}&token={self.token}"

        print(f"\n=========================================")
        print(f"       CHESS TERMINAL CLIENT ({self.player_name})")
        print(f"=========================================")
        print(f"Connecting to {url}...")

        try:
            async with websockets.connect(url) as ws:
                print("✓ Connected to server.")

                # If not using token URL, handle create/join room via actions
                if not (self.token and self.room_code):
                    if self.create_room:
                        create_payload = {
                            "action": "create_room",
                            "preferred_color": self.preferred_color
                        }
                        if self.time_control == "unlimited":
                            create_payload["time_control"] = "unlimited"
                        else:
                            try:
                                mins = float(self.time_control)
                                create_payload["time_control"] = {"minutes": mins}
                            except ValueError:
                                create_payload["time_control"] = "unlimited"

                        await ws.send(json.dumps(create_payload))
                    elif self.room_code:
                        join_payload = {
                            "action": "join_room",
                            "room_code": self.room_code
                        }
                        await ws.send(json.dumps(join_payload))
                    else:
                        print("Error: Specify either --create or --room <CODE>.")
                        return

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
    parser.add_argument("--url", default="ws://127.0.0.1:9002", help="WebSocket server URL")
    parser.add_argument("--create", action="store_true", help="Create a new game room")
    parser.add_argument("--room", default="", help="Room code to join / reconnect")
    parser.add_argument("--color", default="white", choices=["white", "black", "random"], help="Preferred color when creating")
    parser.add_argument("--time-control", default="unlimited", help="Time control ('unlimited' or minutes)")
    parser.add_argument("--user-id", default="", help="Unique user ID for seat ownership and reconnecting (e.g. 'walid', 'alice')")
    parser.add_argument("--secret", default="chess_secret_dev_key", help="JWT secret matching CHESS_JWT_SECRET on server")
    parser.add_argument("--token", default="", help="Direct JWT auth token")
    parser.add_argument("--name", default="Player", help="Player label for output")

    args = parser.parse_args()

    room_code = args.room.strip().upper()
    token = args.token

    if args.user_id:
        if args.create and not room_code:
            charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            room_code = "".join(random.choices(charset, k=6))

        if room_code and not token:
            pref_color = args.color if args.create else ""
            init_sec = 0
            if args.time_control != "unlimited":
                try:
                    init_sec = int(float(args.time_control) * 60)
                except ValueError:
                    init_sec = 0
            token = make_jwt_token(
                room_code=room_code,
                user_id=args.user_id,
                display_name=args.name if args.name != "Player" else args.user_id,
                color=pref_color,
                initial_seconds=init_sec,
                increment_seconds=0,
                secret=args.secret
            )

    client = ChessCliClient(
        ws_url=args.url,
        create_room=args.create,
        room_code=room_code,
        preferred_color=args.color,
        time_control=args.time_control,
        token=token,
        player_name=args.name if args.name != "Player" else (args.user_id or "Player")
    )

    try:
        asyncio.run(client.run())
    except KeyboardInterrupt:
        print("\nDisconnected.")

if __name__ == "__main__":
    main()
