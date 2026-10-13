#!/usr/bin/env python3
"""
Test script verifying:
1. Multiple concurrent games running in isolated rooms on the C++ chess_server simultaneously.
2. Disconnection and safe reconnection of a player mid-game using their signed identity token,
   including preventing an unauthorized stranger (seat-hijacking) and resuming move play.
"""

import asyncio
import base64
import hashlib
import hmac
import json
import subprocess
import time
import sys
import websockets

PORT = 9077
SECRET = "chess_secret_dev_key"
WS_BASE = f"ws://127.0.0.1:{PORT}"

def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")

def make_token(room_code: str, user_id: str, display_name: str, color: str) -> str:
    header = '{"alg":"HS256","typ":"JWT"}'
    claims = {
        "room_code": room_code,
        "user_id": user_id,
        "display_name": display_name,
        "color": color,
        "initial_seconds": 0,
        "increment_seconds": 0,
        "exp": int(time.time()) + 3600
    }
    h_b64 = b64url(header.encode("utf-8"))
    p_b64 = b64url(json.dumps(claims).encode("utf-8"))
    to_sign = f"{h_b64}.{p_b64}"
    sig = hmac.new(SECRET.encode("utf-8"), to_sign.encode("utf-8"), hashlib.sha256).digest()
    return f"{to_sign}.{b64url(sig)}"

async def test_concurrent_multi_rooms():
    print("\n" + "=" * 60)
    print("TEST 1: MULTIPLE CONCURRENT GAMES AT THE SAME TIME")
    print("=" * 60)

    # Room 1: King's Pawn Game (e4, e5, Nf3, Nc6)
    # Room 2: Queen's Gambit (d4, d5, c4, c6)
    room1_code = "ROOM01"
    room2_code = "ROOM02"

    t1_white = make_token(room1_code, "user-alice", "Alice", "white")
    t1_black = make_token(room1_code, "user-bob", "Bob", "black")

    t2_white = make_token(room2_code, "user-charlie", "Charlie", "white")
    t2_black = make_token(room2_code, "user-dave", "Dave", "black")

    print("[Multi-Room] Connecting 4 players into 2 distinct rooms concurrently...")
    async with websockets.connect(f"{WS_BASE}/ws/play?room={room1_code}&token={t1_white}") as ws_r1_w, \
               websockets.connect(f"{WS_BASE}/ws/play?room={room1_code}&token={t1_black}") as ws_r1_b, \
               websockets.connect(f"{WS_BASE}/ws/play?room={room2_code}&token={t2_white}") as ws_r2_w, \
               websockets.connect(f"{WS_BASE}/ws/play?room={room2_code}&token={t2_black}") as ws_r2_b:

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

    room_code = "RECON1"
    token_alice = make_token(room_code, "user-alice", "Alice", "white")
    token_bob = make_token(room_code, "user-bob", "Bob", "black")
    token_mallory = make_token(room_code, "user-mallory", "Mallory", "white")

    print("[Reconnect] Starting Game in room RECON1...")
    # Alice connects as White
    ws_alice = await websockets.connect(f"{WS_BASE}/ws/play?room={room_code}&token={token_alice}")
    # Bob connects as Black
    ws_bob = await websockets.connect(f"{WS_BASE}/ws/play?room={room_code}&token={token_bob}")

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
    async with websockets.connect(f"{WS_BASE}/ws/play?room={room_code}&token={token_mallory}") as ws_mallory:
        mallory_joined = json.loads(await ws_mallory.recv())
        mallory_started = json.loads(await ws_mallory.recv())
        print(f"  Mallory response: {mallory_joined}")
        assert mallory_joined["role"] == "spectator", f"Security violation: Mallory took seat! {mallory_joined}"
        print("✓ Security check passed: Mallory was relegated to SPECTATOR. Seat preserved for Alice!")

    # Now Alice reconnects with her token!
    print("\n[Reconnect] 🔄 Alice reconnects with her signed token...")
    async with websockets.connect(f"{WS_BASE}/ws/play?room={room_code}&token={token_alice}") as ws_alice_reconnected:
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
    server_proc = subprocess.Popen(
        ["./build/bin/chess_server", "--port", str(PORT), "--host", "127.0.0.1"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
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
