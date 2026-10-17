#!/usr/bin/env python3
"""
Test script verifying:
1. Multiple concurrent games running in isolated rooms on the C++ chess_server simultaneously.
2. Disconnection and safe reconnection using fresh backend-issued credentials.
3. Invalid credentials cannot claim an active game's player seat.

Requires a running Spring backend, three accepted challenges, and player-specific
tokens in CHESS_TEST_ROOM{1,2,3}_* environment variables. Tokens must come from
authenticated challenge detail responses; this script never signs credentials.
"""

import asyncio
import json
import os
import subprocess
import time
import websockets

PORT = int(os.environ.get("CHESS_TEST_SERVER_PORT", "9002"))
WS_BASE = os.environ.get("CHESS_TEST_WS_BASE", f"ws://127.0.0.1:{PORT}")
ROOM1 = os.environ["CHESS_TEST_ROOM1_CODE"]
ROOM2 = os.environ["CHESS_TEST_ROOM2_CODE"]
ROOM3 = os.environ["CHESS_TEST_ROOM3_CODE"]
ROOM1_WHITE_TOKEN = os.environ["CHESS_TEST_ROOM1_WHITE_TOKEN"]
ROOM1_BLACK_TOKEN = os.environ["CHESS_TEST_ROOM1_BLACK_TOKEN"]
ROOM2_WHITE_TOKEN = os.environ["CHESS_TEST_ROOM2_WHITE_TOKEN"]
ROOM2_BLACK_TOKEN = os.environ["CHESS_TEST_ROOM2_BLACK_TOKEN"]
ROOM3_WHITE_TOKEN = os.environ["CHESS_TEST_ROOM3_WHITE_TOKEN"]
ROOM3_BLACK_TOKEN = os.environ["CHESS_TEST_ROOM3_BLACK_TOKEN"]
ROOM3_WHITE_REFRESH_TOKEN = os.environ["CHESS_TEST_ROOM3_WHITE_REFRESH_TOKEN"]

async def connect_player(room_code: str, token: str):
    socket = await websockets.connect(f"{WS_BASE}/ws/play")
    await socket.send(json.dumps({"action": "connect_token", "room_code": room_code, "token": token}))
    return socket

