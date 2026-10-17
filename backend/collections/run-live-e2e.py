import asyncio
import base64
import hashlib
import hmac
import json
import os
import time
import urllib.request
import uuid
from pathlib import Path

import websockets


BASE_URL = os.environ.get("CHESS_E2E_BASE_URL", "http://localhost:8080")
SOCKJS_BASE_URL = os.environ.get("CHESS_E2E_SOCKJS_URL", "ws://localhost:8080/ws/000")
ENGINE_URL = os.environ.get("CHESS_E2E_ENGINE_URL", "ws://localhost:9002")
FRONTEND_ORIGIN = os.environ.get("CHESS_E2E_ORIGIN", "http://localhost:5173")
PASSWORD = os.environ["CHESS_E2E_PASSWORD"]
WHITE_USERNAME = os.environ["CHESS_E2E_WHITE_USERNAME"]
BLACK_USERNAME = os.environ["CHESS_E2E_BLACK_USERNAME"]


def post_json(path, payload, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE_URL + path, json.dumps(payload).encode(), headers, method="POST")
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read())


def login(username):
    response = post_json("/api/auth/login", {"identifier": username, "password": PASSWORD})
    return response["data"]["accessToken"]


def stomp_frame(command, headers=None, body=""):
    lines = [command]
    lines.extend(f"{key}:{value}" for key, value in (headers or {}).items())
    return "\n".join(lines) + "\n\n" + body + "\0"


def token_matches_engine_secret(token):
    values = dict(line.split("=", 1) for line in Path("server/.env").read_text().splitlines() if "=" in line)
    secret = values["CHESS_JWT_SECRET"].encode()
    header, payload, signature = token.split(".")
    signed = f"{header}.{payload}".encode()
    expected = base64.urlsafe_b64encode(hmac.new(secret, signed, hashlib.sha256).digest()).decode().rstrip("=")
    claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
    return hmac.compare_digest(expected, signature), claims


async def sockjs_connect(token):
    socket = await websockets.connect(SOCKJS_BASE_URL + "/" + str(uuid.uuid4()) + "/websocket",
                                      origin=FRONTEND_ORIGIN, open_timeout=10)
    if await asyncio.wait_for(socket.recv(), timeout=5) != "o":
        raise RuntimeError("SockJS transport did not open")
    connect = stomp_frame("CONNECT", {
        "accept-version": "1.2",
        "host": "localhost",
        "Authorization": "Bearer " + token,
    })
    await socket.send(json.dumps([connect]))
    await receive_frame(socket, "CONNECTED", 10)
    for index, destination in enumerate(("/user/queue/challenges", "/user/queue/errors", "/user/queue/presence")):
        await socket.send(json.dumps([stomp_frame("SUBSCRIBE", {
            "id": str(index), "destination": destination, "ack": "auto",
        })]))
    return socket


async def receive_frame(socket, command, timeout=10):
    async with asyncio.timeout(timeout):
        while True:
            packet = await socket.recv()
            if packet == "h":
                continue
            if not packet.startswith("a"):
                continue
            for frame in json.loads(packet[1:]):
                if frame.startswith(command + "\n") or frame == command:
                    return frame


async def receive_event(socket, timeout=10):
    frame = await receive_frame(socket, "MESSAGE", timeout)
    body = frame.split("\n\n", 1)[1].rstrip("\0")
    return json.loads(body)


async def send_stomp(socket, destination, payload):
    frame = stomp_frame("SEND", {
        "destination": destination,
        "content-type": "application/json",
    }, json.dumps(payload))
    await socket.send(json.dumps([frame]))


async def wait_for_event_type(socket, event_type, timeout=10):
    async with asyncio.timeout(timeout):
        while True:
            event = await receive_event(socket, timeout)
            if event.get("eventType") == event_type:
                return event
            if event.get("errorCode"):
                raise RuntimeError(f"Unexpected STOMP error: {event['errorCode']}")


async def wait_for_error(socket, error_code, timeout=10):
    async with asyncio.timeout(timeout):
        while True:
            event = await receive_event(socket, timeout)
            if event.get("errorCode") == error_code:
                return event


