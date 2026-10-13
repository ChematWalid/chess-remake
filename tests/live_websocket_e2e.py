#!/usr/bin/env python3
import asyncio
import json
import subprocess
import time
import sys
import websockets

PORT = 9099
WS_URL = f"ws://127.0.0.1:{PORT}"

async def main():
    print(f"[E2E] Starting chess_server on port {PORT}...")
    server_proc = subprocess.Popen(
        ["./build/bin/chess_server", "--port", str(PORT), "--host", "127.0.0.1"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )

    # Wait for server to bind port
    time.sleep(1)

    try:
        print("[E2E] Connecting Player 1 (White)...")
        async with websockets.connect(WS_URL) as ws1:
            # 1. Create Room
            create_msg = {
                "action": "create_room",
                "time_control": "unlimited",
                "preferred_color": "white"
            }
            await ws1.send(json.dumps(create_msg))
            resp1 = json.loads(await ws1.recv())
            print(f"[E2E] Player 1 received: {resp1}")
            assert resp1["event"] == "room_created", f"Expected room_created, got {resp1}"
            assert resp1["color"] == "white"
            room_code = resp1["room_code"]
            assert len(room_code) == 6, f"Expected 6-char room code, got {room_code}"

            print(f"[E2E] Connecting Player 2 (Black) to room {room_code}...")
            async with websockets.connect(WS_URL) as ws2:
                # 2. Join Room
                join_msg = {
                    "action": "join_room",
                    "room_code": room_code
                }
                await ws2.send(json.dumps(join_msg))

                # Both players should receive game_started
                resp_join = json.loads(await ws2.recv())
                print(f"[E2E] Player 2 joined: {resp_join}")
                assert resp_join["event"] == "room_joined"

                start2 = json.loads(await ws2.recv())
                start1 = json.loads(await ws1.recv())
                print(f"[E2E] Both received game_started: {start1['event']}, {start2['event']}")
                assert start1["event"] == "game_started"
                assert start2["event"] == "game_started"
                assert start1["time_control"] == "unlimited"

                # 3. Player 1 plays e4
                print("[E2E] Player 1 plays e4...")
                await ws1.send(json.dumps({"action": "move", "san": "e4"}))

                m1_p1 = json.loads(await ws1.recv())
                m1_p2 = json.loads(await ws2.recv())
                assert m1_p1["event"] == "move_made" and m1_p1["san"] == "e4"
                assert m1_p2["event"] == "move_made" and m1_p2["san"] == "e4"
                assert m1_p1["side_to_move"] == "black"
                print(f"[E2E] Move e4 broadcasted successfully! Next turn: {m1_p1['side_to_move']}")

                # 4. Player 2 plays e5
                print("[E2E] Player 2 plays e5...")
                await ws2.send(json.dumps({"action": "move", "uci": "e7e5"}))

                m2_p1 = json.loads(await ws1.recv())
                m2_p2 = json.loads(await ws2.recv())
                assert m2_p1["event"] == "move_made" and m2_p1["san"] == "e5"
                assert m2_p2["event"] == "move_made" and m2_p2["san"] == "e5"
                assert m2_p1["side_to_move"] == "white"
                print(f"[E2E] Move e5 broadcasted successfully! Next turn: {m2_p1['side_to_move']}")

                # 5. Player 1 tries illegal move (moving rook through pawn)
                print("[E2E] Player 1 tries illegal move Ra3...")
                await ws1.send(json.dumps({"action": "move", "san": "Ra3"}))
                err = json.loads(await ws1.recv())
                print(f"[E2E] Player 1 received expected error: {err}")
                assert err["event"] == "error"

                # 6. Mutual Draw Offer and Accept
                print("[E2E] Player 1 offers draw...")
                await ws1.send(json.dumps({"action": "offer_draw"}))
                draw_offered = json.loads(await ws2.recv())
                print(f"[E2E] Player 2 received draw offer: {draw_offered}")
                assert draw_offered["event"] == "draw_offered"

                print("[E2E] Player 2 accepts draw...")
                await ws2.send(json.dumps({"action": "accept_draw"}))

                game_over1 = json.loads(await ws1.recv())
                game_over2 = json.loads(await ws2.recv())
                print(f"[E2E] Game over broadcasted: {game_over1}")
                assert game_over1["event"] == "game_over" and game_over1["result"] == "Draw"
                assert game_over2["event"] == "game_over" and game_over2["result"] == "Draw"

        print("\n========================================================")
        print(" [SUCCESS] Live End-to-End WebSocket Test Passed!      ")
        print("========================================================\n")
    finally:
        server_proc.terminate()
        server_proc.wait(timeout=5)

if __name__ == "__main__":
    asyncio.run(main())