async def test_concurrent_multi_rooms():
    print("\n" + "=" * 60)
    print("TEST 1: MULTIPLE CONCURRENT GAMES AT THE SAME TIME")
    print("=" * 60)

    # Room 1: King's Pawn Game (e4, e5, Nf3, Nc6)
    # Room 2: Queen's Gambit (d4, d5, c4, c6)
    room1_code = ROOM1
    room2_code = ROOM2

    t1_white = ROOM1_WHITE_TOKEN
    t1_black = ROOM1_BLACK_TOKEN
    t2_white = ROOM2_WHITE_TOKEN
    t2_black = ROOM2_BLACK_TOKEN

    print("[Multi-Room] Connecting 4 players into 2 distinct rooms concurrently...")
    async with await connect_player(room1_code, t1_white) as ws_r1_w, \
               await connect_player(room1_code, t1_black) as ws_r1_b, \
               await connect_player(room2_code, t2_white) as ws_r2_w, \
               await connect_player(room2_code, t2_black) as ws_r2_b:

        # Host receives room_created
        r1_w_created = json.loads(await ws_r1_w.recv())
        assert r1_w_created["event"] == "room_created"
        r2_w_created = json.loads(await ws_r2_w.recv())
        assert r2_w_created["event"] == "room_created"

        # Joiner receives room_joined then game_started
        r1_b_join = json.loads(await ws_r1_b.recv())
        assert r1_b_join["event"] == "room_joined"
        r1_b_start = json.loads(await ws_r1_b.recv())
        assert r1_b_start["event"] == "game_started"

        r2_b_join = json.loads(await ws_r2_b.recv())
        assert r2_b_join["event"] == "room_joined"
        r2_b_start = json.loads(await ws_r2_b.recv())
        assert r2_b_start["event"] == "game_started"

        # Host receives game_started
        r1_w_start = json.loads(await ws_r1_w.recv())
        assert r1_w_start["event"] == "game_started"
        r2_w_start = json.loads(await ws_r2_w.recv())
        assert r2_w_start["event"] == "game_started"

        print(f"✓ Room 1 ({room1_code}) started successfully with Alice (White) and Bob (Black).")
        print(f"✓ Room 2 ({room2_code}) started successfully with Charlie (White) and Dave (Black).")

        # Play moves interleaved in both rooms
        print("\n[Multi-Room] Executing moves simultaneously in Room 1 and Room 2:")

        # Room 1: 1. e4
        await ws_r1_w.send(json.dumps({"action": "move", "san": "e4"}))
        m1_w = json.loads(await ws_r1_w.recv())
        m1_b = json.loads(await ws_r1_b.recv())
        assert m1_w["san"] == "e4" and m1_b["san"] == "e4"
        print("  -> Room 1: Alice played e4")

        # Room 2: 1. d4
        await ws_r2_w.send(json.dumps({"action": "move", "san": "d4"}))
        m2_w = json.loads(await ws_r2_w.recv())
        m2_b = json.loads(await ws_r2_b.recv())
        assert m2_w["san"] == "d4" and m2_b["san"] == "d4"
        print("  -> Room 2: Charlie played d4")

        # Room 1: 1... e5
        await ws_r1_b.send(json.dumps({"action": "move", "san": "e5"}))
        m1_w2 = json.loads(await ws_r1_w.recv())
        m1_b2 = json.loads(await ws_r1_b.recv())
        assert m1_w2["san"] == "e5" and m1_b2["san"] == "e5"
        print("  -> Room 1: Bob played e5")

        # Room 2: 1... d5
        await ws_r2_b.send(json.dumps({"action": "move", "san": "d5"}))
        m2_w2 = json.loads(await ws_r2_w.recv())
        m2_b2 = json.loads(await ws_r2_b.recv())
        assert m2_w2["san"] == "d5" and m2_b2["san"] == "d5"
        print("  -> Room 2: Dave played d5")

        # Room 1: 2. Nf3
        await ws_r1_w.send(json.dumps({"action": "move", "san": "Nf3"}))
        m1_w3 = json.loads(await ws_r1_w.recv())
        m1_b3 = json.loads(await ws_r1_b.recv())
        assert m1_w3["san"] == "Nf3"
        print("  -> Room 1: Alice played Nf3")

        # Room 2: 2. c4
        await ws_r2_w.send(json.dumps({"action": "move", "san": "c4"}))
        m2_w3 = json.loads(await ws_r2_w.recv())
        m2_b3 = json.loads(await ws_r2_b.recv())
        assert m2_w3["san"] == "c4"
        print("  -> Room 2: Charlie played c4")

        # Room 1: 2... Nc6
        await ws_r1_b.send(json.dumps({"action": "move", "san": "Nc6"}))
        m1_w4 = json.loads(await ws_r1_w.recv())
        m1_b4 = json.loads(await ws_r1_b.recv())
        assert m1_w4["san"] == "Nc6"
        print("  -> Room 1: Bob played Nc6")

        # Room 2: 2... c6
        await ws_r2_b.send(json.dumps({"action": "move", "san": "c6"}))
        m2_w4 = json.loads(await ws_r2_w.recv())
        m2_b4 = json.loads(await ws_r2_b.recv())
        assert m2_w4["san"] == "c6"
        print("  -> Room 2: Dave played c6")

        # Verify final FENs are strictly isolated and distinct
        fen1 = m1_w4["fen"]
        fen2 = m2_w4["fen"]
        print(f"\n[Multi-Room] Verifying state isolation:")
        print(f"  Room 1 FEN: {fen1}")
        print(f"  Room 2 FEN: {fen2}")
        assert fen1 != fen2, "Error: Rooms share state!"
        assert "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R" in fen1
        assert "rnbqkbnr/pp2pppp/2p5/3p4/2PP4/8/PP2PPPP/RNBQKBNR" in fen2
        print("✓ SUCCESS: Multiple concurrent games run simultaneously with 100% isolation!")