async def run():
    white_token = login(WHITE_USERNAME)
    black_token = login(BLACK_USERNAME)
    white = await sockjs_connect(white_token)
    black = await sockjs_connect(black_token)
    try:
        await send_stomp(white, "/app/challenge.send", {
            "toUsername": WHITE_USERNAME,
            "challengerSide": "WHITE",
            "initialTimeSeconds": 300,
            "incrementSeconds": 0,
        })
        await wait_for_error(white, "SELF_CHALLENGE", 5)
        print("STOMP self-challenge: typed error delivered; socket remains open")

        await send_stomp(white, "/app/challenge.send", {
            "toUsername": BLACK_USERNAME,
            "challengerSide": "WHITE",
            "initialTimeSeconds": 4,
            "incrementSeconds": 0,
        })
        await wait_for_error(white, "VALIDATION_FAILED", 5)
        await send_stomp(white, "/app/challenge.send", {
            "toUsername": BLACK_USERNAME,
            "challengerSide": "WHITE",
            "initialTimeSeconds": 300,
        })
        await wait_for_error(white, "INVALID_TIME_CONTROL", 5)
        await send_stomp(black, "/app/challenge.accept", {"challengeId": 9223372036854775807})
        await wait_for_error(black, "CHALLENGE_NOT_FOUND", 5)
        print("STOMP invalid time controls and unknown challenge: typed errors; sessions remained open")

        await send_stomp(white, "/app/challenge.send", {
            "toUsername": BLACK_USERNAME,
            "challengerSide": "WHITE",
            "initialTimeSeconds": 300,
            "incrementSeconds": 0,
        })
        invitation = await wait_for_event_type(black, "INVITATION_RECEIVED")
        challenge_id = invitation["challengeId"]
        await wait_for_event_type(white, "INVITATION_SENT")
        await send_stomp(black, "/app/challenge.accept", {"challengeId": challenge_id})
        white_match = await wait_for_event_type(white, "MATCH_ASSIGNED")
        black_match = await wait_for_event_type(black, "MATCH_ASSIGNED")
        for match in (white_match, black_match):
            valid_signature, claims = token_matches_engine_secret(match["token"])
            print(f"Engine token signature={valid_signature}, room_match={claims.get('room_code') == match.get('roomCode')}, expires_in={claims.get('exp', 0) - int(time.time())}s")
        print(f"STOMP challenge {challenge_id}: assigned room and per-player tokens")

        white_socket = await websockets.connect(white_match["connectUrl"], open_timeout=10)
        black_socket = await websockets.connect(black_match["connectUrl"], open_timeout=10)
        try:
            async def wait_cpp(socket, event_name, timeout=10):
                async with asyncio.timeout(timeout):
                    while True:
                        message = json.loads(await socket.recv())
                        if message.get("event") == event_name:
                            return message
                        if message.get("event") == "error":
                            raise RuntimeError("C++ server rejected action: " + message.get("message", "unknown error"))

            async def wait_cpp_error(socket, timeout=5):
                async with asyncio.timeout(timeout):
                    while True:
                        message = json.loads(await socket.recv())
                        if message.get("event") == "error":
                            return message

            await wait_cpp(white_socket, "game_started")
            await wait_cpp(black_socket, "game_started")
            await black_socket.send(json.dumps({"action": "move", "san": "e5"}))
            out_of_turn = await wait_cpp_error(black_socket)
            assert "turn" in out_of_turn.get("message", "").lower(), out_of_turn
            await white_socket.send(json.dumps({"action": "move", "san": "e9"}))
            illegal_move = await wait_cpp_error(white_socket)
            assert "move" in illegal_move.get("message", "").lower(), illegal_move
            print("C++ invalid and out-of-turn moves: error events returned; match remained playable")
            for socket, san in ((white_socket, "f3"), (black_socket, "e5"), (white_socket, "g4"), (black_socket, "Qh4#")):
                await socket.send(json.dumps({"action": "move", "san": san}))
                await asyncio.gather(wait_cpp(white_socket, "move_made"), wait_cpp(black_socket, "move_made"))
            white_over, black_over = await asyncio.gather(
                wait_cpp(white_socket, "game_over"), wait_cpp(black_socket, "game_over"),
            )
            assert white_over.get("reason_code") == "CHECKMATE", white_over
            assert len(white_over.get("moves", [])) == 4, white_over
            print("C++ Fool's Mate: both players received 4 moves and CHECKMATE game-over")
        finally:
            await white_socket.close()
            await black_socket.close()
    finally:
        await white.close()
        await black.close()


asyncio.run(run())