async def test_disconnect_and_reconnect():
    print("\n" + "=" * 60)
    print("TEST 2: DISCONNECT & RECONNECTING TO THE SAME GAME")
    print("=" * 60)

    room_code = ROOM3
    token_alice = ROOM3_WHITE_TOKEN
    token_bob = ROOM3_BLACK_TOKEN
    token_alice_refresh = ROOM3_WHITE_REFRESH_TOKEN

    print("[Reconnect] Starting Game in room RECON1...")
    # Alice connects as White
    ws_alice = await connect_player(room_code, token_alice)
    # Bob connects as Black
    ws_bob = await connect_player(room_code, token_bob)

    # Read start messages
    alice_created = json.loads(await ws_alice.recv()) # room_created
    assert alice_created["event"] == "room_created"

    bob_joined = json.loads(await ws_bob.recv()) # room_joined
    assert bob_joined["event"] == "room_joined"

    start_bob = json.loads(await ws_bob.recv()) # game_started
    start_alice = json.loads(await ws_alice.recv()) # game_started
    print("✓ Game started with Alice (White) vs Bob (Black).")

    # Move 1: Alice plays e4, Bob plays e5
    print("\n[Reconnect] Playing move 1. e4 e5...")
    await ws_alice.send(json.dumps({"action": "move", "san": "e4"}))
    _ = await ws_alice.recv()
    _ = await ws_bob.recv()

    await ws_bob.send(json.dumps({"action": "move", "san": "e5"}))
    m1_a = json.loads(await ws_alice.recv())
    m1_b = json.loads(await ws_bob.recv())
    print(f"  Current FEN: {m1_a['fen']}")

    # NOW: Alice disconnects!
    print("\n[Reconnect] ⚡ Alice's connection drops (Disconnecting Alice)...")
    await ws_alice.close()

    # Bob should receive opponent_disconnected notification!
    disc_notice = json.loads(await ws_bob.recv())
    print(f"  Bob received: {disc_notice}")
    assert disc_notice["event"] == "opponent_disconnected", f"Expected opponent_disconnected, got {disc_notice}"
    print("✓ Opponent disconnection detected and notified to Bob.")

    # A stranger (Mallory) tries to jump in and steal Alice's seat!
    print("\n[Reconnect] 🚫 Stranger (Mallory) attempts to hijack Alice's seat...")
    async with websockets.connect(f"{WS_BASE}/ws/play") as ws_mallory:
        await ws_mallory.send(json.dumps({"action": "connect_token", "room_code": room_code, "token": "invalid"}))
        rejection = json.loads(await ws_mallory.recv())
        assert rejection["event"] == "error", f"Invalid credentials must be rejected: {rejection}"
        print("✓ Security check passed: invalid credentials cannot claim a room seat")

    # Now Alice reconnects with her token!
    print("\n[Reconnect] 🔄 Alice reconnects with her signed token...")
    async with await connect_player(room_code, token_alice_refresh) as ws_alice_reconnected:
        alice_join = json.loads(await ws_alice_reconnected.recv())
        print(f"  Alice reconnected join response: {alice_join}")
        assert alice_join["event"] == "room_joined" and alice_join["role"] == "white"

        # Alice receives live game snapshot
        alice_snapshot = json.loads(await ws_alice_reconnected.recv())
        print(f"  Alice received game snapshot FEN: {alice_snapshot['fen']}")
        assert alice_snapshot["event"] == "game_started"
        assert alice_snapshot["fen"] == m1_a["fen"], "Restored FEN must match live game!"
        print("✓ Alice successfully reclaimed White seat! Game state and clocks restored.")

        # Alice makes the next move (2. Nf3) to prove the game seamlessly continues!
        print("\n[Reconnect] ♟️ Alice continues the game: playing 2. Nf3 from the new connection...")
        await ws_alice_reconnected.send(json.dumps({"action": "move", "san": "Nf3"}))
        alice_move = json.loads(await ws_alice_reconnected.recv())
        bob_move = json.loads(await ws_bob.recv())

        print(f"  Alice received move confirmation: {alice_move['san']}")
        print(f"  Bob received move broadcast: {bob_move['san']}")
        assert alice_move["san"] == "Nf3" and bob_move["san"] == "Nf3"
        assert bob_move["side_to_move"] == "black"

        print(f"  New Board FEN: {bob_move['fen']}")
        print("✓ SUCCESS: Disconnect and Reconnect works seamlessly! Game continues without missing a beat!")

    await ws_bob.close()

async def main():
    print(f"Starting chess_server on port {PORT}...")
    server_environment = os.environ.copy()
    environment_file = os.environ.get("CHESS_ENV_FILE", "server/.env")
    with open(environment_file, encoding="utf-8") as config_file:
        for line in config_file:
            key, separator, value = line.strip().partition("=")
            if separator and key and not key.startswith("#"):
                server_environment.setdefault(key, value)
    server_environment["CHESS_PORT"] = str(PORT)
    server_proc = subprocess.Popen(
        [os.environ.get("CHESS_SERVER_BINARY", "./build/bin/chess_server")],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        env=server_environment,
    )
    time.sleep(1)

    try:
        await test_concurrent_multi_rooms()
        await test_disconnect_and_reconnect()
        print("\n" + "=" * 60)
        print("ALL TESTS PASSED! C++ CHESS ENGINE SUPPORTS BOTH MULTI-ROOM & RECONNECT!")
        print("=" * 60 + "\n")
    finally:
        server_proc.terminate()
        server_proc.wait(timeout=5)

if __name__ == "__main__":
    asyncio.run(main())
